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
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.jfinal.core.paragetter.LegacyJsonRequest;
import cn.eova.compat.jfinal.aop.LegacyInvocation;
import cn.eova.compat.render.LegacyRender;
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

        // ★ r332（DES-012 P2-U10）：抛出的 LegacyActionException **不再在本类 catch** ——
        //   错误渲染已交回 Spring 的异常解析链（{@link LegacyActionExceptionResolver}）。
        //   行为等价：旧实现的"按错误码拼日志前缀 + 用异常自带 errorRender（缺则同码错误渲染）渲染"
        //   整段已搬进该解析器；抛出方（{@code LegacyRender#renderError}）语义未动
        //   （AuthInterceptor 那条"renderError(503) 之后仍 inv.invoke()"的既有缺陷也照旧不可达）。
        new LegacyInvocation(action, controller).invoke();

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
