/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import cn.eova.compat.jfinal.core.LegacyActionException;
import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyRenderManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * **动作异常的错误渲染交回 Spring 的异常解析链**（DES-012 P2-U10，r332）。
 *
 * <p><b>它替换掉的是什么</b>：U10 之前，{@link LegacyActionException} 由
 * {@link LegacyActionHandler} **自己 catch 并渲染**（`handleActionException` 的等价物）。
 * 现在异常**照旧向外抛**（不动 `LegacyRender#renderError` 的抛语义，见下），由 Spring 的
 * {@link HandlerExceptionResolver} 扩展点承接 —— 错误渲染回到框架管线，执行侧只剩"跑动作 + 渲染成功路径"。</p>
 *
 * <p><b>为什么不能改成"不抛"</b>：旧实现里 {@code renderError(code)} 的语义**就是抛**
 * （`LegacyActionException` 携带错误渲染），且 {@code AuthInterceptor} 有一条分支
 * "`renderError(503)` 之后仍 `inv.invoke()`" 是**既有缺陷、原样保留**的（那行不可达正是因为抛）。
 * 故本类只换**承接方**，不换抛出方。</p>
 *
 * <p><b>顺序</b>：order = 0 ⇒ 先于 Spring 的 `DefaultHandlerExceptionResolver`
 * （它是 `LOWEST_PRECEDENCE`）等解析器；非 {@link LegacyActionException} 一律返回 {@code null}
 * 交给后续解析器（保持既有 500 语义不变）。</p>
 *
 * <p><b>★ 返回空 ModelAndView（不是 null）</b>：本类已把响应**完整写出**（状态码 + 正文 + 清 Cookie
 * 由渲染器负责），返回非 null 表示"已处理"，从而**阻止** `BasicErrorController`
 * 再写一次体（否则 401 会变成 Spring 默认的 JSON 500 形状）。</p>
 */
public class LegacyActionExceptionResolver implements HandlerExceptionResolver, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LegacyActionExceptionResolver.class);

    /**
     * 解析异常：只认 {@link LegacyActionException}，其余放行。
     *
     * @param request  请求
     * @param response 响应
     * @param handler  当前处理器（本处不依赖）
     * @param ex       抛出的异常
     * @return 空 {@link ModelAndView}（已写响应）；非目标异常返回 null
     */
    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        if (!(ex instanceof LegacyActionException e)) {
            return null;
        }
        int errorCode = e.getErrorCode();
        String prefix;
        switch (errorCode) {
            case 404:
                prefix = "404 Not Found: ";
                break;
            case 400:
                prefix = "400 Bad Request: ";
                break;
            case 401:
                prefix = "401 Unauthorized: ";
                break;
            case 403:
                prefix = "403 Forbidden: ";
                break;
            default:
                prefix = errorCode + " Error: ";
                break;
        }
        // 旧实现的 target 拼装：target + (queryString != null ? "?" + queryString : "")
        //   target 即规范化后的请求路径（与执行侧同一份口径，见 RequestPath）
        String target = RequestPath.of(request);
        String queryString = request.getQueryString();
        String url = queryString == null ? target : target + "?" + queryString;
        String msg = prefix + url;
        if (e.getMessage() != null) {
            msg = msg + "\n" + e.getMessage();
        }

        LegacyRender render = e.getErrorRender();
        if (render != null) {
            // 旧实现：自带 errorRender ⇒ 警告级
            log.warn(msg);
        } else {
            // 旧实现：无自带 errorRender 时走 error 日志，并由渲染工厂补一个同码错误渲染
            log.error(msg);
            render = LegacyRenderManager.getRenderFactory().getErrorRender(errorCode);
        }
        render.setContext(request, response).render();
        return new ModelAndView();
    }

    /**
     * 解析器顺序：0 ⇒ 先于 Spring 默认解析器（`LOWEST_PRECEDENCE`）。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return 0;
    }
}
