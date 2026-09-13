/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * jfinal 5.2.6 的 {@code com.jfinal.template.Engine} 的等价接缝（**配置面**）。
 *
 * <p>ported from: com.jfinal.template.Engine（jfinal 5.2.6 制品；该类由 enjoy 制品提供，新栈同源）
 *
 * <p><b>为什么是"配置面"：</b>旧 {@code EovaConfig.configEngine(Engine me)} 只做四件事：
 * <s>{@code setSourceFactory(new EovaRenderSourceFactory())}</s>（★ r310：源工厂与引擎一起按口径授权摘除）、
 * {@code addSharedMethod(new BaseSharedMethod())}、
 * {@code addDirective("json", JsonDirective.class)}（★ r309：{@code JsonDirective} 已按授权删除，
 * {@code EovaConfig} 里这条注册同步移除 ⇒ 本映射在新栈为空）、
 * （注释掉的 {@code addSharedFunction/addSharedObject}）。
 * 真正渲染模板的是 enjoy 引擎；新栈的**页面渲染**已由 {@code LegacyPageRenderer}（极简渲染器，
 * 与 enjoy 逐字节等价）+ {@code LegacyTemplateRender}（接缝）承担。
 * ★ r310：引擎本身已按口径授权摘除（兜底实测 0 次 + 可达模板面逐面枚举）⇒ 本接缝现在只剩
 * "记录配置项"这一个职责（供引导驱动与判据读取），<b>源工厂</b>那一项随之消失。</p>
 *
 * <p><b>类型说明（r310 起）：</b>本接缝**不再引用任何 enjoy 类型** —— 原有的
 * {@code ISourceFactory}（源工厂）随引擎一起删除；指令类型退化为 {@code Class<?>}（纯记录项）。
 * 这也是"生产代码零 {@code com.jfinal.*} 依赖"的一部分（判据：{@code EnjoyUsageInventoryTest}）。</p>
 */
public class LegacyEngine {

    /** 共享方法（EOVA 装 BaseSharedMethod） */
    private final List<Object> sharedMethods = new ArrayList<>();

    /**
     * 指令：名字 → 类型。
     *
     * <p>★ r310：类型不再是 enjoy 的 {@code Directive} —— 引擎已摘除，"指令"在本接缝里只是**记录项**；
     * 旧 EOVA 唯一的指令（{@code "json" → JsonDirective}）也已于 r309 按授权删除 ⇒ 实际恒为空映射。</p>
     */
    private final Map<String, Class<?>> directives = new LinkedHashMap<>();

    /** 共享模板函数（文件路径） */
    private final List<String> sharedFunctions = new ArrayList<>();

    /** 共享对象：名字 → 值 */
    private final Map<String, Object> sharedObjects = new LinkedHashMap<>();

    /**
     * 追加共享方法对象。
     *
     * @param sharedMethod 共享方法对象
     * @return this
     */
    public LegacyEngine addSharedMethod(Object sharedMethod) {
        sharedMethods.add(sharedMethod);
        return this;
    }

    /**
     * 取共享方法对象列表。
     *
     * @return 列表
     */
    public List<Object> getSharedMethods() {
        return sharedMethods;
    }

    /**
     * 追加指令。
     *
     * @param name      指令名
     * @param directive 指令类型
     * @return this
     */
    public LegacyEngine addDirective(String name, Class<?> directive) {
        directives.put(name, directive);
        return this;
    }

    /**
     * 取指令登记表。
     *
     * @return 名字 → 类型
     */
    public Map<String, Class<?>> getDirectives() {
        return directives;
    }

    /**
     * 追加共享模板函数。
     *
     * @param fileName 模板路径
     * @return this
     */
    public LegacyEngine addSharedFunction(String fileName) {
        sharedFunctions.add(fileName);
        return this;
    }

    /**
     * 取共享模板函数列表。
     *
     * @return 列表
     */
    public List<String> getSharedFunctions() {
        return sharedFunctions;
    }

    /**
     * 追加共享对象。
     *
     * @param key   名字
     * @param value 值
     * @return this
     */
    public LegacyEngine addSharedObject(String key, Object value) {
        sharedObjects.put(key, value);
        return this;
    }

    /**
     * 取共享对象表。
     *
     * @return 名字 → 值
     */
    public Map<String, Object> getSharedObjects() {
        return sharedObjects;
    }

}
