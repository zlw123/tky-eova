/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInvocation;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyJsonRender;
import cn.eova.compat.render.LegacyRedirectRender;
import cn.eova.compat.render.LegacyRenderFactory;
import cn.eova.compat.render.LegacyRenderManager;
import cn.eova.testkit.OldImplementationLoader;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 决策 3 地基波（W1a）判据：{@code LegacyController} 参数族 + {@code LegacyInvocation} 链语义。
 *
 * <p><b>口径：</b>① 方法集钉死（分批实现，未实现部分必须机器可跟踪）；
 * ② 声明面跨实现比对（对旧 jfinal {@code Controller}）；③ 行为定点断言（全部来自旧字节码）；
 * ④ 带参 action 清单钉住。</p>
 *
 * <p><b>⚠️ 临时限制（记录不粉饰）：</b>行为<b>跨实现</b>比对本波未做 ——
 * 旧 {@code com.jfinal.core.Controller} 是 {@code public abstract class}（javap 实测）无法实例化；
 * 而把 {@code com.jfinal:jfinal:5.2.6} 加为测试依赖会危及既有判据前提
 * （它们正依赖"enjoy 在 classpath、jfinal 不在"观察 R37/R40 同名类漂移，R44 族）。
 * <b>真正的行为比对放在 W4</b>：那时 EOVA {@code BaseController} 已 port，两侧都有具体实例。</p>
 *
 * <p>acceptanceProfile: golden-mvc-foundation</p>
 */
class MvcFoundationGoldenTest {

    /** 记录工厂被调用的轨迹（验证 Controller 的 render 族确实经工厂） */
    private static final List<String> CALLS = java.util.Collections.synchronizedList(new ArrayList<>());

    /** W1a 已实现的非私有方法名 */
    private static final List<String> W1A_METHODS = List.of(
            "_clear_", "setHttpServletRequest", "setHttpServletResponse", "setUrlPara",
            "getRequest", "getResponse", "getPara", "getParaMap", "getRawData",
            "getParaToInt", "getParaToBoolean", "getInt", "getBoolean", "set", "setAttr", "getAttr",
            "removeAttr", "keepPara", "getKv", "render", "getRender",
            // W1b：toLong 族 + Cookie 族
            "getParaToLong", "getLong", "getCookie", "getCookieObject",
            "doSetCookie", "setCookie", "removeCookie",
            // W2：渲染族
            "render", "renderTemplate", "renderJson", "renderError", "redirect",
            "getControllerKey", "getViewPath", "getControllerPath");

    /**
     * 带参方法清单（钉住）。前两条是<b>真正的带参 action</b> —— 它们推翻了我
     * "EOVA 的 action 全部无参"的初始假设，并要求 W1c 补 jfinal 参数绑定框架。
     */
    private static final List<String> PINNED_ARG_BEARING = List.of(
            "BaseController.java -> NO(String msg)",
            "BaseController.java -> OK(String msg)",
            "BaseController.java -> render(String view)",
            "BaseController.java -> renderEnjoy(String view)",
            "BaseController.java -> renderMsg(String msg)",
            "BaseController.java -> updateUser(User user)",
            "BaseController.java -> uploadCallback(boolean succeed, String msg)",
            "MetaController.java -> importMeta(String ds, String type, String table, String name, String code, String pk)",
            "RouterController.java -> signCheck(String appKey, String method, String timestamp, String sign)");

    /** 挂渲染工厂替身（toInt/toBoolean 失败路径要构造错误渲染） */
    @BeforeAll
    static void setUp() {
        LegacyRenderManager.setRenderFactory(new LegacyRenderFactory() {
            @Override
            public LegacyRender getErrorRender(int errorCode) {
                return new StubRender();
            }

            @Override
            public LegacyRender getErrorRender(int errorCode, String view) {
                return new StubRender();
            }

            @Override
            public LegacyRender getRender(String view) {
                CALLS.add("getRender:" + view);
                return new StubRender();
            }

            @Override
            public LegacyRender getTemplateRender(String view) {
                CALLS.add("getTemplateRender:" + view);
                return new StubRender();
            }

            @Override
            public LegacyRender getJsonRender() {
                CALLS.add("getJsonRender()");
                return new StubRender();
            }

            @Override
            public LegacyRender getJsonRender(String[] attrs) {
                CALLS.add("getJsonRender(String[])");
                return new StubRender();
            }

            @Override
            public LegacyRender getJsonRender(String jsonText) {
                CALLS.add("getJsonRender(String)");
                return new StubRender();
            }

            @Override
            public LegacyRender getJsonRender(Object object) {
                CALLS.add("getJsonRender(Object)");
                return new StubRender();
            }

            @Override
            public LegacyRender getJsonRender(String attr, Object object) {
                CALLS.add("getJsonRender(String,Object)");
                return new StubRender();
            }

            @Override
            public LegacyRender getRedirectRender(String url) {
                CALLS.add("getRedirectRender:" + url);
                return new StubRender();
            }

            @Override
            public LegacyRender getRedirectRender(String url, boolean withQueryString) {
                CALLS.add("getRedirectRender:" + url + ":" + withQueryString);
                return new StubRender();
            }
        });
    }

    /** 方法集钉死 */
    @Test
    @DisplayName("方法集钉死（W1a + W1b + W2）")
    void w1aMethodSetIsPinned() {
        List<String> actual = new ArrayList<>();
        for (Method m : LegacyController.class.getDeclaredMethods()) {
            if (!m.isSynthetic() && !m.isBridge() && !Modifier.isPrivate(m.getModifiers())) {
                actual.add(m.getName());
            }
        }
        assertEquals(new ArrayList<>(new TreeSet<>(W1A_METHODS)), new ArrayList<>(new TreeSet<>(actual)),
                "方法集必须与清单一致；新增（W1c/W2）时同步清单");
    }

    /**
     * 声明面跨实现比对。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("声明面比对：W1a 每方法在旧 jfinal Controller 上同名同签名")
    void declarationSurfaceMatchesOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.core.Controller", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());
        assertTrue(Modifier.isAbstract(oldCls.getModifiers()), "旧 Controller 应为抽象类");

        int compared = 0;
        List<String> problems = new ArrayList<>();
        for (Method m : LegacyController.class.getDeclaredMethods()) {
            if (m.isSynthetic() || m.isBridge() || Modifier.isPrivate(m.getModifiers())) {
                continue;
            }
            // _clear_ 在旧实现里是 protected，getMethods() 取不到，故按声明方法再找一次
            Method oldM = findOld(oldCls, m);
            if (oldM == null) {
                oldM = findOldDeclared(oldCls, m);
            }
            if (oldM == null) {
                problems.add(m.getName() + " 在旧 Controller 上找不到同名同签名方法");
                continue;
            }
            assertEquals(normalize(oldM.getReturnType().getName()), normalize(m.getReturnType().getName()),
                    m.getName() + " 返回类型必须一致");
            compared++;
        }
        assertTrue(problems.isEmpty(), "声明面差异：\n  " + String.join("\n  ", problems));
        assertTrue(compared >= 25, "至少比对 25 个方法，实际 " + compared + "（防空洞）");
    }

    /** getPara(String) 空串归一 */
    @Test
    @DisplayName("getPara(String)：空串归一为 null，空白串不归一")
    void getParaStringSemantics() {
        LegacyController c = controller(Map.of("a", "1", "empty", "", "url", "  "));
        assertEquals("1", c.getPara("a"));
        assertNull(c.getPara("empty"), "长度 0 必须归一为 null");
        assertEquals("  ", c.getPara("url"), "空白串不归一（旧实现只判 length != 0）");
        assertNull(c.getPara("missing"));
        assertEquals("d", c.getPara("missing", "d"));
    }

    /** urlPara 切分 */
    @Test
    @DisplayName("getPara(int)：按 \"-\" 切分、空段归一为 null、越界为 null")
    void getParaIntSemantics() {
        LegacyController c = new LegacyController();
        c.setUrlPara("1-2");
        assertEquals("1-2", c.getPara(-1));
        assertEquals("1", c.getPara(0));
        assertEquals("2", c.getPara(1));
        assertNull(c.getPara(2));

        LegacyController d = new LegacyController();
        d.setUrlPara("1--2");
        assertEquals("1", d.getPara(0));
        assertNull(d.getPara(1), "空段必须归一为 null");
        assertEquals("2", d.getPara(2));

        LegacyController e = new LegacyController();
        e.setUrlPara("");
        assertNull(e.getPara(0));
        assertNull(e.getPara(-1));
    }

    /** N 前缀取负 */
    @Test
    @DisplayName("getParaToInt：\"N\"/\"n\" 前缀取负（不是 null）")
    void toIntNPrefixMeansNegate() {
        assertEquals(5, controller(Map.of("v", "5")).getParaToInt("v"));
        assertEquals(-5, controller(Map.of("v", "N5")).getParaToInt("v"), "N5 应为 -5");
        assertEquals(-7, controller(Map.of("v", "n7")).getParaToInt("v"), "n7 应为 -7");
        assertEquals(42, controller(Map.of("v", " 42 ")).getParaToInt("v"));
        assertEquals(0, controller(Map.of("v", "N0")).getParaToInt("v"));
        assertNull(controller(Map.of("v", "")).getParaToInt("v"));
    }

    /** toBoolean 取值域 */
    @Test
    @DisplayName("getParaToBoolean：1/true/0/false（忽略大小写、先 trim）")
    void toBooleanSemantics() {
        for (String v : new String[]{"1", "true", "TRUE", " True "}) {
            assertEquals(Boolean.TRUE, controller(Map.of("v", v)).getParaToBoolean("v"), v);
        }
        for (String v : new String[]{"0", "false", "FALSE", " False "}) {
            assertEquals(Boolean.FALSE, controller(Map.of("v", v)).getParaToBoolean("v"), v);
        }
    }

    /** getKv */
    @Test
    @DisplayName("getKv：取单值且空串归一为 null")
    void getKvSemantics() {
        LegacyKv kv = controller(Map.of("a", "1", "empty", "", "b", "x")).getKv();
        assertEquals("1", kv.get("a"));
        assertNull(kv.get("empty"));
        assertEquals("x", kv.get("b"));
        assertEquals(new TreeSet<>(Arrays.asList("a", "b", "empty")), new TreeSet<>(kv.keySet()));
    }

    /** keepPara */
    @Test
    @DisplayName("keepPara：单值存 String、多值存数组、缺失不动作")
    void keepParaSemantics() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        LegacyController c = new LegacyController();
        c.setHttpServletRequest(spy(attrs, Map.of("one", new String[]{"1"}, "many", new String[]{"a", "b"})));
        c.keepPara("one", "absent");
        assertEquals("1", attrs.get("one"));
        assertNull(attrs.get("absent"), "缺失参数不动作");
        c.keepPara("many");
        assertTrue(Arrays.equals(new String[]{"a", "b"}, (String[]) attrs.get("many")), "多值存数组");
    }

    /** set/setAttr 同实现 + render 只赋值 + _clear_ */
    @Test
    @DisplayName("set/setAttr 同实现；render 只赋值；_clear_ 清空")
    void setAttrRenderClearSemantics() throws Exception {
        Map<String, Object> attrs = new LinkedHashMap<>();
        LegacyController c = new LegacyController();
        c.setHttpServletRequest(spy(attrs, Map.of()));

        c.set("k", "v1");
        assertEquals("v1", attrs.get("k"));
        c.setAttr("k", "v2");
        assertEquals("v2", attrs.get("k"), "setAttr 与 set 写同一处");
        assertEquals("v2", c.getAttr("k"));
        c.removeAttr("k");
        assertNull(attrs.get("k"));

        StubRender r = new StubRender();
        c.render(r);
        assertEquals(r, c.getRender(), "render(Render) 只赋值，不渲染");

        c.setUrlPara("x");
        LegacyController.class.getDeclaredMethod("_clear_").invoke(c);
        assertNull(c.getRequest());
        assertNull(c.getRender());
        assertNull(c.getPara());
    }

    /** toLong 的 N 前缀取负 */
    @Test
    @DisplayName("getParaToLong：\"N\"/\"n\" 前缀取负；超 int 范围不溢出")
    void toLongNPrefixMeansNegate() {
        assertEquals(9L, controller(Map.of("v", "9")).getParaToLong("v"));
        assertEquals(-9L, controller(Map.of("v", "N9")).getParaToLong("v"), "N9 应为 -9");
        assertEquals(-12L, controller(Map.of("v", "n12")).getParaToLong("v"));
        long big = 3_000_000_000L;
        assertEquals(big, controller(Map.of("v", String.valueOf(big))).getParaToLong("v"),
                "超出 int 范围的值必须按 Long 正确解析");
        assertEquals(-big, controller(Map.of("v", "N" + big)).getParaToLong("v"));
        assertNull(controller(Map.of("v", "")).getParaToLong("v"));
    }

    /** Cookie 族：写-读-删往返 + doSetCookie 的三处语义 */
    @Test
    @DisplayName("Cookie 族：setCookie/removeCookie 的 path 归一与 setHttpOnly（不是 setSecure）")
    void cookieSemantics() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        List<jakarta.servlet.http.Cookie> added = new ArrayList<>();
        LegacyController c = new LegacyController();
        c.setHttpServletRequest(spy(attrs, Map.of()));
        c.setHttpServletResponse(responseSpy(added));

        c.setCookie("t", "v", 60);
        assertEquals(1, added.size());
        jakarta.servlet.http.Cookie ck = added.get(0);
        assertEquals("t", ck.getName());
        assertEquals("v", ck.getValue());
        assertEquals(60, ck.getMaxAge());
        assertEquals("/", ck.getPath(), "空 path 必须归一为 \"/\"");
        assertTrue(!ck.isHttpOnly(), "secure 参数为 null 时不应设置 HttpOnly");

        c.removeCookie("t");
        assertEquals(2, added.size());
        jakarta.servlet.http.Cookie gone = added.get(1);
        assertNull(gone.getValue(), "removeCookie 置空值");
        assertEquals(0, gone.getMaxAge(), "removeCookie 置 maxAge=0");
        assertEquals("/", gone.getPath());

        // 【判别性用例】secure = TRUE 时必须走 setHttpOnly（不是 setSecure）
        // 不加这一条，"setHttpOnly 被改成 setSecure" 不会被任何判据发现
        // （secure 为 null 时两条分支都不执行）—— 该漏检由变异测试实测发现。
        Probe p2 = new Probe();
        Map<String, Object> a2 = new LinkedHashMap<>();
        List<jakarta.servlet.http.Cookie> add2 = new ArrayList<>();
        p2.setHttpServletRequest(spy(a2, Map.of()));
        p2.setHttpServletResponse(responseSpy(add2));
        p2.doSetCookie("h", "v", 10, null, null, Boolean.TRUE);
        assertTrue(add2.get(0).isHttpOnly(),
                "secure=TRUE 必须设置 HttpOnly —— 旧实现的形参名叫 secure 但调用的是 setHttpOnly");
        assertTrue(!add2.get(0).getSecure(),
                "【不得】设置 Secure 标志（与形参名相反，属 jfinal 的既有命名与行为不一致）");
    }

    /** Cookie 名比对区分大小写 */
    @Test
    @DisplayName("getCookieObject：Cookie 名比对【区分大小写】")
    void cookieNameLookupIsCaseSensitive() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        Map<String, jakarta.servlet.http.Cookie> jar = new LinkedHashMap<>();
        jar.put("T", new jakarta.servlet.http.Cookie("T", "upper"));
        jar.put("t", new jakarta.servlet.http.Cookie("t", "lower"));
        LegacyController c = new LegacyController();
        c.setHttpServletRequest(cookieSpy(jar));

        assertEquals("upper", c.getCookie("T"));
        assertEquals("lower", c.getCookie("t"), "大小写不同是【两个】Cookie");
        assertNull(c.getCookie("Tt"), "找不到时必须走缺省值；大小写敏感，不得 equalsIgnoreCase");
        assertEquals("d", c.getCookie("Tt", "d"));
        assertNull(c.getCookieObject("TT"));
    }

    /** render 族确实经工厂转发；renderError 直接抛异常 */
    @Test
    @DisplayName("render 族：经工厂转发；renderError 直接抛 ActionException（不设 render）")
    void renderFamilyDelegatesToFactory() {
        CALLS.clear();
        LegacyController c = new LegacyController();
        c.render("v1");
        c.renderTemplate("v2");
        c.renderJson();
        c.renderJson("raw");
        c.renderJson(new Object());
        c.renderJson(new String[]{"a"});
        c.renderJson("k", new Object());
        c.redirect("/r1");
        c.redirect("/r2", true);
        assertEquals(List.of("getRender:v1", "getTemplateRender:v2", "getJsonRender()",
                        "getJsonRender(String)", "getJsonRender(Object)", "getJsonRender(String[])",
                        "getJsonRender(String,Object)", "getRedirectRender:/r1",
                        "getRedirectRender:/r2:true"),
                CALLS, "每个方法都必须转发到工厂的对应方法");

        // renderJson(Object) 的分支：入参已是 Render 时直接使用，不经工厂
        CALLS.clear();
        StubRender r = new StubRender();
        c.renderJson(r);
        assertEquals(r, c.getRender(), "入参已是 Render 时应直接使用");
        assertTrue(CALLS.isEmpty(), "该分支不得调用工厂");

        // renderError 直接抛
        LegacyActionException ex = org.junit.jupiter.api.Assertions.assertThrows(
                LegacyActionException.class, () -> c.renderError(404));
        assertEquals(404, ex.getErrorCode());
        assertTrue(ex.getErrorRender() != null);
    }

    /** LegacyRedirectRender 的 URL 构建规则（全部为行为断言，不读 protected 字段） */
    @Test
    @DisplayName("LegacyRedirectRender：单参不附带查询串；上下文前缀与绝对 URL 规则")
    void redirectRenderUrlRules() {
        LegacyRedirectRender.setContextPath("/app");

        // 单参构造：即使请求有查询串也【不】附带（旧字节码 withQueryString=false）
        LegacyRedirectRender r1 = new LegacyRedirectRender("/a");
        r1.setContext(probeRequest(Map.of("__qs", "x=1")), null);
        assertEquals("/app/a", r1.buildFinalUrl(),
                "单参构造不附带查询串 —— 这一点若凭直觉会写成 true");

        // 两参构造 withQueryString=true：附带查询串
        LegacyRedirectRender r2 = new LegacyRedirectRender("/a", true);
        r2.setContext(probeRequest(Map.of("__qs", "x=1")), null);
        assertEquals("/app/a?x=1", r2.buildFinalUrl(), "无 ? 时用 ?");

        LegacyRedirectRender r3 = new LegacyRedirectRender("/a?k=v", true);
        r3.setContext(probeRequest(Map.of("__qs", "x=1")), null);
        assertEquals("/app/a?k=v&x=1", r3.buildFinalUrl(), "已有 ? 时用 &");

        // 绝对 URL 不补上下文前缀（协议出现在 index <= 5 处）
        LegacyRedirectRender r4 = new LegacyRedirectRender("http://x/y");
        r4.setContext(probeRequest(Map.of()), null);
        assertEquals("http://x/y", r4.buildFinalUrl());
        LegacyRedirectRender r5 = new LegacyRedirectRender("https://x/y");
        r5.setContext(probeRequest(Map.of()), null);
        assertEquals("https://x/y", r5.buildFinalUrl());

        // 上下文路径为 "" 或 "/" 时归一为 null（旧实现如此）
        LegacyRedirectRender.setContextPath("/");
        LegacyRedirectRender r6 = new LegacyRedirectRender("/a");
        r6.setContext(probeRequest(Map.of()), null);
        assertEquals("/a", r6.buildFinalUrl(), "上下文为 \"/\" 时应归一为 null");
        LegacyRedirectRender.setContextPath("");
        LegacyRedirectRender r7 = new LegacyRedirectRender("/a");
        r7.setContext(probeRequest(Map.of()), null);
        assertEquals("/a", r7.buildFinalUrl(), "上下文为 \"\" 时应归一为 null");

        LegacyRedirectRender.setContextPath("/app");
    }

    /** LegacyJsonRender：走真实 render() 路径，捕获写出内容 */
    @Test
    @DisplayName("LegacyJsonRender：attrs 分支 / 排除项分支 / contentType")
    void jsonRenderBuildRules() {
        assertTrue(LegacyJsonRender.EXCLUDED_ATTRS.contains("_res"),
                "排除项初值含 _res（逐字取自旧字节码）");
        assertEquals(6, LegacyJsonRender.EXCLUDED_ATTRS.size(), "初值恰为 6 项");

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("a", "1");
        attrs.put("_res", "hdr");

        // attrs 分支：只取指定项
        java.io.StringWriter sb1 = new java.io.StringWriter();
        LegacyJsonRender r1 = new LegacyJsonRender(new String[]{"a"});
        r1.setContext(probeRequest(attrs), writerSpy(sb1));
        r1.render();
        assertTrue(sb1.toString().contains("\"a\""), "attrs 分支应输出 a，实际：" + sb1);
        assertTrue(!sb1.toString().contains("_res"), "attrs 分支只取指定项");

        // 无 attrs 分支：取全部但跳过排除项
        java.io.StringWriter sb2 = new java.io.StringWriter();
        LegacyJsonRender r2 = new LegacyJsonRender();
        r2.setContext(probeRequest(attrs), writerSpy(sb2));
        r2.render();
        assertTrue(sb2.toString().contains("\"a\""), "实际：" + sb2);
        assertTrue(!sb2.toString().contains("_res"), "无 attrs 分支必须跳过排除项 _res");

        // 直接给定 JSON 文本时不再构建
        java.io.StringWriter sb3 = new java.io.StringWriter();
        LegacyJsonRender r3 = new LegacyJsonRender("{\"z\":1}");
        r3.setContext(probeRequest(Map.of()), writerSpy(sb3));
        r3.render();
        assertEquals("{\"z\":1}", sb3.toString(), "给定文本应原样写出");
    }

    /**
     * 建把 {@code getWriter()} 接到给定缓冲的响应替身。
     *
     * @param sb 输出缓冲
     * @return 替身
     */
    private static jakarta.servlet.http.HttpServletResponse writerSpy(java.io.Writer sb) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getWriter":
                    return new java.io.PrintWriter(sb);
                case "setContentType":
                    return null;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (jakarta.servlet.http.HttpServletResponse) Proxy.newProxyInstance(
                MvcFoundationGoldenTest.class.getClassLoader(),
                new Class<?>[]{jakarta.servlet.http.HttpServletResponse.class}, h);
    }

    /**
     * 建一个只提供 getAttribute/getAttributeNames 的请求替身。
     *
     * @param attrs 属性表
     * @return 替身
     */
    private static HttpServletRequest probeRequest(Map<String, Object> attrs) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getAttribute":
                    return attrs.get(args[0]);
                case "getAttributeNames":
                    return java.util.Collections.enumeration(new ArrayList<>(attrs.keySet()));
                case "getQueryString":
                    return attrs.get("__qs");
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                MvcFoundationGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, h);
    }

    /** invoke 链 */
    @Test
    @DisplayName("LegacyInvocation.invoke：拦截器按序 + action 只执行一次")
    void invokeChainAndActionRunsOnce() throws Exception {
        List<String> trace = new ArrayList<>();
        CounterController controller = new CounterController(trace);
        Method m = CounterController.class.getMethod("action");

        LegacyInterceptor i1 = inv -> {
            trace.add("i1-before");
            inv.invoke();
            trace.add("i1-after");
        };
        LegacyInterceptor i2 = inv -> {
            trace.add("i2-before");
            inv.invoke();
            inv.invoke();
            trace.add("i2-after");
        };

        LegacyAction action = new LegacyAction("/c/a", "/c", CounterController.class, m, "action",
                new LegacyInterceptor[]{i1, i2}, "/view");
        LegacyInvocation inv = new LegacyInvocation(action, controller);
        assertEquals("/c/a", inv.getActionKey());
        assertTrue(inv.isActionInvocation());

        inv.invoke();
        assertEquals(List.of("i1-before", "i2-before", "action", "i2-after", "i1-after"), trace);
        assertEquals(1, controller.runs, "action 必须只执行一次（守卫语义）");
    }

    /**
     * <b>本波最关键的一条断言</b>：验证 jfinal 参数绑定框架<b>不需要</b> port。
     *
     * <p><b>为什么需要它：</b>我在这一点上连续判断错两次（先称"action 全无参"，
     * 后称"发现带参 action 故绑定必需"）。静态扫描容易把
     * {@code @NotAction} 辅助方法与<b>未注册</b>类的方法误算成 action。
     * 故本条按 jfinal 的真实语义重算：<b>已注册 controller + 非 {@code @NotAction} + 带参</b>。</p>
     *
     * <p>实测：注册 controller 21 个；带参 public 方法 9 个 —— 8 个 {@code @NotAction}
     * 辅助方法、1 个在未注册类中（{@code RouterController}，全树无引用）。
     * 故结论为 <b>0</b>，绑定额外框架不需要，{@code LegacyAction.args} 恒空数组正确。</p>
     *
     * @throws Exception 读文件失败
     */
    @Test
    @DisplayName("参数绑定不需要：已注册 controller 中不存在带参 action（实测 0）")
    void argBearingActionsAreNone() throws Exception {
        Path base = OldImplementationLoader.locateRepoRoot()
                .resolve("meta-eova/eova/core/src/main/java/cn/eova");
        assertTrue(Files.isDirectory(base), "旧源码目录缺失：" + base);

        // ① 显式注册的 controller（旧栈经 add("/path", Xxx.class) 注册；无动态扫描）
        TreeSet<String> registered = new TreeSet<>();
        // 允许【全限定名】注册（add("/x", cn.eova.core.api.XxxController.class)）——
        // 只认简单名的版本会漏掉这种写法（该健壮性缺陷由变异测试实测发现）。
        Pattern addCall = Pattern.compile("(?:^|[^\\w.])add\\(\\s*[^,]+,\\s*([\\w.]+)\\.class\\s*\\)",
                Pattern.MULTILINE);
        try (Stream<Path> walk = Files.walk(base)) {
            for (Path p : (Iterable<Path>) walk.filter(x -> x.toString().endsWith(".java"))::iterator) {
                Matcher m = addCall.matcher(Files.readString(p));
                while (m.find()) {
                    String fq = m.group(1);
                    registered.add(fq.substring(fq.lastIndexOf('.') + 1));
                }
            }
        }

        // ② 扫描 *Controller 类的带参 public 方法，并识别 @NotAction
        Pattern decl = Pattern.compile(
                "^\\s+public\\s+(?:void|[\\w.<>\\[\\]]+)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{?\\s*$");
        List<String> hits = new ArrayList<>();
        List<String> pinned = new ArrayList<>();
        int classes = 0;
        try (Stream<Path> walk = Files.walk(base)) {
            for (Path p : (Iterable<Path>) walk.filter(x -> x.toString().endsWith("Controller.java"))::iterator) {
                String src = Files.readString(p);
                if (!src.contains("extends BaseController") && !src.contains("extends Controller")) {
                    continue;
                }
                classes++;
                String simple = p.getFileName().toString().replace(".java", "");
                String[] lines = src.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    Matcher m = decl.matcher(lines[i]);
                    if (!m.matches() || lines[i].contains(" class ") || m.group(2).trim().isEmpty()) {
                        continue;
                    }
                    String ann = "";
                    for (int k = i - 1; k >= 0; k--) {
                        String tt = lines[k].trim();
                        if (tt.startsWith("@")) {
                            ann = tt;
                            break;
                        }
                        if (tt.isEmpty() || tt.startsWith("*") || tt.startsWith("/*") || tt.startsWith("//")) {
                            continue;
                        }
                        break;
                    }
                    boolean notAction = ann.contains("@NotAction");
                    pinned.add((registered.contains(simple) ? "[注册]" : "[未注册]")
                            + (notAction ? "[@NotAction]" : "[-]") + " " + simple + "." + m.group(1));
                    if (registered.contains(simple) && !notAction) {
                        hits.add(simple + "." + m.group(1) + "(" + m.group(2) + ")");
                    }
                }
            }
        }

        assertTrue(classes >= 20, "应扫描到 ≥20 个 Controller 类，实际 " + classes);
        assertTrue(registered.size() >= 20, "应识别出 ≥20 个已注册 controller，实际 " + registered.size());
        assertEquals(9, pinned.size(), "带参 public 方法清单变化了，须复核分类：\n  "
                + String.join("\n  ", new TreeSet<>(pinned)));
        assertTrue(hits.isEmpty(),
                "发现【已注册 + 非 @NotAction + 带参】的方法，说明 jfinal 参数绑定框架是必需的：\n  "
                        + String.join("\n  ", hits));
    }

    // ---------------- 支撑 ----------------

    /**
     * 建注入了参数的 Controller。
     *
     * @param params 单值参数
     * @return Controller
     */
    private static LegacyController controller(Map<String, String> params) {
        Map<String, String[]> multi = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            multi.put(e.getKey(), new String[]{e.getValue()});
        }
        LegacyController c = new LegacyController();
        c.setHttpServletRequest(spy(new LinkedHashMap<>(), multi));
        return c;
    }

    /**
     * 建带 Cookie 罐的请求替身。
     *
     * @param jar 名 -> Cookie
     * @return 替身
     */
    private static HttpServletRequest cookieSpy(Map<String, jakarta.servlet.http.Cookie> jar) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getCookies":
                    return jar.values().toArray(new jakarta.servlet.http.Cookie[0]);
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                case "toString":
                    return "CookieSpy";
                default:
                    return null;
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                MvcFoundationGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, h);
    }

    /** 探针子类：把 protected 的 doSetCookie 暴露给判据 */
    static class Probe extends LegacyController {
        /**
         * 暴露 doSetCookie。
         *
         * @param n 名
         * @param v 值
         * @param age 存活
         * @param path 路径
         * @param domain 域
         * @param secure HttpOnly
         * @return this
         */
        public LegacyController doSetCookie(String n, String v, int age, String path, String domain, Boolean secure) {
            return super.doSetCookie(n, v, age, path, domain, secure);
        }
    }

    /**
     * 建响应替身（只记录 {@code addCookie}）。
     *
     * @param added 收集被 add 的 Cookie
     * @return 替身
     */
    private static jakarta.servlet.http.HttpServletResponse responseSpy(
            List<jakarta.servlet.http.Cookie> added) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "addCookie":
                    added.add((jakarta.servlet.http.Cookie) args[0]);
                    return null;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                case "toString":
                    return "RespSpy";
                default:
                    return null;
            }
        };
        return (jakarta.servlet.http.HttpServletResponse) Proxy.newProxyInstance(
                MvcFoundationGoldenTest.class.getClassLoader(),
                new Class<?>[]{jakarta.servlet.http.HttpServletResponse.class}, h);
    }

    /**
     * 建请求替身（单接口动态代理）。
     *
     * @param attrs 属性表
     * @param multi 多值参数
     * @return 替身
     */
    private static HttpServletRequest spy(Map<String, Object> attrs, Map<String, String[]> multi) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getParameter": {
                    String[] v = multi.get(args[0]);
                    return (v != null && v.length > 0) ? v[0] : null;
                }
                case "getParameterValues":
                    return multi.get(args[0]);
                case "getParameterMap":
                    return new LinkedHashMap<>(multi);
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
                case "toString":
                    return "AttrSpy";
                default:
                    return null;
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                MvcFoundationGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, h);
    }

    /**
     * 旧类上按同名 + 归一后同形参在【声明方法】里找（含 protected）。
     *
     * @param oldCls 旧类
     * @param m      新方法
     * @return 旧方法；找不到返回 null
     */
    private static Method findOldDeclared(Class<?> oldCls, Method m) {
        for (Method om : oldCls.getDeclaredMethods()) {
            if (om.getName().equals(m.getName()) && om.getParameterCount() == m.getParameterCount()) {
                return om;
            }
        }
        return null;
    }

    /**
     * 旧类上按同名 + 归一后同形参找方法。
     *
     * @param oldCls 旧类
     * @param m      新方法
     * @return 旧方法；找不到返回 null
     */
    private static Method findOld(Class<?> oldCls, Method m) {
        outer:
        for (Method om : oldCls.getMethods()) {
            if (!om.getName().equals(m.getName()) || om.getParameterCount() != m.getParameterCount()) {
                continue;
            }
            Class<?>[] op = om.getParameterTypes();
            Class<?>[] np = m.getParameterTypes();
            for (int i = 0; i < op.length; i++) {
                if (!normalize(op[i].getName()).equals(normalize(np[i].getName()))) {
                    continue outer;
                }
            }
            return om;
        }
        return null;
    }

    /**
     * 类型名归一。
     *
     * @param n 类型全名
     * @return 归一后名字
     */
    private static String normalize(String n) {
        // 已声明适配：servlet 命名空间迁移 + Kv 接缝改名
        if (n.startsWith("javax.servlet.")) {
            return "jakarta.servlet." + n.substring("javax.servlet.".length());
        }
        if (n.equals("com.jfinal.kit.Kv")) {
            return "cn.eova.compat.jfinal.kit.LegacyKv";
        }
        if (n.equals("com.jfinal.render.Render")) {
            return "cn.eova.compat.render.LegacyRender";
        }
        if (n.equals("com.jfinal.core.Controller")) {
            return "cn.eova.compat.jfinal.core.LegacyController";
        }
        return n;
    }

    /** 计数 Controller */
    public static class CounterController extends LegacyController {
        /** 执行次数 */
        int runs = 0;

        private final List<String> trace;

        /**
         * 构造。
         *
         * @param trace 轨迹
         */
        public CounterController(List<String> trace) {
            this.trace = trace;
        }

        /** action */
        public void action() {
            runs++;
            trace.add("action");
        }
    }

    /** 渲染替身 */
    static final class StubRender extends LegacyRender {
        @Override
        public void render() {
            // 替身
        }
    }

}
