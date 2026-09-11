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
 * jfinal 5.2.6 的 {@code com.jfinal.aop.Clear} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.aop.Clear}（jfinal 5.2.6）。</p>
 *
 * <p>语义：清除指定拦截器；{@code value()} 为空数组时清除<b>全部</b>拦截器
 * （EOVA 的 {@code @Clear} 用法即空数组）。</p>
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface LegacyClear {

    /**
     * 要清除的拦截器类型；空数组表示清除全部
     *
     * @return 拦截器类型
     */
    /**
     * 要清除的拦截器类型；<b>默认空数组</b>（旧制品的 {@code AnnotationDefault: []}）。
     *
     * <p><b>第 72 轮修正：</b>port {@code IndexController} 时 {@code @Clear} 无参使用编译失败 ——
     * 本接缝原先漏了默认值。旧注解的 {@code AnnotationDefault} 是 {@code []}，
     * 故 {@code @Clear} 不带参数即"清除全部拦截器"。</p>
     *
     * @return 拦截器类型数组
     */
    Class<? extends LegacyInterceptor>[] value() default {};
}
