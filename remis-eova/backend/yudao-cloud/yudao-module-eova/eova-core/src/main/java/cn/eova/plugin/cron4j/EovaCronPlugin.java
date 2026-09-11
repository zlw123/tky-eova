package cn.eova.plugin.cron4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.eova.tools.x;
import cn.eova.common.utils.io.ClassUtil;
import cn.eova.common.utils.util.JsonUtil;
import cn.eova.model.Task;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.compat.jfinal.plugin.LegacyPlugin;
import cn.eova.compat.jfinal.plugin.cron4j.LegacyCron4jPlugin;
import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.TaskExecutionContext;
import it.sauronsoftware.cron4j.TaskExecutor;

/**
 * <p>ported from: cn.eova.plugin.cron4j.EovaCronPlugin
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>定时任务插件（202 行）：按 taskId 动态增删任务，用 cron4j 调度</li>
 *   <li>【已声明适配】IPlugin -> LegacyPlugin；Cron4jPlugin -> LegacyCron4jPlugin；Kv -> LegacyKv</li>
 *   <li>注意：cron4j 的 Scheduler/TaskExecutionContext/TaskExecutor 是真实第三方制品（2.2.5），沿用；</li>
 *   <li>    cn.eova.model.Task 是 EOVA 自己的模型（已 port），【不得】与 cron4j 的 Task 混同</li>
 *   <li>CPS（taskId -> 插件）为静态注册表，增删语义属既有行为</li>
 * </ol>
 */
/**
 * Eova 定时调度
 * <pre>
 * 使用更轻量级的Cron4j 64KB
 * 如果要支持秒级调度, 可以直接使用 Executors.newSingleThreadScheduledExecutor();
 * 如果要支持毫秒级调度, 建议使用EovaMQ
 * 表达式(秒 分 时 日 月 星期)
 * 分钟（0-59）
 * 小时（0-23）
 * 日（1-31）
 * 月（1-12）
 * 星期（0-7），其中 0 和 7 都表示星期日
 * </pre>
 * @author Jieven
 */
public class EovaCronPlugin implements LegacyPlugin {

    /**
     * 任务列表(多插件多实例管理, 可以分别管控)
     */
    private static final Map<Integer, LegacyCron4jPlugin> CPS = new HashMap<>();

    /**
     * 获取任务
     * @param taskId 任务ID
     * @return
     */
    public static LegacyCron4jPlugin get(Integer taskId) {
        return CPS.get(taskId);
    }

    /**
     * 移除任务
     * @param taskId
     * @return
     */
    public static boolean remove(Integer taskId) {
        LegacyCron4jPlugin cp = CPS.get(taskId);
        try {
            if (cp != null) {
                cp.stop();
                CPS.remove(taskId);
                return true;
            }
        } catch (Exception e) {
            x.log.error("任务停止异常, 可能任务已经停止:TaskId=" + taskId);
        }
        return false;
    }

    @Override
    public boolean start() {
        initTask();
        return true;
    }

    @Override
    public boolean stop() {
        CPS.forEach((k, v) -> {
            v.stop();
//            try {
//            } catch (Exception e) {
//
//            }
        });
        return true;
    }

    /**
     * 初始化加载任务
     */
    public void initTask() {
        String jobBiz = x.conf.get("job.biz", "all");
        List<Task> tasks = Task.dao.findTask();
        x.log.info("---------------------------< EovaTask {} >-----------------------------------", jobBiz);
        for (Task task : tasks) {
            Integer taskId = task.getInt("id");
            String name = task.getStr("name");
            String className = task.getStr("clazz");
            String cronExp = task.getStr("exp");
            boolean state = task.getBoolean("state");
            String params = task.getStr("params");

            String clazz_ = String.format("%-40s", className);
            LegacyKv kv = createTask(taskId, name, className, cronExp, params, state);
            String msg = kv.getStr("msg");
            x.log.info("[{}] {} {} >> {}", kv.getStr("state"), clazz_, name, x.isEmpty(msg) ? cronExp : msg);
        }
        x.log.info("------------------------------------------------------------------------------");
    }

    /**
     * 创建任务
     * @param taskId
     * @param name
     * @param className
     * @param cronExp
     * @param params
     * @param state
     * @return
     */
    public static LegacyKv createTask(Integer taskId, String name, String className, String cronExp, String params, boolean state) {
        LegacyKv result;

        try {

            // 获取任务参数
            LegacyKv kv = JsonUtil.toKv(params);

            LegacyCron4jPlugin cp = new LegacyCron4jPlugin();

            // 创建该类的实例
            BaseTask o = ClassUtil.newClass(className);
            if (o == null) {
                result = LegacyKv.of("state", "异常").set("msg", String.format("实例化任务异常:%s", className));
            } else {
                o.setParam(kv);
                // 添加任务
                cp.addTask(cronExp, o, false, x.toBoolean(state));
                CPS.put(taskId, cp);// 启动任务
                if (state) {
                    cp.start();
                    result = LegacyKv.of("state", "启用").set("msg", "");
                } else {
                    result = LegacyKv.of("state", "禁用").set("msg", "");
                }
            }

        } catch (Exception e) {
            // e.printStackTrace();
            result = LegacyKv.of("state", "异常").set("msg", e.getMessage());
        }
        return result;
    }

    /**
     * 立即运行一次
     * @param taskClass
     * @param kv
     */
    public static void runTask(String taskClass, LegacyKv kv) {
        try {
            BaseTask o = ClassUtil.newClass(taskClass);
            if (o == null) {
                throw new RuntimeException("任务类不存在:" + taskClass);
            }
            TaskExecutionContext ec = new TaskExecutionContext() {
                @Override
                public Scheduler getScheduler() {
                    return null;
                }

                @Override
                public TaskExecutor getTaskExecutor() {
                    return null;
                }

                @Override
                public void setStatusMessage(String message) {

                }

                @Override
                public void setCompleteness(double completeness) {

                }

                @Override
                public void pauseIfRequested() {

                }

                @Override
                public boolean isStopped() {
                    return false;
                }
            };
            o.setParam(kv);
            o.execute(ec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
