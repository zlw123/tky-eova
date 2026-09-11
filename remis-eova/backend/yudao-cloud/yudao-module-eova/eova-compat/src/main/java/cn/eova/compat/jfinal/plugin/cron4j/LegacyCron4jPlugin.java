/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin.cron4j;

import java.util.ArrayList;
import java.util.List;

import cn.eova.compat.jfinal.plugin.LegacyPlugin;
import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.Task;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.plugin.cron4j.Cron4jPlugin} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.plugin.cron4j.Cron4jPlugin}（jfinal 5.2.6）。
 * EOVA 的 {@code EovaCronPlugin}（202 行）用它管理"按 taskId 动态增删的定时任务"。</p>
 *
 * <p><b>语义逐条取自旧字节码：</b>
 * <ol>
 *   <li>{@code addTask(...)} <b>只收集</b>（{@code taskInfoList.add(new TaskInfo(...))}），
 *       <b>不</b>立即调度 —— 调度发生在 {@code start()}。</li>
 *   <li>{@code start()}：已启动则直接返回 {@code true}；否则
 *       先对全部 TaskInfo 调 {@code schedule()}，再对全部调 {@code start()}，
 *       最后置 {@code isStarted = true} 并返回 {@code true}。</li>
 *   <li>{@code stop()}：对全部 TaskInfo 调 {@code stop()}，置 {@code isStarted = false}，返回 {@code true}。</li>
 *   <li>{@code TaskInfo.schedule()}：仅当 {@code enable} 时建 {@link Scheduler}；
 *       依次判 {@code task instanceof Runnable} → {@code scheduler.schedule(cron, (Runnable) task)}；
 *       否则 {@code task instanceof Task} → {@code scheduler.schedule(cron, (Task) task)}；
 *       都不匹配则把 scheduler 置 null 并抛
 *       {@code IllegalStateException("Task 必须是 Runnable、ITask、ProcessTask 或者 Task 类型")}
 *       （<b>消息逐字</b>，含它并未真正处理的 ITask/ProcessTask —— 旧实现如此，照抄）；
 *       最后 {@code scheduler.setDaemon(daemon)}。</li>
 *   <li>{@code TaskInfo.start()}：仅当 {@code enable} 时 {@code scheduler.start()}。</li>
 *   <li>{@code TaskInfo.stop()}：仅当 {@code enable} 时，
 *       若 {@code task instanceof ITask} 先调其 {@code stop()}，再 {@code scheduler.stop()}。</li>
 * </ol>
 *
 * <p><b>宿主替换：</b>旧实现实现 jfinal 的 {@code IPlugin}，此处实现
 * {@link LegacyPlugin}（同形接口）。cron4j 本身（{@code it.sauronsoftware.cron4j}）
 * 是真实第三方制品，与旧工程同版本（2.2.5），故直接复用。</p>
 */
public class LegacyCron4jPlugin implements LegacyPlugin {

    /** 任务信息列表（addTask 只往里加，start 时才调度） */
    private final List<TaskInfo> taskInfoList = new ArrayList<>();

    /** 是否已启动（volatile，旧实现如此） */
    private volatile boolean isStarted = false;

    /**
     * 取任务信息列表。
     *
     * @return 列表
     */
    public List<TaskInfo> getTaskInfoList() {
        return taskInfoList;
    }

    /**
     * 无参构造。
     */
    public LegacyCron4jPlugin() {
    }

    /**
     * 添加任务（Runnable）。
     *
     * @param cron    cron 表达式
     * @param task    任务
     * @param daemon  是否守护
     * @param enable  是否启用
     * @return this
     */
    public LegacyCron4jPlugin addTask(String cron, Runnable task, boolean daemon, boolean enable) {
        taskInfoList.add(new TaskInfo(cron, task, daemon, enable));
        return this;
    }

    /**
     * 添加任务（Runnable，非守护）。
     *
     * @param cron   cron 表达式
     * @param task   任务
     * @param enable 是否启用
     * @return this
     */
    public LegacyCron4jPlugin addTask(String cron, Runnable task, boolean enable) {
        return addTask(cron, task, false, enable);
    }

    /**
     * 添加任务（cron4j Task）。
     *
     * @param cron   cron 表达式
     * @param task   任务
     * @param daemon 是否守护
     * @param enable 是否启用
     * @return this
     */
    public LegacyCron4jPlugin addTask(String cron, Task task, boolean daemon, boolean enable) {
        taskInfoList.add(new TaskInfo(cron, task, daemon, enable));
        return this;
    }

    /**
     * 添加任务（cron4j Task，非守护）。
     *
     * @param cron   cron 表达式
     * @param task   任务
     * @param enable 是否启用
     * @return this
     */
    public LegacyCron4jPlugin addTask(String cron, Task task, boolean enable) {
        return addTask(cron, task, false, enable);
    }

    /**
     * 启动：先对全部任务 schedule，再全部 start。
     *
     * @return true
     */
    @Override
    public boolean start() {
        if (isStarted) {
            return true;
        }
        for (TaskInfo ti : taskInfoList) {
            ti.schedule();
        }
        for (TaskInfo ti : taskInfoList) {
            ti.start();
        }
        isStarted = true;
        return true;
    }

    /**
     * 停止全部任务。
     *
     * @return true
     */
    @Override
    public boolean stop() {
        for (TaskInfo ti : taskInfoList) {
            ti.stop();
        }
        isStarted = false;
        return true;
    }

    /**
     * 单个任务的信息与生命周期（对应旧实现的内部类）——<b>public static</b>，
     * 以便 {@link #getTaskInfoList()} 的返回类型可用。
     */
    public static class TaskInfo {

        Scheduler scheduler;

        String cron;

        Object task;

        boolean daemon;

        boolean enable;

        /**
         * 构造。
         *
         * @param cron   cron 表达式
         * @param task   任务
         * @param daemon 是否守护
         * @param enable 是否启用
         */
        TaskInfo(String cron, Object task, boolean daemon, boolean enable) {
            this.cron = cron;
            this.task = task;
            this.daemon = daemon;
            this.enable = enable;
        }

        /**
         * 建调度器并注册任务（规则见外层类注释）。
         */
        void schedule() {
            if (enable) {
                scheduler = new Scheduler();
                if (task instanceof Runnable) {
                    scheduler.schedule(cron, (Runnable) task);
                } else if (task instanceof Task) {
                    scheduler.schedule(cron, (Task) task);
                } else {
                    scheduler = null;
                    throw new IllegalStateException(
                            "Task 必须是 Runnable、ITask、ProcessTask 或者 Task 类型");
                }
                scheduler.setDaemon(daemon);
            }
        }

        /**
         * 启动调度（仅 enable 时）。
         */
        void start() {
            if (enable) {
                scheduler.start();
            }
        }

        /**
         * 停止任务与调度（仅 enable 时；ITask 先 stop）。
         */
        void stop() {
            if (enable) {
                if (task instanceof LegacyITask) {
                    ((LegacyITask) task).stop();
                }
                scheduler.stop();
            }
        }

    }

}
