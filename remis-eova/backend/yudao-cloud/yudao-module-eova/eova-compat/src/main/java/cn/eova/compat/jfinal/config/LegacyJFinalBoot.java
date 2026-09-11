/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

/**
 * jfinal 5.2.6 {@code com.jfinal.core.JFinal.init()} 的等价接缝（**引导驱动**）。
 *
 * <p>ported from: com.jfinal.core.JFinal#init（jfinal 5.2.6 制品）
 *
 * <p><b>为什么必须有它：</b>第 82～83 轮把 W3 接缝族立起来之后，{@code EovaConfig} 的
 * 6 个 {@code config*} 与 {@code onStart} 仍然<b>没有调用者</b> —— 在旧栈里是 jfinal 的
 * {@code JFinal.init()} 按固定顺序逐个回调。本类就是那个驱动，
 * 它同时是<b>将来 Spring 宿主唯一需要调用的入口</b>
 * （宿主只负责把各容器准备好，顺序与 jfinal 一致）。</p>
 *
 * <p><b>顺序逐条取自旧字节码</b>（{@code JFinal.init()} 的执行序列）：
 * {@code configConstant(constants)} → {@code configRoute(routes)} → {@code configEngine(engine)}
 * → {@code configPlugin(plugins)} → {@code configInterceptor(interceptors)}
 * → {@code configHandler(handlers)} → 常量/上传配置下发（{@code initUploadConfig}：把
 * {@code baseUploadPath/maxPostSize/encoding} 推给 {@code UploadConfig}）→ 插件 start
 * → {@code onStart()}。<b>本驱动把"插件 start"留成显式步骤</b>
 * （jfinal 里由 {@code Plugins} 逐个 {@code start()}；新栈的插件多为 Spring bean，故宿主可选择跳过）。</p>
 */
public class LegacyJFinalBoot {

    /** 本次引导产出的容器（宿主/判据读取） */
    private final LegacyConstants constants = new LegacyConstants();

    private final LegacyRoutes routes = new LegacyRoutes() {
        @Override
        public void config() {
            // 旧 jfinal 的 Routes 由 config.configRoute(me) 填充；本驱动不依赖 Routes.config()
        }
    };  // LegacyRoutes 是抽象类（config() 抽象），此处给空实现

    private final LegacyEngine engine = new LegacyEngine();

    private final LegacyPlugins plugins = new LegacyPlugins();

    private final LegacyInterceptors interceptors = new LegacyInterceptors();

    private final LegacyHandlers handlers = new LegacyHandlers();

    private boolean started;

    /**
     * 取常量容器。
     *
     * @return 常量容器
     */
    public LegacyConstants getConstants() {
        return constants;
    }

    /**
     * 取路由容器。
     *
     * @return 路由容器
     */
    public LegacyRoutes getRoutes() {
        return routes;
    }

    /**
     * 取引擎配置面。
     *
     * @return 引擎配置面
     */
    public LegacyEngine getEngine() {
        return engine;
    }

    /**
     * 取插件容器。
     *
     * @return 插件容器
     */
    public LegacyPlugins getPlugins() {
        return plugins;
    }

    /**
     * 取拦截器容器。
     *
     * @return 拦截器容器
     */
    public LegacyInterceptors getInterceptors() {
        return interceptors;
    }

    /**
     * 取 handler 容器。
     *
     * @return handler 容器
     */
    public LegacyHandlers getHandlers() {
        return handlers;
    }

    /**
     * 是否已执行过引导。
     *
     * @return 是否已引导
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * 执行引导（等价 jfinal {@code JFinal.init()} 的配置阶段 + {@code onStart}）。
     *
     * @param config 配置类（旧栈为 {@code EovaConfig}）
     */
    public void init(LegacyJFinalConfig config) {
        config.configConstant(constants);
        config.configRoute(routes);
        config.configEngine(engine);
        config.configPlugin(plugins);
        config.configInterceptor(interceptors);
        config.configHandler(handlers);

        // initUploadConfig：把常量推给上传配置（旧 JFinal.initUploadConfig 逐行等价）
        cn.eova.compat.jfinal.upload.LegacyUploadConfig.init(
                constants.getBaseUploadPath(), constants.getMaxPostSize(), constants.getEncoding());

        // 插件 start（旧 JFinal 在此逐个 start；新栈插件多为 Spring bean，宿主可跳过该步）
        for (cn.eova.compat.jfinal.plugin.LegacyPlugin plugin : plugins.getPluginList()) {
            plugin.start();
        }

        config.onStart();
        config.afterJFinalStart();
        started = true;
    }

    /**
     * 停止（等价 jfinal 停机序列：{@code beforeJFinalStop} → 插件 stop → {@code onStop}）。
     *
     * @param config 配置类
     */
    public void stop(LegacyJFinalConfig config) {
        config.beforeJFinalStop();
        for (cn.eova.compat.jfinal.plugin.LegacyPlugin plugin : plugins.getPluginList()) {
            plugin.stop();
        }
        config.onStop();
        started = false;
    }

}
