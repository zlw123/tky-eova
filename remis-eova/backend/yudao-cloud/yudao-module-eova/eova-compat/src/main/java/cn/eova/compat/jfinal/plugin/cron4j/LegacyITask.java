/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin.cron4j;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.plugin.cron4j.ITask} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.plugin.cron4j.ITask}（jfinal 5.2.6）。</p>
 *
 * <p><b>契约：{@code ITask extends Runnable}，另加一个 {@code stop()}</b>
 * —— 即"可被调度执行、且可被主动停止"的任务。</p>
 */
public interface LegacyITask extends Runnable {

    /**
     * 停止任务。
     */
    void stop();

}
