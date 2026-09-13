/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInterceptorManager;
import cn.eova.compat.jfinal.aop.LegacyInvocation;
import cn.eova.compat.jfinal.config.LegacyJFinalBoot;
import cn.eova.compat.jfinal.config.LegacyRoutes;
import cn.eova.compat.jfinal.core.LegacyAction;
import cn.eova.compat.jfinal.core.LegacyActionException;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.jfinal.core.paragetter.LegacyJsonRequest;
import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyRenderManager;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * **Eova 自有 Web 层的请求分发器（切片 S2，第 248 轮）** —— 新栈的"HTTP 容器接线"。
 *
 * <p><b>它做什么</b>：把 URL 解析成旧语义的 {@link LegacyAction}，交给 {@link LegacyInvocation}
 * 跑完拦截器链并调用动作，最后让控制器持有的 {@link LegacyRender} 写响应。**MVC 语义不重写**，
 * 只做接线（设计见 {@code docs/DES-005-R1-eova-web-layer-design.md} §4/§5）。</p>
 *
 * <p><b>解析口径（与旧 jfinal 一致，逐条有据）</b>：</p>
 * <ol>
 *   <li><b>路由表</b> = **顶层 Routes（{@code boot.getRoutes()}）+ 静态 {@code routesList}**（被
 *       {@code add(Routes)} 加进来的子路由）。r246 实测：只取其一都会漏（顶层漏子路由；
 *       静态列表漏顶层）⇒ 实测完整表 23 条（含 {@code /user}、{@code /api/home}）。</li>
 *   <li><b>最长前缀匹配</b>：路径等于某 controllerPath，或以其 + "/" 开头；多个命中取**最长**者
 *       （旧栈即按注册路径前缀匹配）。</li>
 *   <li><b>动作键</b> = 该控制器上的**公开无参方法名**（旧 jfinal actionKey 口径；该前提已由
 *       {@code MvcFoundationGoldenTest.allEovaActionMethodsAreNoArg} 机器校验）。</li>
 *   <li><b>urlPara</b> = 动作键之后的剩余段，以 {@code "/"} 连接（旧 {@code setUrlPara} 口径）。</li>
 *   <li><b>未命中 ⇒ 404</b>，日志文案对齐旧栈（{@code 404 Action Not Found: <path>}，r175 的旧 demo
 *       日志里见过该行）。**不得静默回落到 SPA** —— 那会把路由错误伪装成正常页面。</li>
 *   <li><b>拦截器链</b> = 全局（{@code boot.getInterceptors().getInterceptors()}，实测只有
 *       {@code ExceptionInterceptor}）+ **命中路由所属 Routes 对象上的**拦截器
 *       （{@code configRoute} 里 {@code me.addInterceptor(new LoginInterceptor()/AuthInterceptor())}
 *       落在 Routes 上）。顺序：全局在前、路由在后（旧 jfinal 同口径）。</li>
 * </ol>
 *
 * <p><b>本切片不做（如实登记）</b>：模板渲染（默认视图）、静态资源、上传（multipart 注入）、
 * 以及"动作只写了一半就抛"的容器级收尾语义 —— 属 S3/S4 及后续；S2 的判据只覆盖 JSON 端点。</p>
 */
// ★ 必须是 @RestController 而不是 @Component：Spring MVC 只把 @Controller/@RestController
//   里的 @RequestMapping 注册为处理器方法（实测：用 @Component 时所有请求都被 Spring 判 404）。
@RestController
public class LegacyDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LegacyDispatcher.class);

    private final LegacyJFinalBoot boot;
    /** 已解析的 (controllerPath, Routes) 对照表，按路径长度降序（最长前缀优先） */
    private final List<Entry> entries = new ArrayList<>();

    /** 旧静态空间 {@code /eova/**} 的供给组件（切片 S3） */
    private final LegacyStaticAssets staticAssets;

    public LegacyDispatcher(LegacyJFinalBoot boot, LegacyStaticAssets staticAssets) {
        this.boot = boot;
        this.staticAssets = staticAssets;
    }

    /** 一条路由所属的 Routes 对象（拦截器从它取） */
    private static final class Entry {
        final String controllerPath;
        final Class<? extends LegacyController> controllerClass;
        final LegacyInterceptor[] routeInters;

        Entry(LegacyRoutes.Route route, LegacyRoutes owner) {
            this.controllerPath = route.getControllerPath();
            this.controllerClass = route.getControllerClass();
            this.routeInters = owner.getInterceptors();
        }
    }

    /** 启动后建索引：顶层 + 静态子路由，去重后按路径长度降序 */
    @PostConstruct
    void indexRoutes() {
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
     * 全路径分发入口（catch-all）。未命中路由 ⇒ 404（不回落到 SPA）。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @throws IOException 响应写出失败
     */
    @RequestMapping("/**")
    public void dispatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        // 去掉尾部 "/"（根路径除外）
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        // ★ 静态空间优先（切片 S3）—— 旧栈顺序是"资源处理器先于 jfinal 动作"：
        //   ① 静态空间**只有** `/eova/**`（旧 demo 实测：`/eova/lib/**`、`/eova/ui/**` 200，
        //      而 `/ui/**`、`/_eova/**` 404 ⇒ 来源是 classpath 的 `webapp/eova/**`）；
        //   ② **文件真实存在**才直出，否则继续走动作路由（旧栈 resource handler miss 后交给动作层）
        //      ⇒ 动作 URL（`/eova/admin` 等）不受影响。
        if (staticAssets.serve(path, response)) {
            return;
        }

        Entry hit = null;
        for (Entry e : entries) {
            String p = e.controllerPath;
            if (path.equals(p) || path.startsWith(p.endsWith("/") ? p : p + "/")) {
                hit = e;
                break;
            }
        }
        if (hit == null) {
            log.info("404 Action Not Found: {}", path);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
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
            Entry byIndex = findRouteByPath(prefix);
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
            log.info("404 Action Not Found: {}", path);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 请求级控制器实例 + 上下文注入（旧栈每请求一实例）
        LegacyController controller;
        try {
            controller = hit.controllerClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("控制器无法实例化（需公开无参构造）：" + hit.controllerClass.getName(), e);
        }
        controller.setHttpServletRequest(request);
        controller.setHttpServletResponse(response);
        controller.setUrlPara(urlPara);

        // ★ JSON 请求包装 —— 旧 jfinal {@code ActionHandler} 逐行等价：
        //     if (resolveJson && controller.isJsonRequest())
        //         controller.setHttpServletRequest(jsonRequestFactory.apply(controller.getRawData(), controller.getRequest()));
        //   不包的话 {@code WebUtil.isAjax} 里的 {@code instanceof JsonRequest} 恒为 false，未登录的
        //   JSON 请求就会走"同步跳登录页"分支。旧栈实测（curl 无 Cookie POST /api/home/menu）是
        //   401 + {"state":"fail","msg":"401 Unauthorized"} + 清 Cookie ⇒ 必须补齐这层包装。
        if (boot.getConstants().getResolveJsonRequest() && isJsonRequest(controller.getRequest())) {
            controller.setHttpServletRequest(
                    new LegacyJsonRequest(controller.getRawData(), controller.getRequest()));
        }

        LegacyInterceptor[] chain = buildActionChain(
                boot.getInterceptors().getInterceptors(), hit.routeInters, hit.controllerClass, method);
        LegacyAction action = new LegacyAction(actionKey, hit.controllerPath, hit.controllerClass,
                method, method.getName(), chain, null);

        try {
            new LegacyInvocation(action, controller).invoke();
        } catch (LegacyActionException e) {
            // ★ 错误渲染落在【宿主】这一层 —— 旧 jfinal 的等价物是 ActionHandler.handleActionException：
            //   {@code renderError(code)} 在旧实现里就是"抛 ActionException，由框架渲染"，
            //   而 ExceptionInterceptor（全局中间件）对非 500 的 ActionException 只做原样再抛
            //   （逐行等价 port 里保留了这一行为），所以不在这里渲染，401/403 就会变成 500。
            handleActionException(e, path, controller.getRequest(), response);
            return;
        }

        // 渲染：控制器内 render* 设立的渲染器负责写响应
        LegacyRender render = controller.getRender();
        if (render == null) {
            log.warn("动作 {}#{} 未产生渲染器（S2 尚无默认模板视图）⇒ 按 404 处理",
                    hit.controllerClass.getSimpleName(), method.getName());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        render.setContext(controller.getRequest(), response).render();
    }

    /**
     * 等价旧 jfinal {@code ActionHandler.handleActionException}：按错误码拼日志前缀 → 记录 →
     * 用 {@link LegacyActionException#getErrorRender()} 渲染（为 null 时由渲染工厂补一个）。
     *
     * <p>旧实现里警告/错误两条日志分支取决于"异常是否自带 errorRender"；此处保留该分支语义。</p>
     *
     * @param e       带错误码的动作异常
     * @param target  目标路径（旧实现的 target）
     * @param request  请求
     * @param response 响应
     */
    private void handleActionException(LegacyActionException e, String target,
            HttpServletRequest request, HttpServletResponse response) {
        int errorCode = e.getErrorCode();
        String prefix;
        switch (errorCode) {
            case 404:
                prefix = "404 Not Found: ";
                break;
            case 400:
                prefix = "400 Bad Request: ";
                break;
            case 401:
                prefix = "401 Unauthorized: ";
                break;
            case 403:
                prefix = "403 Forbidden: ";
                break;
            default:
                prefix = errorCode + " Error: ";
                break;
        }
        // 旧实现的 target 拼装：target + (queryString != null ? "?" + queryString : "")
        String queryString = request.getQueryString();
        String url = queryString == null ? target : target + "?" + queryString;
        String msg = prefix + url;
        if (e.getMessage() != null) {
            msg = msg + "\n" + e.getMessage();
        }

        LegacyRender render = e.getErrorRender();
        if (render != null) {
            log.warn(msg);
        } else {
            // 旧实现：无自带 errorRender 时走 error 日志，并由渲染工厂补一个同码错误渲染
            log.error(msg);
            render = LegacyRenderManager.getRenderFactory().getErrorRender(errorCode);
        }
        render.setContext(request, response).render();
    }

    /**
     * 等价旧 jfinal {@code Controller.isJsonRequest()}：已经是包装类型即 true，否则
     * {@code Content-Type} 含 {@code "json"}（**逐字保留旧实现的大小写敏感 {@code indexOf}**）。
     *
     * @param request 原始请求
     * @return 是否按 JSON 请求处理
     */
    private static boolean isJsonRequest(HttpServletRequest request) {
        if (request instanceof LegacyJsonRequest) {
            return true;
        }
        String contentType = request.getContentType();
        return contentType != null && contentType.indexOf("json") != -1;
    }

    /**
     * 按【已注册路由路径】精确查条目（JFinal 退化规则第 ② 步用：前缀必须是**真实存在**的动作键）。
     *
     * <p>为什么必须精确匹配、而不是像首版那样"就地继续剥段"：旧栈实测多段未知路径是 **404**
     * （`/a/b`、`/definitely/not/a/route`），只有前缀**恰好是某条已注册路由**时才落该控制器的
     * `index()` + urlPara（`/app/meta_product` ⇒ `/app`；`/zzz_unknown` ⇒ 空前缀 = 根路由 `/`）。</p>
     *
     * @param prefix 待查前缀；空串代表根路由（旧 jfinal 把 {@code me.add("/", X.class)} 的 index 键规范成根）
     * @return 命中的路由条目；无则 null（⇒ 调用方 404）
     */
    private Entry findRouteByPath(String prefix) {
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
    static LegacyInterceptor[] buildActionChain(java.util.List<LegacyInterceptor> globalInters,
                                               LegacyInterceptor[] routeInters,
                                               Class<? extends cn.eova.compat.jfinal.core.LegacyController> controllerClass,
                                               java.lang.reflect.Method method) {
        return LegacyInterceptorManager.buildControllerActionInterceptor(
                globalInters == null ? null : globalInters.toArray(new LegacyInterceptor[0]),
                routeInters,
                LegacyInterceptorManager.createControllerInterceptor(controllerClass),
                controllerClass,
                method);
    }
}
