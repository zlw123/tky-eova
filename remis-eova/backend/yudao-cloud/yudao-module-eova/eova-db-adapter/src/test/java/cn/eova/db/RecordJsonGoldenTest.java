/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EovaRecord#toJson()} 与旧 {@code com.jfinal.plugin.activerecord.Record#toJson()}
 * 的跨实现等价验证。
 *
 * <p><b>为什么必须单独做这个判据：</b>{@code RecordSemanticsGoldenTest} 把
 * {@code json|toJson*} 与 {@code null|toJson} 三条探针<b>整体排除</b>了，理由是
 * "SP6 实测旧实现 toJson 键序非插入序，故键序不可作契约"（§3.8 第 3 条）。
 * 该理由本身成立，但结论<b>过度</b>：它把【值语义】也一并放弃了 ——
 * 而转义、数字、日期格式、null 处理恰恰是手写序列化器最容易错的地方。
 * 结果是 `EovaRecord.toJson()` 至今<b>从未被比对过</b>，却已在契约路径上被使用
 * （如 {@code FormControler:200} 的 {@code renderJson(record.toJson())}）。
 *
 * <p><b>本判据的口径：</b>不比对键序（承认其非契约），只比对<b>内容</b> ——
 * 把两侧输出解析为 Map 后逐键比对，另单独断言键集合相同。
 * 这样既补上值语义验证，又不把非契约的东西固化成契约。
 *
 * <p><b>为什么能直接跑旧实现：</b>{@code com.jfinal.json.Json} 的静态初始化即
 * {@code defaultJsonFactory = new JFinalJsonFactory()}，
 * 故 {@code JsonKit.toJson} / {@code Record.toJson} <b>无需 JFinal 启动</b>即可执行。
 * 加载器用 {@code createForJFinalOnly()}（只挂 jfinal 制品、不挂旧 EOVA 产物），
 * 结构性避开 R38 的陷阱。
 *
 * <p>acceptanceProfile: golden-record-tojson
 */
class RecordJsonGoldenTest {

    /** 旧实现类（jfinal 5.2.6，子优先加载） */
    private static Class<?> oldRecordClass;

    /** 待比对的值矩阵：名称 -> 值（覆盖转义/数字/日期/null/嵌套五类高风险面） */
    private static Map<String, Supplier<Object>> values() {
        Map<String, Supplier<Object>> m = new LinkedHashMap<>();
        // —— null 与空 ——
        m.put("null", () -> null);
        m.put("空串", () -> "");
        // —— 字符串转义（手写序列化器最易错）——
        m.put("中文", () -> "德玛西亚之力2");
        m.put("双引号", () -> "he said \"hi\"");
        m.put("反斜杠", () -> "a\\b");
        m.put("换行", () -> "a\nb");
        m.put("回车", () -> "a\rb");
        m.put("制表", () -> "a\tb");
        m.put("退格", () -> "a\bb");
        m.put("换页", () -> "a\fb");
        m.put("控制字符0x01", () -> "a\u0001b");
        m.put("正斜杠", () -> "a/b");
        m.put("单引号", () -> "it's");
        // —— 数字 ——
        m.put("Integer", () -> 7);
        m.put("Long", () -> 1234567890123L);
        m.put("Double", () -> 1.5d);
        m.put("Double整数", () -> 2.0d);
        m.put("Float", () -> 1.25f);
        m.put("BigDecimal", () -> new BigDecimal("1.50"));
        m.put("BigInteger", () -> new BigInteger("123456789012345678901234567890"));
        m.put("负整数", () -> -42);
        m.put("Short", () -> (short) 3);
        m.put("Byte", () -> (byte) 4);
        // —— 布尔 ——
        m.put("Boolean真", () -> Boolean.TRUE);
        m.put("Boolean假", () -> Boolean.FALSE);
        // —— 日期时间（JFinalJson 有专用处理器，格式与 toString 不同）——
        m.put("util.Date", () -> new Date(1568964679000L));
        m.put("sql.Date", () -> java.sql.Date.valueOf("2019-09-20"));
        m.put("sql.Time", () -> Time.valueOf("15:31:19"));
        m.put("Timestamp", () -> Timestamp.valueOf("2019-09-20 15:31:19"));
        m.put("LocalDateTime", () -> LocalDateTime.of(2019, 9, 20, 15, 31, 19));
        m.put("LocalDate", () -> LocalDate.of(2019, 9, 20));
        m.put("LocalTime", () -> LocalTime.of(15, 31, 19));
        // —— 嵌套 ——
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("k1", "v1");
        nested.put("k2", 2);
        m.put("嵌套Map", () -> nested);
        List<Object> list = new ArrayList<>();
        list.add("a");
        list.add(1);
        list.add(null);
        m.put("嵌套List", () -> list);
        m.put("枚举", () -> java.time.DayOfWeek.MONDAY);
        // —— 兜底与容器分支（手写版本在此全错：倒成字符串）——
        // 注：刻意【不】放 Calendar 探针 —— 实测旧侧 JFinalJsonKit$BeanToJson 反射进
        // GregorianCalendar 时会撞上 sun.util.calendar.ZoneInfo 未导出（Java 17 模块系统），
        // 即旧实现在 Java 17 上无法序列化 JDK 内部类型 bean。EOVA 不序列化 Calendar，
        // 该路径不在契约面上，故不作为本判据的输入（属探针越界）。
        m.put("Character", () -> 'x');
        m.put("Character引号", () -> '"');
        // 用确定性 toString 的对象压兜底分支：直接 new Object() 会因 identity hash
        // 不同而永远"有差异"（本征非确定，属探针缺陷而非实现分歧）
        m.put("Object兜底", () -> new Object() {
            @Override
            public String toString() {
                return "自定义\"值\\x";
            }
        });
        m.put("Object数组", () -> new Object[]{1, "a", null});
        m.put("Iterator", () -> list.iterator());
        m.put("深层嵌套", () -> deepNest());
        m.put("嵌套空Map", () -> new LinkedHashMap<String, Object>());
        m.put("嵌套空List", () -> new ArrayList<>());
        return m;
    }

    /** 构造三层嵌套结构，压一压递归写出路径 */
    private static Map<String, Object> deepNest() {
        Map<String, Object> lvl3 = new LinkedHashMap<>();
        lvl3.put("s", "x\"y");
        List<Object> lvl2 = new ArrayList<>();
        lvl2.add(lvl3);
        lvl2.add(1.5d);
        Map<String, Object> lvl1 = new LinkedHashMap<>();
        lvl1.put("list", lvl2);
        lvl1.put("n", null);
        return lvl1;
    }

    @Test
    @DisplayName("EovaRecord.toJson 与旧 Record.toJson 逐键比对（不计键序）：差异应为 0")
    void toJsonMatchesOldRecord() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldJFinalJarAvailable(),
                "旧 jfinal 制品缺失：" + OldImplementationLoader.oldJFinalJar());

        ClassLoader oldLoader = OldImplementationLoader.createForJFinalOnly();
        oldRecordClass = oldLoader.loadClass("com.jfinal.plugin.activerecord.Record");
        OldImplementationLoader.assertFromJar(oldRecordClass, OldImplementationLoader.oldJFinalJar());

        Method oldSet = oldRecordClass.getMethod("set", String.class, Object.class);
        Method oldToJson = oldRecordClass.getMethod("toJson");

        List<String> diffs = new ArrayList<>();
        int compared = 0;
        int escaped = 0;
        int dates = 0;

        for (Map.Entry<String, Supplier<Object>> e : values().entrySet()) {
            // 两侧各取一次值：Iterator 等有状态输入若共享实例，旧侧会先消费光，
            // 造成"新侧输出空"的假差异（属测试假象，非实现分歧）
            Object oldRec = oldRecordClass.getDeclaredConstructor().newInstance();
            oldSet.invoke(oldRec, "col", e.getValue().get());
            String oldJson = String.valueOf(oldToJson.invoke(oldRec));
            String newJson = new EovaRecord().set("col", e.getValue().get()).toJson();

            compared++;
            if (e.getKey().contains("引号") || e.getKey().contains("斜杠")
                    || e.getKey().contains("换行") || e.getKey().contains("回车")
                    || e.getKey().contains("制表") || e.getKey().contains("退格")
                    || e.getKey().contains("换页") || e.getKey().contains("控制字符")) {
                escaped++;
            }
            if (e.getKey().contains("Date") || e.getKey().contains("Time")) {
                dates++;
            }

            String oldContent = normalize(oldJson);
            String newContent = normalize(newJson);
            if (!oldContent.equals(newContent)) {
                diffs.add(e.getKey() + ":\n    旧=" + oldJson + "\n    新=" + newJson);
            }
        }

        System.out.println("[toJson 比对] 值 " + compared + " 个（转义 " + escaped
                + " / 日期时间 " + dates + "）；差异 " + diffs.size());

        // 空记录：零列时的输出（旧侧同样走 getColumns()）
        Object emptyOld = oldRecordClass.getDeclaredConstructor().newInstance();
        String emptyOldJson = String.valueOf(oldToJson.invoke(emptyOld));
        String emptyNewJson = new EovaRecord().toJson();
        if (!emptyOldJson.equals(emptyNewJson)) {
            diffs.add("空记录:\n    旧=" + emptyOldJson + "\n    新=" + emptyNewJson);
        }

        // 嵌套记录：记录作为值嵌在另一个记录里（对应 jfinal 的 RecordToJson）
        Object nestedOld = oldRecordClass.getDeclaredConstructor().newInstance();
        oldSet.invoke(nestedOld, "inner", "v");
        Object outerOld = oldRecordClass.getDeclaredConstructor().newInstance();
        oldSet.invoke(outerOld, "col", nestedOld);
        String nestedOldJson = String.valueOf(oldToJson.invoke(outerOld));
        String nestedNewJson =
                new EovaRecord().set("col", new EovaRecord().set("inner", "v")).toJson();
        if (!normalize(nestedOldJson).equals(normalize(nestedNewJson))) {
            diffs.add("嵌套记录:\n    旧=" + nestedOldJson + "\n    新=" + nestedNewJson);
        }
        compared += 2;
        // 非空转证据：矩阵必须真正压到高风险面
        assertTrue(escaped >= 6 && dates >= 5,
                "比对矩阵退化：转义 " + escaped + " / 日期时间 " + dates);
        assertTrue(diffs.isEmpty(),
                "toJson 与旧实现差异 " + diffs.size() + " 条：\n" + String.join("\n", diffs));
    }

    /**
     * 归一化：解析为「键=值」排序串，<b>刻意不计键序</b>（键序非契约，见类注释）。
     * 解析能力仅覆盖本判据用到的形态（扁平对象 + 标量/嵌套），够用即可。
     */
    private static String normalize(String json) {
        if (json == null) {
            return "null";
        }
        String s = json.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) {
            return s;
        }
        Map<String, String> kv = new LinkedHashMap<>();
        for (String pair : splitTopLevel(s.substring(1, s.length() - 1))) {
            int colon = indexOfTopLevelColon(pair);
            if (colon < 0) {
                return s;
            }
            kv.put(pair.substring(0, colon).trim(), pair.substring(colon + 1).trim());
        }
        List<String> keys = new ArrayList<>(kv.keySet());
        keys.sort(null);
        StringBuilder sb = new StringBuilder("{");
        for (String k : keys) {
            sb.append(k).append('=').append(kv.get(k)).append(';');
        }
        return sb.append('}').toString();
    }

    /** 按顶层逗号切分（跳过字符串内与嵌套结构内的逗号） */
    private static List<String> splitTopLevel(String body) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        StringBuilder cur = new StringBuilder();
        for (char c : body.toCharArray()) {
            if (inStr) {
                cur.append(c);
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
                cur.append(c);
            } else if (c == '{' || c == '[') {
                depth++;
                cur.append(c);
            } else if (c == '}' || c == ']') {
                depth--;
                cur.append(c);
            } else if (c == ',' && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    /** 顶层冒号位置（跳过字符串内） */
    private static int indexOfTopLevelColon(String pair) {
        boolean inStr = false;
        boolean esc = false;
        for (int i = 0; i < pair.length(); i++) {
            char c = pair.charAt(i);
            if (inStr) {
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
            } else if (c == ':') {
                return i;
            }
        }
        return -1;
    }
}
