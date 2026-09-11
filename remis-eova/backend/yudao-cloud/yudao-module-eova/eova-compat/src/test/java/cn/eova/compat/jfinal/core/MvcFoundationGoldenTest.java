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
import cn.eova.compat.jfinal.kit.LegacyHandlerKit;
import cn.eova.compat.render.DefaultLegacyRenderFactory;
import cn.eova.compat.render.LegacyErrorRender;
import cn.eova.compat.render.LegacyJsonRender;
import cn.eova.compat.render.LegacyTemplateRender;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

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
            // 第 54 轮补齐：get 别名族 + renderText/renderHtml/renderNull
            "get", "renderText", "renderHtml", "renderNull", "setAttrs",
            // W1b：toLong 族 + Cookie 族
            "getParaToLong", "getLong", "getCookie", "getCookieObject",
            "doSetCookie", "setCookie", "removeCookie",
            // 第 59 轮：Date 族（阻塞它的 TypeConverter 已于第 57 轮 port）
            "getDate", "getParaToDate",
            // 第 77 轮：上传族接缝（getFile/getFiles + 宿主注入部件容器的两个入口）
            "getFile", "getFiles", "getMultipartRequest", "setMultipartRequest",
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
                CALLS.add("getErrorRender:" + errorCode);
                return new StubRender();
            }

            @Override
            public LegacyRender getErrorRender(int errorCode, String view) {
                CALLS.add("getErrorRender:" + errorCode + ":" + view);
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
            public LegacyRender getTextRender(String text) {
                CALLS.add("getTextRender:" + text);
                return new StubRender();
            }

            @Override
            public LegacyRender getHtmlRender(String text) {
                CALLS.add("getHtmlRender:" + text);
                return new StubRender();
            }

            @Override
            public LegacyRender getNullRender() {
                CALLS.add("getNullRender()");
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

    /**
     * <b>覆盖断言：我方实现的方法集必须覆盖"全树实际被调用"的方法集。</b>
     *
     * <p><b>为什么必须单独有这一条：</b>我此前只有"方法集钉死"（断言实现集等于一份手写清单），
     * 而那份清单本身可能<b>漏项</b> —— 事实也确实漏了：</p>
     * <ol>
     *   <li>第一版普查只统计<b>裸调用</b>（{@code renderNull(}），漏掉了
     *       "持有 Controller 的普通类"的<b>限定调用</b>（{@code c.renderNull()}）；</li>
     *   <li>因此 {@code renderNull} 被误判为"未被调用"，而实际上
     *       {@code SseKit} 需要它；</li>
     *   <li>更要紧的是 {@code get}（4 个重载）—— <b>全树调用最多的方法（238 处）</b>——
     *       只因为我把它当成"别名"而没做，钉死清单也没能发现。</li>
     * </ol>
     * 本条按<b>裸调用 + 限定调用</b>两种形态重算普查集，并断言实现集 ⊇ 普查集。
     * 于是"清单漏项"这类错误会在这里失败，而不是等到 port 某个单元时才发现。
     *
     * <p><b>注意趋势方向：</b>普查集是<b>超集</b>（限定调用可能属于别的类型，例如
     * {@code record.get(...)} 会误算成 Controller 的 {@code get}）。这是<b>有意的</b> ——
     * 对"接缝面是否足够"而言，宁可多实现一个方法，也不能少。</p>
     *
     * @throws Exception 反射/读文件失败
     */
    @Test
    @DisplayName("覆盖断言：实现集必须覆盖全树实际被调用的 Controller 方法（裸调用 + 限定调用）")
    void controllerMethodCoverage() throws Exception {
        Path base = OldImplementationLoader.locateRepoRoot()
                .resolve("meta-eova/eova/core/src/main/java/cn/eova");
        assertTrue(Files.isDirectory(base), "旧源码目录缺失：" + base);

        // jfinal Controller 的方法名（含 protected）
        java.util.Set<String> all = new java.util.TreeSet<>();
        for (Method m : Class.forName("com.jfinal.core.Controller", false,
                OldImplementationLoader.createForJFinalOnly()).getDeclaredMethods()) {
            if (!Modifier.isPrivate(m.getModifiers())) {
                all.add(m.getName());
            }
        }
        assertTrue(all.size() >= 70, "应取到 ≥70 个 Controller 方法名，实际 " + all.size());

        java.util.Set<String> used = new java.util.TreeSet<>();
        int files = 0;
        try (Stream<Path> walk = Files.walk(base)) {
            for (Path p : (Iterable<Path>) walk.filter(x -> x.toString().endsWith(".java"))::iterator) {
                String src = Files.readString(p);
                if (!src.contains("com.jfinal.core.Controller")
                        && !src.contains("extends BaseController")
                        && !src.contains("Controller ")) {
                    continue;
                }
                files++;
                for (String n : all) {
                    // 子串搜索 "名称(" —— 同时覆盖裸调用（renderNull(）与限定调用（c.renderNull(）。
                    // 不用正则：上一步我写的正则转义非法，且此处无需边界断言（见方法注释中
                    // "普查集是有意取超集"的说明）。
                    if (src.contains(n + "(")) {
                        used.add(n);
                    }
                }
            }
        }
        assertTrue(files >= 30, "应扫描到 ≥30 个引用 Controller 的文件，实际 " + files);

        // 我方实现集
        java.util.Set<String> impl = new java.util.TreeSet<>();
        for (Method m : LegacyController.class.getDeclaredMethods()) {
            if (!Modifier.isPrivate(m.getModifiers()) && !m.isSynthetic() && !m.isBridge()) {
                impl.add(m.getName());
            }
        }

        java.util.Set<String> missing = new java.util.TreeSet<>(used);
        missing.removeAll(impl);
        missing.removeAll(DECLARED_PENDING.keySet());
        assertTrue(missing.isEmpty(),
                "以下方法在全树被调用但我方未实现，且未登记为已声明待办 —— 必须补上：\n  " + missing
                        + "\n（若是依赖未 port 的底座，请登记到 DECLARED_PENDING 并写明阻塞它的接缝）");
        assertTrue(used.size() >= 25, "普查集应 ≥25 个方法，实际 " + used.size() + "（防判据空洞）");
    }

    /**
     * <b>已声明待办</b>的 Controller 方法：全树被调用、但依赖尚未 port 的底座。
     *
     * <p>这是"覆盖断言"的白名单，<b>必须随对应底座落地而清空</b>——
     * 每项都写明了阻塞它的接缝，防止它变成"永远不做的借口"。</p>
     */
    private static final Map<String, String> DECLARED_PENDING = Map.of(
            // getFile/getFiles 已于第 77 轮落地（LegacyMultipartRequest 承担落盘语义），故从待办清单移除
            "getModel", "需 jfinal Model/Table 的绑定期语义",
            "validateCaptcha", "需验证码服务接缝（renderCaptcha/validateCaptcha 一族）");

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
        List<String> newSeamMembers = new ArrayList<>();
        for (Method m : LegacyController.class.getDeclaredMethods()) {
            if (m.isSynthetic() || m.isBridge() || Modifier.isPrivate(m.getModifiers())) {
                continue;
            }
            if (NEW_SEAM_MEMBERS.containsKey(m.getName())) {
                newSeamMembers.add(m.getName());
                continue;  // 旧实现没有的宿主注入点，见 NEW_SEAM_MEMBERS
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
        assertEquals(new ArrayList<>(new TreeSet<>(NEW_SEAM_MEMBERS.keySet())),
                new ArrayList<>(new TreeSet<>(newSeamMembers)),
                "『旧实现没有的新增接缝成员』必须与登记表逐项一致 —— 新增即登记，不得静默扩张声明面");
        assertTrue(problems.isEmpty(), "声明面差异：\n  " + String.join("\n  ", problems));
        assertTrue(compared >= 25, "至少比对 25 个方法，实际 " + compared + "（防空洞）");
    }

    /**
     * <b>旧实现没有、但新栈必需的宿主注入点</b>（声明面比对的显式豁免表）。
     *
     * <p>为什么需要这张表：旧 {@code Controller} 的 multipart 是<b>自己从原始请求解析</b>的
     * （{@code getFiles(uploadPath)} 内部 {@code new MultipartRequest(request, uploadPath)}），
     * 故它没有任何"注入上传部件"的成员；新栈由 Spring 解析 multipart，宿主必须有一个入口把
     * 解析结果交给控制器。这张表把这类成员<b>显式登记</b>，而不是让判据放宽成"找不到就跳过"。</p>
     */
    private static final Map<String, String> NEW_SEAM_MEMBERS = Map.of(
            "setMultipartRequest", "宿主注入上传部件容器（Spring 解析 multipart 的落点，第 77 轮）",
            "getMultipartRequest", "上者的对称读取口（供判据与宿主自检）");

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

    /**
     * {@code getKv()} 的 <b>{@code instanceof JsonRequest} 保真分支</b>。
     *
     * <p>该分支是我在第 58 轮<b>补上</b>的：W1a 实现 {@code getKv} 时还没有
     * {@code JsonRequest} 接缝，故漏了旧字节码开头这一支。补上之后必须有判据 ——
     * 否则"补了什么"无人看守。</p>
     */
    @Test
    @DisplayName("getKv：JSON 请求体（LegacyJsonRequest）的字段会并入 Kv")
    void getKvMergesJsonRequestFields() {
        // 普通请求：只有参数
        LegacyController plain = controller(Map.of("a", "1"));
        assertEquals("1", plain.getKv().get("a"));
        assertNull(plain.getKv().get("fromJson"), "普通请求不应有 JSON 字段");

        // JSON 请求：JSON 字段并入（且随后被参数表覆盖/补充）
        LegacyController c = new LegacyController();
        jakarta.servlet.http.HttpServletRequest json = new cn.eova.compat.jfinal.core.paragetter
                .LegacyJsonRequest("{\"fromJson\":\"J\",\"a\":\"json-a\"}",
                spy(new LinkedHashMap<>(), Map.of("a", new String[]{"1"})));
        c.setHttpServletRequest(json);
        LegacyKv kv = c.getKv();
        assertEquals("J", kv.get("fromJson"), "JSON 字段必须并入 Kv（该分支此前缺失）");
        // 【次序要点】同名键最终取 JSON 值 —— 因为 LegacyJsonRequest.createParaMap
        // 是"先 putAll(被包装请求的参数) ，再遍历 jsonObject（后者覆盖前者）"，
        // 故"JSON 优先"在 map 层与 getParameter 层【一致】。
        // 我第一版把期望写成参数值 "1"，被本判据当场纠正。
        assertEquals("json-a", kv.get("a"), "同名键取 JSON 值（createParaMap 里 JSON 后写覆盖）");
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
        c.renderText("t1");
        c.renderHtml("h1");
        c.renderNull();
        c.redirect("/r1");
        c.redirect("/r2", true);
        assertEquals(List.of("getRender:v1", "getTemplateRender:v2", "getJsonRender()",
                        "getJsonRender(String)", "getJsonRender(Object)", "getJsonRender(String[])",
                        "getJsonRender(String,Object)", "getTextRender:t1", "getHtmlRender:h1",
                        "getNullRender()", "getRedirectRender:/r1",
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

    /**
     * LegacyTemplateRender：contentType 与 toString 对照旧 jfinal 制品。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("LegacyTemplateRender：contentType/toString/init(null) 对照旧制品")
    void templateRenderMatchesOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.render.TemplateRender", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());

        Object oldRender = oldCls.getConstructor(String.class).newInstance("v/x.html");
        assertEquals(oldCls.getMethod("getContentType").invoke(oldRender),
                new LegacyTemplateRender("v/x.html").getContentType(),
                "contentType 必须逐字一致（旧为 \"text/html; charset=\" + 编码）");
        assertEquals("text/html; charset=UTF-8",
                new LegacyTemplateRender("v/x.html").getContentType(),
                "默认编码为 UTF-8");

        // toString 返回 view 本身（【不是】"TemplateRender: " + view）
        assertEquals(oldCls.getMethod("toString").invoke(oldRender),
                new LegacyTemplateRender("v/x.html").toString(),
                "toString 必须与旧实现一致");
        assertEquals("v/x.html", new LegacyTemplateRender("v/x.html").toString(),
                "旧实现直接返回 view —— 若凭直觉加类名前缀就错了");

        // init(null) 的消息逐字一致
        IllegalArgumentException oldEx = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> {
                    try {
                        java.lang.reflect.Method m = oldCls.getDeclaredMethod("init",
                                Class.forName("com.jfinal.template.Engine", true, jf));
                        m.setAccessible(true);
                        m.invoke(null, new Object[]{null});
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw (RuntimeException) e.getCause();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        IllegalArgumentException newEx = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> LegacyTemplateRender.init(null));
        assertEquals(oldEx.getMessage(), newEx.getMessage(), "init(null) 的消息必须逐字一致");
    }

    /**
     * <b>内建错误页与旧制品逐字节比对</b>（最强的一条：字节相等蕴含内容相等）。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("LegacyErrorRender：内建错误页/回退页与旧 jfinal 制品逐字节一致")
    void errorRenderPagesMatchOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.render.ErrorRender", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());

        // ① 内建表：逐状态码比对字节
        int compared = 0;
        for (String mapName : new String[]{"errorHtmlMap", "errorJsonMap"}) {
            java.lang.reflect.Field oldF = oldCls.getDeclaredField(mapName);
            oldF.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Integer, byte[]> oldMap = (Map<Integer, byte[]>) oldF.get(null);
            java.lang.reflect.Field newF = LegacyErrorRender.class.getDeclaredField(mapName);
            newF.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Integer, byte[]> newMap = (Map<Integer, byte[]>) newF.get(null);

            assertEquals(new TreeSet<>(oldMap.keySet()), new TreeSet<>(newMap.keySet()),
                    mapName + " 的状态码集合必须一致");
            for (Integer code : oldMap.keySet()) {
                assertTrue(Arrays.equals(oldMap.get(code), newMap.get(code)),
                        mapName + "[" + code + "] 必须逐字节一致");
                compared++;
            }
        }
        assertEquals(10, compared, "应有 10 项内建页（html/json × 400/401/403/404/500）");

        // ② 回退页（未命中内建表的状态码）逐字节比对
        Object oldFallback = oldCls.getConstructor(int.class).newInstance(999);
        LegacyErrorRender newFallback = new LegacyErrorRender(999);
        assertTrue(Arrays.equals(
                        (byte[]) oldCls.getMethod("getErrorHtml").invoke(oldFallback),
                        newFallback.getErrorHtml()),
                "回退 HTML 页必须逐字节一致");
        assertTrue(Arrays.equals(
                        (byte[]) oldCls.getMethod("getErrorJson").invoke(oldFallback),
                        newFallback.getErrorJson()),
                "回退 JSON 体必须逐字节一致（含键序 state,msg）");
    }

    /** ErrorRender.render 的两条分支 */
    @Test
    @DisplayName("LegacyErrorRender.render：内建字节分支（状态码+contentType+字节）")
    void errorRenderBuiltinBranch() throws Exception {
        java.io.StringWriter out = new java.io.StringWriter();
        java.util.List<Integer> status = new ArrayList<>();
        java.util.List<String> cts = new ArrayList<>();
        LegacyErrorRender r = new LegacyErrorRender(404);
        r.setContext(probeRequest(Map.of()), outSpy(status, cts, out));
        r.render();

        assertEquals(List.of(404), status, "必须先 setStatus(404)");
        assertEquals(List.of("text/html; charset=UTF-8"), cts, "非 JSON 请求用 HTML contentType");
        assertTrue(out.toString().contains("404 Not Found"), "应写内建 404 页，实际：" + out);

        // JSON 请求（contentType 含 json）→ 走 JSON contentType 与 JSON 体
        java.io.StringWriter out2 = new java.io.StringWriter();
        java.util.List<Integer> st2 = new ArrayList<>();
        java.util.List<String> ct2 = new ArrayList<>();
        LegacyErrorRender r2 = new LegacyErrorRender(500);
        r2.setContext(jsonRequest(), outSpy(st2, ct2, out2));
        r2.render();
        assertEquals(List.of("application/json; charset=UTF-8"), ct2, "JSON 请求用 JSON contentType");
        assertTrue(out2.toString().contains("\"state\":\"fail\""), "实际：" + out2);
    }

    /**
     * 建能记录 setStatus/setContentType 并捕获写出内容的响应替身。
     *
     * @param status 状态码收集
     * @param cts    contentType 收集
     * @param out    输出缓冲
     * @return 替身
     */
    private static jakarta.servlet.http.HttpServletResponse outSpy(
            java.util.List<Integer> status, java.util.List<String> cts, java.io.Writer out) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "setStatus":
                    status.add((Integer) args[0]);
                    return null;
                case "setContentType":
                    cts.add((String) args[0]);
                    return null;
                case "getOutputStream":
                    return outputStreamOf(out);
                case "getWriter":
                    return new java.io.PrintWriter(out);
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
     * 把 Writer 包成 {@code ServletOutputStream}。
     *
     * <p><b>为什么不能用 Proxy：</b>{@code jakarta.servlet.ServletOutputStream}
     * 是<b>抽象类</b>（不是接口），{@code Proxy} 只能代理接口 ——
     * 该错误由本判据第一次运行时报出（"is not an interface"）。</p>
     *
     * @param out 目标 Writer
     * @return 输出流
     */
    private static jakarta.servlet.ServletOutputStream outputStreamOf(java.io.Writer out) {
        return new jakarta.servlet.ServletOutputStream() {
            @Override
            public void write(int b) throws java.io.IOException {
                out.write(b);
            }

            @Override
            public void write(byte[] b) throws java.io.IOException {
                out.write(new String(b));
            }

            @Override
            public void write(byte[] b, int off, int len) throws java.io.IOException {
                out.write(new String(b, off, len));
            }

            @Override
            public void flush() throws java.io.IOException {
                out.flush();
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {
                // 判据不需要
            }
        };
    }

    /**
     * 建 contentType 为 application/json 的请求替身。
     *
     * @return 替身
     */
    private static HttpServletRequest jsonRequest() {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getContentType":
                    return "application/json;charset=UTF-8";
                case "getAttributeNames":
                    return java.util.Collections.emptyEnumeration();
                case "getAttribute":
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
                MvcFoundationGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, h);
    }

    /** 默认工厂：四类渲染都能造出来，且 render 族端到端可跑 */
    @Test
    @DisplayName("DefaultLegacyRenderFactory：串起四类渲染，render 族端到端可用")
    void defaultFactoryWiresAllRenders() {
        DefaultLegacyRenderFactory f = new DefaultLegacyRenderFactory();
        assertTrue(f.getRender("v") instanceof LegacyTemplateRender);
        assertTrue(f.getTemplateRender("v") instanceof LegacyTemplateRender);
        assertTrue(f.getJsonRender() instanceof LegacyJsonRender);
        assertTrue(f.getJsonRender(new String[]{"a"}) instanceof LegacyJsonRender);
        assertTrue(f.getJsonRender("{}") instanceof LegacyJsonRender);
        assertTrue(f.getJsonRender(new Object()) instanceof LegacyJsonRender);
        assertTrue(f.getJsonRender("k", new Object()) instanceof LegacyJsonRender);
        assertTrue(f.getErrorRender(404) instanceof LegacyErrorRender);
        assertTrue(f.getErrorRender(404, "v") instanceof LegacyErrorRender);
        assertTrue(f.getRedirectRender("/a") instanceof LegacyRedirectRender);
        assertTrue(f.getRedirectRender("/a", true) instanceof LegacyRedirectRender);
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
     * <b>Date 族（第 59 轮）</b>：签名对齐旧制品 + 行为矩阵 + 错误消息逐字钉死。
     *
     * <p><b>为什么不做"旧实例 vs 新实例"的活体比对：</b>本 testkit 已明令
     * {@code createWithOldJFinal} 不得用于 EOVA 类比对（挂载 jfinal 后旧 EOVA 类
     * 会静默返回错值），而 {@code com.jfinal.core.Controller} 是抽象类，
     * 无法在只挂 jfinal 的加载器里实例化。故改用三条<b>制品级</b>证据：</p>
     * <ol>
     *   <li>签名逐项对齐（返回类型、形参类型、throws）—— 从旧制品反射读取；</li>
     *   <li>错误消息的<b>两个字面量片段从旧 class 常量池里读出</b>，
     *       而不是我照 javap 注释手抄（javap 注释里的空白不可数，本工程栽过）；</li>
     *   <li>行为矩阵：blank / 合法 / 非法 三类输入逐一断言。</li>
     * </ol>
     *
     * @throws Exception 反射/读 jar 失败
     */
    @Test
    @DisplayName("getDate 族：签名对齐旧制品 + 常量池消息钉死 + blank/合法/非法矩阵")
    void dateFamilyMatchesOldArtifact() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.core.Controller", false, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());

        // ① 签名对齐：5 个方法逐一比对返回类型 / 形参 / throws
        String[] names = {"getDate", "getDate", "getParaToDate", "getParaToDate", "getParaToDate"};
        int[] arity = {1, 2, 1, 2, 0};
        int checked = 0;
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Method mine = java.util.Arrays.stream(LegacyController.class.getMethods())
                    .filter(m -> m.getName().equals(names[idx]) && m.getParameterCount() == arity[idx])
                    .findFirst().orElseThrow();
            Method old = findOld(oldCls, mine);
            assertNotNull(old, "旧制品应有 " + mine);
            assertEquals(old.getReturnType().getName(), mine.getReturnType().getName(),
                    mine + " 返回类型必须一致");
            for (int k = 0; k < arity[i]; k++) {
                assertEquals(normalize(old.getParameterTypes()[k].getName()),
                        normalize(mine.getParameterTypes()[k].getName()),
                        mine + " 第 " + k + " 个形参类型必须一致");
            }
            assertEquals(old.getExceptionTypes().length, mine.getExceptionTypes().length,
                    mine + " 的 throws 子句必须一致（旧实现声明为 0 个受检异常）");
            checked++;
        }
        assertEquals(5, checked, "必须核到 5 个 Date 方法（防判据空洞）");

        // ② 错误消息字面量：从旧 class 文件的常量池读，不手抄
        //    （javap 反汇编注释里的空白不可数 —— 本工程在 Captcha.toString 上栽过，
        //     所以消息片段必须以【制品字节】为准）
        String oldClassText;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(
                OldImplementationLoader.oldJFinalJar().toFile())) {
            java.util.zip.ZipEntry entry = zip.getEntry("com/jfinal/core/Controller.class");
            assertNotNull(entry, "旧制品里必须有 com/jfinal/core/Controller.class");
            try (java.io.InputStream in = zip.getInputStream(entry)) {
                oldClassText = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
            }
        }
        assertTrue(oldClassText.contains("Can not parse the parameter \""),
                "旧常量池里应有消息前缀（javap 显示的 \\\" 是【显示转义】，常量池存的是真实引号）");
        assertTrue(oldClassText.contains("\" to Date value."),
                "旧常量池里应有消息后缀");

        // ③ 行为矩阵（用 List：早先写成 Map<"d", …> 且五组输入共用同一个键，
        //    塌成 1 组 —— 矩阵计数断言当场抓出。保留注释以免后人重犯）
        List<String> values = List.of("2024-01-02 03:04:05", "2024-01-02", "abc", "   ", "");
        int cells = 0;
        for (String raw : values) {
            String v = raw.isEmpty() ? null : raw;
            Map<String, String[]> params = new LinkedHashMap<>();
            params.put("d", new String[]{v});
            LegacyController c = new LegacyController();
            c.setHttpServletRequest(spy(new LinkedHashMap<>(), params));
            if (v == null || v.trim().isEmpty()) {
                assertNull(c.getDate("d"),
                        "blank/null 必须回落 null（空值判定用 StrKit.isBlank，不是 isEmpty）：" + raw);
                java.util.Date def = new java.util.Date(1234567890L);
                assertSame(def, c.getDate("d", def), "blank 时必须原样回落【同一个】缺省实例");
                cells += 2;
                continue;
            }
            if ("abc".equals(v)) {
                try {
                    java.util.Date r = c.getDate("d");
                    fail("非法日期必须抛异常，而不是返回：" + r);
                } catch (LegacyActionException e) {
                    assertEquals(400, e.getErrorCode(), "必须是 400");
                    assertNotNull(e.getErrorRender(), "必须带 400 错误渲染");
                    assertTrue(e.getMessage().startsWith("Can not parse the parameter \""),
                            "消息前缀必须逐字一致，实际：" + e.getMessage());
                    assertTrue(e.getMessage().endsWith("\" to Date value."),
                            "消息后缀必须逐字一致，实际：" + e.getMessage());
                    assertTrue(e.getMessage().contains("\"" + v + "\""),
                            "消息必须内插【原始值】（不可改成参数名），实际：" + e.getMessage());
                    cells++;
                }
                continue;
            }
            java.util.Date parsed = c.getDate("d");
            assertNotNull(parsed, "合法日期必须解析成功：" + v);
            cells++;
        }
        assertEquals(7, cells, "矩阵必须逐格断言（防判据空洞），实际 " + cells);
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

    /**
     * {@code LegacyHandlerKit} 的四个方法（逐字节码语义）。
     */
    @Test
    @DisplayName("LegacyHandlerKit：isHandled 置位 / 404 / 查询串拼接 / 301 三头")
    void handlerKitSemantics() {
        // 【必须清空】CALLS 是静态共享列表：别的用例（如 renderFamilyDelegatesToFactory 的
        // renderError(404)）会往里面放 "getErrorRender:404"，断言 contains 就会被残留满足 ——
        // 该漏检由变异测试实测发现（把 404 改成 500 竟然仍通过）。
        CALLS.clear();

        // ① renderError404(req,resp,isHandled)：置位 + 经工厂取 404 渲染 + render()
        boolean[] h1 = {false};
        java.util.List<String> cts1 = new ArrayList<>();
        LegacyHandlerKit.renderError404(probeRequest(Map.of()), outSpy(new ArrayList<>(), cts1,
                new java.io.StringWriter()), h1);
        assertTrue(h1[0], "isHandled 必须置 true");
        assertTrue(CALLS.contains("getErrorRender:404"),
                "应经工厂取【404】错误渲染（记录状态码才能抓住改码这类变异），实际：" + CALLS);

        // ② renderError404(view,...)：置位 + setStatus(404) + 取视图渲染
        boolean[] h2 = {false};
        java.util.List<Integer> st2 = new ArrayList<>();
        java.util.List<String> cts2 = new ArrayList<>();
        LegacyHandlerKit.renderError404("err/404.html", probeRequest(Map.of()),
                outSpy(st2, cts2, new java.io.StringWriter()), h2);
        assertTrue(h2[0]);
        assertEquals(List.of(404), st2, "该重载必须显式 setStatus(404)（与少参重载不同）");

        // ③ redirect：查询串按 url 是否含 '?' 选分隔符
        boolean[] h3 = {false};
        List<String> redirected = new ArrayList<>();
        LegacyHandlerKit.redirect("/a", probeRequest(Map.of("__qs", "x=1")),
                redirectSpy(redirected, null), h3);
        assertTrue(h3[0]);
        assertEquals(List.of("/a?x=1"), redirected, "无 ? 时用 ?");

        boolean[] h4 = {false};
        List<String> redirected2 = new ArrayList<>();
        LegacyHandlerKit.redirect("/a?k=v", probeRequest(Map.of("__qs", "x=1")),
                redirectSpy(redirected2, null), h4);
        assertEquals(List.of("/a?k=v&x=1"), redirected2, "已有 ? 时用 &");

        // 无查询串时原样
        boolean[] h5 = {false};
        List<String> redirected3 = new ArrayList<>();
        LegacyHandlerKit.redirect("/a", probeRequest(Map.of()), redirectSpy(redirected3, null), h5);
        assertEquals(List.of("/a"), redirected3);

        // ④ redirect301：不 sendRedirect，而是 setStatus(301) + Location + Connection
        boolean[] h6 = {false};
        List<String> redirected4 = new ArrayList<>();
        List<Integer> st6 = new ArrayList<>();
        java.util.List<String[]> headers = new ArrayList<>();
        LegacyHandlerKit.redirect301("/b", probeRequest(Map.of("__qs", "y=2")),
                redirectSpy(redirected4, headers, st6), h6);
        assertTrue(h6[0]);
        assertTrue(redirected4.isEmpty(), "301 分支【不得】调用 sendRedirect（旧字节码如此）");
        assertEquals(List.of(301), st6);
        assertEquals(List.of("/b?y=2"), List.of(headers.get(0)[1]), "Location 头应为拼接后的 URL");
        assertEquals("Connection", headers.get(1)[0]);
        assertEquals("close", headers.get(1)[1]);
    }

    /**
     * 建只记录 sendRedirect 的响应替身。
     *
     * @param redirected 收集 sendRedirect 的目标
     * @param headers    收集 setHeader（可为 null）
     * @return 替身
     */
    private static jakarta.servlet.http.HttpServletResponse redirectSpy(
            List<String> redirected, java.util.List<String[]> headers) {
        return redirectSpy(redirected, headers, new ArrayList<>());
    }

    /**
     * 建记录 sendRedirect / setHeader / setStatus 的响应替身。
     *
     * @param redirected 收集 sendRedirect
     * @param headers    收集 setHeader（可为 null）
     * @param status     收集 setStatus
     * @return 替身
     */
    private static jakarta.servlet.http.HttpServletResponse redirectSpy(
            List<String> redirected, java.util.List<String[]> headers, List<Integer> status) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "sendRedirect":
                    redirected.add((String) args[0]);
                    return null;
                case "setHeader":
                    if (headers != null) {
                        headers.add(new String[]{(String) args[0], (String) args[1]});
                    }
                    return null;
                case "setStatus":
                    status.add((Integer) args[0]);
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
        if (n.equals("com.jfinal.upload.UploadFile")) {
            // 第 77 轮：上传文件类型接缝（字段/构造/getFile() 语义等价，类型名不同）
            return "cn.eova.compat.jfinal.upload.LegacyUploadFile";
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
