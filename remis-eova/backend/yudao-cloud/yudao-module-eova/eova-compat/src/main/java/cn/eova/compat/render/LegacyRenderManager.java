/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.render.RenderManager} 的等价接缝（<b>W1 子集</b>）。
 *
 * <p>旧 {@code RenderManager} 是"渲染工厂 + 模板引擎 + 视图路径"的全局单例，
 * 由 JFinal 启动时装配。新栈由 Spring Boot 装配，故此处只保留<b>注入点</b>：
 * {@link #setRenderFactory(LegacyRenderFactory)} 由宿主装配调用，
 * {@link #getRenderFactory()} 供 {@code Controller} 使用。</p>
 *
 * <p><b>为什么是接口 + 注入点而不是空壳：</b>本类只做"持有并转发"，
 * 与已落地的 {@code CacheServices} / {@code EovaGateways} 同一形态 ——
 * 未注入时<b>明确报错</b>（不静默给一个"看似能用"的实现，那会让
 * {@code toInt} 解析失败时抛出携带 null 渲染的异常，把问题推到更难定位的地方）。</p>
 */
public final class LegacyRenderManager {

    private static volatile LegacyRenderFactory renderFactory;

    private LegacyRenderManager() {
    }

    /**
     * 装配渲染工厂（由宿主启动时调用）。
     *
     * @param factory 渲染工厂
     */
    public static void setRenderFactory(LegacyRenderFactory factory) {
        renderFactory = factory;
    }

    /**
     * 取渲染工厂。
     *
     * @return 渲染工厂
     * @throws IllegalStateException 未装配时
     */
    public static LegacyRenderFactory getRenderFactory() {
        LegacyRenderFactory f = renderFactory;
        if (f == null) {
            throw new IllegalStateException(
                    "未装配渲染工厂（LegacyRenderManager.setRenderFactory）—— 请由宿主在启动时注入");
        }
        return f;
    }

    /**
     * 是否已装配（供判据与诊断使用）。
     *
     * @return 已装配返回 true
     */
    public static boolean isReady() {
        return renderFactory != null;
    }

    /**
     * 清空装配（仅供测试隔离）。
     */
    public static void clear() {
        renderFactory = null;
    }

}
