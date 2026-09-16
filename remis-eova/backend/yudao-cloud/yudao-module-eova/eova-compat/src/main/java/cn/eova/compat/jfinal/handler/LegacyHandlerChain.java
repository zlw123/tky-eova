/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.handler;

import java.util.List;

/**
 * **旧 Handler 责任链的串链接缝**（DES-012 P3-U19，r332 · 新接缝，非 port 单元）。
 *
 * <p><b>为什么需要它</b>：jfinal 的 {@code Handler} 是**责任链** —— 框架只调用第一个 handler，
 * 后续由各 handler 自己 {@code next.handle(...)} 传递，链尾是框架自建的 {@code ActionHandler}
 * （它在链内完成动作派发）。{@code LegacyHandler} 的 {@code next}/{@code nextHandler} 是
 * {@code protected} 字段，**跨包无法赋值**（旧栈里这段串链由 jfinal 框架内部完成）；
 * 而新栈的宿主（{@code cn.eova.web.LegacyHandlerFilter}）必须自己串链。</p>
 *
 * <p>故这里提供一个**最小公开接缝**：不改动 {@link LegacyHandler}/{@code LegacyHandlers}
 * 这两个被 port 的类（它们的字节级等价由台账 `--verify` 复核），只把"框架当年做的那一步"显式化。</p>
 *
 * <p><b>为什么两个字段都写</b>：{@code next} 与 {@code nextHandler} 是旧实现里的历史别名，
 * 子类可能引用其中任一个（见 {@link LegacyHandler} 类注释），故串链时二者同时指向下一环。</p>
 */
public final class LegacyHandlerChain {

    private LegacyHandlerChain() {
    }

    /**
     * 按列表顺序串链，链尾接 {@code terminal}。
     *
     * @param handlers 已按注册顺序排列的 handler 列表（{@code EovaConfig#configHandler} 的 add 顺序）
     * @param terminal 链尾终端（旧栈里是框架的 {@code ActionHandler}；新栈由宿主提供"继续 servlet 管线"）
     */
    public static void wire(List<LegacyHandler> handlers, LegacyHandler terminal) {
        for (int i = 0; i < handlers.size(); i++) {
            LegacyHandler current = handlers.get(i);
            LegacyHandler following = i + 1 < handlers.size() ? handlers.get(i + 1) : terminal;
            current.next = following;
            current.nextHandler = following;
        }
    }
}
