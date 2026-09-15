/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.config.LegacyJFinalBoot;
import cn.eova.compat.jfinal.config.LegacyRoutes;
import cn.eova.compat.jfinal.core.LegacyController;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;

/**
 * **旧动作路由的 Spring 机制承载物**（DES-012 P2-U11/U12，r332）。
 *
 * <p><b>它替换掉的是什么</b>：U11 之前，动作分发是 {@code LegacyDispatcher} 的
 * **catch-all** 处理器（{@code @RestController} + {@code @RequestMapping("/**")}）：
 * 路由表、最长前缀匹配、actionKey/urlPara 拆分、单段降级、404 全部内联在那一个方法里，
 * 而且是靠"Spring 在同一个 {@code RequestMappingHandlerMapping} 里按具体性排序"才没有把
 * {@code /api/page/bootstrap} 这类**显式 Spring 端点**吞掉。现在：
 * 本类（Spring 的 {@link AbstractHandlerMapping} 扩展点）承担**匹配**，
 * {@link LegacyActionHandler} 承担**执行**，显式端点与我们之间靠 **order** 分层。</p>
 *
 * <p><b>顺序（关键，不是可调项）</b>：本映射 order = {@code 1}，即排在
 * {@code RequestMappingHandlerMapping}（= 0）**之后** ⇒ **显式 Spring 端点优先，旧式分发是最后手段**。
 * 与旧行为的等价性：旧实现两者同处一个映射、由"具体路径优先于 {@code /**}"保证同一结果；
 * 分层后由 order 保证。若把本映射排到前面，{@code /api/page/bootstrap} 会被根路由兜底吃掉（404）。</p>
 *
 * <p><b>未命中 ⇒ 返回 null</b>（不是自己写 404）：{@code DispatcherServlet} 在没有任何映射认领时
 * 原生 404，**绝不回落 SPA** —— 旧栈实测 {@code /meta/find/main-table/1} = 404。
 * 旧日志文案 {@code 404 Action Not Found: <path>} 在返回前照旧打印（保持可观测性）。</p>
 *
 * <p><b>C-12 语义逐条保留</b>（与旧 jfinal 一致，逐条有实跑证据，注释随逻辑一并搬移）：
 * actionKey = 公开无参方法名；空 actionKey ⇒ {@code index}；urlPara **只吃一段**；
 * 降级**只剥一段**且前缀必须是已注册路由；末段含 {@code .} 的"文件型"路径不降级。</p>
 *
 * @see LegacyActionHandler
 * @see RequestPath
 */
public class LegacyActionHandlerMapping extends AbstractHandlerMapping {

    private static final Logger log = LoggerFactory.getLogger(LegacyActionHandlerMapping.class);

    /** 匹配结果的请求属性名（本类写、{@link LegacyActionHandler} 读） */
    static final String MATCH_ATTRIBUTE = LegacyActionHandlerMapping.class.getName() + ".match";

    private final LegacyJFinalBoot boot;

    /** 执行侧处理器（Spring bean，被所有请求共享） */
    private final LegacyActionHandler handler;

    /** 已解析的 (controllerPath, Routes) 对照表，按路径长度降序（最长前缀优先） */
    private final List<Entry> entries = new ArrayList<>();

    /**
     * 构造：**在构造期**建索引（与旧 {@code @PostConstruct} 同期）—— 此时 {@code boot} 已完成
     * {@code init()}（它是本 bean 的依赖，容器保证先建），故路由表已填充。
     *
     * @param boot    引导对象（路由表与拦截器的来源）
     * @param handler 执行侧处理器
     */
    public LegacyActionHandlerMapping(LegacyJFinalBoot boot, LegacyActionHandler handler) {
        this.boot = boot;
        this.handler = handler;
        // ★ 必须晚于 RequestMappingHandlerMapping（= 0）：显式 Spring 端点优先，旧式分发兜底
        setOrder(1);
        indexRoutes();
    }

    /** 一条路由所属的 Routes 对象（拦截器从它取） */
    static final class Entry {
        final String controllerPath;
        final Class<? extends LegacyController> controllerClass;
        final LegacyInterceptor[] routeInters;

        Entry(LegacyRoutes.Route route, LegacyRoutes owner) {
            this.controllerPath = route.getControllerPath();
            this.controllerClass = route.getControllerClass();
            this.routeInters = owner.getInterceptors();
        }
    }

    /** 一次匹配的结果（含降级后的最终取值） */
    static final class Match {
        Entry entry;
        String actionKey;
        String urlPara;
        Method method;
    }

    /** 启动后建索引：顶层 + 静态子路由，去重后按路径长度降序 */
    private void indexRoutes() {
        List<LegacyRoutes> all = new ArrayList<>();
        all.add(boot.getRoutes());
        all.addAll(LegacyRoutes.getRoutesList());
        for (LegacyRoutes owner : all) {
            for (LegacyRoutes.Route r : owner.getRouteItemList()) {
                entries.add(new Entry(r, owner));
            }
        }
        entries.sort(Comparator.comparingInt((Entry e) -> e.controllerPath.length()).reversed());
        log.info("Eova Web 层：分发索引建立，共 {} 条路由路径 {}", entries.size(),
                entries.stream().map(e -> e.controllerPath).collect(Collectors.toList()));
    }

    /**
     * 匹配动作：**命中返回匹配结果，未命中返回 null**（交下一个映射 ⇒ 最终原生 404，不回落 SPA）。
     *
     * <p>抽成包级静态方法（与 {@code buildActionChain} 同一理由）：接线/规则的正确性可以直接被判据驱动，
     * 一旦退化规则被改坏，判据立刻红 —— 而不是只能靠端到端页面去撞。</p>
     *
     * @param entries 路由索引（长度降序）
     * @param path    规范化路径（见 {@link RequestPath}）
     * @return 匹配结果；未命中 null
     */
    static Match match(List<Entry> entries, String path) {
        Entry hit = null;
        for (Entry e : entries) {
            String p = e.controllerPath;
            if (path.equals(p) || path.startsWith(p.endsWith("/") ? p : p + "/")) {
                hit = e;
                break;
            }
        }
        if (hit == null) {
            return null;
        }

        // 切出 actionKey 与 urlPara
        String rest = path.substring(hit.controllerPath.length());
        while (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
        String actionKey;
        String urlPara = null;
        int slash = rest.indexOf('/');
        if (slash < 0) {
            actionKey = rest;
        } else {
            actionKey = rest.substring(0, slash);
            urlPara = rest.substring(slash + 1);
        }

        // ★ r250：空 actionKey ⇒ `index`（旧 jfinal 约定）。实测证据：旧栈带会话 `GET /` 与 `GET /main`
        //   都是 200（首页由 `IndexController.index()` 渲染），而本分发器初版对 `/` 取不到名为 "" 的方法
        //   ⇒ 直接 404 —— 这是 S5 真浏览器/HTTP 验收才暴露出来的**移植缺口**（构建与既有判据全绿）。
        if (actionKey.isEmpty()) {
            actionKey = "index";
        }

        // ★★ r306（U2 · 实跑抓出的第二个移植缺口）：**urlPara 只有一段**。
        //   旧 jfinal 的动作键空间 = {控制器路径} ∪ {控制器路径 + "/" + 方法名}，而 `ActionMapping#getAction`
        //   最多只把**一个**尾段当 urlPara ⇒ 「方法名 + 2 段以上」的 URL **根本没有对应动作键** ⇒ 404。
        //   （多值 urlPara 不是用 `/` 分隔，而是**同一段内用 `-` 分隔** —— 这正是旧
        //    `MetaController#find` 的 `get(0)`/`get(1)` 配 `/meta/find/main-table` 的由来。）
        //   实测：旧栈 `/meta/find/main-table/1` **404**，而新栈此前把 `main-table/1` 整段当 urlPara
        //   ⇒ 真的去渲染模板 ⇒ **500** ⇒ 不等价。修法：多段时**跳过直接动作匹配**，交给下面的退化路径
        //   （退化要求"前缀是已注册路由"，多段前缀必然不是 ⇒ 404，与旧栈一致）。
        boolean multiSegmentPara = false;
        if (slash >= 0) {
            multiSegmentPara = rest.indexOf('/', slash + 1) >= 0;
        }

        Method method = multiSegmentPara ? null : findAction(hit.controllerClass, actionKey);
        // ★ 兜底**只对页面请求生效**：末段含 `.` 的"文件型"路径不走退化，直接 404。
        //   实测旧栈：`/demo/test/nope.js`、`/nope/x.css`、`/zzz_unknown.js` 全 **404**，
        //   而 `/zzz_unknown`（无扩展名）落首页 —— 静态层与动作层是分开的。
        //   ⚠️ 不设这条会出真事故：`/demo/test/btn.js`（demo 静态资产，旧栈 200 application/javascript）
        //   在新栈会退化到 `/` 兜底 ⇒ 返回 **HTML** ⇒ 浏览器执行 HTML 当脚本 ⇒
        //   `SyntaxError: Unexpected token '<'`（本轮 S5 ⑨ 就是被这个打红的）。
        boolean fileLike = false;
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        if (lastSegment.indexOf('.') >= 0) {
            fileLike = true;
        }
        if (method == null && !fileLike && !"index".equals(actionKey)) {
            // ★★ r305（U1 · 生产态才暴露的移植缺口）：**JFinal 的 actionKey 退化规则只有一层**。
            //   旧 `ActionMapping#getAction`：① 按**完整 url** 查动作键；② 查不到 ⇒ **只剥掉最后一段**
            //   当 urlPara，再用**剩下的前缀**查一次；③ 仍查不到 ⇒ 404（**不再继续剥离**）。
            //   旧栈实测（带会话直连 9090）：
            //     `/zzz_unknown`          **200**（前缀为空 ⇒ 命中根路由 `IndexController#index` + urlPara）
            //     `/app/meta_product`     **200**（前缀 `/app` 是已注册路由 ⇒ index + urlPara=meta_product）
            //     `/a/b`、`/definitely/not/a/route`  **404**（前缀 `/a`、`/definitely/not/a` 都不是动作键）
            //   ★ 首版实现写成"**逐段剥离直到命中**"⇒ 多段未知路径统统落首页 ⇒ 与旧栈**不等价**；
            //     这是本轮闸门（`LegacyHttpContractTest.unknownPathIsNotFound`）抓出来的：
            //     该判据锁的"未知路径 ⇒ 404，不得回落"是**对的**，错的是实现。
            int lastSlash = path.lastIndexOf('/');
            String prefix = lastSlash > 0 ? path.substring(0, lastSlash) : "";
            String tail = path.substring(lastSlash + 1);
            Entry byIndex = findRouteByPath(entries, prefix);
            if (byIndex != null) {
                Method index = findAction(byIndex.controllerClass, "index");
                if (index != null) {
                    method = index;
                    urlPara = tail;
                    // 命中控制器可能是**另一条路由**（空前缀 = 根路由兜底）⇒ 必须换掉 hit
                    hit = byIndex;
                }
            }
        }
        if (method == null) {
            return null;
        }

        Match match = new Match();
        match.entry = hit;
        match.actionKey = actionKey;
        match.urlPara = urlPara;
        match.method = method;
        return match;
    }

    /**
     * 按【已注册路由路径】精确查条目（JFinal 退化规则第 ② 步用：前缀必须是**真实存在**的动作键）。
     *
     * <p>为什么必须精确匹配、而不是像首版那样"就地继续剥段"：旧栈实测多段未知路径是 **404**
     * （`/a/b`、`/definitely/not/a/route`），只有前缀**恰好是某条已注册路由**时才落该控制器的
     * `index()` + urlPara（`/app/meta_product` ⇒ `/app`；`/zzz_unknown` ⇒ 空前缀 = 根路由 `/`）。</p>
     *
     * @param entries 路由索引
     * @param prefix  待查前缀；空串代表根路由（旧 jfinal 把 {@code me.add("/", X.class)} 的 index 键规范成根）
     * @return 命中的路由条目；无则 null（⇒ 调用方 404）
     */
    private static Entry findRouteByPath(List<Entry> entries, String prefix) {
        String key = prefix.isEmpty() ? "/" : prefix;
        for (Entry e : entries) {
            if (key.equals(e.controllerPath)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 找动作方法：该控制器（含继承）上的**公开、无参、方法名等于 actionKey** 的方法。
     *
     * @param controllerClass 控制器类型
     * @param actionKey       动作键
     * @return 命中方法；无则 null
     */
    private static Method findAction(Class<? extends LegacyController> controllerClass, String actionKey) {
        if (actionKey == null || actionKey.isEmpty()) {
            return null;
        }
        // getMethods() 已含继承的公开方法 ⇒ 对应旧栈 mappingSuperClass=true 的"支持注册父类 Action"
        return Stream.of(controllerClass.getMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && m.getParameterCount() == 0)
                .filter(m -> m.getName().equals(actionKey))
                .filter(m -> !m.getDeclaringClass().equals(Object.class))
                .findFirst()
                .orElse(null);
    }

    /**
     * 查找动作处理器：命中则暂存匹配结果并交出 {@link LegacyActionHandler}；未命中返回 null。
     *
     * @param request 请求
     * @return 动作处理器；未命中 null（⇒ DispatcherServlet 原生 404，不回落 SPA）
     */
    @Override
    protected Object getHandlerInternal(HttpServletRequest request) {
        String path = RequestPath.of(request);
        Match match = match(entries, path);
        if (match == null) {
            log.info("404 Action Not Found: {}", path);
            return null;
        }
        request.setAttribute(MATCH_ATTRIBUTE, match);
        return handler;
    }
}
