/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.aop;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.aop.Interceptor} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.aop.Interceptor}（jfinal 5.2.6）。</p>
 *
 * <p><b>契约：</b>旧接口只有一个方法 {@code void intercept(Invocation inv)}。
 * EOVA 有 20 个拦截器实现它，其中实际用到 {@code Invocation} 的成员只有 3 个：
 * {@code getController()}(16)、{@code invoke()}(12)、{@code getActionKey()}(1) ——
 * 见 {@link LegacyInvocation} 的方法集口径。</p>
 */
public interface LegacyInterceptor {

    /**
     * 拦截（旧实现无返回值；是否放行由调用方决定是否调 {@code inv.invoke()}）。
     *
     * @param inv 调用上下文
     */
    void intercept(LegacyInvocation inv);

}
