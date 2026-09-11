/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.aop.Before} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.aop.Before}（jfinal 5.2.6）。</p>
 *
 * <p><b>逐条保真：</b>旧注解的形态是
 * {@code Class<? extends Interceptor>[] value()}，且带
 * {@code @Inherited}、{@code @Retention(RUNTIME)}、{@code @Target({TYPE, METHOD})} ——
 * 这几项都属契约（缺 {@code @Inherited} 会让子类不再继承父类声明的拦截器）。</p>
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface LegacyBefore {

    /**
     * 拦截器类型数组（按声明顺序执行）
     *
     * @return 拦截器类型
     */
    Class<? extends LegacyInterceptor>[] value();
}
