/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.render.HtmlRender} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.render.HtmlRender}（jfinal 5.2.6）。</p>
 *
 * <p>旧实现只有一个构造器 {@code HtmlRender(String)}，语义为
 * {@code super(text, ContentType.HTML)}，且<b>不</b>走 {@code ContentType.parse}
 * （字节码直接取静态常量）。由于 {@code ContentType.HTML.value() == "text/html"}，
 * 这里等价地用原始串 {@code "text/html"} 转发给两参构造器 ——
 * <b>不可</b>改成传 {@code "HTML"} 名，那会走 parse 路径而语义相同但路径不同。</p>
 */
public class LegacyHtmlRender extends LegacyTextRender {

    /**
     * 旧实现唯一构造器：以 {@code text/html} 输出文本。
     *
     * @param text 待输出 HTML 文本
     */
    public LegacyHtmlRender(String text) {
        super(text, "text/html");
    }

}
