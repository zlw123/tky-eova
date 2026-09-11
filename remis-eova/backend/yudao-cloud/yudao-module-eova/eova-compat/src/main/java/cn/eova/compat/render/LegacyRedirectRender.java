/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

import java.io.IOException;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.render.RedirectRender} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.render.RedirectRender}（jfinal 5.2.6）。</p>
 *
 * <p><b>语义逐条取自旧字节码：</b>
 * <ol>
 *   <li>{@code RedirectRender(String url)} → {@code withQueryString = <b>false</b>}
 *       —— <b>不是 true</b>（这一点我若凭直觉会写错）。</li>
 *   <li>{@link #buildFinalUrl()}：
 *       <pre>
 * if (contextPath != null
 *         &amp;&amp; (url.indexOf("://") == -1 || url.indexOf("://") &gt; 5)) {
 *     finalUrl = contextPath + url;      // 相对路径补上下文前缀
 * } else {
 *     finalUrl = url;                    // 已是绝对 URL（协议在 index &lt;= 5 处）
 * }
 * if (withQueryString) {
 *     String qs = request.getQueryString();
 *     if (qs != null) finalUrl += (finalUrl.indexOf('?') == -1 ? "?" : "&amp;") + qs;
 * }
 *       </pre></li>
 *   <li>{@link #render()}：{@code response.sendRedirect(buildFinalUrl())}；
 *       {@code IOException} 包成 {@link LegacyRenderException}。</li>
 * </ol>
 *
 * <p><b>上下文路径来源：</b>旧实现读 {@code JFinal.me().getContextPath()}，并在其值为
 * {@code ""} 或 {@code "/"} 时<b>归一为 null</b>（即"无上下文前缀"）。
 * 新栈由 Spring Boot 提供该值，故此处提供 {@link #setContextPath(String)}
 * 作为宿主装配注入口 —— 语义与旧实现的归一规则一致。</p>
 */
public class LegacyRedirectRender extends LegacyRender {

    /** 上下文路径；null 表示无前缀（旧实现在 "" 或 "/" 时归一为 null） */
    private static volatile String contextPath;

    /** 目标 URL */
    protected String url;

    /** 是否附带原查询串 */
    protected boolean withQueryString;

    /**
     * 装配上下文路径（宿主启动时调用）。
     *
     * @param contextPath 上下文路径；{@code ""} 或 {@code "/"} 会被归一为 null
     */
    public static void setContextPath(String contextPath) {
        LegacyRedirectRender.contextPath = ("".equals(contextPath) || "/".equals(contextPath))
                ? null : contextPath;
    }

    /**
     * 取当前上下文路径。
     *
     * @return 上下文路径；无前缀时为 null
     */
    public static String getContextPath() {
        return contextPath;
    }

    /**
     * 单参构造：<b>不</b>附带查询串（旧字节码如此）。
     *
     * @param url 目标 URL
     */
    public LegacyRedirectRender(String url) {
        this.url = url;
        this.withQueryString = false;
    }

    /**
     * 两参构造。
     *
     * @param url             目标 URL
     * @param withQueryString 是否附带原查询串
     */
    public LegacyRedirectRender(String url, boolean withQueryString) {
        this.url = url;
        this.withQueryString = withQueryString;
    }

    /**
     * 构建最终跳转 URL（规则见类注释）。
     *
     * @return 最终 URL
     */
    public String buildFinalUrl() {
        final String cp = contextPath;
        String finalUrl;
        if (cp != null && (url.indexOf("://") == -1 || url.indexOf("://") > 5)) {
            finalUrl = cp + url;
        } else {
            finalUrl = url;
        }
        if (withQueryString) {
            String qs = request.getQueryString();
            if (qs != null) {
                finalUrl += (finalUrl.indexOf('?') == -1 ? "?" : "&") + qs;
            }
        }
        return finalUrl;
    }

    /**
     * 执行跳转。
     */
    @Override
    public void render() {
        try {
            response.sendRedirect(buildFinalUrl());
        } catch (IOException e) {
            throw new LegacyRenderException(e);
        }
    }

}
