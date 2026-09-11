/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.render.ViewType} 的等价接缝。
 *
 * <p>ported from: com.jfinal.render.ViewType（jfinal 5.2.6 制品）
 *
 * <p><b>逐条取自旧字节码：</b>三个常量，<b>顺序即 {@code values()} 顺序</b>：
 * {@code JFINAL_TEMPLATE}、{@code JSP}、{@code FREE_MARKER}；
 * jfinal 的 {@code Const.DEFAULT_VIEW_TYPE} 就是 {@code JFINAL_TEMPLATE}
 * （{@code Constants} 构造器里 {@code getstatic Const.DEFAULT_VIEW_TYPE} 实测）。</p>
 */
public enum LegacyViewType {

    /** jfinal Enjoy 模板（旧栈默认值） */
    JFINAL_TEMPLATE,

    /** JSP */
    JSP,

    /** FreeMarker */
    FREE_MARKER

}
