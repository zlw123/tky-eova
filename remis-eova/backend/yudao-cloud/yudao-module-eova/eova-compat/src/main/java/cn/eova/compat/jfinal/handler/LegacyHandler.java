/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.handler.Handler} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.handler.Handler}（jfinal 5.2.6）。</p>
 *
 * <p><b>EOVA 侧使用面：</b>3 个类继承它 —— {@code WAFHandler}、{@code UrlBanHandler}、
 * {@code ApiRouterHandler}（合计 239 行）。旧实现里这是<b>责任链</b>：
 * 每个 handler 处理完若未消费请求（{@code isHandled[0]} 仍为 false），
 * 由框架调用 {@code next.handle(...)} 继续传递。</p>
 *
 * <p><b>逐条保真：</b>旧类持有两个 protected 字段 —— {@code next} 与
 * {@code nextHandler}（后者是历史别名）。两者都保留，因为子类可能引用其中任一个；
 * "只留一个"会让引用了另一个的旧子类编译失败。</p>
 *
 * <p><b>底座替换：</b>{@code javax.servlet.http.*} → {@code jakarta.servlet.http.*}
 * （Spring Boot 3 强制，见 DES-002-R4 §B 面决策 1）。</p>
 */
public abstract class LegacyHandler {

    /** 责任链下一环 */
    protected LegacyHandler next;

    /** 责任链下一环（旧实现中的历史别名，与 {@link #next} 并存） */
    protected LegacyHandler nextHandler;

    /**
     * 处理请求。若消费了请求，须把 {@code isHandled[0]} 置为 {@code true}
     * （否则框架会继续沿链传递）；{@code isHandled} 是<b>长度 1 的数组</b>，
     * 用作"可写布尔"出口。
     *
     * @param target    请求路径（不含上下文路径）
     * @param request   请求
     * @param response  响应
     * @param isHandled 是否已消费（长度 1 的布尔数组）
     */
    public abstract void handle(String target, HttpServletRequest request,
                                HttpServletResponse response, boolean[] isHandled);

}
