/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin.activerecord;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.plugin.activerecord.tx.TxConfig} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.plugin.activerecord.tx.TxConfig}（jfinal 5.2.6）。</p>
 *
 * <p><b>逐条保真（四项注解元数据全部由 {@code javap -v} 读出）：</b>
 * {@code @Inherited}、{@code @Documented}、{@code @Retention(RUNTIME)}、
 * {@code @Target({TYPE, METHOD})}，成员为 {@code String value()}。</p>
 *
 * <p><b>为什么 {@code @Inherited} 与 {@code TYPE} 是契约：</b>
 * {@code Tx} 查注解的顺序是"<b>先方法、后目标类</b>"，而"目标类"这一路依赖
 * 注解能被类继承 —— 去掉 {@code @Inherited} 或 {@code TYPE}，
 * 标在父类上的数据源配置会静默失效，事务落到默认数据源上。</p>
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface LegacyTxConfig {

    /**
     * 数据源名（旧实现用它取 {@code DbKit.getConfig(value())}）。
     *
     * @return 数据源名，如 {@code "eova"}
     */
    String value();
}
