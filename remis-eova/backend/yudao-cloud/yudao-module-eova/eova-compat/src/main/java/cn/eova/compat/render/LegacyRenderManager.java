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

    /** 单例（旧 RenderManager.me() 的返回物；本接缝无实例状态，故复用同一实例） */
    private static final LegacyRenderManager INSTANCE = new LegacyRenderManager();

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
     * 单例访问器（旧 {@code RenderManager.me()}）。
     *
     * <p><b>本方法存在的理由：</b>旧源码里 {@code RenderManager.me().getRenderFactory()...}
     * 是常见写法（如 {@code ApiRouterHandler} 的 {@code renderJson}）。
     * 本接缝原先只提供静态 {@link #getRenderFactory()}，那些调用点就得被改写成另一形状 ——
     * 于是"port 保形"这条优势就丢了，且每次遇到都要单独声明一次适配。
     * 补上 {@code me()} 后，旧调用点可以<b>原样保留</b>。</p>
     *
     * <p>旧实现的 {@code me()} 返回一个进程级单例；本接缝的状态本就是静态的
     * （{@code renderFactory} 为 static），故返回同一个实例即可，
     * <b>不引入新的可变状态</b>。</p>
     *
     * @return 单例
     */
    public static LegacyRenderManager me() {
        return INSTANCE;
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
