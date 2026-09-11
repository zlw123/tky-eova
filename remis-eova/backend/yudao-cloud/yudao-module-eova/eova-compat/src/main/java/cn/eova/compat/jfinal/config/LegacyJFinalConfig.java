/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

import cn.eova.compat.jfinal.kit.LegacyProp;
import cn.eova.compat.jfinal.kit.LegacyPropKit;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.config.JFinalConfig} 的等价接缝。
 *
 * <p>ported from: com.jfinal.config.JFinalConfig（jfinal 5.2.6 制品）
 *
 * <p><b>为什么需要它：</b>旧 {@code EovaConfig extends JFinalConfig}，6 个 {@code config*}
 * 是<b>抽象</b>方法、{@code onStart}/{@code afterJFinalStart}/{@code onStop}/{@code beforeJFinalStop}
 * 是<b>空实现</b>。新栈要保留 {@code EovaConfig} 的形状（否则配置类无法逐文件 port），
 * 故把基类接缝出来；<b>真正调用它们的顺序</b>由引导驱动 {@code LegacyJFinalBoot} 决定
 * （等价 jfinal {@code JFinal.init()}），Spring 宿主将来调用的就是那个入口。</p>
 *
 * <p><b>逐条取自旧字节码：</b>字段 {@code protected Prop prop}（默认 null）；
 * {@code onStart}/{@code afterJFinalStart}/{@code onStop}/{@code beforeJFinalStop} 为空方法体；
 * {@code useFirstFound(String...)} = {@code prop = PropKit.useFirstFound(fileNames)} 并返回它；
 * {@code getProperty*} 家族委派给 {@code getProp()}，而 {@code getProp()} 在 prop 为 null 时
 * 抛 {@code IllegalStateException("PropKit.useFirstFound(...) has not been invoked")}
 * —— 该消息在本接缝逐字保留。</p>
 */
public abstract class LegacyJFinalConfig {

    /** 属性文件（旧实现 protected 字段） */
    protected LegacyProp prop;

    /**
     * 配置常量。
     *
     * @param me 常量容器
     */
    public abstract void configConstant(LegacyConstants me);

    /**
     * 配置路由。
     *
     * @param me 路由容器
     */
    public abstract void configRoute(LegacyRoutes me);

    /**
     * 配置模板引擎。
     *
     * @param me 引擎配置面
     */
    public abstract void configEngine(LegacyEngine me);

    /**
     * 配置插件。
     *
     * @param plugins 插件容器
     */
    public abstract void configPlugin(LegacyPlugins plugins);

    /**
     * 配置拦截器。
     *
     * @param me 拦截器容器
     */
    public abstract void configInterceptor(LegacyInterceptors me);

    /**
     * 配置 handler。
     *
     * @param me handler 容器
     */
    public abstract void configHandler(LegacyHandlers me);

    /** 启动后回调（旧实现空方法体） */
    public void onStart() {
    }

    /** JFinal 启动完成后回调（旧实现空方法体） */
    public void afterJFinalStart() {
    }

    /** 停止前回调（旧实现空方法体） */
    public void onStop() {
    }

    /** JFinal 停止前回调（旧实现空方法体） */
    public void beforeJFinalStop() {
    }

    /**
     * 加载第一个存在的属性文件（旧实现 {@code useFirstFound}）。
     *
     * @param fileNames 候选文件
     * @return 属性对象
     */
    public LegacyProp useFirstFound(String... fileNames) {
        prop = LegacyPropKit.useFirstFound(fileNames);
        return prop;
    }

    /**
     * 取属性对象（旧实现在未装配时抛消息逐字）。
     *
     * @return 属性对象
     */
    private LegacyProp getProp() {
        if (prop == null) {
            throw new IllegalStateException("PropKit.useFirstFound(...) has not been invoked");
        }
        return prop;
    }

    /**
     * 取属性。
     *
     * @param key 键
     * @return 值
     */
    public String getProperty(String key) {
        return getProp().get(key);
    }

    /**
     * 取属性（带默认值）。
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 值
     */
    public String getProperty(String key, String defaultValue) {
        return getProp().get(key, defaultValue);
    }

    /**
     * 取整型属性。
     *
     * @param key 键
     * @return 值
     */
    public Integer getPropertyToInt(String key) {
        return getProp().getInt(key);
    }

    /**
     * 取整型属性（带默认值）。
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 值
     */
    public Integer getPropertyToInt(String key, Integer defaultValue) {
        return getProp().getInt(key, defaultValue);
    }

    /**
     * 取布尔属性。
     *
     * @param key 键
     * @return 值
     */
    public Boolean getPropertyToBoolean(String key) {
        return getProp().getBoolean(key);
    }

    /**
     * 取布尔属性（带默认值）。
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 值
     */
    public Boolean getPropertyToBoolean(String key, Boolean defaultValue) {
        return getProp().getBoolean(key, defaultValue);
    }

}
