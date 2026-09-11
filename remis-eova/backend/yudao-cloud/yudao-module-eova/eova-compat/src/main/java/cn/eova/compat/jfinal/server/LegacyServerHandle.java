/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.server;

/**
 * jfinal-undertow 的 {@code com.jfinal.server.undertow.UndertowServer} 的等价接缝
 * （<b>仅覆盖 EOVA 实际使用的两个成员</b>）。
 *
 * <p>{@code ported from} {@code com.jfinal.server.undertow.UndertowServer}（jfinal-undertow 3.0）。</p>
 *
 * <p><b>为什么这样做而不是把这两个单元登记为"不 port"：</b>
 * {@code EovaSystem.server} 与 {@code UndertowUtil.restart()} 承载的<b>是功能</b>
 * （开发期热重启 / 是否处于 devMode 的判定），把它们砍掉就是"丢功能"。
 * 而它们真正需要的老宿主能力只有两个方法 —— 故收敛成接口，由新宿主
 * （Spring Boot 启动器）在启动时注入实现。这样：
 * ① 上层代码<b>原样保留</b>（含 {@code restart()} 的三道守卫与 500ms 延迟）；
 * ② 新宿主若未注入，{@code server == null} ⇒ {@code isServer() == false} ⇒
 * {@code restart()} 打印"启动类未正常配置, 无法重启"并返回 —— 与旧行为一致（旧栈未配置时同样如此）。</p>
 *
 * <p><b>已声明的适配：</b>旧栈是 Undertow 嵌入式容器（{@code UndertowServer.restart()} 走
 * Undertow 的容器重启）；新栈是 Spring Boot（等价能力由 spring-boot-devtools 的
 * restart 机制提供）。实现类属<b>宿主编排</b>，不在本次代码级 port 范围内；
 * 阶段 1 的验收需核对"开发期重启"这一开发工作流在新栈确有对应物（见 R4 §验收）。</p>
 */
public interface LegacyServerHandle {

    /**
     * 是否处于开发模式（旧 {@code UndertowServer.isDevMode()}）。
     *
     * <p>{@code UndertowUtil.restart()} 用它作为第二道守卫：非 devMode 时
     * 打印"Undertow 未开启热加载, 请手工重启服务!"并返回。</p>
     *
     * @return 是开发模式
     */
    boolean isDevMode();

    /**
     * 重启当前服务（旧 {@code UndertowServer.restart()}）。
     */
    void restart();
}
