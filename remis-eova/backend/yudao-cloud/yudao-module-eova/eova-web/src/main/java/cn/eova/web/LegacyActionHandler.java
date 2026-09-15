/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.IOException;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.config.LegacyJFinalBoot;
import cn.eova.compat.jfinal.core.LegacyAction;
import cn.eova.compat.jfinal.core.LegacyActionException;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.jfinal.core.paragetter.LegacyJsonRequest;
import cn.eova.compat.jfinal.aop.LegacyInvocation;
import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyRenderManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.HttpRequestHandler;

/**
 * **旧动作的执行侧**（DES-012 P2-U11/U12，r332）—— 从 {@code LegacyDispatcher#dispatch} 逐行搬移。
 *
 * <p>职责边界：本类**不做匹配**（那是 {@link LegacyActionHandlerMapping} 的事），只负责
 * "拿到匹配结果 → 造请求级控制器实例 → 注入上下文 → 跑拦截器链 → 执行动作 → 渲染/异常处理"。
 * 代码体连同 r250/r305/r306/U4 的现场注释一并搬移，**语义零改动**。</p>
 *
 * @see LegacyActionHandlerMapping
 */
public class LegacyActionHandler implements HttpRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(LegacyActionHandler.class);

    private final LegacyJFinalBoot boot;

    /**
     * @param boot 引导对象（常量面与全局拦截器）
     */
    public LegacyActionHandler(LegacyJFinalBoot boot) {
        this.boot = boot;
    }

    /**
     * 执行一次动作分发（等价旧 jfinal `ActionHandler` 的动作段）。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @throws IOException 响应写出失败
     */
    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LegacyActionHandlerMapping.Match match =
                (LegacyActionHandlerMapping.Match) request.getAttribute(LegacyActionHandlerMapping.MATCH_ATTRIBUTE);
        if (match == null) {
            // 只有"映射认领了却没放结果"才会到这里（装配缺陷）⇒ 响亮报错，不静默
            throw new IllegalStateException("动作处理器被调用但没有匹配结果（装配缺陷）："
                    + LegacyActionHandlerMapping.MATCH_ATTRIBUTE);
        }
        LegacyActionHandlerMapping.Entry hit = match.entry;
        String actionKey = match.actionKey;
        String urlPara = match.urlPara;
        java.lang.reflect.Method method = match.method;
        String path = RequestPath.of(request);

        // 请求级控制器实例 + 上下文注入（旧栈每请求一实例）
        LegacyController controller;
        try {
            controller = hit.controllerClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("控制器无法实例化（需公开无参构造）：" + hit.controllerClass.getName(), e);
        }
        controller.setHttpServletRequest(request);
        controller.setHttpServletResponse(response);
        controller.setUrlPara(urlPara);

        // ★ r332（DES-012 P1-U4）：**注入上传部件容器** —— 旧栈这一步由 jfinal 的
        //   `Controller.getFiles()` **按需**把 request 包成 MultipartRequest（内部 COS 从原始体解析）；
        //   新栈的解析由 Spring 完成（Boot 的 StandardServletMultipartResolver 已在 DispatcherServlet
        //   前置把请求包成 MultipartHttpServletRequest），故宿主必须在 action 之前把部件交给控制器
        //   —— 这正是 r77 声明、却一直**没有调用点**的 `LegacyController#setMultipartRequest`。
        //   实测缺口：旧栈 `POST /upload/file`（合法文件）= 200 state:ok，新栈修前 = 500（未注入）。
        //   ⚠️ 必须在下面的 JSON 包装**之前**调用：包装类型不再实现 MultipartHttpServletRequest。
        LegacyMultipartInjector.inject(controller, request);

        // ★ JSON 请求包装 —— 旧 jfinal {@code ActionHandler} 逐行等价：
        //     if (resolveJson && controller.isJsonRequest())
        //         controller.setHttpServletRequest(jsonRequestFactory.apply(controller.getRawData(), controller.getRequest()));
        //   不包的话 {@code WebUtil.isAjax} 里的 {@code instanceof JsonRequest} 恒为 false，未登录的
        //   JSON 请求就会走"同步跳登录页"分支。旧栈实测（curl 无 Cookie POST /api/home/menu）是
        //   401 + {"state":"fail","msg":"401 Unauthorized"} + 清 Cookie ⇒ 必须补齐这层包装。
        if (boot.getConstants().getResolveJsonRequest() && isJsonRequest(controller.getRequest())) {
            controller.setHttpServletRequest(
                    new LegacyJsonRequest(controller.getRawData(), controller.getRequest()));
        }

        LegacyInterceptor[] chain = LegacyDispatcher.buildActionChain(
                boot.getInterceptors().getInterceptors(), hit.routeInters, hit.controllerClass, method);
        LegacyAction action = new LegacyAction(actionKey, hit.controllerPath, hit.controllerClass,
                method, method.getName(), chain, null);

        try {
            new LegacyInvocation(action, controller).invoke();
        } catch (LegacyActionException e) {
            // ★ 错误渲染落在【宿主】这一层 —— 旧 jfinal 的等价物是 ActionHandler.handleActionException：
            //   {@code renderError(code)} 在旧实现里就是"抛 ActionException，由框架渲染"，
            //   而 ExceptionInterceptor（全局中间件）对非 500 的 ActionException 只做原样再抛
            //   （逐行等价 port 里保留了这一行为），所以不在这里渲染，401/403 就会变成 500。
            handleActionException(e, path, controller.getRequest(), response);
            return;
        }

        // 渲染：控制器内 render* 设立的渲染器负责写响应
        LegacyRender render = controller.getRender();
        if (render == null) {
            log.warn("动作 {}#{} 未产生渲染器（S2 尚无默认模板视图）⇒ 按 404 处理",
                    hit.controllerClass.getSimpleName(), method.getName());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        render.setContext(controller.getRequest(), response).render();
    }

    /**
     * 等价旧 jfinal {@code ActionHandler.handleActionException}：按错误码拼日志前缀 → 记录 →
     * 用 {@link LegacyActionException#getErrorRender()} 渲染（为 null 时由渲染工厂补一个）。
     *
     * <p>旧实现里警告/错误两条日志分支取决于"异常是否自带 errorRender"；此处保留该分支语义。</p>
     *
     * @param e       带错误码的动作异常
     * @param target  目标路径（旧实现的 target）
     * @param request  请求
     * @param response 响应
     */
    private void handleActionException(LegacyActionException e, String target,
            HttpServletRequest request, HttpServletResponse response) {
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
        String queryString = request.getQueryString();
        String url = queryString == null ? target : target + "?" + queryString;
        String msg = prefix + url;
        if (e.getMessage() != null) {
            msg = msg + "\n" + e.getMessage();
        }

        LegacyRender render = e.getErrorRender();
        if (render != null) {
            log.warn(msg);
        } else {
            // 旧实现：无自带 errorRender 时走 error 日志，并由渲染工厂补一个同码错误渲染
            log.error(msg);
            render = LegacyRenderManager.getRenderFactory().getErrorRender(errorCode);
        }
        render.setContext(request, response).render();
    }

    /**
     * 等价旧 jfinal {@code Controller.isJsonRequest()}：已经是包装类型即 true，否则
     * {@code Content-Type} 含 {@code "json"}（**逐字保留旧实现的大小写敏感 {@code indexOf}**）。
     *
     * @param request 原始请求
     * @return 是否按 JSON 请求处理
     */
    private static boolean isJsonRequest(HttpServletRequest request) {
        if (request instanceof LegacyJsonRequest) {
            return true;
        }
        String contentType = request.getContentType();
        return contentType != null && contentType.indexOf("json") != -1;
    }
}
