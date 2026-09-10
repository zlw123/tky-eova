/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.render.TextRender} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.render.TextRender}（jfinal 5.2.6）。</p>
 *
 * <p><b>三个构造器逐条照抄旧语义：</b>
 * <ol>
 *   <li>{@code (String)} → contentType 取 {@link #DEFAULT_CONTENT_TYPE}（{@code "text/plain"}），
 *       旧实现<b>不</b>走 parse，直接常量赋值；</li>
 *   <li>{@code (String, String)} → {@code LegacyContentType.parse(ct)}，命中则取其 value，
 *       <b>未命中保留原串</b>（旧实现三元表达式，无异常）；</li>
 *   <li>{@code (String, LegacyContentType)} → 直接取 {@code value()}。</li>
 * </ol>
 *
 * <p><b>{@link #render()} 的两处易错语义（逐条取自字节码，不得"顺手修正"）：</b>
 * <ul>
 *   <li>异常表覆盖范围<b>包含 {@code getContentType()} 与 {@code setContentType()} 本身</b>
 *       （字节码 from 2 to 61），故 {@code ct} 为 null 时的 NPE 会被包成
 *       {@link LegacyRenderException} 抛出，而不是逸出。因此 {@code ct} 声明在 try 内。</li>
 *   <li><b>成功路径不关闭 writer</b>（字节码 {@code goto 86} 直接 return），
 *       仅在捕获到 {@code IOException} 时调 {@link #close(AutoCloseable)}。
 *       这是旧实现的既有行为（连接由容器回收），<b>保留不修</b>。</li>
 * </ul>
 */
public class LegacyTextRender extends LegacyRender {

    protected static final String DEFAULT_CONTENT_TYPE = "text/plain";

    protected String text;

    protected String contentType;

    /**
     * 旧构造器 1：contentType 直接取 {@code "text/plain"}。
     *
     * @param text 待输出文本
     */
    public LegacyTextRender(String text) {
        this.text = text;
        this.contentType = DEFAULT_CONTENT_TYPE;
    }

    /**
     * 旧构造器 2：按名称或别名解析 contentType，未命中保留原串。
     *
     * @param text        待输出文本
     * @param contentType 名称、别名或原始 MediaType 串
     */
    public LegacyTextRender(String text, String contentType) {
        this.text = text;
        LegacyContentType ct = LegacyContentType.parse(contentType);
        this.contentType = (ct != null ? ct.value() : contentType);
    }

    /**
     * 旧构造器 3：直接取枚举取值。
     *
     * @param text        待输出文本
     * @param contentType 内容类型枚举
     */
    public LegacyTextRender(String text, LegacyContentType contentType) {
        this.text = text;
        this.contentType = contentType.value();
    }

    /**
     * 取内容类型。
     *
     * @return contentType
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * 设置内容类型。
     *
     * @param contentType 内容类型
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * 取待输出文本。
     *
     * @return text
     */
    public String getText() {
        return text;
    }

    /**
     * 设置待输出文本。
     *
     * @param text 待输出文本
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * 写出文本：先 setContentType，再在未含 charset 时补 setCharacterEncoding，
     * 然后 getWriter/write/flush。
     */
    public void render() {
        PrintWriter writer = null;
        try {
            String ct = getContentType();
            response.setContentType(ct);
            if (ct.indexOf("charset") == -1) {
                response.setCharacterEncoding(getEncoding());
            }
            writer = response.getWriter();
            writer.write(text);
            writer.flush();
        } catch (Exception e) {
            if (e instanceof IOException) {
                close(writer);
            }
            throw new LegacyRenderException(e);
        }
    }

}
