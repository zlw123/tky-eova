/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.sql.dql;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TableSource 的行为等价测试（acceptanceProfile: golden-behavior-equivalence）。
 *
 * <p>方法：把【旧源码编译产物】（{@code meta-eova/eova/core/target/classes}）通过隔离
 * 类加载器加载进来，与本次 port 的新实现跑<b>同一输入矩阵</b>，逐项比对返回值与异常类型。
 *
 * <p>为什么不用断言自造期望值：本单元存在两处必须原样保留的既有行为 ——
 * {@code getAlias()} 的 null 回退、{@code getField()} 的 NPE 语义。
 * 用旧实现在环比对，才能证明 port 是"等价"而不是"看起来对"。
 *
 * <p>旧产物缺失时本测试 <b>skip</b>（未执行即如实记录，符合 DES-002-R4 §7.1）。
 */
class TableSourceGoldenTest {

    /** 输入矩阵取值：含 null，用于覆盖回退与 NPE 分支 */
    private static final String[] VALUES = {"t1", "t2", null};

    private static Class<?> oldClass;
    private static ClassLoader isolated;

    @BeforeAll
    static void loadOldImplementation() {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable(),
                "旧编译产物缺失（未执行旧工程 mvn install）：" + OldImplementationLoader.oldClassesDir());
        try {
            // 子优先加载旧产物，确保取到旧版本而非本次 port 的新实现
            isolated = OldImplementationLoader.create(null);

        // 自校验：确认确实取到旧产物，而非本次 port 的新实现
        OldImplementationLoader.assertFromOldArtifacts(Class.forName("cn.eova.sql.dql.TableSource", false, isolated));
            oldClass = Class.forName("cn.eova.sql.dql.TableSource", true, isolated);
        } catch (Exception e) {
            throw new IllegalStateException("加载旧实现失败", e);
        }
    }

    @Test
    @DisplayName("输入矩阵下 getAlias/toString/getField 与旧实现逐项等价（含异常语义）")
    void behaviorMatchesOldImplementation() throws Exception {
        List<String> mismatches = new ArrayList<>();
        int checked = 0;

        for (String table : VALUES) {
            for (String alias : VALUES) {
                for (String leftField : VALUES) {
                    for (String leftAlias : VALUES) {
                        for (String rigthField : VALUES) {
                            for (String rigthAlias : VALUES) {
                                checked++;
                                String desc = String.format(
                                        "table=%s alias=%s leftField=%s leftAlias=%s rigthField=%s rigthAlias=%s",
                                        table, alias, leftField, leftAlias, rigthField, rigthAlias);

                                Object oldObj = oldClass.getDeclaredConstructor().newInstance();
                                invoke(oldObj, "setTable", table);
                                invoke(oldObj, "setAlias", alias);
                                invoke(oldObj, "setLeftField", leftField);
                                invoke(oldObj, "setLeftAlias", leftAlias);
                                invoke(oldObj, "setRigthField", rigthField);
                                invoke(oldObj, "setRigthAlias", rigthAlias);

                                TableSource newObj = new TableSource();
                                newObj.setTable(table);
                                newObj.setAlias(alias);
                                newObj.setLeftField(leftField);
                                newObj.setLeftAlias(leftAlias);
                                newObj.setRigthField(rigthField);
                                newObj.setRigthAlias(rigthAlias);

                                compare(mismatches, desc, "getAlias",
                                        call(oldObj, "getAlias"), callNew(newObj, "getAlias"));
                                compare(mismatches, desc, "toString",
                                        call(oldObj, "toString"), callNew(newObj, "toString"));
                                compare(mismatches, desc, "getField",
                                        call(oldObj, "getField"), callNew(newObj, "getField"));

                                // setter/getter 往返也一并核对
                                assertEquals(invoke(oldObj, "getRigthField"), newObj.getRigthField(), desc);
                                assertEquals(invoke(oldObj, "getRigthAlias"), newObj.getRigthAlias(), desc);
                            }
                        }
                    }
                }
            }
        }

        assertTrue(mismatches.isEmpty(),
                "与旧实现存在 " + mismatches.size() + " 处行为差异：\n" + String.join("\n", mismatches));
        assertEquals(729, checked, "输入矩阵规模应为 3^6");
    }

    @Test
    @DisplayName("getField 在 leftAlias 为 null 时抛 NPE —— 既有异常语义须保留")
    void getFieldNullLeftAliasThrowsNpe() {
        TableSource ts = new TableSource();
        ts.setAlias("a");
        // leftAlias 未设置 -> 旧实现会 NPE，本实现必须一致
        try {
            ts.getField();
            assertTrue(false, "预期 NPE 未抛出，行为与旧实现不一致");
        } catch (NullPointerException expected) {
            // 既有异常语义，符合预期
        }
    }

    @Test
    @DisplayName("getAlias 未设别名时回退为表名 —— stub 曾丢失该行为")
    void getAliasFallsBackToTable() {
        TableSource ts = new TableSource();
        ts.setTable("t_demo");
        assertEquals("t_demo", ts.getAlias(), "别名未设置时应回退为表名");
        ts.setAlias("d");
        assertEquals("d", ts.getAlias(), "别名设置后应返回别名");
    }

    /** 逐项比对：返回值或异常类型任一不同即记录差异 */
    private static void compare(List<String> mismatches, String desc, String method,
                                Object oldVal, Object newVal) {
        String o = render(oldVal);
        String n = render(newVal);
        if (!o.equals(n)) {
            mismatches.add("  " + desc + " -> " + method + "(): 旧=" + o + " 新=" + n);
        }
    }

    /** 把返回值或异常渲染为可比较字符串 */
    private static String render(Object v) {
        if (v instanceof Throwable t) {
            return "throw " + t.getClass().getName();
        }
        if (v == null) {
            return "null";
        }
        return "value[" + v + "]";
    }

    /** 反射调用旧实现的方法，异常转为返回值以便比对异常类型 */
    private static Object call(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return e.getCause();
        } catch (Exception e) {
            return e;
        }
    }

    /**
     * 调用新实现的方法，异常同样转为返回值参与比对。
     * 必须与 {@link #call} 对称 —— getField() 在 leftAlias 为 null 时两侧都会抛 NPE，
     * 若这里不接异常，测试会因语义【一致】的 NPE 而误报失败。
     */
    private static Object callNew(TableSource target, String method) {
        try {
            Method m = TableSource.class.getMethod(method);
            return m.invoke(target);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return e.getCause();
        } catch (Exception e) {
            return e;
        }
    }

    /** 反射调用旧实现的 setter */
    private static Object invoke(Object target, String method, String arg) throws Exception {
        Method m = target.getClass().getMethod(method, String.class);
        return m.invoke(target, arg);
    }

    /** 反射调用旧实现的无参 getter */
    private static Object invoke(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }
}
