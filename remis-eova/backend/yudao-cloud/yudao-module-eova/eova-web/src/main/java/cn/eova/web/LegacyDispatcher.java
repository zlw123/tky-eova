/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.lang.reflect.Method;
import java.util.List;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInterceptorManager;
import cn.eova.compat.jfinal.core.LegacyController;

/**
 * **旧动作分发器的退化遗留**（DES-012 P2-U11/U12，r332）。
 *
 * <p><b>它曾经是什么</b>：S2（r248）起，本类是"Eova 自有 Web 层的请求分发器" ——
 * {@code @RestController} + {@code @RequestMapping("/**")} 的 **catch-all**，
 * 里面同时装着路由表、最长前缀匹配、actionKey/urlPara 拆分、单段降级、404 与执行/渲染。</p>
 *
 * <p><b>现在是什么</b>：这些职责已按 Spring 机制重新分层 ——
 * **匹配**在 {@link LegacyActionHandlerMapping}（Spring {@code HandlerMapping} 扩展点，order 晚于
 * 显式请求映射 ⇒ 旧式分发兜底）、**执行**在 {@link LegacyActionHandler}（Spring {@code HttpRequestHandler}）。
 * 本类**不再是 MVC 入口**：catch-all 已摘除，{@code @RestController}/{@code @RequestMapping} 已移除。</p>
 *
 * <p><b>为什么还留着这一个静态方法（不是没删干净）</b>：{@code buildActionChain} 是
 * **被既有判据直接钉住**的接线点 —— {@code ActionChainWiringTest} 用 {@code LegacyDispatcher#buildActionChain}
 * 驱动"注解拦截器真的进链"（r305 真缺陷的回归锁）。按红线 R1「既有判据一行不改仍全绿」，
 * 本类只能**退化**为这一个静态接线工具，不能删类；要迁走它需同步演进那条判据，属单独裁定的动作。</p>
 *
 * @see LegacyActionHandlerMapping
 * @see LegacyActionHandler
 */
public class LegacyDispatcher {

    /**
     * 构建一个 action 的完整拦截器链：**全局 → 路由级 → 类级 `@Before` → 方法级 `@Before`**（含 `@Clear`）。
     *
     * <p>★ r305 修（真缺陷 P1）：此前这里（内联写法）只拼"全局 + 路由级"，**从未读注解** ⇒
     * 方法级 {@code @LegacyBefore(LegacyTx.class)}（全仓 24 处）与类级
     * {@code @LegacyBefore(AdminInterceptor/OpsInterceptor.class)} 全部惰性：
     * 事务不开启 ⇒ 回滚标记 {@code LegacyNestedTransactionHelpException} 无处被吞 ⇒
     * {@code GET /menu/add} 由旧栈的 fail JSON 变成 500；运维/超管守卫也未执行。</p>
     *
     * <p><b>为什么抽成方法</b>（与 {@code EovaDataSource.registerOne} 同一教训）：
     * 内联写法下"接线是否正确"只能靠读代码，而**变异证明不了**它 —— 实测 M3（把接线退回
     * "只拼全局+路由级"）在装配器自身的判据下**未被捕获**。抽出来后判据可直接驱动本方法，
     * 断言"真实控制器的注解确实进链"，接线一旦退回立刻红。</p>
     *
     * @param globalInters    全局拦截器（旧 jfinal {@code globalActionInters}）
     * @param routeInters     路由级拦截器
     * @param controllerClass action 所在控制器类
     * @param method          action 方法
     * @return 合并后的链（顺序即执行顺序）
     */
    static LegacyInterceptor[] buildActionChain(List<LegacyInterceptor> globalInters,
                                               LegacyInterceptor[] routeInters,
                                               Class<? extends LegacyController> controllerClass,
                                               Method method) {
        return LegacyInterceptorManager.buildControllerActionInterceptor(
                globalInters == null ? null : globalInters.toArray(new LegacyInterceptor[0]),
                routeInters,
                LegacyInterceptorManager.createControllerInterceptor(controllerClass),
                controllerClass,
                method);
    }
}
