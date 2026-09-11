/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.function.Supplier;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LegacyJsonKit#toJson(Object)} 对 jfinal 5.2.6 真制品的**逐样本等价验证**（第 76 轮，修 R59）。
 *
 * <p><b>为什么必须补这个判据：</b>第 55 轮首版只实现了兜底 {@code UnknownToJson}
 * （未注册类型一律 {@code toString()}），漏掉了 {@code createToJson} 的倒数第二步
 * {@code buildBeanToJson}（POJO 反射 getter）。既有对照台账的输入全是
 * {@code Record}/{@code Model}/{@code Map}/{@code Collection}/{@code Date}/字符串 ——
 * <b>每一级都有专门处理器，从不触及 bean 分支</b>，于是"缺一级分派"这件事
 * 在编译绿、判据全绿的情况下潜伏了 20 轮，直到 {@code BaseApi.OK/NO} 用 POJO 渲染时才暴露
 * （企业侧 {@code /router/eova/*} 应答体从 {@code {"code":0,…}} 退化为 {@code cn.eova…@hash}）。</p>
 *
 * <p><b>本判据的组织原则：样本必须覆盖"分派链的每一级"，尤其兜底级。</b>
 * 每条样本两侧各建一个<b>独立实例</b>（{@code Supplier}），避免共享可变状态造成假差异；
 * 两侧输出<b>逐字符</b>比对，并对异常形态比对"异常类型 + 消息"。</p>
 *
 * acceptanceProfile: golden-legacy-json-bean
 */
class LegacyJsonKitBeanCrossGoldenTest {

    private static Class<?> oldJsonKitClass;

    // ---------------------------------------------------------------- 样本类型

    /** 平面 POJO（形状同 {@code ApiResponse}：两个标量 + 一个可为 null 的对象） */
    public static class FlatBean {
        private final int code;
        private final String msg;
        private final Object data;

        /**
         * @param code 状态码
         * @param msg  消息
         * @param data 载荷
         */
        public FlatBean(int code, String msg, Object data) {
            this.code = code;
            this.msg = msg;
            this.data = data;
        }

        public int getCode() {
            return code;
        }

        public String getMsg() {
            return msg;
        }

        public Object getData() {
            return data;
        }
    }

    /** 继承 + 布尔 is 取值器 + 嵌套 POJO + 特例名（{@code getClass} 必须被排除） */
    public static class ParentBean {
        public String getName() {
            return "父";
        }
    }

    /** 子类：含 {@code isXxx()}、{@code island()} 这类名字、以及嵌套 POJO 字段 */
    public static class ChildBean extends ParentBean {
        private final FlatBean inner;

        /**
         * @param inner 嵌套对象
         */
        public ChildBean(FlatBean inner) {
            this.inner = inner;
        }

        public boolean isOk() {
            return true;
        }

        public String island() {
            return "岛";
        }

        public FlatBean getInner() {
            return inner;
        }

        public String getEmpty() {
            return null;
        }
    }

    /** 取值器抛异常（验证 ReflectiveOperationException → RuntimeException 的包装形态） */
    public static class ThrowingBean {
        public String getBoom() {
            throw new IllegalStateException("boom-msg");
        }
    }

    /** 无任何取值器 ⇒ 必须落到 UnknownToJson（toString 兜底）；toString 固定以便两侧可比 */
    public static class NoGetterBean {
        @Override
        public String toString() {
            return "NO-GETTER";
        }
    }

    /** 枚举（含字段） */
    public enum Color {
        /** 红 */
        RED(1),
        /** 绿 */
        GREEN(2);

        private final int code;

        Color(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    /** 覆写了 {@code toString()} 的枚举：旧实现输出 toString()（不是 name()） */
    public enum Loud {
        /** 唯一常量 */
        ONLY;

        @Override
        public String toString() {
            return "LOUD!";
        }
    }

    /** 记录容器替身：验证 JsonColumns 分支（新接缝特有）—— 本判据不跨实现比对它 */
    public static class ColumnsBean implements LegacyJsonKit.JsonColumns {
        private final Map<String, Object> map = new LinkedHashMap<>();

        @Override
        public Map<String, Object> jsonColumns() {
            map.put("a", 1);
            map.put("b", "二");
            return map;
        }
    }

    // ---------------------------------------------------------------- 装配

    /**
     * 装载旧 jfinal 制品的 {@code com.jfinal.kit.JsonKit}。
     *
     * @throws Exception 装载失败
     */
    @BeforeAll
    static void loadOldJsonKit() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldJFinalJarAvailable(),
                "旧 jfinal 制品缺失：" + OldImplementationLoader.oldJFinalJar());
        ClassLoader oldLoader = OldImplementationLoader.createWithOldJFinal(
                OldImplementationLoader.locateRepoRoot());
        oldJsonKitClass = Class.forName("com.jfinal.kit.JsonKit", true, oldLoader);
    }

    /**
     * 自校验：确保参与比对的是<b>旧 jfinal 制品</b>而非本项目的新接缝。
     *
     * <p>加载器一旦配错，比对会退化成"新 vs 新"并恒真 —— 这种错误不会以失败暴露，
     * 只会让判据失去意义，故必须显式断言来源（同 {@code OldImplementationLoader.assertFromOldArtifacts} 的动机）。</p>
     */
    private static void assertOldArtifact() {
        var src = oldJsonKitClass.getProtectionDomain().getCodeSource();
        String loc = src == null || src.getLocation() == null ? "null" : src.getLocation().toString();
        assertTrue(loc.endsWith("jfinal-5.2.6.jar"),
                "参与比对的 JsonKit 必须来自 jfinal-5.2.6.jar，实际=" + loc);
        assertNotEquals(LegacyJsonKit.class.getName(), oldJsonKitClass.getName(),
                "新接缝与旧制品不得是同一个类");
    }

    /**
     * 调旧制品的 {@code JsonKit.toJson(Object)}。
     *
     * @param value 输入
     * @return JSON 文本
     * @throws Exception 反射失败
     */
    private static String oldToJson(Object value) throws Exception {
        Method m = oldJsonKitClass.getMethod("toJson", Object.class);
        return (String) m.invoke(null, value);
    }

    // ---------------------------------------------------------------- 样本矩阵

    /**
     * 样本工厂（每次调用返回一个**新实例**，两侧各取一次）。
     *
     * @return 名称 → 样本工厂
     */
    private static Map<String, Supplier<Object>> samples() {
        Map<String, Supplier<Object>> m = new LinkedHashMap<>();
        m.put("null", () -> null);
        m.put("String", () -> "a\"b\\c\nd\te\u0001f 中 \u2003");
        m.put("Character", () -> 'x');
        m.put("Integer", () -> 42);
        m.put("Long", () -> 7L);
        m.put("Double", () -> 1.5d);
        m.put("Double.NaN", () -> Double.NaN);
        m.put("Float", () -> 1.25f);
        m.put("Short", () -> (short) 3);
        m.put("Byte", () -> (byte) 4);
        m.put("BigDecimal", () -> new BigDecimal("1.50"));
        m.put("BigInteger", () -> new BigInteger("9"));
        m.put("Boolean", () -> Boolean.TRUE);
        m.put("Enum", () -> Color.GREEN);
        m.put("Enum覆写toString", () -> Loud.ONLY);
        m.put("util.Date", () -> new Date(1568964679000L));
        m.put("sql.Date", () -> java.sql.Date.valueOf("2019-09-20"));
        m.put("Timestamp", () -> Timestamp.valueOf("2019-09-20 15:31:19"));
        m.put("sql.Time", () -> Time.valueOf("15:31:19"));
        m.put("LocalDateTime", () -> LocalDateTime.of(2019, 9, 20, 15, 31, 19));
        m.put("LocalDate", () -> LocalDate.of(2019, 9, 20));
        m.put("LocalTime", () -> LocalTime.of(15, 31, 19));
        // 未注册的 Temporal：必须走 Bean 分支（旧版本按 toString 写出，是缺陷）
        m.put("ZonedDateTime", () -> ZonedDateTime.of(2019, 9, 20, 15, 31, 19, 0, ZoneOffset.UTC));
        m.put("Instant", () -> Instant.ofEpochMilli(1568964679000L));
        m.put("OffsetDateTime", () -> OffsetDateTime.of(2019, 9, 20, 15, 31, 19, 0, ZoneOffset.UTC));

        m.put("Map平面", () -> {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("a", 1);
            n.put("b", null);
            n.put("c", "中");
            return n;
        });
        m.put("Map特殊键", () -> {
            Map<Object, Object> n = new LinkedHashMap<>();
            n.put("k\"q", 1);
            n.put("k\\b", 2);
            n.put("k\nn", 3);
            n.put(5, "intKey");
            return n;
        });
        m.put("Map嵌套", () -> {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("i", "内");
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("outer", inner);
            n.put("list", new ArrayList<>(Arrays.asList(1, 2, "三")));
            return n;
        });
        m.put("List", () -> new ArrayList<>(Arrays.asList("a", 1, null)));
        m.put("Set", () -> new LinkedHashSet<>(Arrays.asList("x", "y")));
        m.put("String数组", () -> new String[]{"p", "q"});
        m.put("int数组", () -> new int[]{1, 2, 3});
        m.put("byte数组", () -> new byte[]{1, 2});
        m.put("char数组", () -> new char[]{'a', '"'});
        m.put("boolean数组", () -> new boolean[]{true, false});
        m.put("long数组", () -> new long[]{5L, 6L});
        m.put("double数组含NaN", () -> new double[]{1.5d, Double.NaN, Double.POSITIVE_INFINITY});
        m.put("二维数组", () -> new int[][]{{1, 2}, {3}});
        m.put("对象数组", () -> new FlatBean[]{new FlatBean(1, "a", null)});
        m.put("Enumeration", () -> {
            Vector<String> v = new Vector<>();
            v.add("e1");
            v.add("e2");
            return v.elements();
        });
        m.put("Iterator", () -> Arrays.asList("i1", "i2").iterator());
        m.put("Iterable", () -> (Iterable<String>) () -> Arrays.asList("it1").iterator());

        // —— Bean 分支（R59 的主体）——
        m.put("Bean平面", () -> new FlatBean(0, "ok", null));
        m.put("Bean带载荷", () -> new FlatBean(0, "ok", new FlatBean(7, "内", "值")));
        m.put("Bean继承+is+特例名", () -> new ChildBean(new FlatBean(1, "m", null)));
        m.put("Bean无取值器", () -> new NoGetterBean());
        m.put("Bean取值器抛异常", () -> new ThrowingBean());
        m.put("Bean嵌套在Map", () -> {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("bean", new FlatBean(2, "m2", null));
            return n;
        });
        m.put("Bean嵌套在List", () -> new ArrayList<>(List.of(new FlatBean(3, "m3", null))));

        // —— 深度边界（顶层 depth=16；每个容器 -1；第 18 层起应为 null）——
        m.put("深度15", () -> nest(15));
        m.put("深度16", () -> nest(16));
        m.put("深度17", () -> nest(17));
        m.put("深度18", () -> nest(18));
        m.put("深度20", () -> nest(20));
        return m;
    }

    /**
     * 造 n 层嵌套 Map（叶值为字符串）。
     *
     * @param n 层数
     * @return 最外层 Map
     */
    private static Map<String, Object> nest(int n) {
        Map<String, Object> cur = new LinkedHashMap<>();
        cur.put("leaf", "L");
        for (int i = 0; i < n; i++) {
            Map<String, Object> outer = new LinkedHashMap<>();
            outer.put("k" + i, cur);
            cur = outer;
        }
        return cur;
    }

    // ---------------------------------------------------------------- 判据

    @Test
    @DisplayName("toJson：样本矩阵逐字符等价（含 Bean 分支与深度边界）")
    void toJsonMatchesOldOnEverySample() throws Exception {
        assertOldArtifact();
        List<String> diffs = new ArrayList<>();
        int compared = 0;
        int beanSamples = 0;
        for (Map.Entry<String, Supplier<Object>> e : samples().entrySet()) {
            String name = e.getKey();
            String expect;
            try {
                expect = oldToJson(e.getValue().get());
            } catch (Exception ex) {
                expect = "EX:" + rootName(ex);
            }
            String actual;
            try {
                actual = LegacyJsonKit.toJson(e.getValue().get());
            } catch (Exception ex) {
                actual = "EX:" + rootName(ex);
            }
            compared++;
            if (name.startsWith("Bean")) {
                beanSamples++;
            }
            if (!expect.equals(actual)) {
                diffs.add(name + "\n    旧=" + expect + "\n    新=" + actual);
            }
        }
        assertTrue(beanSamples >= 7, "Bean 分支样本必须成组覆盖（当前 " + beanSamples + "）");
        assertEquals(List.of(), diffs, "与 jfinal 5.2.6 存在 " + diffs.size() + " 处差异");
        assertTrue(compared >= 40, "样本数过少（当前 " + compared + "）");
    }

    /**
     * 取根因名字（解包 InvocationTargetException）。
     *
     * @param t 异常
     * @return 根因类名
     */
    private static String rootName(Throwable t) {
        Throwable cur = t;
        if (cur instanceof java.lang.reflect.InvocationTargetException && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getClass().getName();
    }

    @Test
    @DisplayName("Bean 分支：键名是取值器裁剪名（含 is 分支与 getClass 排除），且不转义")
    void beanBranchShape() throws Exception {
        // 形状断言以【旧制品】为准（不写死顺序：键序 = getMethods() 返回序，非契约）
        ChildBean bean = new ChildBean(new FlatBean(1, "m", null));
        String json = LegacyJsonKit.toJson(bean);
        assertEquals(oldToJson(bean), json, "整串必须与旧实现一致");
        assertTrue(json.contains("\"name\":\"父\""), "父类取值器也要被收集：" + json);
        assertTrue(json.contains("\"ok\":true"), "isXxx() 分支的键名是裁掉 is 后的 ok：" + json);
        assertTrue(json.contains("\"land\":\"岛\""),
                "island() 走 is 分支 ⇒ 裁掉前两个字符得到 land（既有行为，原样保留）：" + json);
        assertTrue(json.contains("\"empty\":null"), "null 字段不跳过：" + json);
        assertTrue(!json.contains("\"class\""), "getClass() 必须被排除：" + json);

        // 无取值器 ⇒ toString 兜底
        assertEquals("\"NO-GETTER\"", LegacyJsonKit.toJson(new NoGetterBean()),
                "无 getXxx/isXxx 时必须落到 UnknownToJson");
        // 取值器抛异常 ⇒ RuntimeException（原因保留为反射层的 InvocationTargetException）
        RuntimeException ex = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> LegacyJsonKit.toJson(new ThrowingBean()));
        assertNotNull(ex.getCause(), "必须保留原因（旧字节码 new RuntimeException(e)）");
        assertEquals("java.lang.reflect.InvocationTargetException", ex.getCause().getClass().getName(),
                "原因是反射层的 InvocationTargetException（不是业务异常本身）");
    }

    @Test
    @DisplayName("逐级纠错回归：NaN/Infinity → null、基本类型数组按结构展开、枚举走 toString")
    void correctedDispatchLevels() {
        assertEquals("null", LegacyJsonKit.toJson(Double.NaN), "Double.NaN 必须写 null（JSON 无该字面量）");
        assertEquals("null", LegacyJsonKit.toJson(Double.POSITIVE_INFINITY));
        assertEquals("null", LegacyJsonKit.toJson(Float.NEGATIVE_INFINITY));
        assertEquals("null", LegacyJsonKit.toJson(Float.NaN));
        assertEquals("1.5", LegacyJsonKit.toJson(1.5d));
        assertEquals("1.25", LegacyJsonKit.toJson(1.25f));
        assertEquals("[1,2,3]", LegacyJsonKit.toJson(new int[]{1, 2, 3}),
                "基本类型数组必须按结构展开（不是 [I@hash）");
        assertEquals("[[1,2],[3]]", LegacyJsonKit.toJson(new int[][]{{1, 2}, {3}}));
        assertEquals("\"LOUD!\"", LegacyJsonKit.toJson(Loud.ONLY),
                "枚举走 Enum.toString() 而不是 name()");
    }

    @Test
    @DisplayName("契约回归：ApiResponse 形状的 POJO 输出 code/msg/data（R59 的原始症状）")
    void apiResponseShapeRegression() throws Exception {
        // 注意：键序【非契约】（第 55 轮 §3.8 已定：键序 = getMethods() 返回序，JVM 不保证稳定），
        // 故此处只断言"键集合 + 语义"，并与旧制品整串比对（同 JVM 同序才比整串 —— 这才是稳的写法）。
        FlatBean bean = new FlatBean(0, "ok", null);
        String json = LegacyJsonKit.toJson(bean);
        assertEquals(oldToJson(bean), json, "整串必须与 jfinal 5.2.6 一致");
        assertTrue(json.contains("\"code\":0"), json);
        assertTrue(json.contains("\"msg\":\"ok\""), json);
        assertTrue(json.contains("\"data\":null"), json);
        assertTrue(json.startsWith("{") && json.endsWith("}"), "必须是 JSON 对象：" + json);
        assertTrue(!json.contains("FlatBean@"), "不得退化为 toString()：" + json);
    }

    @Test
    @DisplayName("深度上限：15/16/17/18/20 层嵌套的输出与旧实现逐字符一致")
    void depthLimit() throws Exception {
        assertOldArtifact();
        for (int n : new int[]{15, 16, 17, 18, 20}) {
            assertEquals(oldToJson(nest(n)), LegacyJsonKit.toJson(nest(n)),
                    "嵌套 " + n + " 层的输出必须与 jfinal 5.2.6 一致");
        }
        // 结构性事实：深度用尽处写出 null（而不是写出半个容器）
        assertTrue(LegacyJsonKit.toJson(nest(20)).contains(":null"),
                "20 层时必然出现被截断的 null：" + LegacyJsonKit.toJson(nest(20)));
    }

    @Test
    @DisplayName("JsonColumns 分支（新接缝特有的记录出口）仍按 Map 展开")
    void jsonColumnsBranch() {
        assertEquals("{\"a\":1,\"b\":\"二\"}", LegacyJsonKit.toJson(new ColumnsBean()));
    }

    @Test
    @DisplayName("旧制品加载器自校验：类必须来自 jfinal-5.2.6.jar")
    void loaderSelfCheck() {
        assertOldArtifact();
    }
}
