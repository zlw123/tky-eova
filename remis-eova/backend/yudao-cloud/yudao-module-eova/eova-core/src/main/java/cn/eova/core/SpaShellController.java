/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core;

import cn.eova.common.base.BaseController;

/**
 * **SPA 独有页面的壳控制器**（第 305 轮 · U1「生产态供给 + 页面入口退役」）。
 *
 * <p>有一批 URL 只属于 SPA：旧栈由 **demo 工程**处理（`/main`、`/theme`、`/test`、`/ip`、`/sso`）
 * 或由旧前端直接跳转（`/su` 实测 302 → `/main`），新旧栈的**后端都没有对应路由**。
 * 实测（带会话直连 8080）这些 URL 全部 **404** ⇒ "生产态"下 SPA 根本拿不到它们。</p>
 *
 * <p>本控制器把它们**显式接管为 SPA 壳**：注册在 {@link cn.eova.EovaWebRoutes} 里，
 * 因此仍走**同一套全局拦截器链**（未登录照样 302 到 `/user/login`，与旧栈一致），
 * 只是响应体换成壳，由前端路由决定渲染哪一页（demo 那几个 URL 由 SPA 的 `Placeholder` 显式登记）。</p>
 *
 * <p><b>为什么不"整站 fallback"</b>：整站兜底会**吞掉后端动作**（`/menu/add` 这类提交动作、
 * `/api/**`、`/eova/**`、`/_eova/**` 静态空间）。这里坚持**逐路径显式登记** ——
 * 新增一个 SPA 独有 URL 必须同时改本类与路由表，且由判据盯着
 * （{@code owned-paths.spec.ts} 的契约用例 + 后端 {@code SpaShellRoutesTest}）。</p>
 */
public class SpaShellController extends BaseController {

    /**
     * 壳入口：任一被登记的 SPA 独有 URL 都落到这里。
     *
     * <p>空 actionKey（路径等于 controllerPath）由分发器按旧 jfinal 约定映射到 {@code index}。</p>
     */
    public void index() {
        renderSpaShell();
    }
}
