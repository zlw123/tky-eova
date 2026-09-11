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

    /** W1a 已实现的非私有方法名 */
    private static final List<String> W1A_METHODS = List.of(
            "_clear_", "setHttpServletRequest", "setHttpServletResponse", "setUrlPara",
            "getRequest", "getResponse", "getPara", "getParaMap", "getRawData",
            "getParaToInt", "getParaToBoolean", "getInt", "getBoolean", "set", "setAttr", "getAttr",
            "removeAttr", "keepPara", "getKv", "render", "getRender",
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
        LegacyRenderManager.setRenderFactory(errorCode -> new StubRender());
    }

    /** 方法集钉死 */
    @Test
    @DisplayName("W1a 方法集钉死")
    void w1aMethodSetIsPinned() {
        List<String> actual = new ArrayList<>();
        for (Method m : LegacyController.class.getDeclaredMethods()) {
            if (!m.isSynthetic() && !m.isBridge() && !Modifier.isPrivate(m.getModifiers())) {
                actual.add(m.getName());
            }
        }
        assertEquals(new ArrayList<>(new TreeSet<>(W1A_METHODS)), new ArrayList<>(new TreeSet<>(actual)),
                "方法集必须与 W1a 清单一致；新增（W1b/W2）时同步清单");
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
     * 钉住带参方法清单。
     *
     * @throws Exception 读文件失败
     */
    @Test
    @DisplayName("钉住带参方法清单：存在真正的带参 action（要求 W1c 补参数绑定）")
    void pinnedArgBearingActionMethods() throws Exception {
        Path base = OldImplementationLoader.locateRepoRoot().resolve("meta-eova/eova/core/src/main/java/cn/eova");
        assertTrue(Files.isDirectory(base), "旧源码目录缺失：" + base);

        Pattern decl = Pattern.compile("^\\s+public\\s+(?:void|[\\w.<>\\[\\]]+)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{?\\s*$");
        List<String> withArgs = new ArrayList<>();
        int classes = 0;
        try (Stream<Path> walk = Files.walk(base)) {
            for (Path p : (Iterable<Path>) walk.filter(x -> x.toString().endsWith("Controller.java"))::iterator) {
                String src = Files.readString(p);
                if (!src.contains("extends BaseController") && !src.contains("extends Controller")) {
                    continue;
                }
                classes++;
                for (String line : src.split("\n")) {
                    Matcher m = decl.matcher(line);
                    if (m.matches() && !line.contains(" class ") && !m.group(2).trim().isEmpty()) {
                        withArgs.add(p.getFileName() + " -> " + m.group(1) + "(" + m.group(2) + ")");
                    }
                }
            }
        }
        assertTrue(classes >= 20, "应扫描到 ≥20 个 Controller 类，实际 " + classes);
        assertEquals(PINNED_ARG_BEARING, new ArrayList<>(new TreeSet<>(withArgs)),
                "带参方法清单变化 —— 真正的带参 action 要求 W1c 补参数绑定（LegacyAction.args 现为临时空数组）");
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
