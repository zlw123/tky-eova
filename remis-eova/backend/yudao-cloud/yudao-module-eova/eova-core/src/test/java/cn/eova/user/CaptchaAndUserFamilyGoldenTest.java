/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.user;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.eova.EovaWebRoutes;
import cn.eova.common.base.BaseController;
import cn.eova.compat.cache.CacheServices;
import cn.eova.compat.cache.LegacyCacheKit;
import cn.eova.compat.jfinal.captcha.LegacyCaptcha;
import cn.eova.compat.jfinal.captcha.LegacyCaptchaCache;
import cn.eova.compat.jfinal.captcha.LegacyCaptchaManager;
import cn.eova.compat.jfinal.captcha.LegacyCaptchaRender;
import cn.eova.compat.jfinal.config.LegacyRoutes;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.jfinal.kit.LegacyRet;
import cn.eova.compat.render.DefaultLegacyRenderFactory;
import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyRenderFactory;
import cn.eova.compat.render.LegacyRenderManager;
import cn.eova.config.EovaConfig;
import cn.eova.core.admin.AdminController;
import cn.eova.core.auth.AuthController;
import cn.eova.core.button.ButtonController;
import cn.eova.core.dict.DictController;
import cn.eova.core.HomeController;
import cn.eova.core.menu.MenuController;
import cn.eova.core.task.TaskController;
import cn.eova.core.sse.SSEController;
import cn.eova.meta.api.FormControler;
import cn.eova.meta.api.MetaControler;
import cn.eova.meta.api.TableController;
import cn.eova.meta.api.WidgetController;
import cn.eova.model.User;
import cn.eova.ops.OpsController;
import cn.eova.service.LoginService;
import cn.eova.service.biz;
import cn.eova.service.sm;
import cn.eova.template.single.SingleController;
import cn.eova.testkit.MemoryCacheService;
import cn.eova.tools.x;
import cn.eova.widget.tree.TreeController;
import cn.eova.widget.upload.UploadController;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证码接缝 + 用户控制器 + Web 路由总表（第 81 轮）的判据。
 *
 * <p><b>为什么验证码接缝要单独重判：</b>它是<b>安全相关</b>且<b>一次性</b>的 ——
 * 校验成功必须同时（a）移除缓存项、（b）移除 Cookie；过期或大小写处理错一处，
 * 要么形同虚设（可重放），要么永远登不进去。判据因此把"值的字符集/长度/有效期"、
 * "Cookie 名与三个属性"、"响应头与内容类型"、"一次性移除"逐条钉住。</p>
 *
 * <p><b>本轮判不了的部分（如实标注）：</b>验证码图片的<b>像素级一致性不声称、不验收</b>
 * （旧侧产物本身是随机图，且不构成调用方契约 —— 见 {@link LegacyCaptchaRender} 类注释）；
 * {@code UserController} 的 {@code reset()}/{@code doPassword()} 需要 baseline 库，记为 pending。</p>
 *
 * <p><b>环境纪律（R58）</b>：验证码管理器、缓存服务、渲染工厂、{@code x.conf} 都是全局静态，
 * 逐项在 {@code @AfterEach} 还原。</p>
 */
class CaptchaAndUserFamilyGoldenTest {

    /** 记录 put/get/remove 的缓存替身 */
    static final class RecCache implements LegacyCaptchaCache {

        final Map<String, LegacyCaptcha> map = new LinkedHashMap<>();

        final List<String> calls = new ArrayList<>();

        @Override
        public void put(LegacyCaptcha captcha) {
            calls.add("put:" + captcha.getKey());
            map.put(captcha.getKey(), captcha);
        }

        @Override
        public LegacyCaptcha get(String key) {
            calls.add("get:" + key);
            return map.get(key);
        }

        @Override
        public void remove(String key) {
            calls.add("remove:" + key);
            map.remove(key);
        }

        @Override
        public void removeAll() {
            calls.add("removeAll");
            map.clear();
        }
    }

    /** 捕获响应（Cookie / 头 / 内容类型 / 输出字节） */
    static final class Captured {
        final List<Cookie> cookies = new ArrayList<>();
        final Map<String, String> headers = new LinkedHashMap<>();
        final Map<String, Long> dateHeaders = new LinkedHashMap<>();
        String contentType;
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final PrintWriter writer = new PrintWriter(body);
    }

    static HttpServletResponse response(Captured cap) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "addCookie":
                    cap.cookies.add((Cookie) args[0]);
                    return null;
                case "setHeader":
                    cap.headers.put((String) args[0], (String) args[1]);
                    return null;
                case "setDateHeader":
                    cap.dateHeaders.put((String) args[0], (Long) args[1]);
                    return null;
                case "setContentType":
                    cap.contentType = (String) args[0];
                    return null;
                case "getOutputStream":
                    return new ServletOutputStream() {
                        @Override
                        public void write(int b) {
                            cap.body.write(b);
                        }

                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setWriteListener(jakarta.servlet.WriteListener wl) {
                        }
                    };
                case "getWriter":
                    return cap.writer;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (HttpServletResponse) Proxy.newProxyInstance(
                CaptchaAndUserFamilyGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, h);
    }

    /** 带属性表的请求替身（{@code set(...)} 后可读回） */
    static final class Req {
        final Map<String, Object> attrs = new HashMap<>();
        final Map<String, String> params = new HashMap<>();
        final List<Cookie> cookies = new ArrayList<>();

        HttpServletRequest build() {
            InvocationHandler h = (p, m, args) -> {
                switch (m.getName()) {
                    case "getAttribute":
                        return attrs.get(args[0]);
                    case "setAttribute":
                        attrs.put((String) args[0], args[1]);
                        return null;
                    case "removeAttribute":
                        attrs.remove(args[0]);
                        return null;
                    case "getCookies":
                        return cookies.isEmpty() ? null : cookies.toArray(new Cookie[0]);
                    case "getParameter":
                        return params.get(args[0]);
                    case "getRequestURI":
                        return "/user/act";
                    case "getRequestURL":
                        return new StringBuffer("http://localhost:8080/user/act");
                    case "getRemoteAddr":
                        return "127.0.0.1";
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
                    CaptchaAndUserFamilyGoldenTest.class.getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class}, h);
        }
    }

    /** UserController 探针：把会话/会话钩子相关入口替换成可控值 */
    public static class ProbeUser extends UserController {

        User user;

        final List<String> removedCookies = new ArrayList<>();

        Req req;

        @Override
        public User getUser() {
            return user;
        }

        @Override
        public LegacyController removeCookie(String name) {
            removedCookies.add(name);
            return this;
        }

        @Override
        public String get(int index) {
            return index == 0 ? "probe" : null;
        }
    }

    /**
     * 严格探针：把 {@code get(String)} 变成"禁止调用"，用来钉住
     * {@code validateCaptcha} 必须走 {@code getPara(name)}（旧字节码如此）。
     *
     * <p>为什么需要它：{@code getPara} 与 {@code get} 在本项目的请求替身上都会落到
     * {@code getParameter}，差别只在"空串归一"这一处，而在验证码路径上二者结果相同 ⇒
     * 只断言"校验结果"无法区分实现走了哪个入口（实测：变异 M11 曾因此漏网）。
     * 故此处把入口本身变成可观测。</p>
     */
    public static class StrictGetProbe extends ProbeUser {
        @Override
        public String get(String name) {
            throw new UnsupportedOperationException("validateCaptcha 必须用 getPara(name)，不得用 get(name)");
        }
    }

    /** 验证码探针：继承以访问 protected 的 getRandomString/createCaptcha（跨包只能经子类） */
    static class CaptchaProbe extends LegacyCaptchaRender {

        String randString() {
            return getRandomString();
        }

        LegacyCaptcha captcha() {
            return createCaptcha();
        }
    }

    /** 记录 logout 的登录服务替身 */
    static class ProbeLogin extends LoginService {
        boolean logoutCalled;

        LegacyRet loginResult;

        @Override
        public void logout(String sid) {
            logoutCalled = true;
        }

        @Override
        public LegacyRet login(String loginId, String loginPwd, boolean keepLogin, String ip) {
            return loginResult;
        }
    }

    private LegacyRenderFactory savedFactory;

    private boolean factoryReady;

    private LegacyCaptchaCache savedCache;

    private cn.eova.compat.cache.CacheService savedCacheService;

    private LoginService savedLogin;

    @BeforeEach
    void setUp() {
        factoryReady = LegacyRenderManager.isReady();
        savedFactory = factoryReady ? LegacyRenderManager.getRenderFactory() : null;
        LegacyRenderManager.setRenderFactory(new DefaultLegacyRenderFactory());

        savedCache = LegacyCaptchaManager.me().getCaptchaCache();
        LegacyCaptchaManager.me().setCaptchaCache(new RecCache());

        savedCacheService = CacheServices.get();
        CacheServices.set(new MemoryCacheService());

        savedLogin = biz.login;
    }

    @AfterEach
    void tearDown() {
        if (factoryReady) {
            LegacyRenderManager.setRenderFactory(savedFactory);
        } else {
            LegacyRenderManager.clear();
        }
        LegacyCaptchaManager.me().setCaptchaCache(savedCache);
        LegacyCaptchaManager.me().setCaptchaCache(null);
        CacheServices.set(savedCacheService);
        biz.login = savedLogin;
        x.conf.getProps().remove("isCaptcha");
        x.conf.getProps().remove("app.name");
        x.conf.getProps().remove("dev.login_id");
        x.conf.getProps().remove("dev.login_pwd");
    }

    /**
     * 造一个把请求/响应都接好的控制器。
     *
     * @param cap 响应捕获器
     * @return [控制器, 请求]
     */
    private ProbeUser ctrl(Captured cap) {
        ProbeUser c = new ProbeUser();
        Req req = new Req();
        c.req = req;
        c.setHttpServletRequest(req.build());
        c.setHttpServletResponse(response(cap));
        return c;
    }

    // ------------------------------------------------------------ 验证码接缝

    @Test
    @DisplayName("验证码：render 的 Cookie 名/属性、响应头、内容类型与 JPEG 产物")
    void captchaRenderSurface() {
        RecCache cache = (RecCache) LegacyCaptchaManager.me().getCaptchaCache();
        Captured cap = new Captured();
        LegacyCaptchaRender render = new LegacyCaptchaRender();
        render.setContext(new Req().build(), response(cap));
        render.render();

        assertEquals(1, cap.cookies.size());
        Cookie cookie = cap.cookies.get(0);
        assertEquals("_jfinal_captcha", cookie.getName(), "Cookie 名属契约（_jfinal_captcha）");
        assertEquals(-1, cookie.getMaxAge(), "会话 Cookie（setMaxAge(-1)）");
        assertEquals("/", cookie.getPath());
        assertTrue(cookie.isHttpOnly(), "必须 HttpOnly");

        assertEquals("no-cache", cap.headers.get("Pragma"));
        assertEquals("no-cache", cap.headers.get("Cache-Control"));
        assertEquals(Long.valueOf(0L), cap.dateHeaders.get("Expires"));
        assertEquals("image/jpeg", cap.contentType);

        assertEquals(1, cache.map.size(), "验证码必须入缓存");
        LegacyCaptcha stored = cache.map.values().iterator().next();
        assertEquals(cookie.getValue(), stored.getKey(), "Cookie 值 = 缓存键");
        assertEquals(4, stored.getValue().length(), "验证码 4 位");
        assertTrue(stored.notExpired());
        long left = stored.getExpireAt() - System.currentTimeMillis();
        assertTrue(left > 170_000 && left <= 180_000, "有效期应为 180 秒，实际剩余 " + left + "ms");

        byte[] bytes = cap.body.toByteArray();
        assertTrue(bytes.length > 100, "必须写出图片字节：" + bytes.length);
        assertEquals((byte) 0xFF, bytes[0]);
        assertEquals((byte) 0xD8, bytes[1], "JPEG SOI 魔数");
        assertEquals((byte) 0xFF, bytes[2]);
    }

    @Test
    @DisplayName("验证码：有效期 180 秒 + 字符集限定 + 逐次不同")
    void captchaValueRules() {
        CaptchaProbe r = new CaptchaProbe();
        r.setContext(new Req().build(), response(new Captured()));
        String charset = "3456789ABCDEFGHJKMNPQRSTUVWXYabcdefghjkmnpqrstuvwxy";
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String v = r.randString();
            assertEquals(4, v.length());
            for (char ch : v.toCharArray()) {
                assertTrue(charset.indexOf(ch) >= 0, "字符必须来自旧字符集：" + ch);
            }
            seen.add(v);
        }
        assertTrue(seen.stream().distinct().count() > 1, "50 次生成不应恒同（随机源）");

        LegacyCaptcha captcha = r.captcha();
        assertEquals(180, captcha.getExpireAt() / 1000 - System.currentTimeMillis() / 1000,
                "有效期 180 秒（旧实现 new Captcha(key, value, 180)）");
    }

    @Test
    @DisplayName("验证码：validate 的三条判定与【一次性移除】（缓存 + Cookie 都清）")
    void captchaValidateIsOneShot() {
        RecCache cache = (RecCache) LegacyCaptchaManager.me().getCaptchaCache();
        cache.put(new LegacyCaptcha("key-1", "aB3d", 180));

        assertFalse(LegacyCaptchaRender.validate("key-1", "wrong"), "值不匹配必须失败");
        assertTrue(cache.map.containsKey("key-1"), "失败不得移除缓存");

        assertTrue(LegacyCaptchaRender.validate("key-1", "AB3D"), "大小写不敏感");
        assertFalse(cache.map.containsKey("key-1"), "成功后必须移除缓存（一次性）");
        assertFalse(LegacyCaptchaRender.validate("key-1", "aB3d"), "重放必须失败");

        // 过期项
        LegacyCaptcha expired = new LegacyCaptcha("key-2", "zzzz", 180);
        expired.setExpireAt(System.currentTimeMillis() - 1000);
        cache.put(expired);
        assertFalse(LegacyCaptchaRender.validate("key-2", "zzzz"), "过期必须失败");

        // 控制器入口：成功后移除 Cookie（用严格探针 ⇒ 入口方法本身被钉住）
        Captured cap = new Captured();
        StrictGetProbe c = new StrictGetProbe();
        Req strictReq = new Req();
        c.req = strictReq;
        c.setHttpServletRequest(strictReq.build());
        c.setHttpServletResponse(response(cap));
        cache.put(new LegacyCaptcha("key-3", "q1w2", 180));
        c.req.cookies.add(new Cookie("_jfinal_captcha", "key-3"));
        c.req.params.put("captcha", "Q1W2");
        assertTrue(c.validateCaptcha("captcha"), "validateCaptcha 读表单字段 + Cookie 键");
        assertTrue(c.removedCookies.contains("_jfinal_captcha"), "成功后必须移除验证码 Cookie：" + c.removedCookies);

        // 缺 key（未携带 Cookie）⇒ 用随机 UUID 作键，且校验必失败
        Captured cap2 = new Captured();
        ProbeUser c2 = ctrl(cap2);
        assertFalse(c2.validateCaptcha("captcha"));
    }

    @Test
    @DisplayName("验证码：控制器 renderCaptcha() 装配的是验证码渲染")
    void controllerRenderCaptcha() {
        ProbeUser c = ctrl(new Captured());
        c.renderCaptcha();
        assertInstanceOf(LegacyCaptchaRender.class, c.getRender());
    }

    // ------------------------------------------------------------ UserController

    @Test
    @DisplayName("UserController：captcha() 渲染验证码；password() 渲染修改密码页")
    void userCaptchaAndPasswordPages() {
        ProbeUser c = ctrl(new Captured());
        c.captcha();
        assertInstanceOf(LegacyCaptchaRender.class, c.getRender());

        ProbeUser c2 = ctrl(new Captured());
        c2.password();
        // EOVA 自有 BaseController.render(String) 会把 /eova/x 重写成 /eova/_view/x（既有契约，逐字保留）
        assertEquals("/eova/_view/user/password/app.html", c2.getRender().getView());
    }

    @Test
    @DisplayName("UserController：login() 的六个 set 键 + 版权年份 + 默认登录页")
    void userLoginPageAttrs() {
        x.conf.addConfig("isCaptcha", "true");
        x.conf.addConfig("app.name", "探针系统");
        ProbeUser c = ctrl(new Captured());
        c.login();

        assertEquals(Boolean.TRUE, c.req.attrs.get("isCaptcha"));
        assertEquals(Boolean.FALSE, c.req.attrs.get("isI18N"));
        assertEquals("探针系统", c.req.attrs.get("app_name"));
        String cp = (String) c.req.attrs.get("copyright");
        assertTrue(cp.contains("© 2015-" + LocalDate.now().getYear() + " EOVA.CN"), cp);
        assertEquals("/eova/_view/index/login.html", c.getRender().getView(),
                "默认登录页 /eova/index/login.html 经 BaseController.render 的 _view 重写后落点");
    }

    @Test
    @DisplayName("UserController：logout() 走会话钩子 + 销毁 SID + 跳首页")
    void userLogout() {
        ProbeLogin probe = new ProbeLogin();
        biz.login = probe;

        final boolean[] hookCalled = {false};
        EovaConfig.setUserSessionIntercept(new cn.eova.aop.UserSessionIntercept() {
            @Override
            public String loginBefore(User user) {
                return null;
            }

            @Override
            public void login(User user) {
            }

            @Override
            public void logout(User user) {
                hookCalled[0] = true;
            }

            @Override
            public void su(User user, com.alibaba.fastjson.JSONObject switchUser) {
            }
        });

        Captured cap = new Captured();
        ProbeUser c = ctrl(cap);
        c.user = new User();
        c.logout();

        assertTrue(hookCalled[0], "登出钩子必须被调用");
        assertTrue(probe.logoutCalled, "必须调用 LoginService.logout(sid)");
        assertTrue(c.removedCookies.contains(LoginService.CKSID), c.removedCookies.toString());
        cn.eova.compat.render.LegacyRedirectRender redirect =
                assertInstanceOf(cn.eova.compat.render.LegacyRedirectRender.class, c.getRender());
        assertEquals(EovaConfig.EOVA_INDEX, redirect.buildFinalUrl(), "登出后跳首页（EovaConfig.EOVA_INDEX）");
        EovaConfig.setUserSessionIntercept(null);
    }

    @Test
    @DisplayName("UserController：doLogin 的验证码失败 / 密码失败 / 错误次数计数")
    void userDoLoginBranches() {
        // ① 验证码开启且未携带 ⇒ 直接拒绝
        x.conf.addConfig("isCaptcha", "true");
        ProbeUser c1 = ctrl(new Captured());
        c1.req.params.put("login_id", "admin");
        c1.req.params.put("login_pwd", "pwd");
        c1.doLogin();
        assertTrue(jsonOf(c1).contains("验证码错误"), jsonOf(c1));

        // ② 验证码通过 + 登录失败 ⇒ 返回登录服务的 msg，并累加错误次数
        x.conf.addConfig("isCaptcha", "false");
        ProbeLogin probe = new ProbeLogin();
        probe.loginResult = LegacyRet.fail("账号或密码错误");
        biz.login = probe;

        ProbeUser c2 = ctrl(new Captured());
        c2.req.params.put("login_id", "admin");
        c2.req.params.put("login_pwd", "pwd");
        c2.doLogin();
        assertTrue(jsonOf(c2).contains("账号或密码错误"), jsonOf(c2));
        Integer num = LegacyCacheKit.get(cn.eova.common.base.BaseCache.LOGIN_ERROR, "login_erroradmin");
        assertEquals(Integer.valueOf(1), num, "失败必须累加错误次数（键 = login_error + loginId）");
    }

    /** 取 json 渲染文本 */
    private static String jsonOf(BaseController ctrl) {
        LegacyRender r = ctrl.getRender();
        assertInstanceOf(cn.eova.compat.render.LegacyJsonRender.class, r);
        return ((cn.eova.compat.render.LegacyJsonRender) r).getJsonText();
    }

    // ------------------------------------------------------------ EovaWebRoutes

    @Test
    @DisplayName("EovaWebRoutes：18 条路由的顺序/路径/控制器 + 继承登录鉴权拦截器")
    void eovaWebRoutesTable() {
        EovaWebRoutes routes = new EovaWebRoutes();
        routes.config();

        assertEquals(2, routes.getInterceptors().length, "父类 config() 挂登录 + 鉴权");
        List<String> paths = new ArrayList<>();
        List<Class<?>> classes = new ArrayList<>();
        for (LegacyRoutes.Route r : routes.getRouteItemList()) {
            paths.add(r.getControllerPath());
            classes.add(r.getControllerClass());
        }
        assertEquals(List.of(
                "/api/home", "/api/meta", "/api/widget", "/api/form", "/api/table", "/api/tree",
                "/excel", "/upload", "/sse", "/eova/admin", "/eova/ops", "/user",
                "/meta", "/menu", "/button", "/auth", "/task", "/dict"), paths, "18 条路由的顺序与路径属契约");
        assertEquals(List.of(
                HomeController.class, MetaControler.class, WidgetController.class, FormControler.class,
                TableController.class, TreeController.class, cn.eova.meta.api.ExcelController.class,
                UploadController.class, SSEController.class, AdminController.class, OpsController.class,
                UserController.class, cn.eova.core.meta.MetaController.class, MenuController.class,
                ButtonController.class, AuthController.class, TaskController.class, DictController.class),
                classes);
    }

    @Test
    @DisplayName("判据自检：探针控制器可被调用（防判据空洞）")
    void probeSelfCheck() {
        ProbeUser c = ctrl(new Captured());
        assertNull(c.getRender());
        assertNotNull(c.getRequest());
        // 本轮同批收尾的 SingleController：确认可实例化（其行为验收需 baseline 库，见判据类注释）
        assertNotNull(new SingleController());
    }
}
