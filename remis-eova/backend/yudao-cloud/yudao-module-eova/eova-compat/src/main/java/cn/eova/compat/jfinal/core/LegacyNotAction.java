/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.core.NotAction} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.core.NotAction}（jfinal 5.2.6）。</p>
 *
 * <p><b>逐条保真：</b>用 {@code javap -v} 读旧注解的
 * {@code RuntimeVisibleAnnotations} 得到三项，全部属契约：</p>
 * <ol>
 *   <li>{@code @Inherited} —— 子类继承父类的 {@code @NotAction} 声明；</li>
 *   <li>{@code @Retention(RUNTIME)} —— 运行期可见（Action 映射要读它）；</li>
 *   <li>{@code @Target(METHOD)} —— 只允许标注方法。</li>
 * </ol>
 *
 * <p><b>语义：</b>被标注的 public 方法【不】映射为 Action。
 * EOVA 用它在 {@code BaseController} 上声明 {@code UID()/RID()/CID()/SID()} 这类
 * 供子类调用的工具方法 —— 若漏掉该注解语义，它们会被当成 URL 动作暴露出去。</p>
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LegacyNotAction {
}
