/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.io.IOException;

import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyRenderException;
import cn.eova.compat.render.LegacyRenderManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.kit.HandlerKit} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.kit.HandlerKit}（jfinal 5.2.6）。
 * EOVA 的 {@code WAFHandler} 与 {@code UrlBanHandler} 都只用
 * {@code renderError404(request, response, isHandled)}。</p>
 *
 * <p><b>四个方法逐条取自旧字节码：</b>
 * <ol>
 *   <li>{@code renderError404(req, resp, isHandled)}：
 *       {@code isHandled[0] = true} → {@code factory.getErrorRender(404).setContext(req,resp).render()}
 *       （注意：<b>不设状态码</b>，404 由 {@code ErrorRender} 自己在 {@code render()} 里 setStatus）；</li>
 *   <li>{@code renderError404(view, req, resp, isHandled)}：
 *       {@code isHandled[0] = true} → {@code response.setStatus(404)} →
 *       {@code factory.getRender(view).setContext(req,resp).render()}；</li>
 *   <li>{@code redirect(url, req, resp, isHandled)}：{@code isHandled[0] = true} →
 *       追加原查询串（按 url 是否已含 {@code '?'} 选 {@code ?} 或 {@code &}）→
 *       {@code response.sendRedirect(url)}；{@code IOException} 包 {@link LegacyRenderException}；</li>
 *   <li>{@code redirect301(url, req, resp, isHandled)}：同样的查询串拼接，然后
 *       <b>不</b>用 sendRedirect，而是 {@code setStatus(301)} +
 *       {@code setHeader("Location", url)} + {@code setHeader("Connection", "close")}。</li>
 * </ol>
 *
 * <p><b>{@code isHandled} 语义：</b>它是<b>长度 1 的布尔数组</b>，作为"可写布尔出口" ——
 * 本工具类做的第一件事都是把它置 {@code true}（表示请求已被消费，责任链不再往下传）。</p>
 */
public final class LegacyHandlerKit {

    private LegacyHandlerKit() {
    }

    /**
     * 渲染 404 错误页（用内建/配置的 404 页）。
     *
     * @param request   请求
     * @param response  响应
     * @param isHandled 是否已消费（长度 1 的数组）
     */
    public static void renderError404(HttpServletRequest request, HttpServletResponse response,
                                      boolean[] isHandled) {
        isHandled[0] = true;
        LegacyRenderManager.getRenderFactory()
                .getErrorRender(404)
                .setContext(request, response)
                .render();
    }

    /**
     * 渲染 404 错误页（指定视图），并设置状态码 404。
     *
     * @param view      视图
     * @param request   请求
     * @param response  响应
     * @param isHandled 是否已消费（长度 1 的数组）
     */
    public static void renderError404(String view, HttpServletRequest request,
                                      HttpServletResponse response, boolean[] isHandled) {
        isHandled[0] = true;
        response.setStatus(404);
        LegacyRenderManager.getRenderFactory()
                .getRender(view)
                .setContext(request, response)
                .render();
    }

    /**
     * 302 跳转（{@code sendRedirect}），并把原查询串带上。
     *
     * @param url       目标 URL
     * @param request   请求
     * @param response  响应
     * @param isHandled 是否已消费（长度 1 的数组）
     */
    public static void redirect(String url, HttpServletRequest request,
                                HttpServletResponse response, boolean[] isHandled) {
        isHandled[0] = true;
        url = appendQueryString(url, request);
        try {
            response.sendRedirect(url);
        } catch (IOException e) {
            throw new LegacyRenderException(e);
        }
    }

    /**
     * 301 永久跳转（<b>不用</b> sendRedirect，而是手动设置状态码与响应头）。
     *
     * @param url       目标 URL
     * @param request   请求
     * @param response  响应
     * @param isHandled 是否已消费（长度 1 的数组）
     */
    public static void redirect301(String url, HttpServletRequest request,
                                   HttpServletResponse response, boolean[] isHandled) {
        isHandled[0] = true;
        url = appendQueryString(url, request);
        response.setStatus(301);
        response.setHeader("Location", url);
        response.setHeader("Connection", "close");
    }

    /**
     * 把请求的查询串追加到 URL 上（旧字节码：按 url 是否已含 {@code '?'} 选择分隔符）。
     *
     * @param url     目标 URL
     * @param request 请求
     * @return 追加后的 URL；无查询串时原样返回
     */
    private static String appendQueryString(String url, HttpServletRequest request) {
        String qs = request.getQueryString();
        if (qs != null) {
            url += (url.indexOf('?') == -1 ? "?" : "&") + qs;
        }
        return url;
    }

}
