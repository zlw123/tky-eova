/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.IOException;
import java.util.List;

import cn.eova.compat.jfinal.config.LegacyJFinalBoot;
import cn.eova.compat.jfinal.handler.LegacyHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * **把旧 jfinal 的 Handler 链接回请求路径**（DES-012 P3-U19，r332）。
 *
 * <p><b>它补的是什么缺口（实测）</b>：`EovaConfig#configHandler` 注册了四个 handler
 * （`WAFHandler` → `LegacyDruidStatViewHandler("/druid")` → `UrlBanHandler(".*\.(html|tag|sql)")` →
 * `ApiRouterHandler`），但新栈里 {@code LegacyHandlers} **只有写入方、没有任何请求路径上的读取方**
 * ⇒ 四个 handler 全部从未执行。实测后果：旧栈 `GET /druid/index.html` = **200**（Druid 监控页），
 * 新栈 = **404**；其余三个 handler 的效果（`.sql/.html/.tag` ⇒ 404、`/api/*` ⇒ 404、WAF 探针 ⇒ 200）
 * 两端实测一致 —— 属"恰好等价"，不是"已接线"。</p>
 *
 * <p><b>★ 链的驱动语义必须与 jfinal 一致（本单元的坑）</b>：旧实现是**责任链** ——
 * 框架只调用**第一个** handler，后续由各 handler 自己 {@code next.handle(...)} 传递，
 * 且链尾是 jfinal 的 {@code ActionHandler}（它**在链内**完成动作派发）。
 * 这解释了 `WAFHandler` 的写法（`next.handle(...)` 之后再检查 `getStatus()==404` 做封禁计数）
 * —— 只有"派发发生在链内"，那段 404 统计才有意义。</p>
 *
 * <p>因此本过滤器：① 启动期把 `next`/`nextHandler` 串好（含一个终端）；
 * ② 每请求只调**链头**；③ 终端在链内继续 servlet 管线（等价旧 `ActionHandler` 的位置）。
 * 终端通过 {@link ThreadLocal} 拿当前请求的 {@link FilterChain} —— **不按请求改共享 handler 的
 * `next` 字段**（那会在并发下把一个请求的链尾写到另一个请求上，属跨请求污染）。</p>
 *
 * <p>另有端口内部的一处不一致被本单元修掉：{@code LegacyDruidStatViewHandler} 在路径不匹配时
 * **直接 return、不调 next**（其类注释写的是"交给后续 handler"，属按"框架迭代"写的），
 * 与 `WAFHandler` 的"责任链"假设冲突。既然旧栈是责任链，该分支补 `next.handle(...)` 才忠实。</p>
 */
public class LegacyHandlerFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LegacyHandlerFilter.class);

    /** 当前请求的 servlet 链（终端在链内继续管线时取用；仅本线程可见） */
    private static final ThreadLocal<FilterChain> CURRENT_CHAIN = new ThreadLocal<>();

    private final LegacyJFinalBoot boot;

    /** 是否已串链（惰性、幂等；由 {@link #ensureWired()} 在 boot 就绪后置位） */
    private volatile boolean wired;

    /**
     * @param boot 引导对象（handler 容器的来源）
     */
    public LegacyHandlerFilter(LegacyJFinalBoot boot) {
        this.boot = boot;
    }

    /**
     * **惰性串链**（幂等）。
     *
     * <p>★ 为什么不能放在构造期（本轮实测踩到）：handler 列表由 {@code EovaConfig#configHandler}
     * 填充，而 U3 之后启动副作用在 {@code SmartLifecycle#start()}（phase=0）里执行 ——
     * 构造期列表**还是空的**，串链会空转；等到请求进来时列表已非空、但 {@code next} 仍是 null
     * ⇒ {@code WAFHandler} 直接 NPE（实测：登录 500，`this.next is null`）。</p>
     */
    private void ensureWired() {
        if (wired || !boot.isStarted()) {
            return;
        }
        synchronized (this) {
            if (wired) {
                return;
            }
            List<LegacyHandler> handlers = boot.getHandlers().getHandlerList();
            if (handlers.isEmpty()) {
                return;
            }
            cn.eova.compat.jfinal.handler.LegacyHandlerChain.wire(handlers, new Terminal());
            wired = true;
            log.info("Eova Web 层：旧 Handler 链已接回请求路径，共 {} 环（WAF → 监控页 → UrlBan → ApiRouter）",
                    handlers.size());
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ensureWired();
        List<LegacyHandler> handlers = boot.getHandlers().getHandlerList();
        if (!wired || handlers.isEmpty()) {
            // 未就绪（启动窗口内）⇒ 不做任何拦截，直接继续 servlet 管线
            chain.doFilter(request, response);
            return;
        }
        boolean[] isHandled = {false};
        CURRENT_CHAIN.set(chain);
        try {
            // ★ 只调链头：后续由各 handler 自己 next.handle(...)（jfinal 责任链语义）
            handlers.get(0).handle(RequestPath.of(request), request, response, isHandled);
        } finally {
            CURRENT_CHAIN.remove();
        }
        if (!isHandled[0]) {
            // 兜底（链尾终端已置位；这里只防"链被改坏"时静默不派发）
            log.warn("Eova Web 层：Handler 链未消费请求且未继续（链尾终端缺失？）⇒ 直接继续 servlet 管线：{}",
                    RequestPath.of(request));
            chain.doFilter(request, response);
        }
    }

    /**
     * 链尾终端：在链内继续 servlet 管线（位置等价旧 jfinal 的 {@code ActionHandler}）。
     */
    static final class Terminal extends LegacyHandler {

        @Override
        public void handle(String target, HttpServletRequest request, HttpServletResponse response,
                boolean[] isHandled) {
            FilterChain chain = CURRENT_CHAIN.get();
            if (chain == null) {
                throw new IllegalStateException("Handler 链终端不在请求线程上（装配缺陷）");
            }
            try {
                chain.doFilter(request, response);
                // 置位表示"已消费/已继续" ⇒ 过滤器不再二次派发
                isHandled[0] = true;
            } catch (IOException | ServletException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
