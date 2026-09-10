/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LegacyKv} / {@link LegacyRet} 对 jfinal 5.2.6 真制品的逐方法等价验证。
 *
 * <p><b>为什么必须做：</b>{@code Kv}/{@code Ret} 是 EOVA 的通用数据容器，
 * 被大量单元间接使用；而 §2.1 曾据"enjoy 也提供 {@code Kv}"判断无需固化 ——
 * 实测该判断只对编译成立、对语义不成立（enjoy 版移除了 {@code toJson()}，
 * 且 {@code Ret} 在 enjoy 中整个缺失），故按 R37/R40 固化为项目自有类。
 * 固化之后，等价性必须由旧实现亲自作证。
 *
 * <p><b>本判据覆盖三块：</b>
 * <ol>
 *   <li><b>取值器 × 值矩阵</b>：逐个 getter × 逐个值类型，比对"返回值"或"异常类型+消息"。
 *       这一块专门用来钉住 <b>Kv 与 Ret 的类间不一致</b>：
 *       {@code Kv.getInt} 走 TypeKit 转换（字符串 "1" 可解析），
 *       而 {@code Ret.getInt} 是 {@code (Number)} 强转（字符串抛 ClassCastException）；
 *       {@code Kv.isTrue} 对 null 抛 NPE，而 {@code Ret.isTrue} 返回 false。</li>
 *   <li><b>静态工厂</b>：{@code ok/fail/state/data/msg/of/by/create} 的内容与 {@code toJson}。</li>
 *   <li><b>容器语义</b>：{@code equals} 的类型限制、{@code keep} 的原地修改、
 *       {@code toMap} 的同一性、{@code isOk}/{@code isFail} 的异常行为。</li>
 * </ol>
 *
 * <p>acceptanceProfile: golden-legacy-kv-ret
 */
class LegacyKvRetGoldenTest {

    private static Class<?> oldKvClass;
    private static Class<?> oldRetClass;

    /** Kv 的待比对取值器（两侧同名同签名） */
    private static final List<String> KV_GETTERS = List.of(
            "getStr", "getInt", "getLong", "getBigDecimal", "getDouble", "getFloat",
            "getNumber", "getBoolean", "getDate", "getLocalDateTime",
            "notNull", "isNull", "notBlank", "isBlank", "isTrue", "isFalse");

    /** Ret 的待比对取值器（注意 Ret 无 getBigDecimal/getDate/getLocalDateTime/notBlank/isBlank） */
    private static final List<String> RET_GETTERS = List.of(
            "getStr", "getInt", "getLong", "getDouble", "getFloat",
            "getNumber", "getBoolean",
            "notNull", "isNull", "isTrue", "isFalse");

    /** 值矩阵（每侧各取一次，避免共享有状态输入造成假差异） */
    private static Map<String, Supplier<Object>> values() {
        Map<String, Supplier<Object>> m = new LinkedHashMap<>();
        m.put("null", () -> null);
        m.put("Integer", () -> 1);
        m.put("Long", () -> 7L);
        m.put("Double", () -> 1.5d);
        m.put("Float", () -> 1.25f);
        m.put("Short", () -> (short) 3);
        m.put("Byte", () -> (byte) 4);
        m.put("BigDecimal", () -> new BigDecimal("1.50"));
        m.put("BigInteger", () -> new BigInteger("9"));
        m.put("Str数字", () -> "1");
        m.put("Str小数", () -> "1.5");
        m.put("Str非数字", () -> "abc");
        m.put("Str中文", () -> "这个");
        m.put("Str空", () -> "");
        m.put("Str空白", () -> "   ");
        m.put("Str日期", () -> "2019-09-20");
        m.put("Str日期时间", () -> "2019-09-20 15:31:19");
        m.put("Boolean真", () -> Boolean.TRUE);
        m.put("Boolean假", () -> Boolean.FALSE);
        m.put("Str true", () -> "true");
        m.put("Str 1", () -> "1 ");
        m.put("util.Date", () -> new Date(1568964679000L));
        m.put("Timestamp", () -> Timestamp.valueOf("2019-09-20 15:31:19"));
        m.put("sql.Time", () -> Time.valueOf("15:31:19"));
        m.put("LocalDateTime", () -> LocalDateTime.of(2019, 9, 20, 15, 31, 19));
        m.put("LocalDate", () -> LocalDate.of(2019, 9, 20));
        m.put("LocalTime", () -> LocalTime.of(15, 31, 19));
        m.put("Map", () -> {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("a", 1);
            return n;
        });
        m.put("List", () -> new ArrayList<>(List.of("a", 1)));
        return m;
    }

    @Test
    @DisplayName("取值器 × 值矩阵：返回/异常应与 jfinal 5.2.6 逐条一致")
    void gettersMatchOld() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldJFinalJarAvailable(),
                "旧 jfinal 制品缺失：" + OldImplementationLoader.oldJFinalJar());
        setUp();

        List<String> diffs = new ArrayList<>();
        int compared = 0;
        int throwsSeen = 0;
        List<String> crossClassProofs = new ArrayList<>();

        for (String getter : KV_GETTERS) {
            Method oldM = oldKvClass.getMethod(getter, Object.class);
            Method newM = LegacyKv.class.getMethod(getter, Object.class);
            for (Map.Entry<String, Supplier<Object>> e : values().entrySet()) {
                compared++;
                Object oldOut = invokeOnOld(oldKvClass, oldM, e.getValue().get());
                Object newOut = invokeOnNewKv(newM, e.getValue().get());
                if (oldOut instanceof Throw) {
                    throwsSeen++;
                }
                if (!render(oldOut).equals(render(newOut))) {
                    diffs.add("Kv." + getter + "(" + e.getKey() + "): 旧=" + render(oldOut)
                            + " / 新=" + render(newOut));
                }
            }
        }

        for (String getter : RET_GETTERS) {
            Method oldM = oldRetClass.getMethod(getter, Object.class);
            Method newM = LegacyRet.class.getMethod(getter, Object.class);
            for (Map.Entry<String, Supplier<Object>> e : values().entrySet()) {
                compared++;
                Object oldOut = invokeOnOld(oldRetClass, oldM, e.getValue().get());
                Object newOut = invokeOnNewRet(newM, e.getValue().get());
                if (oldOut instanceof Throw) {
                    throwsSeen++;
                }
                if (!render(oldOut).equals(render(newOut))) {
                    diffs.add("Ret." + getter + "(" + e.getKey() + "): 旧=" + render(oldOut)
                            + " / 新=" + render(newOut));
                }
            }
        }

        System.out.println("[LegacyKv/Ret 比对] 取值器 " + (KV_GETTERS.size() + RET_GETTERS.size())
                + " × 值 " + values().size() + " = 比对 " + compared + " 条；抛异常 "
                + throwsSeen + " 条；差异 " + diffs.size());
        assertTrue(throwsSeen > 0, "比对矩阵退化：没有任何异常分支被覆盖，判据不具判别力");
        assertTrue(diffs.isEmpty(),
                "LegacyKv/LegacyRet 与 jfinal 5.2.6 差异 " + diffs.size() + " 条：\n"
                        + String.join("\n", diffs));
    }

    @Test
    @DisplayName("静态工厂与 isOk/isFail：内容、toJson 与异常行为应与旧实现一致")
    void factoriesMatchOld() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldJFinalJarAvailable(),
                "旧 jfinal 制品缺失：" + OldImplementationLoader.oldJFinalJar());
        setUp();

        List<String> diffs = new ArrayList<>();

        // —— ok()/fail()/ok(msg)/fail(msg)/ok(k,v)/fail(k,v)/state/data/msg ——
        List<Supplier<Object>> oldFactories = List.of(
                () -> callStatic(oldRetClass, "ok"),
                () -> callStatic(oldRetClass, "fail"),
                () -> callStatic(oldRetClass, "ok", String.class, "你好"),
                () -> callStatic(oldRetClass, "fail", String.class, "密码错误"),
                () -> callStatic(oldRetClass, "ok", Object.class, Object.class, "k", 1),
                () -> callStatic(oldRetClass, "fail", Object.class, Object.class, "k", 1),
                () -> callStatic(oldRetClass, "state", Object.class, "custom"),
                () -> callStatic(oldRetClass, "data", Object.class, "payload"),
                () -> callStatic(oldRetClass, "msg", String.class, "hi"),
                () -> callStatic(oldRetClass, "of", Object.class, Object.class, "a", 1),
                () -> callStatic(oldRetClass, "create"));
        List<Supplier<Object>> newFactories = List.of(
                LegacyRet::ok,
                LegacyRet::fail,
                () -> LegacyRet.ok("你好"),
                () -> LegacyRet.fail("密码错误"),
                () -> LegacyRet.ok("k", 1),
                () -> LegacyRet.fail("k", 1),
                () -> LegacyRet.state("custom"),
                () -> LegacyRet.data("payload"),
                () -> LegacyRet.msg("hi"),
                () -> LegacyRet.of("a", 1),
                LegacyRet::create);
        List<String> names = List.of("ok", "fail", "ok(msg)", "fail(msg)", "ok(k,v)", "fail(k,v)",
                "state", "data", "msg", "of", "create");

        for (int i = 0; i < oldFactories.size(); i++) {
            Object oldRet = oldFactories.get(i).get();
            Object newRet = newFactories.get(i).get();
            String oldJson = String.valueOf(oldRet.getClass().getMethod("toJson").invoke(oldRet));
            String newJson = ((LegacyRet) newRet).toJson();
            if (!oldJson.equals(newJson)) {
                diffs.add("Ret." + names.get(i) + " 内容: 旧=" + oldJson + " / 新=" + newJson);
            }
            // isOk/isFail（含未设 state 时应抛 IllegalStateException）
            for (String m : List.of("isOk", "isFail")) {
                Object oldOut = invokeQuietly(oldRet, oldRet.getClass().getMethod(m));
                Object newOut = invokeQuietly(newRet, LegacyRet.class.getMethod(m));
                if (!render(oldOut).equals(render(newOut))) {
                    diffs.add("Ret." + names.get(i) + "." + m + ": 旧=" + render(oldOut)
                            + " / 新=" + render(newOut));
                }
            }
            // equals 的类型限制：Ret vs 同内容 HashMap 必须为 false
            boolean oldEqMap = oldRet.equals(new HashMap<>(toMapOf(oldRet)));
            boolean newEqMap = newRet.equals(new HashMap<>(toMapOf(newRet)));
            if (oldEqMap != newEqMap) {
                diffs.add("Ret." + names.get(i) + ".equals(HashMap): 旧=" + oldEqMap + " / 新=" + newEqMap);
            }
        }

        // —— Kv：of/by/create + toJson + equals 类型限制 ——
        LegacyKv kvNew = LegacyKv.of("a", 1);
        Object kvOld = callStatic(oldKvClass, "of", Object.class, Object.class, "a", 1);
        String kvOldJson = String.valueOf(kvOld.getClass().getMethod("toJson").invoke(kvOld));
        if (!kvOldJson.equals(kvNew.toJson())) {
            diffs.add("Kv.of 内容: 旧=" + kvOldJson + " / 新=" + kvNew.toJson());
        }
        boolean kvOldEqMap = kvOld.equals(new HashMap<>(toMapOf(kvOld)));
        if (kvOldEqMap != kvNew.equals(new HashMap<>(toMapOf(kvNew)))) {
            diffs.add("Kv.equals(HashMap) 不一致");
        }

        System.out.println("[LegacyKv/Ret 比对] 静态工厂比对完成；差异 " + diffs.size());
        assertTrue(diffs.isEmpty(), "静态工厂差异 " + diffs.size() + " 条：\n" + String.join("\n", diffs));
    }

    @Test
    @DisplayName("keep 为原地修改、toMap 返回自身、equals 限制类型：三处易错语义")
    void containerSemantics() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldJFinalJarAvailable(),
                "旧 jfinal 制品缺失：" + OldImplementationLoader.oldJFinalJar());
        setUp();

        // keep()：原地修改，且返回 this
        LegacyKv kv = LegacyKv.create();
        kv.set("a", 1).set("b", 2).set("c", 3);
        LegacyKv kept = kv.keep("a", "c");
        assertSame(kv, kept, "keep 必须返回 this（原地修改），不是新对象");
        assertEquals(2, kv.size(), "keep 后应只剩命中的键");
        assertTrue(kv.containsKey("a") && kv.containsKey("c") && !kv.containsKey("b"));

        Object oldKv = callStatic(oldKvClass, "create");
        Method oldSet = oldKvClass.getMethod("set", Object.class, Object.class);
        oldSet.invoke(oldKv, "a", 1);
        oldSet.invoke(oldKv, "b", 2);
        oldSet.invoke(oldKv, "c", 3);
        Method oldKeep = oldKvClass.getMethod("keep", String[].class);
        Object oldKept = oldKeep.invoke(oldKv, (Object) new String[]{"a", "c"});
        assertSame(oldKv, oldKept, "旧实现 keep 也是原地修改 —— 本断言用于确认口径");
        assertEquals(kv.size(), ((Map<?, ?>) oldKv).size(), "keep 后元素数应与旧实现一致");

        // keep(null) 等价于 clear
        kv.keep((String[]) null);
        assertEquals(0, kv.size(), "keep(null) 应清空");
        Object oldKv2 = callStatic(oldKvClass, "create");
        oldSet.invoke(oldKv2, "a", 1);
        oldKeep.invoke(oldKv2, (Object) null);
        assertEquals(0, ((Map<?, ?>) oldKv2).size(), "旧实现 keep(null) 也清空 —— 确认口径");

        // toMap() 返回 this 本身
        LegacyKv kv3 = LegacyKv.of("x", 1);
        assertSame(kv3, kv3.toMap(), "toMap 必须返回 this 本身，不是副本");

        // equals 的类型限制
        LegacyKv kv4 = LegacyKv.of("x", 1);
        LegacyRet ret4 = LegacyRet.of("x", 1);
        assertFalse(kv4.equals(ret4), "Kv 与 Ret 内容相同也不相等（类型限制）");
        assertFalse(ret4.equals(kv4), "Ret 与 Kv 内容相同也不相等（类型限制）");
        assertTrue(kv4.equals(LegacyKv.of("x", 1)), "同为 Kv 且内容相同应相等");
        assertTrue(ret4.equals(LegacyRet.of("x", 1)), "同为 Ret 且内容相同应相等");

        // isOk/isFail 未设 state 时抛 IllegalStateException，消息与旧实现一致
        Object oldBare = callStatic(oldRetClass, "create");
        Object oldIsOk = invokeQuietly(oldBare, oldRetClass.getMethod("isOk"));
        Object newIsOk = invokeQuietly(LegacyRet.create(), LegacyRet.class.getMethod("isOk"));
        assertEquals(render(oldIsOk), render(newIsOk),
                "未设 state 时 isOk() 的异常类型与消息应与旧实现一致");
        Object oldIsFail = invokeQuietly(oldBare, oldRetClass.getMethod("isFail"));
        Object newIsFail = invokeQuietly(LegacyRet.create(), LegacyRet.class.getMethod("isFail"));
        assertEquals(render(oldIsFail), render(newIsFail),
                "未设 state 时 isFail() 的异常类型与消息应与旧实现一致");

        System.out.println("[LegacyKv/Ret 比对] 容器语义断言全部通过（keep 原地修改 / toMap 同一性 / equals 类型限制 / isOk 异常消息）");
    }

    @Test
    @DisplayName("LegacyJsonKit.parse 与真实 jfinal Json.getJson().parse 同构（内容一致）")
    void parseMatchesOldJson() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldJFinalJarAvailable(),
                "旧 jfinal 制品缺失");
        ClassLoader loader = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldJsonClass = loader.loadClass("com.jfinal.json.Json");
        OldImplementationLoader.assertFromJar(oldJsonClass, OldImplementationLoader.oldJFinalJar());

        // 关键：生产里 EovaConfig 装的是 MixedJsonFactory（MixedJson.parse -> fastjson）。
        // 不装它时 Json.getJson() 返回 JFinalJson，走的是另一条 parse 路径 ——
        // 那样比对的就不是生产行为了。该 setter 包内可见，故用反射装入。
        // （反向对照已验证：不装时下面这条断言会红，说明本守卫不是摆设。）
        Class<?> factoryClass = loader.loadClass("com.jfinal.json.MixedJsonFactory");
        java.lang.reflect.Field f = oldJsonClass.getDeclaredField("defaultJsonFactory");
        f.setAccessible(true);
        f.set(null, factoryClass.getDeclaredConstructor().newInstance());

        Object oldJson = oldJsonClass.getMethod("getJson").invoke(null);
        assertEquals("MixedJson", oldJson.getClass().getSimpleName(),
                "装入 MixedJsonFactory 后应得到 MixedJson（与生产一致）");
        Class<?> oldKvClass = loader.loadClass("com.jfinal.kit.Kv");

        String[] samples = {
                "{\"a\":1,\"b\":\"x\"}",
                "{\"n\":null,\"f\":1.5,\"t\":true}",
                "{\"中文\":\"值\",\"nested\":{\"k\":\"v\"}}",
                "{}",
        };
        int compared = 0;
        for (String json : samples) {
            Object oldKv = oldJsonClass.getMethod("parse", String.class, Class.class)
                    .invoke(oldJson, json, oldKvClass);
            LegacyKv newKv = LegacyJsonKit.parse(json, LegacyKv.class);
            assertNotNull(oldKv, "旧侧解析不应为 null：" + json);
            assertNotNull(newKv, "新侧解析不应为 null：" + json);
            assertEquals(String.valueOf(oldKv), String.valueOf(newKv),
                    "解析结果内容应一致：" + json);
            compared++;
        }
        System.out.println("[LegacyJsonKit] parse 与真实 jfinal Json.getJson().parse 同构（"
                + compared + " 个样例）");
    }

    // ———————————————————————— 辅助 ————————————————————————

    private static void setUp() throws Exception {
        ClassLoader oldLoader = OldImplementationLoader.createForJFinalOnly();
        oldKvClass = oldLoader.loadClass("com.jfinal.kit.Kv");
        oldRetClass = oldLoader.loadClass("com.jfinal.kit.Ret");
        OldImplementationLoader.assertFromJar(oldKvClass, OldImplementationLoader.oldJFinalJar());
        OldImplementationLoader.assertFromJar(oldRetClass, OldImplementationLoader.oldJFinalJar());
    }

    private static Object invokeOnOld(Class<?> cls, Method m, Object value) throws Exception {
        Object target = cls.getDeclaredConstructor().newInstance();
        cls.getMethod("set", Object.class, Object.class).invoke(target, "k", value);
        return invokeQuietly(target, m, "k");
    }

    private static Object invokeOnNewKv(Method m, Object value) throws Exception {
        return invokeQuietly(LegacyKv.create().set("k", value), m, "k");
    }

    private static Object invokeOnNewRet(Method m, Object value) throws Exception {
        return invokeQuietly(LegacyRet.create().set("k", value), m, "k");
    }

    /** 反射调用旧实现的静态工厂：显式传类型数组与实参数组，避免 varargs 歧义 */
    private static Object callStatic(Class<?> cls, String name, Class<?>[] types, Object[] args) {
        try {
            return cls.getMethod(name, types).invoke(null, args);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object callStatic(Class<?> cls, String name) {
        return callStatic(cls, name, new Class<?>[0], new Object[0]);
    }

    private static Object callStatic(Class<?> cls, String name, Class<?> t1, Object a1) {
        return callStatic(cls, name, new Class<?>[]{t1}, new Object[]{a1});
    }

    private static Object callStatic(Class<?> cls, String name, Class<?> t1, Class<?> t2,
                                     Object a1, Object a2) {
        return callStatic(cls, name, new Class<?>[]{t1, t2}, new Object[]{a1, a2});
    }

    /** 反射调用并把"正常返回"与"抛异常"统一为可观测量 */
    private static Object invokeQuietly(Object target, Method m, Object... args) {
        try {
            return new Ok(m.invoke(target, args));
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            return new Throw(t.getClass().getName(), t.getMessage());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> toMapOf(Object container) {
        return new LinkedHashMap<>((Map<String, Object>) container);
    }

    private record Ok(Object value) {
    }

    private record Throw(String type, String message) {
    }

    /** 渲染比对口径：异常类型与消息都逐字比对（消息本身也是金标口径的一部分） */
    private static String render(Object o) {
        if (o instanceof Ok ok) {
            return "Ok:" + ok.value();
        }
        if (o instanceof Throw t) {
            return "Throw:" + t.type() + ":" + t.message();
        }
        return "?:" + o;
    }
}
