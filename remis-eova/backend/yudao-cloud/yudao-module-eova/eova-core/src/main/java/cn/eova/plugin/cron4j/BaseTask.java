package cn.eova.plugin.cron4j;

import java.util.Calendar;

import cn.eova.tools.x;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.compat.jfinal.kit.LegacyLogKit;
import it.sauronsoftware.cron4j.Task;
import it.sauronsoftware.cron4j.TaskExecutionContext;

/**
 * <p>ported from: cn.eova.plugin.cron4j.BaseTask
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>定时任务基类；cron4j 与旧工程同版本（2.2.5）</li>
 *   <li>Kv->LegacyKv、LogKit->LegacyLogKit</li>
 * </ol>
 */
public abstract class BaseTask extends Task {

    private LegacyKv kv = null;

    /**
     * 设置任务参数
     * @param kv
     */
    public void setParam(LegacyKv kv) {
        this.kv = kv;
    }

    /**
     * 获取任务参数
     * @return
     */
    public LegacyKv getParam() {
        return this.kv;
    }

    @Override
    public void execute(TaskExecutionContext context) throws RuntimeException {
        String name = this.getClass().getName();
        try {
            process(context);
        } catch (Exception e) {
            x.log.error(name + ":" + e.getMessage(), e);
        }
    }

    /**
     * 业务处理
     *
     * @param context 任务上下文
     */
    protected abstract void process(TaskExecutionContext context) throws Exception;


    /**
     * 睡眠模式(24H)
     * eg. 2-8 1点到7点 为睡眠时间
     * @param start 开始睡眠时间点
     * @param end 停止睡眠时间点
     * @return 是否为睡眠状态
     */
    protected static boolean sleepMode(int start, int end) {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);

        boolean flag = start <= hour && hour <= end;
        if (flag) {
            LegacyLogKit.info(String.format("Sleeping[%s-%s]: Now Hour=%s", start, end, hour));
        }
        return flag;
    }
}