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
 *   <li><b>⚠️ 这一点我连续判断错了两次，最终由实测判据定案：</b>
 *       <ol>
 *         <li>初次声称"EOVA 的 action 全部无参" —— <b>错</b>（扫描太粗，把
 *             {@code @NotAction} 辅助方法与未注册类的方法都算成了 action）；</li>
 *         <li>随后"发现"两个带参方法并断言"参数绑定框架必需" —— <b>也错</b>；
 *             {@code MetaController.importMeta} 带 {@code @NotAction}，
 *             {@code RouterController.signCheck} 所在类<b>全树未被注册</b>。</li>
 *       </ol>
 *       <b>实测结论（判据 {@code MvcFoundationGoldenTest.argBearingActionsAreNone}）：
 *       "已注册 controller + 非 {@code @NotAction} + 带参"的方法数 = 0</b>，
 *       故 jfinal 的参数绑定框架（{@code paragetter}）<b>不需要</b> port，
 *       {@code args} 恒为空数组是<b>正确而非临时</b>的状态。</li> * </ol>
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
