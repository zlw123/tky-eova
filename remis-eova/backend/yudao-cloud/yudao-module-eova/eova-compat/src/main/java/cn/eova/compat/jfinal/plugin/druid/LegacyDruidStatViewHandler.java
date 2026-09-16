/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin.druid;

import cn.eova.compat.jfinal.handler.LegacyHandler;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.plugin.druid.DruidStatViewHandler} 的等价接缝。
 *
 * <p>ported from: com.jfinal.plugin.druid.DruidStatViewHandler（jfinal 5.2.6 制品）
 *
 * <p><b>旧实现做的事</b>：把匹配监控路径的请求交给 Druid 自带的
 * {@code com.alibaba.druid.support.http.StatViewServlet}（jfinal 只把它包成 Handler，
 * 并用 {@link LegacyDruidStatViewAuth} 做权限判断）。新栈里 Druid 仍是同一制品
 * （父 pom 统一版本），故本接缝<b>直接委派给同一个 StatViewServlet</b> —— 得到同一套监控页，
 * 而不是重写一遍。</p>
 *
 * <p>匹配规则：{@code target.startsWith(statViewPath)} 时处理并置位 {@code isHandled}，
 * 否则不置位（交给后续 handler）。默认路径 {@code "/druid"}。</p>
 */
public class LegacyDruidStatViewHandler extends LegacyHandler {

    /** 默认监控路径（旧实现默认值） */
    public static final String DEFAULT_STAT_VIEW_PATH = "/druid";

    private final String statViewPath;

    private final LegacyDruidStatViewAuth auth;

    /**
     * 反射持有的 Druid 监控 Servlet。
     *
     * <p>★ r332（P3-U19）：改用 druid 1.2.20 **自带的 jakarta 制品**
     * （{@code com.alibaba.druid.support.jakarta.StatViewServlet}）—— 原先指向
     * {@code support.http.StatViewServlet}（javax），在 jakarta 容器下
     * {@code service(jakarta...)} 根本不存在 ⇒ 一旦请求真的走到这里只会抛异常（页面永远拿不到）。
     * 仍用反射：druid 缺席时保持"记日志并放行"的既有降级路径。</p>
     */
    private Object statViewServlet;

    /** 默认构造：路径 {@code /druid}，不校验权限 */
    public LegacyDruidStatViewHandler() {
        this(DEFAULT_STAT_VIEW_PATH, null);
    }

    /**
     * @param statViewPath 监控路径
     * @param auth         权限校验；null 表示不校验（旧实现如此）
     */
    public LegacyDruidStatViewHandler(String statViewPath, LegacyDruidStatViewAuth auth) {
        this.statViewPath = statViewPath;
        this.auth = auth;
    }

    /**
     * 惰性装载 Druid 监控 Servlet（不可用则返回 null）。
     *
     * @return Servlet 实例；不可用时 null
     */
    private Object servlet() {
        if (statViewServlet == null) {
            try {
                statViewServlet = Class.forName("com.alibaba.druid.support.jakarta.StatViewServlet")
                        .getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                return null;
            }
        }
        return statViewServlet;
    }

    /**
     * 处理请求。
     *
     * @param target    目标 URI
     * @param request   请求
     * @param response  响应
     * @param isHandled 是否已处理
     */
    @Override
    public void handle(String target, HttpServletRequest request, HttpServletResponse response,
                       boolean[] isHandled) {
        if (target == null || !target.startsWith(statViewPath)) {
            // ★ r332（DES-012 P3-U19）：旧栈是**责任链**（框架只调链头，后续由各 handler 自己传）
            //   ⇒ 本分支必须交给下一环。原先直接 return 是按"框架迭代"写的，与 WAFHandler 的
            //   `next.handle(...)` 假设冲突；因该链此前从未被执行，一直没暴露。
            if (next != null) {
                next.handle(target, request, response, isHandled);
            }
            return;
        }
        // ★★ r332 逐条对齐 jfinal 5.2.6 字节码（`DruidStatViewHandler.handle`）：
        //   ① 路径命中即置位 isHandled（链到此为止，不再传给后续 handler）；
        //   ② `target` 等于监控路径时，jfinal 自己发 302 → `<path>/index.html`
        //      （实测旧栈：`GET /druid` = 302，Location = http://…/druid/index.html）；
        //   ③ 其余 `/druid/**` 直接 service 给监控 Servlet。
        isHandled[0] = true;
        String path = target;
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && !"/".equals(ctx)) {
            path = ctx + path;
        }
        if (path.equals(statViewPath) && !path.endsWith("/index.html")) {
            try {
                response.sendRedirect(path + "/index.html");
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }
        if (!permitted(request)) {
            response.setStatus(403);
            return;
        }
        Object servlet = servlet();
        if (servlet == null) {
            // jakarta 版 StatViewServlet 不可装载（druid 缺席）⇒ 记日志并放行（不伪造页面）
            LoggerFactory.getLogger(LegacyDruidStatViewHandler.class).warn("Druid 监控页不可用（jakarta 版 StatViewServlet 不可装载，druid 是否在 classpath？）："
                    + statViewPath);
            return;
        }
        try {
            Class<?> jakartaReq = Class.forName("jakarta.servlet.ServletRequest");
            java.lang.reflect.Method service = servlet.getClass()
                    .getMethod("service", jakartaReq, Class.forName("jakarta.servlet.ServletResponse"));
            service.invoke(servlet, new DruidServletPathRequest(request, statViewPath), response);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * **按旧栈的请求形态**调用监控 Servlet：`servletPath` = 完整请求 URI、`pathInfo` = null。
     *
     * <p><b>为什么必须包一层（r332 实测 + druid 字节码）</b>：druid 的
     * {@code ResourceServlet$ResourceHandler#service} 取
     * {@code path = requestURI.substring((contextPath + servletPath).length())}，
     * 再按 {@code path} 取页面（{@code "/index.html"}、{@code "/sql.html"} …）。
     * 旧栈（jfinal + undertow）里 {@code servletPath} = 监控路径 {@code /druid}
     * ⇒ {@code path = "/index.html"} ⇒ 直出；而新栈在过滤器里直调时 {@code servletPath}
     * 是 DispatcherServlet 的映射，算出的 path 不同 ⇒ druid 发 302 **到它自己**
     * （实测 {@code Location: .../druid/index.html}，即自重定向）。故这里把 servletPath
     * 还原成监控路径。</p>
     */
    private static final class DruidServletPathRequest extends jakarta.servlet.http.HttpServletRequestWrapper {

        /** 监控路径（旧栈里它是 servletPath，druid 据此算出子路径 `/index.html`、`/sql.html` …） */
        private final String statViewPath;

        DruidServletPathRequest(HttpServletRequest request, String statViewPath) {
            super(request);
            this.statViewPath = statViewPath;
        }

        @Override
        public String getServletPath() {
            return statViewPath;
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    }

    /**
     * 权限校验（旧栈由 jfinal 把 {@link LegacyDruidStatViewAuth} 注入监控 Servlet 覆写
     * {@code isPermitted} 实现）。
     *
     * <p><b>★ 与旧栈的已声明差异（r332 实测）</b>：旧栈"未授权"时返回的是 **Druid 自己的拒绝页
     * 且状态码 200**（实测 `GET /druid/index.html` 无会话 = 200 + "Sorry, you are not permitted to view this page."），
     * 本接缝改为 **403**。访问控制语义一致（都拒绝），差异仅在状态码/正文形态；要完全复刻需在运行时
     * 子类化监控 Servlet 覆写其 {@code isPermitted}（本单元不做，登记待裁）。</p>
     *
     * @param request 请求
     * @return 是否允许访问；无 auth 时恒允许（旧实现如此）
     */
    private boolean permitted(HttpServletRequest request) {
        return auth == null || auth.isPermitted(request);
    }

}
