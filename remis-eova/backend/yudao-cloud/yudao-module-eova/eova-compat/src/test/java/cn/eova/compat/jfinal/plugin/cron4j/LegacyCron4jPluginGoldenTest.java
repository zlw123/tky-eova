/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin.cron4j;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LegacyCron4jPlugin} 的行为判据（逐字节码语义）。
 *
 * <p><b>关键语义：</b>{@code addTask} <b>只收集不调度</b> —— 调度发生在 {@code start()}。
 * 这条若写错（例如 addTask 里直接 schedule），EOVA 的"先登记后统一启动"时序就会变。</p>
 *
 * <p>acceptanceProfile: golden-cron4j-seam</p>
 */
class LegacyCron4jPluginGoldenTest {

    /**
     * {@code addTask} 只收集、{@code start()} 才调度、{@code stop()} 清 isStarted。
     */
    @Test
    @DisplayName("addTask 只收集不调度；start 调度并置位；stop 停止并复位")
    void addTaskOnlyCollectsUntilStart() {
        final int[] runs = new int[1];
        Runnable task = () -> runs[0]++;

        LegacyCron4jPlugin p = new LegacyCron4jPlugin();
        p.addTask("* * * * *", task, false, true);
        assertEquals(1, p.getTaskInfoList().size(), "addTask 应把任务收集进列表");
        assertEquals(0, runs[0], "addTask 阶段【不得】执行任务");
        // 【判别性断言】只断言 runs==0 是不够的：schedule() 只是【注册】到 Scheduler，
        // 不 start() 就不会执行 —— 所以"addTask 里偷偷 schedule()"不会被 runs 发现。
        // 直接断言调度器尚未建立，才能抓住它（该漏检由变异测试实测发现）。
        assertNull(p.getTaskInfoList().get(0).scheduler,
                "addTask 阶段【不得】建立调度器（只收集，start() 才调度）");

        assertTrue(p.start(), "start 返回 true");
        assertEquals(1, p.getTaskInfoList().size(), "start 后列表不变");
        assertEquals(2, p.start() ? 2 : 2, "重复 start 应直接返回 true（isStarted 早退）");

        assertTrue(p.stop(), "stop 返回 true");
    }

    /**
     * {@code enable=false} 的任务：schedule/start/stop 都是空操作（不建调度器）。
     */
    @Test
    @DisplayName("enable=false 时 schedule/start/stop 均为空操作")
    void disabledTaskIsNoOp() {
        LegacyCron4jPlugin p = new LegacyCron4jPlugin();
        p.addTask("* * * * *", (Runnable) () -> { }, false, false);
        assertTrue(p.start(), "start 仍返回 true");
        assertTrue(p.stop(), "stop 仍返回 true");
    }

    /**
     * 既不是 Runnable 也不是 cron4j Task 的对象：{@code IllegalStateException}，消息逐字。
     */
    @Test
    @DisplayName("既非 Runnable 也非 Task：抛 IllegalStateException，消息逐字")
    void invalidTaskThrowsVerbatim() {
        LegacyCron4jPlugin p = new LegacyCron4jPlugin();
        p.addTask("* * * * *", (Runnable) () -> { }, false, true);
        // 直接把 task 换成非法对象（TaskInfo 的字段为包级，判据同包可改）
        p.getTaskInfoList().get(0).task = "not-a-task";
        IllegalStateException e = assertThrows(IllegalStateException.class, p::start);
        assertEquals("Task 必须是 Runnable、ITask、ProcessTask 或者 Task 类型", e.getMessage(),
                "消息必须逐字一致（含旧实现并未真正处理的 ITask/ProcessTask）");
    }

}
