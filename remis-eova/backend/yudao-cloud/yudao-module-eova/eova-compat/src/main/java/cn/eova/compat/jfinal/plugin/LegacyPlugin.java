/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.plugin.IPlugin} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.plugin.IPlugin}（jfinal 5.2.6）。</p>
 *
 * <p><b>为什么需要：</b>EOVA 有若干"启动时装载/关闭时释放"的组件
 * （{@code EovaConfigPlugin}、{@code EovaCronPlugin}、{@code EovaModPlugin}）实现本接口。
 * 旧栈由 jfinal 的 {@code Plugins} 列表在启动/关停时统一驱动；
 * 新栈由 Spring Boot 装配驱动 —— 但<b>组件自身的 start/stop 语义必须保留</b>，
 * 故把接口本身固化为接缝。</p>
 *
 * <p><b>返回值语义（逐字保真）：</b>旧 {@code IPlugin} 的 {@code start()}/{@code stop()}
 * 返回 {@code boolean}，jfinal 侧不对 {@code false} 做任何处理 ——
 * 即"返回 false 并不会阻止启动"。该"返回值被忽略"的既有形态原样保留，
 * 不得在此改成抛异常。</p>
 */
public interface LegacyPlugin {

    /**
     * 启动（旧实现返回值被 jfinal 忽略）
     *
     * @return 是否成功
     */
    boolean start();

    /**
     * 关停（旧实现返回值被 jfinal 忽略）
     *
     * @return 是否成功
     */
    boolean stop();

}
