/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * **请求路径规范化**（DES-012 P1-U1，r332）—— 静态层与动作路由共用的**同一份**路径口径。
 *
 * <p><b>为什么必须抽出来</b>（r247 M6 的同一教训）：静态优先与动作路由要对"同一个路径"作判断。
 * 两处各写一份规范化，就会出现"静态层看到 A、路由层看到 B"的漂移（例如尾部斜杠、contextPath 的
 * 处理不一致 ⇒ 静态层放行、路由层却按另一条路径匹配）。本方法即 {@code LegacyDispatcher}
 * 自 S2 起内联的口径，**逐行等价**；U1 把它提为单一事实源。</p>
 *
 * @see StaticResourceHandlerMapping
 * @see LegacyDispatcher
 */
public final class RequestPath {

    private RequestPath() {
    }

    /**
     * 取规范化后的请求路径：剥离 contextPath、去掉尾部 {@code "/"}（根路径除外）、空路径按 {@code "/"} 处理。
     *
     * @param request 请求
     * @return 规范化路径（形如 {@code /eova/lib/x.css}）
     */
    public static String of(HttpServletRequest request) {
        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        // 去掉尾部 "/"（根路径除外）
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
