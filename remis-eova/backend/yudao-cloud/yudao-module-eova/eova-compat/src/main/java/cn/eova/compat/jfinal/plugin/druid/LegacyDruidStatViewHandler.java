/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin.druid;

import cn.eova.compat.jfinal.handler.LegacyHandler;
import cn.eova.compat.jfinal.kit.LegacyLogKit;
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

    /** 反射持有的 Druid 监控 Servlet（javax 制品 ⇒ 只能反射，见类注释） */
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
                statViewServlet = Class.forName("com.alibaba.druid.support.http.StatViewServlet")
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
            return;
        }
        if (auth != null && !auth.isPermitted(request)) {
            response.setStatus(403);
            isHandled[0] = true;
            return;
        }
        Object servlet = servlet();
        if (servlet == null) {
            // jakarta 容器下 javax 版 Druid 监控 Servlet 不可用 ⇒ 记日志并放行（不伪造页面）
            LegacyLogKit.warn("Druid 监控页不可用（javax 版 StatViewServlet 在 jakarta 容器下不可装载）："
                    + statViewPath);
            return;
        }
        try {
            Class<?> jakartaReq = Class.forName("jakarta.servlet.ServletRequest");
            java.lang.reflect.Method service = servlet.getClass()
                    .getMethod("service", jakartaReq, Class.forName("jakarta.servlet.ServletResponse"));
            service.invoke(servlet, request, response);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new RuntimeException(e);
        }
        isHandled[0] = true;
    }

}
