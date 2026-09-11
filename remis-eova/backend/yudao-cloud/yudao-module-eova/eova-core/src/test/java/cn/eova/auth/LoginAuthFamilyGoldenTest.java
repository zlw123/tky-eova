/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.auth;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.eova.common.base.BaseController;
import cn.eova.compat.jfinal.aop.LegacyBefore;
import cn.eova.compat.jfinal.aop.LegacyClear;
import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInvocation;
import cn.eova.compat.jfinal.core.LegacyAction;
import cn.eova.compat.jfinal.core.LegacyActionException;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.render.DefaultLegacyRenderFactory;
import cn.eova.compat.render.LegacyHtmlRender;
import cn.eova.compat.render.LegacyJsonRender;
import cn.eova.compat.render.LegacyRedirectRender;
import cn.eova.compat.render.LegacyRenderFactory;
import cn.eova.compat.render.LegacyRenderManager;
import cn.eova.config.EovaConfig;
import cn.eova.config.WebRoutes;
import cn.eova.db.EovaDbGateway;
import cn.eova.db.EovaGateways;
import cn.eova.interceptor.LoginInterceptor;
import cn.eova.model.User;
import cn.eova.ops.OpsConst;
import cn.eova.ops.OpsController;
import cn.eova.ops.OpsInterceptor;
import cn.eova.service.LoginService;
import cn.eova.service.biz;
import cn.eova.service.sm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由/登录族 4 单元（第 78 轮 port：{@code LoginInterceptor} 129 + {@code AuthInterceptor} 121
 * + {@code WebRoutes} 21 + {@code OpsController} 59）的判据。
 *
 * <p><b>为什么这一族必须重判据：</b>它们是<b>整站的准入面</b> ——
 * 漏一条"免登录放行"就是登录页 401 死循环；多一条就是越权。
 * 判据因此按"<b>每条分支的决策结果</b>"组织：放行（action 真被执行）/ 401 / 403 / 503 /
 * 跳登录页 / 渲染提示，逐条断言，并覆盖两个"分流判据"（Ajax 与同步、超管与普通用户）。</p>
 *
 * <p><b>环境纪律（R58）</b>：本判据触碰多处全局静态状态
 * （{@code OpsConst}、{@code LoginInterceptor.excludes}、{@code AuthUri.loginAuth}、
 * {@code EovaConfig.getAuthUris()}、{@code sm.login}、渲染工厂），
 * 每个用例后<b>逐项还原</b>，避免"单跑绿、全量红"。</p>
 */
class LoginAuthFamilyGoldenTest {

    /** 记录器（探针用） */
    static final class Rec {
        String removedCookie;
        final List<String> calls = new ArrayList<>();
    }

    /** 请求替身：只实现判据用到的入口；<b>属性表是有状态的</b>（读回 set 的值才可能被判据断言） */
    static HttpServletRequest request(String uri, String url, String ajaxHeader) {
        Map<String, Object> attrs = new HashMap<>();
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getRequestURI":
                    return uri;
                case "getRequestURL":
                    return new StringBuffer(url);
                case "getHeader":
                    return "X-Requested-With".equals(args[0]) ? ajaxHeader : null;
                case "getRemoteAddr":
                    return "127.0.0.1";
                case "setAttribute":
                    attrs.put((String) args[0], args[1]);
                    return null;
                case "getAttribute":
                    return attrs.get(args[0]);
                case "removeAttribute":
                    attrs.remove(args[0]);
                    return null;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                LoginAuthFamilyGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, h);
    }

    /** 响应替身：记录 addHeader（供 setCross 断言） */
    static HttpServletResponse response(List<String[]> headers) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "addHeader":
                    headers.add(new String[]{(String) args[0], (String) args[1]});
                    return null;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (HttpServletResponse) Proxy.newProxyInstance(
                LoginAuthFamilyGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, h);
    }

    /** 控制器探针：把请求/会话相关入口替换成可控值（renderError 刻意不替换 —— 它是断言目标） */
    public static class ProbeCtrl extends BaseController {

        final Rec rec = new Rec();

        final Map<String, String> params = new HashMap<>();

        User user;

        String uri = "/test/act";

        String url = "http://localhost:8080/test/act";

        String ajaxHeader;

        @Override
        public User getUser() {
            return user;
        }

        @Override
        public String SID() {
            return "sid-probe";
        }

        @Override
        public LegacyController removeCookie(String name) {
            rec.removedCookie = name;
            return this;
        }

        @Override
        public String get(String name) {
            return params.get(name);
        }

        @Override
        public String get(String name, String defaultValue) {
            String v = params.get(name);
            return v == null ? defaultValue : v;
        }

        @Override
        public Integer getInt(String name, Integer defaultValue) {
            String v = params.get(name);
            return v == null ? defaultValue : Integer.valueOf(v);
        }

        /**
         * 把请求替身注入到真实字段（{@code ctrl.set(...)} / {@code renderError} 等都要用它）。
         */
        void attachRequest() {
            setHttpServletRequest(request(uri, url, ajaxHeader));
        }

        /** 探针 action：记录被真正执行 */
        public void act() {
            rec.calls.add("act");
        }
    }

    /** 记录用户被钩子处理过 */
    static final class ProbeSessionIntercept implements cn.eova.aop.UserSessionIntercept {

        boolean loginCalled;

        @Override
        public String loginBefore(User user) {
            return null;
        }

        @Override
        public void login(User user) {
            loginCalled = true;
        }

        @Override
        public void logout(User user) {
        }

        @Override
        public void su(User user, com.alibaba.fastjson.JSONObject switchUser) {
        }
    }

    /** 记录 {@code sm.login.loginBySid} 的替身 */
    static class ProbeLoginService extends LoginService {

        User result;

        int calls;

        @Override
        public User loginBySid(String sid, String ip) {
            calls++;
            return result;
        }
    }

    private LegacyRenderFactory savedFactory;

    private cn.eova.compat.cache.CacheService savedCacheService;

    private LoginService savedLoginService;

    private final List<String> savedExcludes = new ArrayList<>();

    private final Set<String> savedLoginAuth = new HashSet<>();

    private final Map<Integer, Set<String>> savedAuthUris = new HashMap<>();

    private boolean savedWaiting;

    private String savedWaitInfo;

    private String savedDeployVer;

    private String savedDeployGit;

    private String savedDeployTime;

    @BeforeEach
    void setUp() {
        // getRenderFactory() 在未装配时【抛异常】（isReady() 才是探测口），故必须先用 isReady 判定
        savedFactory = LegacyRenderManager.isReady() ? LegacyRenderManager.getRenderFactory() : null;
        LegacyRenderManager.setRenderFactory(new DefaultLegacyRenderFactory());

        savedLoginService = biz.login;
        savedExcludes.addAll(LoginInterceptor.excludes);
        savedLoginAuth.addAll(AuthUri.loginAuth);
        savedAuthUris.putAll(EovaConfig.getAuthUris());
        savedWaiting = OpsConst.WAITING;
        savedWaitInfo = OpsConst.WAIT_INFO;
        savedDeployVer = OpsConst.DEPLOY_VER;
        savedDeployGit = OpsConst.DEPLOY_GIT;
        savedDeployTime = OpsConst.DEPLOY_TIME;

        OpsConst.WAITING = false;
        OpsConst.WAIT_INFO = "系统临时维护，请稍候再试！";
        EovaConfig.getAuthUris().clear();

        // 缓存服务必须换成自带替身：LoginService.update → LegacyCacheKit 走全局 EhCache 单例，
        // 而同 JVM 里别的用例会把它 shut down ⇒ 不注入就是"单跑绿、全量红"（R58/R49）
        savedCacheService = cn.eova.compat.cache.CacheServices.get();
        cn.eova.compat.cache.CacheServices.set(new cn.eova.testkit.MemoryCacheService());
    }

    @AfterEach
    void tearDown() {
        if (savedFactory != null) {
            LegacyRenderManager.setRenderFactory(savedFactory);
        } else {
            LegacyRenderManager.clear();
        }
        biz.login = savedLoginService;
        LoginInterceptor.excludes.clear();
        LoginInterceptor.excludes.addAll(savedExcludes);
        AuthUri.loginAuth.clear();
        AuthUri.loginAuth.addAll(savedLoginAuth);
        EovaConfig.getAuthUris().clear();
        EovaConfig.getAuthUris().putAll(savedAuthUris);
        OpsConst.WAITING = savedWaiting;
        OpsConst.WAIT_INFO = savedWaitInfo;
        OpsConst.DEPLOY_VER = savedDeployVer;
        OpsConst.DEPLOY_GIT = savedDeployGit;
        OpsConst.DEPLOY_TIME = savedDeployTime;
        EovaConfig.setUserSessionIntercept(null);
        EovaGateways.clear();
        cn.eova.compat.cache.CacheServices.set(savedCacheService);
    }

    /**
     * 造一个 invocation（拦截器链为空 ⇒ 放行时会真正执行 action）。
     *
     * @param ctrl 控制器
     * @return invocation
     * @throws Exception 反射失败
     */
    private LegacyInvocation invocation(ProbeCtrl ctrl) throws Exception {
        ctrl.attachRequest();
        Method m = ProbeCtrl.class.getMethod("act");
        LegacyAction action = new LegacyAction("/test/act", "/test", ProbeCtrl.class, m, "act",
                new LegacyInterceptor[0], "/test");
        return new LegacyInvocation(action, ctrl);
    }

    /**
     * 造一个用户。
     *
     * @param rid       角色 ID
     * @param auths     授权 URI 集合（null 表示未分配）
     * @param freshAuth 是否把字段权限时间戳设为【当前】（跳过 5 秒节流分支，避免触库）
     * @return 用户
     */
    private static User user(int rid, Set<String> auths, boolean freshAuth) {
        User u = new User();
        u.put("id", 9);
        u.put("rid", rid);
        u.put("company_id", 1);
        if (auths != null) {
            u.put("auths", auths);
        }
        if (freshAuth) {
            u.put(AuthInterceptor.UPDATE_FIELD_AUTH, System.currentTimeMillis());
        }
        return u;
    }

    /**
     * 注册返回空结果的网关替身（AuthUri.build 在未授权分支会被调用）。
     */
    private static void registerEmptyGateway() {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "find":
                case "query":
                    return new ArrayList<>();
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        EovaGateways.register("eova", (EovaDbGateway) Proxy.newProxyInstance(
                LoginAuthFamilyGoldenTest.class.getClassLoader(),
                new Class<?>[]{EovaDbGateway.class}, h));
    }

    // ------------------------------------------------------------ WebRoutes / OpsController

    @Test
    @DisplayName("WebRoutes：拦截器顺序必须是 登录 → 鉴权")
    void webRoutesOrder() {
        WebRoutes routes = new WebRoutes();
        routes.config();
        LegacyInterceptor[] inters = routes.getInterceptors();
        assertEquals(2, inters.length);
        assertInstanceOf(LoginInterceptor.class, inters[0], "登录拦截器必须在前");
        assertInstanceOf(AuthInterceptor.class, inters[1], "鉴权拦截器必须在后");
        assertTrue(routes.getRouteItemList().isEmpty(), "WebRoutes 只挂拦截器，不注册路由");
    }

    @Test
    @DisplayName("OpsController：类级 @Clear(登录+鉴权) + @Before(OpsInterceptor)")
    void opsControllerAnnotations() throws Exception {
        LegacyClear clear = OpsController.class.getAnnotation(LegacyClear.class);
        assertNotNull(clear, "运维接口必须显式清除登录/鉴权拦截器（否则维护态下无法恢复）");
        List<Class<? extends LegacyInterceptor>> cleared = List.of(clear.value());
        assertTrue(cleared.contains(LoginInterceptor.class), cleared.toString());
        assertTrue(cleared.contains(AuthInterceptor.class), cleared.toString());

        LegacyBefore before = OpsController.class.getAnnotation(LegacyBefore.class);
        assertNotNull(before);
        assertEquals(OpsInterceptor.class, before.value()[0]);
    }

    @Test
    @DisplayName("OpsController：info 回传版本/时间；pause 切维护态；update 写内存常量")
    void opsControllerActions() throws Exception {
        OpsConst.DEPLOY_VER = "1.2.3";
        OpsConst.DEPLOY_TIME = "2026-01-01 00:00:00";

        // ① info：把两个内存常量渲染成 json
        OpsController info = new OpsController();
        info.setHttpServletRequest(paramRequest(new HashMap<>()));
        info.info();
        String infoJson = jsonOf(info);
        assertTrue(infoJson.contains("1.2.3"), infoJson);
        assertTrue(infoJson.contains("2026-01-01 00:00:00"), infoJson);

        // ② pause：无 flag ⇒ 恢复态；有 flag=1 与 info ⇒ 维护态 + 自定义文案
        OpsController pause = new OpsController();
        pause.setHttpServletRequest(paramRequest(new HashMap<>()));
        pause.pause();
        assertFalse(OpsConst.WAITING, "无 flag 参数时 flag=0 ⇒ 不暂停");
        assertTrue(jsonOf(pause).contains("服务已恢复"), jsonOf(pause));

        Map<String, String> pms = new HashMap<>();
        pms.put("flag", "1");
        pms.put("info", "维护中，请稍后再试");
        OpsController pause2 = new OpsController();
        pause2.setHttpServletRequest(paramRequest(pms));
        pause2.pause();
        assertTrue(OpsConst.WAITING);
        assertEquals("维护中，请稍后再试", OpsConst.WAIT_INFO);
        assertTrue(jsonOf(pause2).contains("服务已暂停"), jsonOf(pause2));

        // ③ update：写三个内存常量（旧实现不落盘）
        Map<String, String> up = new HashMap<>();
        up.put("ver", "9.9.9");
        up.put("git", "abc1234");
        OpsController update = new OpsController();
        update.setHttpServletRequest(paramRequest(up));
        update.update();
        assertEquals("9.9.9", OpsConst.DEPLOY_VER);
        assertEquals("abc1234", OpsConst.DEPLOY_GIT);
        assertTrue(OpsConst.DEPLOY_TIME.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "部署时间走 x.time.formatNowTime()：" + OpsConst.DEPLOY_TIME);
    }

    /** 取控制器上 json 渲染的文本 */
    private static String jsonOf(BaseController ctrl) {
        LegacyJsonRender r = assertInstanceOf(LegacyJsonRender.class, ctrl.getRender());
        return r.getJsonText();
    }

    /** 参数请求替身 */
    private static HttpServletRequest paramRequest(Map<String, String> params) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getParameter":
                    return params.get(args[0]);
                case "getAttribute":
                    return null;
                case "getHeader":
                    return null;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                LoginAuthFamilyGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, h);
    }

    // ------------------------------------------------------------ LoginInterceptor

    @Test
    @DisplayName("LoginInterceptor：excludes 四条免登录 URI + setCross 两个响应头")
    void loginInterceptorSurface() {
        assertEquals(List.of("/user/captcha", "/user/login", "/user/doLogin", "/user/logout"),
                LoginInterceptor.excludes, "免登录 URI 顺序与内容属对外契约");

        List<String[]> headers = new ArrayList<>();
        LoginInterceptor.setCross(response(headers));
        assertEquals(2, headers.size());
        assertEquals("Access-Control-Allow-Origin", headers.get(0)[0]);
        assertEquals("*", headers.get(0)[1]);
        assertEquals("Access-Control-Allow-Credentials", headers.get(1)[0]);
        assertEquals("true", headers.get(1)[1]);
    }

    @Test
    @DisplayName("LoginInterceptor：免登录 URI 直接放行（action 真执行）")
    void loginInterceptorExcludedPassesThrough() throws Exception {
        ProbeCtrl ctrl = new ProbeCtrl();
        ctrl.uri = "/user/login";
        ctrl.user = null;
        LegacyInvocation inv = invocation(ctrl);
        new LoginInterceptor().intercept(inv);
        assertEquals(List.of("act"), ctrl.rec.calls, "免登录 URI 必须直接放行到 action");
        assertNull(ctrl.getRender(), "放行路径不得设置渲染");
    }

    @Test
    @DisplayName("LoginInterceptor：已登录 ⇒ 续传 USER 并放行")
    void loginInterceptorLoggedIn() throws Exception {
        ProbeCtrl ctrl = new ProbeCtrl();
        ctrl.user = user(2, new HashSet<>(), true);
        new LoginInterceptor().intercept(invocation(ctrl));
        assertEquals(List.of("act"), ctrl.rec.calls);
        assertEquals(ctrl.user, ctrl.getAttr(LoginService.USER), "必须把登录用户续传到请求属性");
    }

    @Test
    @DisplayName("LoginInterceptor：SID 失效 + 同步请求 ⇒ 清 Cookie 并跳登录页（带 back）")
    void loginInterceptorSidFailRedirect() throws Exception {
        ProbeLoginService probe = new ProbeLoginService();
        probe.result = null;
        biz.login = probe;

        ProbeCtrl ctrl = new ProbeCtrl();
        ctrl.user = null;
        ctrl.ajaxHeader = null;
        new LoginInterceptor().intercept(invocation(ctrl));

        assertEquals(1, probe.calls);
        assertEquals("eovasid", LoginService.CKSID);
        assertEquals("eovasid", ctrl.rec.removedCookie, "失效时必须销毁 SID Cookie");
        assertEquals(List.of(), ctrl.rec.calls, "不得放行");
        LegacyRedirectRender redirect = assertInstanceOf(LegacyRedirectRender.class, ctrl.getRender());
        assertEquals("/user/login?back=" + ctrl.url, redirect.buildFinalUrl(),
                "同步请求跳登录页，且 back 用 getRequestURL()（绝对地址）");
    }

    @Test
    @DisplayName("LoginInterceptor：SID 失效 + Ajax 请求 ⇒ 401（不跳页）")
    void loginInterceptorSidFailAjax() throws Exception {
        ProbeLoginService probe = new ProbeLoginService();
        probe.result = null;
        biz.login = probe;

        ProbeCtrl ctrl = new ProbeCtrl();
        ctrl.user = null;
        ctrl.ajaxHeader = "XMLHttpRequest";
        LegacyActionException ex = assertThrows(LegacyActionException.class,
                () -> new LoginInterceptor().intercept(invocation(ctrl)));
        assertEquals(401, ex.getErrorCode());
        assertEquals("eovasid", ctrl.rec.removedCookie);
        assertEquals(List.of(), ctrl.rec.calls, "不得放行");
    }

    @Test
    @DisplayName("LoginInterceptor：SID 换到用户 ⇒ 走会话钩子 + 刷新缓存 + 放行")
    void loginInterceptorSidSuccess() throws Exception {
        User u = user(2, new HashSet<>(), true);
        ProbeLoginService probe = new ProbeLoginService();
        probe.result = u;
        biz.login = probe;

        ProbeSessionIntercept hook = new ProbeSessionIntercept();
        EovaConfig.setUserSessionIntercept(hook);

        ProbeCtrl ctrl = new ProbeCtrl();
        ctrl.user = null;
        new LoginInterceptor().intercept(invocation(ctrl));

        assertTrue(hook.loginCalled, "会话钩子必须被调用");
        assertEquals(u, ctrl.getAttr(LoginService.USER));
        assertEquals(List.of("act"), ctrl.rec.calls, "换到用户后必须放行");
    }

    // ------------------------------------------------------------ AuthInterceptor

    @Test
    @DisplayName("AuthInterceptor：未登录/超管/维护态/免鉴权 URI 的放行与拦截")
    void authInterceptorFrontGates() throws Exception {
        AuthInterceptor inter = new AuthInterceptor();

        // ① 未登录：放行（登录交给 LoginInterceptor 处理）
        ProbeCtrl anon = new ProbeCtrl();
        anon.user = null;
        inter.intercept(invocation(anon));
        assertEquals(List.of("act"), anon.rec.calls);

        // ② 超管：放行
        ProbeCtrl admin = new ProbeCtrl();
        admin.user = user(1, null, false);
        assertTrue(admin.user.isAdmin(), "rid=1 必须被识别为超管");
        inter.intercept(invocation(admin));
        assertEquals(List.of("act"), admin.rec.calls);

        // ③ 维护态：503（且 renderError 是抛异常，故后续 invoke 不可达）
        OpsConst.WAITING = true;
        OpsConst.WAIT_INFO = "维护中";
        ProbeCtrl waiting = new ProbeCtrl();
        waiting.user = user(2, new HashSet<>(List.of("/**")), true);
        LegacyActionException ex503 = assertThrows(LegacyActionException.class,
                () -> inter.intercept(invocation(waiting)));
        assertEquals(503, ex503.getErrorCode());
        assertEquals("维护中", waiting.getAttr("info"), "维护文案必须续传到 info");
        assertEquals(List.of(), waiting.rec.calls, "维护态不得放行");
        OpsConst.WAITING = false;
    }

    @Test
    @DisplayName("AuthInterceptor：未分配权限 ⇒ 渲染提示（不 403）")
    void authInterceptorNoAuths() throws Exception {
        ProbeCtrl ctrl = new ProbeCtrl();
        ctrl.user = user(2, null, false);
        new AuthInterceptor().intercept(invocation(ctrl));

        assertEquals(List.of(), ctrl.rec.calls);
        LegacyHtmlRender html = assertInstanceOf(LegacyHtmlRender.class, ctrl.getRender());
        assertTrue(html.getText().contains("用户未分配权限"), html.getText());
        assertTrue(html.getText().contains("eova.render.css"), "必须带 eova 样式表：" + html.getText());
    }

    @Test
    @DisplayName("AuthInterceptor：授权命中 ⇒ 放行（含角色自定义授权追加）")
    void authInterceptorAuthorized() throws Exception {
        ProbeCtrl ctrl = new ProbeCtrl();
        Set<String> auths = new HashSet<>(List.of("/nomatch/**"));
        ctrl.user = user(2, auths, true);
        EovaConfig.getAuthUris().put(2, new HashSet<>(List.of("/test/**")));

        new AuthInterceptor().intercept(invocation(ctrl));
        assertEquals(List.of("act"), ctrl.rec.calls, "角色自定义授权命中必须放行");
        assertTrue(auths.contains("/test/**"), "旧实现会把角色授权【就地追加】进用户的 auths：" + auths);
    }

    @Test
    @DisplayName("AuthInterceptor：未授权 ⇒ /api/table/query 走 JSON 提示，其余 403")
    void authInterceptorDenied() throws Exception {
        registerEmptyGateway();

        // ① Ajax 友好路径
        ProbeCtrl ajax = new ProbeCtrl();
        ajax.user = user(2, new HashSet<>(List.of("/nomatch/**")), true);
        ajax.uri = "/api/table/query";
        new AuthInterceptor().intercept(invocation(ajax));
        assertEquals(List.of(), ajax.rec.calls);
        String body = jsonOf(ajax);
        assertTrue(body.contains("未授权资源, 请检查权限配置:" + ajax.uri), body);

        // ② 其余路径：403
        ProbeCtrl page = new ProbeCtrl();
        page.user = user(2, new HashSet<>(List.of("/nomatch/**")), true);
        page.uri = "/test/act";
        LegacyActionException ex = assertThrows(LegacyActionException.class,
                () -> new AuthInterceptor().intercept(invocation(page)));
        assertEquals(403, ex.getErrorCode());
        assertEquals(page.uri, page.getAttr("info"), "403 时把 uri 传给 info");
        assertEquals(List.of(), page.rec.calls);
    }

    @Test
    @DisplayName("AuthInterceptor：免登录/登录后免鉴权 URI 放行")
    void authInterceptorWhiteLists() throws Exception {
        AuthInterceptor inter = new AuthInterceptor();

        ProbeCtrl excluded = new ProbeCtrl();
        excluded.user = user(2, new HashSet<>(List.of("/nomatch/**")), true);
        excluded.uri = "/user/logout";
        inter.intercept(invocation(excluded));
        assertEquals(List.of("act"), excluded.rec.calls, "LoginInterceptor.excludes 命中也免鉴权");

        ProbeCtrl loginAuth = new ProbeCtrl();
        loginAuth.user = user(2, new HashSet<>(List.of("/nomatch/**")), true);
        loginAuth.uri = "/some/loginonly/page";
        AuthUri.loginAuth.add("/some/**");
        try {
            inter.intercept(invocation(loginAuth));
            assertEquals(List.of("act"), loginAuth.rec.calls, "AuthUri.loginAuth 命中也免鉴权");
        } finally {
            AuthUri.loginAuth.remove("/some/**");
        }
    }

    @Test
    @DisplayName("AuthInterceptor：字段权限 5 秒节流（未超时 ⇒ 不刷新、不回写时间戳）")
    void authInterceptorFieldAuthThrottle() throws Exception {
        ProbeCtrl ctrl = new ProbeCtrl();
        long now = System.currentTimeMillis();
        User u = user(2, new HashSet<>(List.of("/**")), true);
        u.put(AuthInterceptor.UPDATE_FIELD_AUTH, now);
        ctrl.user = u;

        new AuthInterceptor().intercept(invocation(ctrl));
        assertEquals(List.of("act"), ctrl.rec.calls);
        assertEquals(now, ((Number) u.get(AuthInterceptor.UPDATE_FIELD_AUTH)).longValue(),
                "5 秒内不得回写时间戳");
        assertTrue(u.getDisableFields().isEmpty(), "5 秒内不得刷新禁用字段");
    }

    @Test
    @DisplayName("AuthInterceptor：UPDATE_FIELD_AUTH 常量名属契约")
    void authInterceptorConstant() {
        assertEquals("update_field_auth", AuthInterceptor.UPDATE_FIELD_AUTH);
    }

    /** 断言探针类可被 LegacyAction 反射执行（防"判据空洞"） */
    @Test
    @DisplayName("判据自检：探针 action 可被 invocation 执行")
    void probeActionIsInvocable() throws Exception {
        ProbeCtrl ctrl = new ProbeCtrl();
        LegacyInvocation inv = invocation(ctrl);
        inv.invoke();
        assertEquals(List.of("act"), ctrl.rec.calls);
    }
}
