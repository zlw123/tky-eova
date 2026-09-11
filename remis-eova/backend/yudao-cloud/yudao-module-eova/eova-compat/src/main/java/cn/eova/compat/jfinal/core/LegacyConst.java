/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.core;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.core.Const} 的等价接缝（仅 EOVA 实际使用的 2 个常量）。
 *
 * <p>{@code ported from} {@code com.jfinal.core.Const}（jfinal 5.2.6）。</p>
 *
 * <p><b>方法/常量集口径：全树普查后只做被实际读取的 2 个</b> ——
 * 旧树对 jfinal {@code Const} 的全部用法只有 3 处（其中 1 处已注释）：
 * <ul>
 *   <li>{@code AppController:55} → {@link #JFINAL_VERSION}（写入返回给前端的 kv）
 *       —— 该值会出现在前端可见的 JSON 里，属<b>对外可见输出</b>；</li>
 *   <li>{@code SseKit:62} → {@link #DEFAULT_ENCODING}（SSE 响应的字符编码）</li>
 *   <li>{@code AppController:56} 已注释掉的 {@code DEFAULT_ENCODING}</li>
 * </ul>
 * jfinal {@code Const} 另有 20+ 个常量，EOVA <b>一个都没用</b>，故不引入。</p>
 *
 * <p><b>易错区分（我一开始搞错过）：</b>EOVA 代码里的 {@code Const.PAGESIZE}、
 * {@code Const.SYS_BIZ}、{@code Const.ALL_MENU} 等<b>不是</b> jfinal 的
 * {@code com.jfinal.core.Const}，而是 EOVA 自己的 {@code cn.eova.config.PageConst} /
 * {@code EovaConst}（源码里 import 的是后者）。用无边界正则统计
 * {@code Const\\.X} 会把 {@code PageConst.X} 的尾部一并算进来 —— 我因此一度把
 * jfinal {@code Const} 的面估算成 30+ 个常量。</p>
 *
 * <p>取值逐字取自旧字节码的 {@code ConstantValue}：{@code JFINAL_VERSION = "5.2.6"}、
 * {@code DEFAULT_ENCODING = "UTF-8"}。</p>
 */
public interface LegacyConst {

    /** jfinal 版本号（旧字节码 ConstantValue = "5.2.6"） */
    String JFINAL_VERSION = "5.2.6";

    /** 默认字符编码（旧字节码 ConstantValue = "UTF-8"） */
    String DEFAULT_ENCODING = "UTF-8";

}
