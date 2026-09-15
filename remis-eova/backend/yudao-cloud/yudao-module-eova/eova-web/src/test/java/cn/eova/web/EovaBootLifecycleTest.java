/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.eova.compat.jfinal.config.LegacyJFinalBoot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.SmartLifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **DES-012 P2-U3 判据：启动/停机由 Spring 生命周期协议驱动**（P1 顺延单元，r332）。
 *
 * <p>U3 之前，整段启动副作用（档桥接 → `x.conf` 兜底 → 数据源/网关/元数据源 → `boot.init` →
 * 多数据源 → 缓存/渲染注入）写在 `@Bean legacyBoot(...)` 的**工厂方法体**里，停机靠 `@PreDestroy`。
 * 现在：工厂只创建实例；启动在 {@link LegacyWebBootstrap#start()}（phase = 0），
 * 路由索引在 {@link LegacyActionHandlerMapping#start()}（phase = 1），停机在 `stop()`。</p>
 *
 * <p><b>★ 相位语义是实测来的，不是记忆</b>：Spring 6.2 探针实测启动顺序
 * {@code [phase=1, phase=100, phase=MAX-1]} ⇒ **低 phase 先启动**；而 web 容器
 * （{@code WebServerStartStopLifecycle}）phase = {@code Integer.MAX_VALUE - 1} 最后启动
 * ⇒ 本编排的"启动 → 建索引 → 容器开始收请求"顺序成立。判据把这条不变量钉住。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EovaBootLifecycleTest {

    @Autowired
    private LegacyWebBootstrap bootstrap;

    @Autowired
    private LegacyActionHandlerMapping actionMapping;

    @Autowired
    private LegacyJFinalBoot legacyBoot;

    @Test
    @DisplayName("接线：宿主是 SmartLifecycle 且已按旧序列启动完成（boot.isStarted()）")
    void bootstrapIsLifecycleDrivenAndStarted() {
        assertTrue(bootstrap instanceof SmartLifecycle, "★ 宿主必须实现 SmartLifecycle（启动不再靠 @Bean 副作用）");
        assertNotNull(legacyBoot, "引导对象必须在容器里");
        assertTrue(legacyBoot.isStarted(), "★ 上下文就绪后 boot 必须已 init（否则一切请求 500）");
        assertTrue(bootstrap.isRunning(), "宿主 isRunning 必须为 true");
        assertTrue(actionMapping.isRunning(), "路由索引构建器 isRunning 必须为 true");
    }

    @Test
    @DisplayName("★ 相位不变量：宿主(0) < 路由索引(1) < web 容器(MAX-1)（实测低 phase 先启动）")
    void phasesPreserveStartupOrder() {
        assertEquals(0, bootstrap.getPhase(), "宿主启动相位");
        assertEquals(1, actionMapping.getPhase(), "路由索引相位（晚于宿主）");
        assertTrue(bootstrap.getPhase() < actionMapping.getPhase(),
                "★ 索引必须晚于 boot.init（否则建出空路由表）");
        assertTrue(actionMapping.getPhase() < Integer.MAX_VALUE - 1,
                "★ 两者都必须早于 web 容器（WebServerStartStopLifecycle phase = MAX-1，实测最后启动）");
    }

    @Test
    @DisplayName("行为：索引已就绪（真路由可命中）——启动序列真的跑完了")
    void routingIndexIsReadyAfterStartup() {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/meta/find");
        assertNotNull(actionMapping.getHandlerInternal(request),
                "★ 索引必须已建立（空表 ⇒ 全站 404）");
    }

    @Test
    @DisplayName("★ 反向：启动副作用不在 @Bean 工厂里（源码断言：init 只在 start() 中）")
    void initSideEffectsLiveInLifecycleStart() throws Exception {
        String src = Files.readString(
                new File("src/main/java/cn/eova/web/LegacyWebBootstrap.java").toPath(), StandardCharsets.UTF_8);

        // 工厂方法体：从 legacyBoot( 到其后的第一个 "this.boot = new LegacyJFinalBoot();"
        int factory = src.indexOf("public LegacyJFinalBoot legacyBoot(");
        assertTrue(factory > 0, "工厂方法必须存在");
        int factoryEnd = src.indexOf("return this.boot;", factory);
        String factoryBody = src.substring(factory, factoryEnd);
        assertTrue(!factoryBody.contains("boot.init("),
                "★ @Bean 工厂不得再做 boot.init（启动副作用必须归 SmartLifecycle#start()）");
        assertTrue(!factoryBody.contains("EovaGateways.register("),
                "★ 网关注册等启动副作用同样不得留在工厂里");

        // start() 里必须真的驱动 init
        Matcher m = Pattern.compile("public void start\\(\\) \\{(.*?)\\n    \\}", Pattern.DOTALL).matcher(src);
        assertTrue(m.find(), "必须存在 start() 方法");
        assertTrue(m.group(1).contains("boot.init("),
                "★ start() 必须真正执行 boot.init（否则启动只是换了个地方但没做）");
        assertTrue(m.group(1).contains("EovaGateways.register("), "★ 网关注册也必须在 start() 里");
    }
}
