/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.render.NullRender} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.render.NullRender}（jfinal 5.2.6）。</p>
 *
 * <p><b>语义：什么都不做</b> —— 旧字节码里 {@code render()} 是
 * {@code public final void render() { return; } }（<b>final</b> 且空实现）。
 * 本类照抄，包括 {@code final} 修饰（子类不得覆写 —— 覆写会让"空渲染"这一契约失真）。</p>
 */
public class LegacyNullRender extends LegacyRender {

    /**
     * 空渲染（不写任何内容）。
     */
    @Override
    public final void render() {
        // 旧实现即空实现
    }

}
