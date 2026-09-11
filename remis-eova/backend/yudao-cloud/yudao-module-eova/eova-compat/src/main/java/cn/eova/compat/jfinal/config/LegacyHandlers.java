/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.eova.compat.jfinal.handler.LegacyHandler;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.config.Handlers} 的等价接缝。
 *
 * <p>ported from: com.jfinal.config.Handlers（jfinal 5.2.6 制品）
 *
 * <p><b>逐条取自旧字节码：</b>内部 {@code List<Handler> handlerList} + 字段
 * {@code actionHandler}（jfinal 的 {@code ActionHandler} 由框架在配置后自建）；
 * {@code add(Handler)} 返回 {@code this}；{@code getHandlerList()} 返回内部列表。
 * EOVA 在 {@code configHandler} 里按序 {@code me.add(...)}：
 * {@code WAFHandler}、{@code DevModeHandler}（{@code dvh}）、
 * {@code UrlBanHandler(".*\\.(html|tag|sql)", false)}、以及条件性的 {@code ApiRouterHandler}
 * —— <b>顺序即语义</b>（handler 链自上而下，API 路由必须最后）。</p>
 *
 * <p><b>未包含：</b>{@code setActionHandler(ActionHandler)}/{@code getActionHandler()} ——
 * jfinal 的 {@code ActionHandler} 承担 action 派发，新栈由 Spring 的 DispatcherServlet + 路由适配层承担，
 * 本接缝不复制它（复制会形成第二套派发路径）。</p>
 */
public final class LegacyHandlers {

    private final List<LegacyHandler> handlerList = new ArrayList<>();

    /**
     * 追加 handler。
     *
     * @param handler handler
     * @return this
     */
    public LegacyHandlers add(LegacyHandler handler) {
        handlerList.add(handler);
        return this;
    }

    /**
     * 取 handler 列表。
     *
     * @return 列表（不可变视图）
     */
    public List<LegacyHandler> getHandlerList() {
        return Collections.unmodifiableList(handlerList);
    }

}
