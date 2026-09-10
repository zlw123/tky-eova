package cn.eova.plugin.cron4j;

import cn.eova.tools.x;
import cn.eova.compat.jfinal.kit.LegacyKv;
import it.sauronsoftware.cron4j.TaskExecutionContext;

/**
 * <p>ported from: cn.eova.plugin.cron4j.DemoTask
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>定时任务示例（22 行）：继承 BaseTask，用 cron4j 的 TaskExecutionContext</li>
 *   <li>cron4j 为真实外部依赖（it.sauronsoftware.cron4j:cron4j:2.2.5，与旧工程同版本）</li>
 *   <li>【已声明适配】com.jfinal.kit.Kv -> cn.eova.compat.jfinal.kit.LegacyKv（R37/R40）</li>
 * </ol>
 */
public class DemoTask extends BaseTask {

    @Override
    protected void process(TaskExecutionContext ac) throws Exception {
        System.out.println("Eova Task: 当前时间：" + x.time.formatNowTimes());

        // 复用Task, 干多件事, 例如 备份Task, 传入不同表, 进行备份.
        LegacyKv param = this.getParam();
        if (param != null) {
            System.out.println(param.getStr("type", "erp"));
        }

        // 夜间休眠, 节省体力
        sleepMode(18, 20);
    }
}