/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.aop;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import cn.eova.compat.jfinal.core.LegacyAction;
import cn.eova.compat.jfinal.core.LegacyController;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.aop.Invocation} 的等价接缝（<b>action 路径</b>）。
 *
 * <p>{@code ported from} {@code com.jfinal.aop.Invocation}（jfinal 5.2.6）。</p>
 *
 * <p><b>EOVA 侧实际使用面（全树普查）：只有 3 个成员</b> ——
 * {@code getController()}(16)、{@code invoke()}(12)、{@code getActionKey()}(1)。
 * 其余成员一并提供，因为它们是 {@link #invoke()} 的语义组成部分且零成本。</p>
 *
 * <p><b>{@link #invoke()} 的语义逐条取自旧字节码，含一个易被忽略的关键守卫：</b>
 * <pre>
 * if (index &lt; inters.length) {
 *     inters[index++].intercept(this);          // 交给下一个拦截器
 * } else if (index++ == inters.length) {        // ← 守卫：action 只执行【一次】
 *     returnValue = action.getMethod().invoke(target, args);
 * }
 * </pre>
 * 那个 {@code index++ == inters.length} 的<b>后置自增比较</b>保证：
 * 即使某拦截器多次调用 {@code inv.invoke()}（或链上出现环），
 * action 方法也<b>只会被执行一次</b>；后续调用落到两个条件都为 false 的分支，
 * <b>静默什么都不做</b>（不抛异常、不重复执行）。
 * 该行为属既有语义，不得改写为"抛异常"或"允许重复执行"。</p>
 *
 * <p><b>已声明的适配：</b>旧实现另有一条"非 action 调用"路径
 * （{@code Callback} 代理调用，用于 {@code Aop.get(...)} 的普通 Bean 切面）。
 * EOVA 不使用该路径，故本接缝只实现 action 路径 ——
 * {@link #isActionInvocation()} 恒为 {@code true}。</p>
 */
public class LegacyInvocation {

    /** 拦截器链 */
    private final LegacyInterceptor[] inters;

    /** action 元信息（非 action 路径时为 null） */
    private final LegacyAction action;

    /** 被调目标（action 路径下即 Controller 实例） */
    private final Object target;

    /** action 方法 */
    private final Method method;

    /** action 方法实参 */
    private final Object[] args;

    /** 返回值 */
    private Object returnValue;

    /** 链游标 */
    private int index;

    /**
     * action 路径构造器（对应旧 {@code Invocation(Action, Controller)}）。
     *
     * @param action     action 元信息
     * @param controller Controller 实例
     */
    public LegacyInvocation(LegacyAction action, LegacyController controller) {
        this.index = 0;
        this.action = action;
        this.inters = action.getInterceptors();
        this.target = controller;
        this.method = action.getMethod();
        // EOVA 的 action 方法全部无参（见 LegacyAction 的适配说明），
        // 故这里不给实参；该前提由 MvcFoundationGoldenTest.allEovaActionMethodsAreNoArg 机器校验。
        this.args = new Object[0];
    }

    /**
     * 推进链：先走拦截器，拦截器走完后执行 action 一次。
     *
     * <p>语义见类注释（含"action 只执行一次"守卫）。</p>
     */
    public void invoke() {
        if (index < inters.length) {
            inters[index++].intercept(this);
        } else if (index++ == inters.length) {
            if (action != null) {
                try {
                    returnValue = method.invoke(target, args);
                } catch (InvocationTargetException e) {
                    // 旧实现把目标方法抛出的异常<b>原样上抛其 cause</b>（不包一层），
                    // 以便上层按原始类型处理（EOVA 的异常拦截器依赖此点）。
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    }
                    if (cause instanceof Error) {
                        throw (Error) cause;
                    }
                    throw new RuntimeException(cause);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * 取第 i 个实参。
     *
     * @param i 下标
     * @return 实参
     */
    public Object getArg(int i) {
        return args[i];
    }

    /**
     * 设置第 i 个实参。
     *
     * @param i   下标
     * @param arg 实参
     */
    public void setArg(int i, Object arg) {
        args[i] = arg;
    }

    /** 取全部实参 */
    public Object[] getArgs() {
        return args;
    }

    /** 取被调目标 */
    @SuppressWarnings("unchecked")
    public <T> T getTarget() {
        return (T) target;
    }

    /** 取 action 方法 */
    public Method getMethod() {
        return method;
    }

    /** 取 action 方法名 */
    public String getMethodName() {
        return method != null ? method.getName() : null;
    }

    /** 取返回值 */
    @SuppressWarnings("unchecked")
    public <T> T getReturnValue() {
        return (T) returnValue;
    }

    /**
     * 设置返回值（拦截器可改写 action 的返回值）。
     *
     * @param returnValue 返回值
     */
    public void setReturnValue(Object returnValue) {
        this.returnValue = returnValue;
    }

    /**
     * 取 Controller 实例（EOVA 拦截图里用得最多的成员）。
     *
     * @return Controller
     */
    public LegacyController getController() {
        return (LegacyController) target;
    }

    /** 取 action 键 */
    public String getActionKey() {
        return action != null ? action.getActionKey() : null;
    }

    /** 取 Controller 路径 */
    public String getControllerPath() {
        return action != null ? action.getControllerPath() : null;
    }

    /**
     * 取 Controller 键（旧实现从 {@code target} 的 {@code getControllerKey()} 取）。
     *
     * @return Controller 键
     */
    public String getControllerKey() {
        return getController() != null ? getController().getControllerKey() : null;
    }

    /**
     * 取视图路径。
     *
     * @return 视图路径
     */
    public String getViewPath() {
        return getController() != null ? getController().getViewPath() : null;
    }

    /**
     * 是否 action 调用（本接缝只实现 action 路径，恒为 true）。
     *
     * @return true
     */
    public boolean isActionInvocation() {
        return action != null;
    }

}
