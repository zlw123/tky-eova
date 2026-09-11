package cn.eova.service;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cn.eova.tools.x;
import cn.eova.common.Ds;
import cn.eova.hook.EovaHookType;
import cn.eova.hook.HookRegistry;
import cn.eova.model.MsgType;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;
import org.ttzero.excel.reader.ExcelReader;
import org.ttzero.excel.reader.Row;

/**
 * <p>ported from: cn.eova.service.ImportBiz
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>导入服务（162 行）：Excel 导入、按元数据落库、写导入日志 —— **退掉其已声明 stub**</li>
 * </ol>
 */
public class ImportBiz {
    // 导入执行线程池
    private final ExecutorService executor;

    public ImportBiz() {
        // 导入并发任务数量
        int importPoolSize = x.conf.getInt("import.pool.size", 5);
        // 导入队列长度
        int importQueueSize = x.conf.getInt("import.queue.size", 50);
//        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
        long keepAliveTime = 60L;

        // 队列大小可以根据实际情况调整，可以排队100个.
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(importQueueSize);

        // 初始化线程池，使用自定义线程工厂命名线程
        this.executor = new ThreadPoolExecutor(
                importPoolSize,
                importPoolSize,// 最大线程数
                keepAliveTime,// 线程存活时间 秒
                TimeUnit.SECONDS,// 时间单位 秒
                workQueue,// 工作队列
                new NamedThreadFactory("ImportWorker")
        );
        // 默认策略AbortPolicy 当所有线程都在忙碌并且工作队列已满时），则会触发拒绝策略。防止大量任务堆积OOM
    }

    /**
     * 开始执行导入任务
     *
     * @param uid 用户ID
     * @param code 导入业务编码
     * @param type 类型业务类型
     * @param path 导入文件名
     * @param log  导入日志
     */
    public void start(int uid, String code, String type, String path, EovaRecord log) {
        executor.submit(() -> execute(uid, code, type, path, log));
    }

    /**
     * 执行导入逻辑，支持中断处理
     */
    private void execute(int uid, String code, String type, String path, EovaRecord log) {
        Integer id = log.getInt("id");
        String work = Thread.currentThread().getName();
        String key = String.format("%s %s[%s]", work, code, id);
        try {
            x.log.info("{} 开始读取文件 {}", key, path);

            try (ExcelReader reader = ExcelReader.read(Paths.get(path))) {
                x.log.info("{} 导入数据行 {}", key, reader.sheet(0).getSize());

                // 按行读取第1个Sheet并打印
                Stream<Row> rows = reader.sheet(0).rows();
                Map<String, Object> header = reader.sheet(0).getHeader().toMap();

                ArrayList<EovaRecord> data = rows.skip(1) // 跳过表头行
                        .map(row -> new EovaRecord().put(row.toMap()))
                        .collect(Collectors.toCollection(ArrayList::new));

                // 执行用户授权钩子, 此处不方便处理用户业务, 由用户自定义实现钩子
                LegacyKv kv = LegacyKv.of("head", header).set("data", data).set("type", type).set("log", log);

                HookRegistry.getBiz(EovaHookType.IMPORT, code).invoke(kv);


                x.log.info("{} 导入成功 条数:{}", key, data.size());

                String msg = "导入成功条数:" + data.size();
                log.set("status", 2);
                log.set("success", msg);

                biz.msg.send(0, uid, "数据导入", msg, MsgType.OK);
            } catch (Exception ex) {
                x.log.info(String.format("%s 导入失败: %s", key, ex.getMessage()));
                log.set("status", 3);
                log.set("fail", ex.getMessage());
                biz.msg.send(0, uid, "数据导入", ex.getMessage(), MsgType.NO);
                ex.printStackTrace();
            } finally {
                // 更新导入记录
                log.set("end_time", new Date());
                EovaGateways.get(Ds.EOVA).update("eova_import", log);
            }
        } catch (Exception e) {
            x.log.info("{} 导入任务被中断", key);
            Thread.currentThread().interrupt(); // 重新设置中断标志
        }
        x.log.info("{} 导入任务执行完成", key);
    }

    /**
     * 关闭线程池，释放资源
     */
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 自定义线程工厂，用于为线程命名
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private int threadCount = 0;

        public NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + "-" + threadCount++);
            // 可选：设置线程优先级、守护状态等
            return thread;
        }
    }
}

// 需要做超时的, 可以在钩子内部做超时处理, 如下代码测试有效
// Future<?> future = executor.submit(() -> {});
//                try {
//                    // 等待任务完成，最多等待5分钟
//                    future.get(5, TimeUnit.SECONDS);
//                } catch (TimeoutException e) {
//                    x.log.info("{} 导入任务超时，正在取消任务...", key);
//                    future.cancel(true);
//                } catch (ExecutionException e) {
//                    x.log.info("{} 任务执行异常: {}", key, e.getCause().getMessage());
//                    e.printStackTrace();
//                } catch (InterruptedException e) {
//                    x.log.info("{} 当前线程被中断", key);
//                    Thread.currentThread().interrupt();
//                } catch (CancellationException e) {
//                    x.log.info("{} 任务已被取消", key);
//                }