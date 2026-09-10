/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.activerecord;

import java.util.Map;
import java.util.Set;

/**
 * jfinal {@code com.jfinal.plugin.activerecord.IContainerFactory} 的等价物。
 *
 * <p><b>为什么必须补：</b>enjoy 5.3.0 不提供 {@code com.jfinal.plugin.activerecord.*}
 * 的任何类型（它只带模板引擎与 `kit` 包）。而 <b>EOVA 自带两个容器工厂实现</b>：
 * <ul>
 *   <li>{@code cn.eova.ext.jfinal.LinkedCaseInsensitiveContainerFactory}
 *       —— 保序 + 大小写不敏感</li>
 *   <li>{@code cn.eova.ext.jfinal.EovaContainerFactory}
 *       —— 大小写不敏感</li>
 * </ul>
 * 二者都只依赖本接口（三个方法），故补出接口即可让它们<b>逐字节 port</b>。
 *
 * <p>ported from: com.jfinal.plugin.activerecord.IContainerFactory（第三方制品）
 * <br>source artifact: com.jfinal:jfinal:5.2.6
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 *
 * <p><b>为什么这件小事是 R4 的核心：</b>SP6 实测 EOVA 运行在
 * {@code CaseInsensitiveContainerFactory(true)} 上，即"列名一律小写、查找大小写不敏感"
 * 是 {@code Record}/{@code Model} 的既有契约；计划 R4 明确把
 * "Record/Kv 语义不等价"列为高风险（曾估 111 个文件受影响）。
 * 本接口 + 两个实现正是该契约的落点，故虽小但必须<b>连同金标</b>一起 port。
 */
public interface IContainerFactory {

    /**
     * 取 attrs 容器（Model 用）
     */
    Map<String, Object> getAttrsMap();

    /**
     * 取 columns 容器（Record 用）
     */
    Map<String, Object> getColumnsMap();

    /**
     * 取"已修改字段"集合容器
     */
    Set<String> getModifyFlagSet();
}
