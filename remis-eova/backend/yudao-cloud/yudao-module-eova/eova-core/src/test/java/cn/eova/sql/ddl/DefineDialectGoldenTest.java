/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.sql.ddl;

import cn.eova.sql.ddl.dialect.EovaType;
import cn.eova.testkit.OldImplementationLoader;
import cn.eova.sql.ddl.dialect.MysqlDefineDialect;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DDL 簇的跨实现行为等价测试（acceptanceProfile: golden-behavior-equivalence）。
 *
 * <p>覆盖：{@code MysqlDefineDialect} / {@code DefineDialect} 默认实现 /
 * {@code DefineColumn} / {@code DefineTable} / {@code DefineDialectFactory}。
 *
 * <p>难点：{@code EovaType} 枚举在旧/新两侧是<b>不同的类</b>，不能直接互传。
 * 故旧侧对象一律用反射构造，且枚举比较统一退化为 {@code name()} 字符串。
 *
 * <p>重点断言的是"看起来像 bug 但属既有契约"的行为：{@code TIMESTAMP -> TIME}、
 * {@code getDefault} 命名、pk 为空时的悬空逗号、{@code updateColumn} 返回 null。
 */
class DefineDialectGoldenTest {

    private static final String[] DB_TYPES = {
            "INT", "BIGINT", "TINYINT", "SMALLINT", "MEDIUMINT", "FLOAT", "DOUBLE", "DECIMAL",
            "BIT", "DATE", "DATETIME", "TIMESTAMP", "TIME", "CHAR", "VARCHAR", "TEXT", "BLOB", ""
    };
    private static final int[] SIZES = {1, 2, 4, 5, 10, 11, 12, 50, 255, 2000, 2001, 4000};
    private static final int[] DECIMALS = {0, 1, 2, 6, 10};

    private static ClassLoader isolated;
    private static Class<?> oldDialect;
    private static Class<?> oldColumn;
    private static Class<?> oldTable;
    private static Class<?> oldEovaType;
    private static Class<?> oldFactory;

    @BeforeAll
    static void loadOldImplementations() {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable(),
                "旧编译产物缺失（未执行旧工程 mvn install）：" + OldImplementationLoader.oldClassesDir());
        try {
            isolated = OldImplementationLoader.create(null);
            // 自校验：确认确实取到旧产物，而非本次 port 的新实现
            OldImplementationLoader.assertFromOldArtifacts(Class.forName(
                    "cn.eova.sql.ddl.dialect.DefineDialect", false, isolated));
            oldDialect = Class.forName("cn.eova.sql.ddl.dialect.MysqlDefineDialect", true, isolated);
            oldColumn = Class.forName("cn.eova.sql.ddl.DefineColumn", true, isolated);
            oldTable = Class.forName("cn.eova.sql.ddl.DefineTable", true, isolated);
            oldEovaType = Class.forName("cn.eova.sql.ddl.dialect.EovaType", true, isolated);
            oldFactory = Class.forName("cn.eova.sql.ddl.DefineDialectFactory", true, isolated);
        } catch (Exception e) {
            throw new IllegalStateException("加载旧实现失败", e);
        }
    }

    // ---------------- toDbType：EovaType × size × decimal 全矩阵 ----------------

    @Test
    @DisplayName("toDbType 全矩阵与旧实现一致（含 DATETIME 返回 null 的既有行为）")
    void toDbTypeMatrix() throws Exception {
        Object oldD = oldDialect.getDeclaredConstructor().newInstance();
        MysqlDefineDialect newD = new MysqlDefineDialect();
        Method oldM = oldDialect.getMethod("toDbType", oldEovaType, int.class, int.class);
        List<String> mismatches = new ArrayList<>();
        int n = 0;

        for (EovaType t : EovaType.values()) {
            Object oldT = Enum.valueOf(oldEovaType.asSubclass(Enum.class), t.name());
            for (int size : SIZES) {
                for (int dec : DECIMALS) {
                    n++;
                    Object o = oldM.invoke(oldD, oldT, size, dec);
                    String s = newD.toDbType(t, size, dec);
                    String os = o == null ? null : String.valueOf(o);
                    if (os == null ? s != null : !os.equals(s)) {
                        mismatches.add("  toDbType(" + t + "," + size + "," + dec + ") 旧=" + os + " 新=" + s);
                    }
                }
            }
        }
        assertTrue(mismatches.isEmpty(), "toDbType 差异 " + mismatches.size() + " 处:\n"
                + String.join("\n", mismatches.subList(0, Math.min(10, mismatches.size()))));
        assertEquals(7 * 12 * 5, n, "矩阵规模应为 7×12×5");

        // 既有契约：switch 无 default -> DATETIME 返回 null
        assertNull(newD.toDbType(EovaType.DATETIME, 10, 0), "DATETIME 应返回 null（switch 无 default）");
    }

    // ---------------- toEovaType：dbType × size ----------------

    @Test
    @DisplayName("toEovaType 全矩阵与旧实现一致（含 TIMESTAMP->TIME 的既有映射）")
    void toEovaTypeMatrix() throws Exception {
        Object oldD = oldDialect.getDeclaredConstructor().newInstance();
        MysqlDefineDialect newD = new MysqlDefineDialect();
        Method oldM = oldDialect.getMethod("toEovaType", String.class, int.class);
        List<String> mismatches = new ArrayList<>();

        for (String dbType : DB_TYPES) {
            for (int size : SIZES) {
                Object o = oldM.invoke(oldD, dbType, size);
                EovaType n = newD.toEovaType(dbType, size);
                String os = o == null ? null : ((Enum<?>) o).name();
                String ns = n == null ? null : n.name();
                if (os == null ? ns != null : !os.equals(ns)) {
                    mismatches.add("  toEovaType(" + dbType + "," + size + ") 旧=" + os + " 新=" + ns);
                }
            }
        }
        assertTrue(mismatches.isEmpty(), "toEovaType 差异 " + mismatches.size() + " 处:\n"
                + String.join("\n", mismatches.subList(0, Math.min(10, mismatches.size()))));

        // 既有契约：TIMESTAMP 映射为 TIME（不是 DATETIME）
        assertEquals(EovaType.TIME, newD.toEovaType("TIMESTAMP", 0), "TIMESTAMP 应映射为 TIME");
        assertEquals(EovaType.NUMBER, newD.toEovaType("BIGINT", 20), "含 INT 应归 NUMBER");
        assertEquals(EovaType.VARCHAR, newD.toEovaType("WHATEVER", 0), "未识别应回退 VARCHAR");
    }

    // ---------------- create / 语句生成 ----------------

    @Test
    @DisplayName("create 语句与旧实现逐字符一致（含 pk 为空时的悬空逗号）")
    void createStatementMatches() throws Exception {
        Object oldD = oldDialect.getDeclaredConstructor().newInstance();
        MysqlDefineDialect newD = new MysqlDefineDialect();
        Method oldCreate = oldDialect.getMethod("create", oldTable);
        List<String> mismatches = new ArrayList<>();

        // 组合：主键有/无 × 字段属性组合
        for (String pk : new String[]{"id", null, ""}) {
            for (boolean notNull : new boolean[]{true, false}) {
                for (boolean auto : new boolean[]{true, false}) {
                    for (String def : new String[]{"0", "x", null}) {
                        List<DefineColumn> newCols = List.of(
                                new DefineColumn("id", EovaType.NUMBER, 11, 0, "主键"),
                                buildNew("name", EovaType.VARCHAR, notNull, auto, def),
                                new DefineColumn("amount", EovaType.NUMBER, 12, 2, "金额"));
                        Object oldTb = buildOldTable("测试表", "t_demo", pk, newCols);

                        String expected = (String) oldCreate.invoke(oldD, oldTb);
                        String actual = newD.create(new DefineTable("测试表", "t_demo", pk, newCols));
                        if (!expected.equals(actual)) {
                            mismatches.add("  pk=" + pk + " notNull=" + notNull + " auto=" + auto
                                    + " def=" + def + "\n    旧=" + esc(expected) + "\n    新=" + esc(actual));
                        }
                    }
                }
            }
        }
        assertTrue(mismatches.isEmpty(), "create 差异 " + mismatches.size() + " 处:\n"
                + String.join("\n", mismatches.subList(0, Math.min(4, mismatches.size()))));

        // 显式记录悬空逗号契约：pk 为空时最后一个字段后仍有逗号，再直接接右括号
        String noPk = newD.create(new DefineTable("c", "t", null,
                List.of(new DefineColumn("a", EovaType.NUMBER, 11, 0, "x"))));
        assertEquals("CREATE TABLE `t` (\n"
                        + " `a` INT(11) COMMENT 'x',\n"
                        + ") COMMENT='c';\n",
                noPk,
                "pk 为空时的输出应保留悬空逗号（既有输出契约，不修正）：" + esc(noPk));
        assertTrue(noPk.contains(",\n)"), "应存在悬空逗号后紧跟右括号的形态");
    }

    @Test
    @DisplayName("rename/addColumn/updateColumn/drop/truncate/addPrimary/escape 与旧实现一致")
    void otherStatementsMatch() throws Exception {
        Object oldD = oldDialect.getDeclaredConstructor().newInstance();
        MysqlDefineDialect newD = new MysqlDefineDialect();

        // escape
        Method oldEscape = oldDialect.getMethod("escape", String.class);
        for (String n : new String[]{"t_demo", "select", "", "a b"}) {
            assertEquals(oldEscape.invoke(oldD, n), newD.escape(n), "escape(" + n + ")");
        }

        // rename
        Method oldRename = oldDialect.getMethod("rename", String.class, String.class);
        assertEquals(oldRename.invoke(oldD, "t1", "t2"), newD.rename("t1", "t2"));

        // addColumn / updateColumn(DefineColumn)
        DefineColumn col = new DefineColumn("amount", EovaType.NUMBER, 12, 2, "金额");
        Object oldCol = buildOldColumn("amount", EovaType.NUMBER, 12, 2, "金额");
        assertEquals(oldDialect.getMethod("addColumn", String.class, oldColumn).invoke(oldD, "t1", oldCol),
                newD.addColumn("t1", col));
        assertEquals(oldDialect.getMethod("updateColumn", String.class, oldColumn).invoke(oldD, "t1", oldCol),
                newD.updateColumn("t1", col));

        // updateColumn(old,new) 返回 null —— 既有行为
        assertEquals(oldDialect.getMethod("updateColumn", String.class, String.class, String.class)
                        .invoke(oldD, "t1", "a", "b"),
                newD.updateColumn("t1", "a", "b"));
        assertNull(newD.updateColumn("t1", "a", "b"), "updateColumn(old,new) 应返回 null");

        // 默认实现
        assertEquals(oldDialect.getMethod("truncate", String.class).invoke(oldD, "t1"), newD.truncate("t1"));
        assertEquals(oldDialect.getMethod("dropTable", String.class).invoke(oldD, "t1"), newD.dropTable("t1"));
        assertEquals(oldDialect.getMethod("dropColumn", String.class, String.class).invoke(oldD, "t1", "c1"),
                newD.dropColumn("t1", "c1"));
        assertEquals(oldDialect.getMethod("addPrimary", String.class, String.class).invoke(oldD, "t1", "id"),
                newD.addPrimary("t1", "id"));
    }

    // ---------------- DefineColumn / DefineTable / Factory ----------------

    @Test
    @DisplayName("DefineColumn 默认值与链式方法行为与旧实现一致")
    void defineColumnMatches() throws Exception {
        // 字符串构造器
        Object oldC = buildOldColumnFromString("c1", "varchar", 50, 0, "备注");
        DefineColumn newC = new DefineColumn("c1", "varchar", 50, 0, "备注");
        assertEquals(field(oldC, "en"), newC.getEn());
        assertEquals(String.valueOf(field(oldC, "cn")), newC.getCn());
        // 枚举跨类加载器不可直接比较，退化为 name()
        assertEquals(((Enum<?>) field(oldC, "type")).name(), newC.getType().name(), "type 解析结果应一致");
        assertEquals(field(oldC, "size"), newC.getSize());
        assertEquals(field(oldC, "decimal"), newC.getDecimal());
        assertEquals(field(oldC, "defaultValue"), newC.getDefault());
        assertEquals(field(oldC, "isNull"), newC.isNull());
        assertEquals(field(oldC, "isAuto"), newC.isAuto());

        // 链式
        assertTrue(newC.auto() == newC && newC.isAuto(), "auto() 应链式返回自身并置位");
        assertTrue(newC.notNull() == newC && !newC.isNull(), "notNull() 应链式返回自身并清位");
        assertTrue(newC.setDefault("d") == newC && "d".equals(newC.getDefault()));

        // 假值默认：新对象 should default isNull=true, isAuto=false
        DefineColumn fresh = new DefineColumn("x", EovaType.NUMBER, 1, 0, "x");
        assertTrue(fresh.isNull(), "默认 isNull 应为 true");
        assertTrue(!fresh.isAuto(), "默认 isAuto 应为 false");
        assertEquals(0, fresh.getDecimal());
        assertNull(fresh.getDefault());
    }

    @Test
    @DisplayName("DefineColumn 字符串构造器对 null 类型抛 NPE —— 既有异常语义")
    void defineColumnNullTypeThrowsNpe() {
        try {
            new DefineColumn("x", (String) null, 1, 0, "x");
            assertTrue(false, "预期 NPE 未抛出");
        } catch (NullPointerException expected) {
            // 既有异常语义
        }
    }

    @Test
    @DisplayName("DefineTable 访问器与构造行为与旧实现一致（getFields 映射 cols）")
    void defineTableMatches() throws Exception {
        List<DefineColumn> cols = List.of(new DefineColumn("a", EovaType.NUMBER, 11, 0, "A"));
        DefineTable t = new DefineTable("中文", "t_en", "id", cols);
        assertEquals("中文", t.getCn());
        assertEquals("t_en", t.getEn());
        assertEquals("id", t.getPk());
        assertEquals(cols, t.getFields());

        // 常量
        assertEquals("number", DefineTable.NUMBER);
        assertEquals("string", DefineTable.STRING);
        assertEquals("date", DefineTable.DATE);
        assertEquals("datetime", DefineTable.DATETIME);
        assertEquals("timestamp", DefineTable.TIMESTAMP);
    }

    @Test
    @DisplayName("DefineDialectFactory 未注册时回退 MySQL 方言，注册后按 ds 返回")
    void dialectFactoryMatches() throws Exception {
        MysqlDefineDialect fallback = (MysqlDefineDialect) DefineDialectFactory.getDialect("__absent__");
        assertEquals("`x`", fallback.escape("x"), "未注册 ds 应回退 MySQL 方言");

        MysqlDefineDialect registered = new MysqlDefineDialect();
        DefineDialectFactory.addDialect("__test_ds__", registered);
        assertTrue(DefineDialectFactory.getDialect("__test_ds__") == registered, "已注册 ds 应返回同一实例");

        // 既有行为：未注册时每次返回【不同】实例（不缓存）
        assertTrue(DefineDialectFactory.getDialect("__absent__")
                        != DefineDialectFactory.getDialect("__absent__"),
                "未注册时应每次新建实例（既有行为，不缓存）");

        // 构造器按 ds 解析
        assertTrue(new DefineDialectFactory("__test_ds__") != null);
    }

    // ---------------- 反射辅助 ----------------

    private static DefineColumn buildNew(String en, EovaType type, boolean notNull, boolean auto, String def) {
        DefineColumn c = new DefineColumn(en, type, 10, 0, "c");
        if (notNull) {
            c.notNull();
        }
        if (auto) {
            c.auto();
        }
        if (def != null) {
            c.setDefault(def);
        }
        return c;
    }

    /** 用旧类的枚举构造旧 DefineColumn */
    private static Object buildOldColumn(String en, EovaType type, int size, int dec, String cn) throws Exception {
        Object oldT = Enum.valueOf(oldEovaType.asSubclass(Enum.class), type.name());
        Constructor<?> ctor = oldColumn.getConstructor(String.class, oldEovaType, int.class, int.class, String.class);
        return ctor.newInstance(en, oldT, size, dec, cn);
    }

    /** 用旧类的字符串构造器构造旧 DefineColumn */
    private static Object buildOldColumnFromString(String en, String type, int size, int dec, String cn)
            throws Exception {
        Constructor<?> ctor = oldColumn.getConstructor(String.class, String.class, int.class, int.class, String.class);
        return ctor.newInstance(en, type, size, dec, cn);
    }

    /** 把新 DefineColumn 列表镜像为旧 DefineColumn 列表 */
    private static Object buildOldTable(String cn, String en, String pk, List<DefineColumn> cols) throws Exception {
        List<Object> oldCols = new ArrayList<>();
        for (DefineColumn c : cols) {
            Object oc = buildOldColumn(c.getEn(), c.getType(), c.getSize(), c.getDecimal(), c.getCn());
            if (!c.isNull()) {
                oldColumn.getMethod("notNull").invoke(oc);
            }
            if (c.isAuto()) {
                oldColumn.getMethod("auto").invoke(oc);
            }
            if (c.getDefault() != null) {
                oldColumn.getMethod("setDefault", String.class).invoke(oc, c.getDefault());
            }
            oldCols.add(oc);
        }
        Constructor<?> ctor = oldTable.getConstructor(String.class, String.class, String.class, List.class);
        return ctor.newInstance(cn, en, pk, oldCols);
    }

    /** 反射读取旧对象字段值 */
    private static Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /** 把控制字符转义，便于失败信息可读 */
    private static String esc(String s) {
        return s == null ? "null" : s.replace("\n", "\\n");
    }
}
