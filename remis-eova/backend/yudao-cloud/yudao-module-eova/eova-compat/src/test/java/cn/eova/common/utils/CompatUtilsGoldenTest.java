/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils;

import cn.eova.common.utils.data.CloneUtil;
import cn.eova.common.utils.data.ListUtil;
import cn.eova.common.utils.util.RegexUtil;
import cn.eova.common.utils.util.SysUtil;
import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 旧工具族的跨实现行为等价测试（acceptanceProfile: golden-behavior-equivalence）。
 *
 * <p>覆盖 {@code RegexUtil} / {@code ExceptionUtil} / {@code SysUtil} /
 * {@code ListUtil} / {@code CloneUtil} 以及 3 个空类（{@code AlgorithmUtil}/
 * {@code ArrayUtil}/{@code MapUtil}）。
 *
 * <p>重点锁定"参数顺序与直觉相反""失败返回 null 而非抛异常"这类既有契约 ——
 * 它们最容易被 port 时"顺手修正"。
 */
class CompatUtilsGoldenTest {

    private static ClassLoader isolated;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable(),
                "旧编译产物缺失（未执行旧工程 mvn install）：" + OldImplementationLoader.oldClassesDir());
        try {
            isolated = OldImplementationLoader.create(null);

        // 自校验：确认确实取到旧产物，而非本次 port 的新实现
        OldImplementationLoader.assertFromOldArtifacts(Class.forName("cn.eova.common.utils.util.RegexUtil", false, isolated));
        } catch (Exception e) {
            throw new IllegalStateException("加载旧实现失败", e);
        }
    }

    // ---------------- RegexUtil ----------------

    @Test
    @DisplayName("RegexUtil：isExist / isTrue / getChinese 与旧实现一致")
    void regexBasicMatches() throws Exception {
        String[][] probes = {
                {"\\d+", "abc123"}, {"\\d+", "abc"}, {"^a.*z$", "abz"}, {"^a.*z$", "ab"},
                {"[A-Z]+", "aBcD"}, {"x", ""},
        };
        for (String[] p : probes) {
            assertEquals(oldStatic("cn.eova.common.utils.util.RegexUtil", "isExist", p[0], p[1]),
                    RegexUtil.isExist(p[0], p[1]), "isExist(" + p[0] + "," + p[1] + ")");
            assertEquals(oldStatic("cn.eova.common.utils.util.RegexUtil", "isTrue", p[0], p[1]),
                    RegexUtil.isTrue(p[0], p[1]), "isTrue(" + p[0] + "," + p[1] + ")");
        }
        for (String s : new String[]{"你好abc世界", "no-chinese", "中文，标点！", ""}) {
            assertEquals(new TreeSet<>((Set<String>) oldStatic("cn.eova.common.utils.util.RegexUtil", "getChinese", s)),
                    new TreeSet<>((HashSet<String>) RegexUtil.getChinese(s)), "getChinese(" + s + ")");
        }
    }

    @Test
    @DisplayName("RegexUtil.replaceAll 参数顺序为 (正则,替换内容,目标串) —— 契约锁定")
    void regexReplaceAllParameterOrder() throws Exception {
        // 该重载默认忽略大小写：4 个字符全部命中
        assertEquals(oldStatic("cn.eova.common.utils.util.RegexUtil", "replaceAll", "a", "X", "aAaA"),
                RegexUtil.replaceAll("a", "X", "aAaA"));
        assertEquals("XXXX", RegexUtil.replaceAll("a", "X", "aAaA"),
                "第一参是正则、第二参是替换内容；默认大小写不敏感，四个字符全部命中");

        // flags 重载：-1 表示不带任何模式（此时大小写敏感）
        assertEquals(oldStatic("cn.eova.common.utils.util.RegexUtil", "replaceAll",
                        "a", "X", "aAaA", java.util.regex.Pattern.CASE_INSENSITIVE),
                RegexUtil.replaceAll("a", "X", "aAaA", java.util.regex.Pattern.CASE_INSENSITIVE));
        assertEquals(oldStatic("cn.eova.common.utils.util.RegexUtil", "replaceAll", "a", "X", "aAaA", -1),
                RegexUtil.replaceAll("a", "X", "aAaA", -1));
        assertEquals("XAXA", RegexUtil.replaceAll("a", "X", "aAaA", -1),
                "flags=-1 应不带模式编译，此时大小写敏感");
    }

    @Test
    @DisplayName("RegexUtil.getMatcherValue：未匹配返回 null，匹配返回不含 group(0) 的数组")
    void regexMatcherValueMatches() throws Exception {
        // 单组：长度为 1 的数组（groupCount()=1，不含整体匹配）
        String[] expected = (String[]) oldStatic("cn.eova.common.utils.util.RegexUtil",
                "getMatcherValue", "'([^']*)'", "a='x' b='y'");
        String[] actual = RegexUtil.getMatcherValue("'([^']*)'", "a='x' b='y'");
        assertNotNull(expected);
        assertEquals(expected.length, actual.length, "数组长度应一致（不含 group(0)）");
        assertEquals(1, actual.length, "单组正则应返回长度 1 的数组");
        assertEquals("[x]", Arrays.toString(actual));

        // 未匹配 -> null（两侧一致）
        assertNull((Object) oldStatic("cn.eova.common.utils.util.RegexUtil",
                "getMatcherValue", "'([^']*)'", "no-quotes"));
        assertNull(RegexUtil.getMatcherValue("'([^']*)'", "no-quotes"), "未匹配应返回 null");

        // 双组
        String[] two = RegexUtil.getMatcherValue("(\\w+)=(\\w+)", "k=v");
        assertEquals("[k, v]", Arrays.toString(two));
    }

    // ---------------- SysUtil / ExceptionUtil ----------------

    @Test
    @DisplayName("SysUtil.isWindows 与旧实现一致（当前平台）")
    void sysUtilMatches() throws Exception {
        Object oldR = oldStatic("cn.eova.common.utils.util.SysUtil", "isWindows");
        assertEquals(oldR, SysUtil.isWindows());
        // 当前为 macOS，应为 false；此处只断言与旧实现一致，不断言具体平台
    }

    @Test
    @DisplayName("ExceptionUtil.getStackTrace 与旧实现一致（按首行比对）")
    void exceptionUtilMatches() throws Exception {
        Throwable t1 = new IllegalStateException("boom");
        Throwable t2 = new IllegalStateException("boom");
        String oldS = (String) oldStatic("cn.eova.common.utils.util.ExceptionUtil", "getStackTrace", t1);
        String newS = (String) ExceptionUtil_getStackTrace(t2);
        String oldFirst = oldS.split("\n")[0];
        String newFirst = newS.split("\n")[0];
        assertEquals(oldFirst, newFirst, "堆栈首行应一致");
        assertTrue(newS.contains("boom"));
    }

    /** 通过反射调用新实现，保持与旧侧调用形态一致（便于替换实现） */
    private static Object ExceptionUtil_getStackTrace(Throwable t) throws Exception {
        return Class.forName("cn.eova.common.utils.util.ExceptionUtil")
                .getMethod("getStackTrace", Throwable.class).invoke(null, t);
    }

    // ---------------- ListUtil ----------------

    @Test
    @DisplayName("ListUtil.toNumber：空列表返回 null、跳过 null 元素、未知类型返回空列表")
    void listUtilMatches() throws Exception {
        Object[][] cases = {
                {new ArrayList<>(), Integer.class},
                {Arrays.asList("1", "2", "3"), Integer.class},
                {Arrays.asList("1", null, "3"), Integer.class},
                {Arrays.asList("1", "2"), Long.class},
                {Arrays.asList("1.5", "2.5"), Float.class},
                {Arrays.asList("1.5", "2.5"), Double.class},
                {Arrays.asList("1", "2"), Short.class},   // 未知支持类型 -> 空列表
        };
        for (Object[] c : cases) {
            @SuppressWarnings("unchecked")
            List<Object> in = (List<Object>) c[0];
            @SuppressWarnings("unchecked")
            Class<? extends Number> cs = (Class<? extends Number>) c[1];
            Object expected = oldStatic("cn.eova.common.utils.data.ListUtil", "toNumber", in, cs);
            List<?> actual = ListUtil.toNumber(in, cs);
            assertEquals(String.valueOf(expected), String.valueOf(actual),
                    "toNumber(" + in + "," + cs.getSimpleName() + ")");
        }
        // 关键契约
        assertNull(ListUtil.toNumber(new ArrayList<>(), Integer.class), "空列表应返回 null");
        assertEquals("[1, 3]", ListUtil.toNumber(Arrays.asList("1", null, "3"), Integer.class).toString(),
                "null 元素应被跳过");
        assertEquals("[]", ListUtil.toNumber(Arrays.asList("1"), Short.class).toString(),
                "不支持的数值类型应返回空列表（不抛异常）");
    }

    // ---------------- CloneUtil ----------------

    /** 可序列化的测试载荷 */
    public static class Payload implements Serializable {
        private static final long serialVersionUID = 1L;
        final String name;
        final int num;

        Payload(String name, int num) {
            this.name = name;
            this.num = num;
        }

        @Override
        public String toString() {
            return name + "/" + num;
        }
    }

    @Test
    @DisplayName("CloneUtil.clone：深度克隆成功；不可序列化对象返回 null（既有契约）")
    void cloneUtilMatches() throws Exception {
        Payload p = new Payload("a", 1);
        Payload cloned = CloneUtil.clone(p);
        assertNotNull(cloned, "可序列化对象应克隆成功");
        assertTrue(cloned != p, "应是不同实例（深度克隆）");
        assertEquals(p.toString(), cloned.toString(), "内容应一致");

        // 不可序列化 -> 吞异常并返回 null（既有契约，不抛异常）
        Object notSerializable = new Object();
        assertNull(CloneUtil.clone(notSerializable), "不可序列化对象应返回 null 而非抛异常");
    }

    // ---------------- 空类契约 ----------------

    @Test
    @DisplayName("AlgorithmUtil / ArrayUtil / MapUtil 保持空类形态（无自定义公开方法）")
    void emptyClassesStayEmpty() throws Exception {
        for (String fqcn : new String[]{
                "cn.eova.common.utils.util.AlgorithmUtil",
                "cn.eova.common.utils.data.ArrayUtil",
                "cn.eova.common.utils.data.MapUtil"}) {
            Class<?> c = Class.forName(fqcn);
            Method[] declared = c.getDeclaredMethods();
            assertEquals(0, declared.length, fqcn + " 应为空类，不应新增方法（原类体为空）");
        }
    }

    // ---------------- 反射辅助 ----------------

    /** 反射调用旧实现的静态方法 */
    private static Object oldStatic(String fqcn, String method, Object... args) throws Exception {
        Class<?> c = Class.forName(fqcn, true, isolated);
        for (Method m : c.getDeclaredMethods()) {
            if (!m.getName().equals(method) || m.getParameterCount() != args.length) {
                continue;
            }
            try {
                return m.invoke(null, args);
            } catch (InvocationTargetException e) {
                return e.getCause();
            } catch (IllegalArgumentException ignored) {
                // 参数类型不匹配，试下一个重载
            }
        }
        throw new NoSuchMethodException(fqcn + "#" + method + " 参数数=" + args.length);
    }
}
