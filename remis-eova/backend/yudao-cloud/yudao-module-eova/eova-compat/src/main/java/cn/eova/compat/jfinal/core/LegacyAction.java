/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.core;

import java.lang.reflect.Method;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.core.Action} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.core.Action}（jfinal 5.2.6）。</p>
 *
 * <p><b>已声明的适配（不 port 的部分及理由）：</b>
 * 旧类另有一个字段 {@code ParameterGetter parameterGetter}
 * （类型 {@code com.jfinal.core.paragetter.ParaProcessor}），其职责是
 * <b>把请求参数绑定到 action 方法的形参上</b>。本接缝<b>不</b>port 该类型，原因有二：
 * <ol>
 *   <li><b>⚠️ 我最初声称"EOVA 的 action 全部无参"，该说法是【错的】并被断言当场推翻。</b>
 *       实测存在<b>真正的带参 action</b>：
 *       {@code MetaController.importMeta(String ds, String type, String table, String name, String code, String pk)}、
 *       {@code RouterController.signCheck(String appKey, String method, String timestamp, String sign)}。
 *       故 <b>jfinal 的参数绑定框架（{@code paragetter}）是必需的</b>，
 *       本接缝的 {@code args} 目前恒为空数组属<b>临时状态</b>，
 *       待 W1c 补参数绑定后修正。清单由
 *       {@code MvcFoundationGoldenTest.pinnedArgBearingActionMethods} 钉住。</li>
 *   <li>{@code ParaProcessor} 会牵出 jfinal 的 {@code paragetter} 整包
 *       （Getter/JsonRequest 等），属宿主装配面，非 EOVA 契约面。</li>
 * </ol>
 */
public class LegacyAction {

    private final Class<? extends LegacyController> controllerClass;

    private final String controllerPath;

    private final String actionKey;

    private final Method method;

    private final String methodName;

    private final LegacyInterceptor[] interceptors;

    private final String viewPath;

    /**
     * 构造（参数表与旧实现一致，仅去掉 {@code parameterGetter}，见类注释）。
     *
     * @param actionKey       action 键（如 {@code /menu/list}）
     * @param controllerPath  Controller 路径
     * @param controllerClass Controller 类型
     * @param method          action 方法
     * @param methodName      方法名
     * @param interceptors    生效的拦截器数组（顺序即执行顺序）
     * @param viewPath        视图路径
     */
    public LegacyAction(String actionKey, String controllerPath,
                        Class<? extends LegacyController> controllerClass,
                        Method method, String methodName,
                        LegacyInterceptor[] interceptors, String viewPath) {
        this.actionKey = actionKey;
        this.controllerPath = controllerPath;
        this.controllerClass = controllerClass;
        this.method = method;
        this.methodName = methodName;
        this.interceptors = interceptors;
        this.viewPath = viewPath;
    }

    /** 取 Controller 类型 */
    public Class<? extends LegacyController> getControllerClass() {
        return controllerClass;
    }

    /** 取 Controller 路径 */
    public String getControllerPath() {
        return controllerPath;
    }

    /** 取 action 键 */
    public String getActionKey() {
        return actionKey;
    }

    /** 取 action 方法 */
    public Method getMethod() {
        return method;
    }

    /** 取 action 方法名 */
    public String getMethodName() {
        return methodName;
    }

    /** 取拦截器数组（顺序即执行顺序） */
    public LegacyInterceptor[] getInterceptors() {
        return interceptors;
    }

    /** 取视图路径 */
    public String getViewPath() {
        return viewPath;
    }

}
