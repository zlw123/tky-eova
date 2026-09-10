/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import cn.eova.compat.table.EovaTableMapping;
import com.jfinal.plugin.activerecord.ActiveRecordPlugin;
import com.jfinal.plugin.activerecord.Model;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EovaModel} 对真实 jfinal 5.2.6 {@code Model} 的等价验证
 * （阶段 1 `D-MODEL` 前置 2）。
 *
 * <p><b>这是"让旧实现上场"的判据，不是自造期望值：</b>本测试同时启动真实的
 * {@code ActiveRecordPlugin} + jfinal {@code Model} 子类与 {@link EovaModel} 子类，
 * 对同一张 {@code eova_user} 表跑同一批操作，逐条比对结果。
 * 该 oracle 已由探针验证可行（find/findById/toJson 均跑通）。
 *
 * <p><b>判据只做读取与属性语义，不修改基线库：</b>save/update 会写库，
 * 其 SQL 生成与 null 过滤细节需单独一轮用<b>临时表</b>比对（已列入计划）。
 * 删除语义词只验证异常分支，不触发真实删除。
 *
 * <p><b>刻意保留的差异（已声明，非缺陷）：</b>列校验与主键缺失的异常类型由
 * {@code com.jfinal.plugin.activerecord.ActiveRecordException} 改名为
 * {@link EovaActiveRecordException} —— 实测 EOVA 对该类型零引用，
 * 且两者同为 {@code RuntimeException}，本判据按<b>消息</b>比对（消息逐字一致）。
 *
 * <p>acceptanceProfile: golden-eova-model
 */
class EovaModelGoldenTest {

    private static final String URL = "jdbc:mysql://127.0.0.1:13306/eova_meta"
            + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String USER = "root";
    private static final String PWD = "root";

    private static ActiveRecordPlugin arp;

    /** 旧栈侧：真实 jfinal Model 子类（_getModifyFlag 是 protected，经子类暴露给判据） */
    public static class JfUser extends Model<JfUser> {
        public static final JfUser dao = new JfUser();

        /** 暴露 protected 的 _getModifyFlag 供判据比对 */
        public java.util.Set<String> modFlag() {
            return _getModifyFlag();
        }
    }

    /** 新栈侧：EovaModel 子类（同样经子类暴露，保证两侧访问路径对称） */
    public static class EvUser extends EovaModel<EvUser> {
        public static final EvUser dao = new EvUser().dao();

        /** 暴露 protected 的 _getModifyFlag 供判据比对 */
        public java.util.Set<String> modFlag() {
            return _getModifyFlag();
        }
    }

    @BeforeAll
    static void setUp() {
        // harness 自检：旧侧必须加载【jfinal 5.2.6 的】com.jfinal.kit.*
        // （enjoy 5.3.0 提供同名类，若它先解析，旧侧会成为混合体，oracle 失去意义 —— R37/R38）
        assertOldSideUsesJFinalKits();
        DataSource ds = dataSource();
        arp = new ActiveRecordPlugin(ds);
        arp.addMapping("eova_user", JfUser.class);
        arp.start();

        EovaTableMapping mapping = EovaTableMapping.me();
        mapping.clear();
        mapping.setMetadataSource(new JdbcTableMetadataSource(ds));
        mapping.addMapping("eova", "eova_user", EvUser.class);
        EovaModel.setGateway(new JdbcEovaDbGateway(ds));
    }

    @AfterAll
    static void tearDown() {
        if (arp != null) {
            arp.stop();
        }
    }

    /**
     * 自检：{@code com.jfinal.kit.TypeKit} 必须来自 jfinal 5.2.6，而非 enjoy 5.3.0。
     *
     * <p>存在的理由与 {@code OldImplementationLoader.assertFromOldArtifacts} 相同：
     * 同名的宿主类跨制品漂移（R37）会让"旧侧"悄悄变成混合体，
     * 此时比对仍会跑、仍会给出数字，但那些数字没有意义。
     * 这种失效不会以失败的形式暴露，只能靠显式断言钉住。
     */
    private static void assertOldSideUsesJFinalKits() {
        String src = codeSourceOf("com.jfinal.kit.TypeKit");
        assertTrue(src.contains("jfinal-5.2.6"),
                "harness 污染：com.jfinal.kit.TypeKit 实际来自 " + src
                        + "，而不是 jfinal-5.2.6 —— 旧侧将退化为"
                        + "\"jfinal Model + enjoy TypeKit\" 的混合体，比对结果无意义");
        String modelSrc = codeSourceOf("com.jfinal.plugin.activerecord.Model");
        assertTrue(modelSrc.contains("jfinal-5.2.6"),
                "harness 污染：Model 实际来自 " + modelSrc);
        System.out.println("[EovaModel 比对] harness 自检通过：旧侧 TypeKit/Model 均来自 jfinal-5.2.6");
    }

    /** 取类的来源 jar 名 */
    private static String codeSourceOf(String className) {
        try {
            Class<?> c = Class.forName(className);
            var cs = c.getProtectionDomain().getCodeSource();
            return cs == null || cs.getLocation() == null ? "(unknown)" : cs.getLocation().toString();
        } catch (ClassNotFoundException e) {
            return "(not found)";
        }
    }

    private static DataSource dataSource() {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(URL);
        ds.setUser(USER);
        ds.setPassword(PWD);
        return ds;
    }

    @Test
    @DisplayName("查询：find / findById 的列与值应与真实 Model 逐列一致")
    void queriesMatchOldModel() {
        List<JfUser> oldList = JfUser.dao.find("select * from eova_user order by id limit 3");
        List<EvUser> newList = EvUser.dao.find("select * from eova_user order by id limit 3");
        assertEquals(oldList.size(), newList.size(), "find 行数应一致");
        assertTrue(oldList.size() > 0, "基线库应有 eova_user 数据");

        List<String> diffs = new ArrayList<>();
        for (int i = 0; i < oldList.size(); i++) {
            Map<String, Object> o = new LinkedHashMap<>(oldList.get(i).toMap());
            Map<String, Object> n = new LinkedHashMap<>(newList.get(i).toMap());
            // 键集合必须一致（键序不承诺 —— §3.8 第 3 条）
            assertEquals(new TreeSet<>(o.keySet()), new TreeSet<>(n.keySet()),
                    "第 " + i + " 行的列集合应一致");
            for (String k : o.keySet()) {
                if (!String.valueOf(o.get(k)).equals(String.valueOf(n.get(k)))) {
                    diffs.add("第" + i + "行 " + k + ": 旧=" + o.get(k) + " / 新=" + n.get(k));
                }
            }
        }
        assertTrue(diffs.isEmpty(), "列值差异：\n" + String.join("\n", diffs));

        Object id = oldList.get(0).get("id");
        JfUser oldOne = JfUser.dao.findById(id);
        EvUser newOne = EvUser.dao.findById(id);
        assertNotNull(oldOne);
        assertNotNull(newOne);
        // 键序【不是】契约（§3.8 第 3 条：旧实现 toJson 键序亦不稳定）→ 按内容比对
        assertEquals(normalizeJson(oldOne.toJson()), normalizeJson(newOne.toJson()),
                "findById 后 toJson 内容应一致（不计键序；键序非契约）");

        System.out.println("[EovaModel 比对] find/findById 与真实 Model 逐列一致（" + oldList.size() + " 行）");
    }

    @Test
    @DisplayName("取值器 × 值矩阵：返回值或异常类型+消息应与真实 Model 一致")
    void gettersMatchOldModel() {
        Map<String, Object> matrix = new LinkedHashMap<>();
        matrix.put("null", null);
        matrix.put("Integer", 1);
        matrix.put("Long", 7L);
        matrix.put("Double", 1.5d);
        matrix.put("Float", 1.25f);
        matrix.put("BigDecimal", new BigDecimal("1.50"));
        matrix.put("BigInteger", new BigInteger("9"));
        matrix.put("Str数字", "1");
        matrix.put("Str小数", "1.5");
        matrix.put("Str非数字", "abc");
        matrix.put("Str中文", "这个");
        matrix.put("Boolean真", Boolean.TRUE);
        matrix.put("Str true", "true");
        matrix.put("util.Date", new Date(1568964679000L));
        matrix.put("Timestamp", Timestamp.valueOf("2019-09-20 15:31:19"));
        matrix.put("sql.Time", java.sql.Time.valueOf("15:31:19"));
        matrix.put("LocalDateTime", LocalDateTime.of(2019, 9, 20, 15, 31, 19));
        matrix.put("LocalDate", LocalDate.of(2019, 9, 20));
        matrix.put("LocalTime", LocalTime.of(15, 31, 19));
        matrix.put("Map", new LinkedHashMap<>(Map.of("a", 1)));

        List<String> getters = List.of("getStr", "getInt", "getLong", "getBigDecimal",
                "getBigInteger", "getDouble", "getFloat", "getNumber", "getBoolean",
                "getDate", "getTime", "getLocalDateTime");

        List<String> diffs = new ArrayList<>();
        int compared = 0;
        int threw = 0;
        for (String g : getters) {
            for (Map.Entry<String, Object> e : matrix.entrySet()) {
                compared++;
                JfUser oldM = new JfUser();
                EvUser newM = new EvUser();
                // 两侧都用 put（不校验列、不记 flag），保证对称
                oldM.put("k", e.getValue());
                newM.put("k", e.getValue());
                Object oldOut = callQuietly(oldM, g);
                Object newOut = callQuietly(newM, g);
                if (oldOut instanceof Thrown) {
                    threw++;
                }
                if (!render(oldOut).equals(render(newOut))) {
                    diffs.add(g + "(" + e.getKey() + "): 旧=" + render(oldOut)
                            + " / 新=" + render(newOut));
                }
            }
        }
        System.out.println("[EovaModel 比对] 取值器 " + getters.size() + " × 值 " + matrix.size()
                + " = 比对 " + compared + " 条；抛异常 " + threw + " 条；差异 " + diffs.size());
        assertTrue(threw > 0, "比对矩阵退化：没有任何异常分支被覆盖");
        assertTrue(diffs.isEmpty(), "取值器差异：\n" + String.join("\n", diffs));
    }

    @Test
    @DisplayName("set 校验列存在、put 不校验；modifyFlag 语义与真实 Model 一致")
    void setAndPutSemanticsMatchOldModel() {
        // 存在的列：两侧都应成功
        JfUser oldM = new JfUser();
        EvUser newM = new EvUser();
        oldM.set("name", "x");
        newM.set("name", "x");
        assertEquals((Object) oldM.get("name"), newM.get("name"), "set 存在的列应写值成功");

        // 两侧的 modifyFlag 都应含该列
        assertTrue(oldM.modFlag().contains("name"), "旧 set 应记 modifyFlag");
        assertTrue(newM.modFlag().contains("name"), "新 set 应记 modifyFlag");

        // put 不记 modifyFlag
        JfUser oldP = new JfUser();
        EvUser newP = new EvUser();
        oldP.put("name", "y");
        newP.put("name", "y");
        assertEquals(oldP.modFlag().contains("name"), newP.modFlag().contains("name"),
                "put 的 modifyFlag 语义应一致（两侧都不记）");
        assertFalse(newP.modFlag().contains("name"), "新 put 不应记 modifyFlag");

        // 不存在的列：两侧都应抛，且消息逐字一致（类型改名已声明）
        String oldMsg = messageOf(() -> {
            JfUser m = new JfUser();
            m.set("no_such_col", 1);
        });
        String newMsg = messageOf(() -> {
            EvUser m = new EvUser();
            m.set("no_such_col", 1);
        });
        assertNotNull(oldMsg, "旧 Model.set 对不存在的列应抛异常");
        assertNotNull(newMsg, "新 set 对不存在的列应抛异常");
        assertEquals(oldMsg, newMsg, "列不存在时的异常消息应逐字一致");

        System.out.println("[EovaModel 比对] set/put 语义一致；列不存在消息：" + newMsg);
    }

    @Test
    @DisplayName("删除语义词：主键为 null / idValue 为 null 的异常消息应与真实 Model 一致")
    void deleteSemanticsMatchOldModel() {
        // 主键为 null → 两侧都抛，消息一致
        String oldDel = messageOf(() -> new JfUser().delete());
        String newDel = messageOf(() -> new EvUser().delete());
        assertNotNull(oldDel, "旧 Model.delete 在主键为 null 时应抛异常");
        assertEquals(oldDel, newDel, "主键为 null 时的异常消息应逐字一致");

        // deleteById(null) → 两侧都抛
        String oldDelById = messageOf(() -> JfUser.dao.deleteById(null));
        String newDelById = messageOf(() -> EvUser.dao.deleteById(null));
        assertNotNull(oldDelById, "旧 deleteById(null) 应抛异常");
        assertEquals(oldDelById, newDelById, "deleteById(null) 的异常消息应逐字一致");

        System.out.println("[EovaModel 比对] 删除语义词一致；主键 null 消息：" + newDel
                + "；idValue null 消息：" + newDelById);
    }

    @Test
    @DisplayName("绑定与属性视图：数据源名、列集合、toMap/toRecord/dao 语义")
    void bindingAndAttributes() {
        assertEquals("eova", EvUser.dao._getConfigName(), "模型应绑定到注册时的数据源名");
        assertNotNull(EvUser.dao._getTable(), "应能取到表元数据");
        assertEquals("eova_user", EvUser.dao._getTable().getName());
        assertTrue(new EvUser().dao().isDao(), "dao() 应标记为 DAO 实例");
        assertTrue(EvUser.dao.isDao(), "测试替身也应经 dao() 构造，与 EOVA 惯用法一致");
        assertFalse(new EvUser().isDao(), "未调 dao() 的实例不应被标记");

        // 未注册映射的模型：getTable 返回 null（与旧 TableMapping 一致）
        assertNull(EovaTableMapping.me().getTable(String.class), "未注册的类应返回 null");

        // toRecord / toMap 是副本，改动不回写
        EvUser m = new EvUser();
        m.put("a", 1);
        Map<String, Object> map = m.toMap();
        map.put("b", 2);
        assertEquals(1, m.size(), "toMap 应是副本，改动不应回写模型");
        EovaRecord rec = m.toRecord();
        rec.put("c", 3);
        assertEquals(1, m.size(), "toRecord 应是副本，改动不应回写模型");

        System.out.println("[EovaModel 比对] 绑定（数据源 eova / 表 eova_user）与属性视图语义已确认");
    }

    // ———————————————————————— 辅助 ————————————————————————

    private static Object callQuietly(Object target, String method) {
        try {
            return new Ok(target.getClass().getMethod(method, String.class).invoke(target, "k"));
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable t = e.getTargetException();
            return new Thrown(t.getClass().getSimpleName(), t.getMessage());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 执行并返回异常消息；未抛异常返回 null */
    private static String messageOf(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    /** 把 JSON 对象归一化为「键=值」排序串 —— 刻意不计键序（键序非契约） */
    private static String normalizeJson(String json) {
        String body = json.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            return body;
        }
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        StringBuilder cur = new StringBuilder();
        for (char c : body.substring(1, body.length() - 1).toCharArray()) {
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
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            parts.add(cur.toString());
        }
        parts.sort(null);
        return String.join(",", parts);
    }

    private record Ok(Object value) {
    }

    /** 异常：只记类型简名与消息（类型改名已声明，故按消息比对） */
    private record Thrown(String type, String message) {
    }

    private static String render(Object o) {
        if (o instanceof Ok ok) {
            return "Ok:" + ok.value();
        }
        if (o instanceof Thrown t) {
            return "Throw:" + t.message();
        }
        return "?:" + o;
    }
}
