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

import com.jfinal.template.Directive;
import com.jfinal.template.source.ISourceFactory;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.template.Engine} 的等价接缝（**配置面**）。
 *
 * <p>ported from: com.jfinal.template.Engine（jfinal 5.2.6 制品；该类由 enjoy 制品提供，新栈同源）
 *
 * <p><b>为什么是"配置面"：</b>旧 {@code EovaConfig.configEngine(Engine me)} 只做四件事：
 * {@code setSourceFactory(new EovaRenderSourceFactory())}、
 * {@code addSharedMethod(new BaseSharedMethod())}、
 * {@code addDirective("json", JsonDirective.class)}、
 * （注释掉的 {@code addSharedFunction/addSharedObject}）。
 * 真正渲染模板的是 enjoy 引擎，新栈已由 {@code EnjoyTemplateRenderService} 承担
 * （它自己 {@code setBaseTemplatePath} + {@code FileSourceFactory}），
 * 故本接缝<b>只记录</b>这些注册项，供引导驱动与判据读取，<b>不</b>再启动第二个引擎。</p>
 *
 * <p><b>类型说明：</b>{@code ISourceFactory}/{@code Directive} 直接使用 enjoy 制品里的
 * {@code com.jfinal.template.*}（eova-compat 已依赖 enjoy ⇒ 无需另设接缝）。</p>
 */
public class LegacyEngine {

    /** 模板源工厂（EOVA 装 EovaRenderSourceFactory） */
    private ISourceFactory sourceFactory;

    /** 共享方法（EOVA 装 BaseSharedMethod） */
    private final List<Object> sharedMethods = new ArrayList<>();

    /** 指令：名字 → 类型（EOVA 装 "json" → JsonDirective） */
    private final Map<String, Class<? extends Directive>> directives = new LinkedHashMap<>();

    /** 共享模板函数（文件路径） */
    private final List<String> sharedFunctions = new ArrayList<>();

    /** 共享对象：名字 → 值 */
    private final Map<String, Object> sharedObjects = new LinkedHashMap<>();

    /**
     * 设置模板源工厂。
     *
     * @param sourceFactory 源工厂
     * @return this
     */
    public LegacyEngine setSourceFactory(ISourceFactory sourceFactory) {
        this.sourceFactory = sourceFactory;
        return this;
    }

    /**
     * 取模板源工厂。
     *
     * @return 源工厂；未设置为 null
     */
    public ISourceFactory getSourceFactory() {
        return sourceFactory;
    }

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
    public LegacyEngine addDirective(String name, Class<? extends Directive> directive) {
        directives.put(name, directive);
        return this;
    }

    /**
     * 取指令登记表。
     *
     * @return 名字 → 类型
     */
    public Map<String, Class<? extends Directive>> getDirectives() {
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
