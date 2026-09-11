/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils;

import java.util.concurrent.atomic.AtomicInteger;

import cn.eova.compat.jfinal.server.LegacyServerHandle;
import cn.eova.config.EovaSystem;
import cn.eova.tools.x;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code EovaSystem}(23) 与 {@code UndertowUtil}(89)（第 66 轮 port）的判据。
 *
 * <p><b>为什么这两个"宿主生命周期"单元也 port 而不是登记不 port：</b>
 * 它们承载的是<b>功能</b>（开发期热重启、devMode 判定、主动触发热加载），
 * 而不是纯粹的宿主装配 —— 砍掉就是丢功能。旧栈真正需要的老宿主能力只有
 * {@code isDevMode()} 与 {@code restart()} 两个方法，故收敛成
 * {@link LegacyServerHandle} 接缝，由新宿主注入；上层代码原样保留。</p>
 */
class UndertowUtilGoldenTest {

    /** 记录式服务句柄替身 */
    static final class ProbeServer implements LegacyServerHandle {

        /** restart 调用次数 */
        final AtomicInteger restarts = new AtomicInteger();

        /** isDevMode 的返回值 */
        boolean devMode;

        @Override
        public boolean isDevMode() {
            return devMode;
        }

        @Override
        public void restart() {
            restarts.incrementAndGet();
        }
    }

    /** 用例前的服务句柄与 devMode 配置（用后还原） */
    private LegacyServerHandle beforeServer;

    /** 用例前的 devMode 配置 */
    private String beforeDevMode;

    /** 保存现场并清空句柄 */
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        beforeServer = EovaSystem.server;
        beforeDevMode = x.conf.get("devMode");
        EovaSystem.server = null;
    }

    /** 还原现场 */
    @AfterEach
    void tearDown() {
        EovaSystem.server = beforeServer;
        x.conf.addConfig("devMode", beforeDevMode == null ? "false" : beforeDevMode);
    }

    @Test
    @DisplayName("EovaSystem.server：默认 null（未注入即未配置），类型是宿主接缝而非 UndertowServer")
    void serverFieldIsSeamTypedAndNullByDefault() {
        assertNull(EovaSystem.server, "默认必须为 null —— 这是 restart() 第一道守卫的依据");
        java.lang.reflect.Field f;
        try {
            f = EovaSystem.class.getField("server");
        } catch (NoSuchFieldException e) {
            throw new AssertionError("EovaSystem.server 必须是 public 字段（旧契约）", e);
        }
        assertEquals(LegacyServerHandle.class, f.getType(), "字段类型必须已声明的宿主接缝");
        assertTrue(java.lang.reflect.Modifier.isStatic(f.getModifiers()), "必须是 static");
    }

    @Test
    @DisplayName("isServer：跟随 EovaSystem.server 是否为 null")
    void isServerFollowsHandle() {
        assertFalse(UndertowUtil.isServer(), "未注入句柄 ⇒ false");
        EovaSystem.server = new ProbeServer();
        assertTrue(UndertowUtil.isServer(), "注入后 ⇒ true");
    }

    @Test
    @DisplayName("restart()：无句柄 / 服务非 devMode / 配置未开 devMode 三道守卫都不得重启")
    void restartGuards() throws Exception {
        // ① 无句柄：直接返回
        x.conf.addConfig("devMode", "true");
        UndertowUtil.restart();
        Thread.sleep(800);

        // ② 有句柄但 isDevMode()==false：配置【开着】devMode，故只有这一道守卫能拦住它
        //    —— 断言前必须等待异步窗口，否则"立即断言"不具判别力（实测：去掉该守卫后
        //    判据仍然全绿，因为重启发生在断言之后）
        ProbeServer s = new ProbeServer();
        s.devMode = false;
        EovaSystem.server = s;
        x.conf.addConfig("devMode", "true");
        UndertowUtil.restart();
        Thread.sleep(800);
        assertEquals(0, s.restarts.get(), "服务非 devMode 时不得重启（等待异步窗口后仍为 0）");

        // ③ isDevMode()==true 但配置 devMode 未开
        s.devMode = true;
        x.conf.addConfig("devMode", "false");
        UndertowUtil.restart();
        Thread.sleep(800);
        assertEquals(0, s.restarts.get(), "配置未开 devMode 时不得重启（等待异步窗口后仍为 0）");
    }

    @Test
    @DisplayName("restart()：两道 devMode 都开时，异线程延迟后调用 handle.restart()")
    void restartInvokesHandleAsynchronously() throws Exception {
        ProbeServer s = new ProbeServer();
        s.devMode = true;
        EovaSystem.server = s;
        x.conf.addConfig("devMode", "true");

        long t0 = System.currentTimeMillis();
        UndertowUtil.restart();
        // 异线程 + 500ms 延迟 ⇒ 立即返回，随后才重启
        assertEquals(0, s.restarts.get(), "restart() 必须立即返回（异步），不得阻塞调用方");
        for (int i = 0; i < 60 && s.restarts.get() == 0; i++) {
            Thread.sleep(50);
        }
        assertEquals(1, s.restarts.get(), "最终必须调用一次 handle.restart()");
        assertTrue(System.currentTimeMillis() - t0 >= 400, "旧实现有 500ms 延迟（避免 Web 来不及返回）");
    }

    @Test
    @DisplayName("hotSwap()：未配置 hotswap.trigger 时什么都不做（不写文件）")
    void hotSwapNoopWithoutTrigger() {
        x.conf.addConfig("hotswap.trigger", "");
        UndertowUtil.hotSwap();
        // 无异常即通过：旧实现在 trigger 为空时直接跳过
    }
}
