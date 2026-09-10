/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.util;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * util 工具族的跨实现行为等价测试（acceptanceProfile: golden-behavior-equivalence）。
 *
 * <p>覆盖 AntPathMatcher（EOVA URI 权限白名单依赖）/ ObjectUtils / StringUtils / RandomUtil。
 *
 * <p>RandomUtil 结果随机，改用形状断言（长度/字符集/范围），不做值比对。
 * <p>identityToString/getIdentityHexString 依赖对象标识，两侧不同实例不可比，故排除。
 */
class UtilUtilsGoldenTest {

    private static ClassLoader isolated;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable(),
                "旧编译产物缺失：" + OldImplementationLoader.oldClassesDir());
        try {
            isolated = OldImplementationLoader.create(null);
            // 自校验：确认确实取到旧产物，而非本次 port 的新实现
            OldImplementationLoader.assertFromOldArtifacts(
                    Class.forName("cn.eova.common.utils.util.StringUtils", false, isolated));
        } catch (Exception e) {
            throw new IllegalStateException("加载旧实现失败", e);
        }
    }

    // ---------------- AntPathMatcher（实例方法） ----------------

    @Test
    @DisplayName("AntPathMatcher.match/matchStart/isPattern 在模式×路径矩阵下与旧实现一致")
    void antPathMatcherMatches() throws Exception {
        Object oldM = newOldMatcher(true);
        AntPathMatcher newM = new AntPathMatcher();
        Method oldMatch = oldM.getClass().getMethod("match", String.class, String.class);
        Method oldMatchStart = oldM.getClass().getMethod("matchStart", String.class, String.class);
        Method oldIsPattern = oldM.getClass().getMethod("isPattern", String.class);

        String[] patterns = {"/eova/api/**", "/eova/api/*", "/eova/**/*.html", "/user/{id}",
                "/user/{id}/detail", "/**", "/*", "/exact/path", "/a/**/c", "/{a}/{b}"};
        String[] paths = {"/eova/api/x", "/eova/api/x/y", "/eova/api/", "/eova/", "/a/b/c.html",
                "/user/1", "/user/1/detail", "/user/1/other", "/exact/path", "/exact/path/x",
                "/a/b/c", "/x/y", "/"};

        StringBuilder diffs = new StringBuilder();
        for (String p : patterns) {
            assertEquals(oldIsPattern.invoke(oldM, p), newM.isPattern(p), "isPattern(" + p + ")");
            for (String path : paths) {
                String o = String.valueOf(oldMatch.invoke(oldM, p, path));
                String n = String.valueOf(newM.match(p, path));
                if (!o.equals(n)) {
                    diffs.append("  match(").append(p).append(",").append(path)
                            .append(") 旧=").append(o).append(" 新=").append(n).append('\n');
                }
                String os = String.valueOf(oldMatchStart.invoke(oldM, p, path));
                String ns = String.valueOf(newM.matchStart(p, path));
                if (!os.equals(ns)) {
                    diffs.append("  matchStart(").append(p).append(",").append(path)
                            .append(") 旧=").append(os).append(" 新=").append(ns).append('\n');
                }
            }
        }
        assertTrue(diffs.length() == 0, "差异：\n" + head(diffs));
    }

    @Test
    @DisplayName("AntPathMatcher.extractUriTemplateVariables / combine 与旧实现一致")
    void antPathMatcherExtractAndCombine() throws Exception {
        Object oldM = newOldMatcher(true);
        AntPathMatcher newM = new AntPathMatcher();
        Method oldExtract = oldM.getClass().getMethod("extractUriTemplateVariables", String.class, String.class);
        Method oldCombine = oldM.getClass().getMethod("combine", String.class, String.class);

        String[][] pairs = {
                {"/user/{id}", "/user/1"},
                {"/{a}/{b}", "/x/y"},
                {"/user/{id}/detail/{tab}", "/user/42/detail/info"},
                {"/fixed/{id}", "/fixed/9"},
        };
        for (String[] pr : pairs) {
            // 对称调用：非全匹配模式下 oldExtract 会抛 IllegalStateException，
            // 新侧直接调用会以 ERROR 中断测试 —— 必须同样捕获后比对
            assertEquals(render(invokeOn(oldM, oldExtract, pr[0], pr[1])),
                    render(invokeOn(newM, "extractUriTemplateVariables",
                            new Class<?>[]{String.class, String.class}, pr[0], pr[1])),
                    "extractUriTemplateVariables(" + pr[0] + "," + pr[1] + ")");
        }
        String[][] combos = {{"/a", "/b"}, {"/a/**", "/b"}, {"/a", "/b/{id}"}, {"/a/*", "b"}};
        for (String[] c : combos) {
            assertEquals(render(invokeOn(oldM, oldCombine, c[0], c[1])),
                    render(invokeOn(newM, "combine", new Class<?>[]{String.class, String.class}, c[0], c[1])),
                    "combine(" + c[0] + "," + c[1] + ")");
        }
    }

    @Test
    @DisplayName("AntPathMatcher 两种构造器行为与旧实现一致")
    void antPathMatcherConstructors() throws Exception {
        for (boolean trim : new boolean[]{true, false}) {
            Object oldM = newOldMatcher(trim);
            AntPathMatcher newM = newMatcher(trim);
            Method m = oldM.getClass().getMethod("match", String.class, String.class);
            Method ip = oldM.getClass().getMethod("isPattern", String.class);
            for (String p : new String[]{"/a/**", "/a/*", "/x/{id}", "/plain"}) {
                assertEquals(String.valueOf(ip.invoke(oldM, p)), String.valueOf(newM.isPattern(p)),
                        "isPattern(trim=" + trim + "," + p + ")");
                for (String path : new String[]{"/a/b", "/a", "/x/1", "/plain"}) {
                    assertEquals(String.valueOf(m.invoke(oldM, p, path)), String.valueOf(newM.match(p, path)),
                            "match(trim=" + trim + "," + p + "," + path + ")");
                }
            }
        }
    }

    // ---------------- ObjectUtils ----------------

    @Test
    @DisplayName("ObjectUtils 空值安全方法与旧实现一致（null / 数组全覆盖）")
    void objectUtilsMatches() throws Exception {
        StringBuilder diffs = new StringBuilder();
        Object[] objs = {null, "", "x", 1, 1L, 1.5, true, new int[]{1, 2}, new String[]{"a", "b"},
                new Object(), new ArrayList<>(List.of("a"))};
        for (Object o : objs) {
            for (String m : new String[]{"nullSafeToString", "nullSafeClassName", "getDisplayString",
                    "nullSafeHashCode"}) {
                cmp(diffs, m + "(" + o + ")", "ObjectUtils", m, new Class<?>[]{Object.class}, o);
            }
            cmp(diffs, "isArray(" + o + ")", "ObjectUtils", "isArray", new Class<?>[]{Object.class}, o);
            cmp(diffs, "toObjectArray(" + o + ")", "ObjectUtils", "toObjectArray",
                    new Class<?>[]{Object.class}, o);
        }
        for (Object a : objs) {
            for (Object b : objs) {
                cmp(diffs, "nullSafeEquals", "ObjectUtils", "nullSafeEquals",
                        new Class<?>[]{Object.class, Object.class}, a, b);
            }
        }
        cmp(diffs, "isEmpty(空数组)", "ObjectUtils", "isEmpty", new Class<?>[]{Object[].class},
                (Object) new Object[0]);
        cmp(diffs, "isEmpty(非空数组)", "ObjectUtils", "isEmpty", new Class<?>[]{Object[].class},
                (Object) new Object[]{"a"});
        cmp(diffs, "containsElement", "ObjectUtils", "containsElement",
                new Class<?>[]{Object[].class, Object.class}, new Object[]{"a", "b"}, "b");
        cmp(diffs, "addObjectToArray", "ObjectUtils", "addObjectToArray",
                new Class<?>[]{Object[].class, Object.class}, new String[]{"a"}, "b");
        cmp(diffs, "isCheckedException", "ObjectUtils", "isCheckedException",
                new Class<?>[]{Throwable.class}, new java.io.IOException("x"));
        cmp(diffs, "isCheckedException(Runtime)", "ObjectUtils", "isCheckedException",
                new Class<?>[]{Throwable.class}, new IllegalStateException("x"));

        // 基本类型数组重载
        cmp(diffs, "nullSafeHashCode(int[])", "ObjectUtils", "nullSafeHashCode",
                new Class<?>[]{int[].class}, (Object) new int[]{1, 2, 3});
        cmp(diffs, "nullSafeToString(int[])", "ObjectUtils", "nullSafeToString",
                new Class<?>[]{int[].class}, (Object) new int[]{1, 2, 3});
        cmp(diffs, "nullSafeToString(boolean[])", "ObjectUtils", "nullSafeToString",
                new Class<?>[]{boolean[].class}, (Object) new boolean[]{true, false});
        cmp(diffs, "hashCode(long)", "ObjectUtils", "hashCode", new Class<?>[]{long.class}, 42L);
        cmp(diffs, "hashCode(double)", "ObjectUtils", "hashCode", new Class<?>[]{double.class}, 1.5d);
        assertTrue(diffs.length() == 0, "差异：\n" + head(diffs));
    }

    @Test
    @DisplayName("ObjectUtils 枚举常量判定与旧实现一致")
    void objectUtilsEnumMatches() throws Exception {
        assertTrue(ObjectUtils.containsConstant(Sample.values(), "A"));
        // caseSensitive 语义（Javadoc）：true -> 严格 equals；false -> equalsIgnoreCase
        assertTrue(ObjectUtils.containsConstant(Sample.values(), "a"), "两参重载默认忽略大小写");
        assertTrue(ObjectUtils.containsConstant(Sample.values(), "a", false), "caseSensitive=false 应忽略大小写");
        assertTrue(!ObjectUtils.containsConstant(Sample.values(), "a", true), "caseSensitive=true 时小写 a 不应命中大写 A");
        assertEquals(Sample.A, ObjectUtils.caseInsensitiveValueOf(Sample.values(), "a"));

        // 与旧侧逐项比对
        assertEquals(String.valueOf(oldContainsConstant("A")),
                String.valueOf(ObjectUtils.containsConstant(Sample.values(), "A")));
        assertEquals(String.valueOf(oldContainsConstant("a")),
                String.valueOf(ObjectUtils.containsConstant(Sample.values(), "a")));
        assertEquals(String.valueOf(oldContainsConstant3("a", false)),
                String.valueOf(ObjectUtils.containsConstant(Sample.values(), "a", false)));
        assertEquals(String.valueOf(oldContainsConstant3("a", true)),
                String.valueOf(ObjectUtils.containsConstant(Sample.values(), "a", true)));
    }

    /** 测试用枚举 */
    public enum Sample { A, B, C }

    // ---------------- StringUtils ----------------

    @Test
    @DisplayName("StringUtils 判定/裁剪/查找类方法与旧实现一致")
    void stringUtilsBasicMatches() throws Exception {
        StringBuilder diffs = new StringBuilder();
        String[] strs = {null, "", " ", "  ", "a", " a ", "\t\na\n", "中文", "aXbXc", "AAAA"};
        for (String s : strs) {
            for (String m : new String[]{"hasLength", "hasText", "containsWhitespace",
                    "trimWhitespace", "trimAllWhitespace", "trimLeadingWhitespace", "trimTrailingWhitespace",
                    "capitalize", "uncapitalize", "quote", "cleanPath", "getFilename",
                    "getFilenameExtension", "stripFilenameExtension"}) {
                cmp(diffs, m + "(" + q(s) + ")", "StringUtils", m, new Class<?>[]{String.class}, s);
            }
            // isEmpty 的形参是 Object（不是 String）
            cmp(diffs, "isEmpty(" + q(s) + ")", "StringUtils", "isEmpty", new Class<?>[]{Object.class}, s);
            cmp(diffs, "isEmpty(CharSequence " + q(s) + ")", "StringUtils", "isEmpty",
                    new Class<?>[]{Object.class}, s);
            cmp(diffs, "unqualify(" + q(s) + ")", "StringUtils", "unqualify", new Class<?>[]{String.class}, s);
        }
        assertTrue(diffs.length() == 0, "差异：\n" + head(diffs));
    }

    @Test
    @DisplayName("StringUtils 匹配/替换/计数/数组类方法与旧实现一致")
    void stringUtilsMatchReplace() throws Exception {
        StringBuilder diffs = new StringBuilder();
        String[][] pairs = {
                {"FileName.txt", "filename"}, {"aXbXc", "X"}, {"", "a"}, {"abc", ""},
                {"a.b.c", "."}, {"中文测试", "测试"},
        };
        for (String[] p : pairs) {
            cmp(diffs, "startsWithIgnoreCase", "StringUtils", "startsWithIgnoreCase",
                    new Class<?>[]{String.class, String.class}, p[0], p[1]);
            cmp(diffs, "endsWithIgnoreCase", "StringUtils", "endsWithIgnoreCase",
                    new Class<?>[]{String.class, String.class}, p[0], p[1]);
            cmp(diffs, "countOccurrencesOf", "StringUtils", "countOccurrencesOf",
                    new Class<?>[]{String.class, String.class}, p[0], p[1]);
            cmp(diffs, "delete", "StringUtils", "delete", new Class<?>[]{String.class, String.class}, p[0], p[1]);
            cmp(diffs, "deleteAny", "StringUtils", "deleteAny", new Class<?>[]{String.class, String.class}, p[0], p[1]);
            cmp(diffs, "split", "StringUtils", "split", new Class<?>[]{String.class, String.class}, p[0], p[1]);
            cmp(diffs, "tokenizeToStringArray", "StringUtils", "tokenizeToStringArray",
                    new Class<?>[]{String.class, String.class}, p[0], p[1]);
            cmp(diffs, "delimitedListToStringArray", "StringUtils", "delimitedListToStringArray",
                    new Class<?>[]{String.class, String.class}, p[0], p[1]);
            cmp(diffs, "commaDelimitedListToSet", "StringUtils", "commaDelimitedListToSet",
                    new Class<?>[]{String.class}, p[0]);
        }
        cmp(diffs, "replace", "StringUtils", "replace",
                new Class<?>[]{String.class, String.class, String.class}, "aXbXc", "X", "-");
        cmp(diffs, "addStringToArray", "StringUtils", "addStringToArray",
                new Class<?>[]{String[].class, String.class}, new String[]{"a"}, "b");
        cmp(diffs, "concatenateStringArrays", "StringUtils", "concatenateStringArrays",
                new Class<?>[]{String[].class, String[].class}, new String[]{"a"}, new String[]{"b"});
        cmp(diffs, "mergeStringArrays", "StringUtils", "mergeStringArrays",
                new Class<?>[]{String[].class, String[].class}, new String[]{"a", "b"}, new String[]{"b", "c"});
        cmp(diffs, "sortStringArray", "StringUtils", "sortStringArray",
                new Class<?>[]{String[].class}, (Object) new String[]{"c", "a", "b"});
        cmp(diffs, "trimArrayElements", "StringUtils", "trimArrayElements",
                new Class<?>[]{String[].class}, (Object) new String[]{" a ", "b "});
        cmp(diffs, "removeDuplicateStrings", "StringUtils", "removeDuplicateStrings",
                new Class<?>[]{String[].class}, (Object) new String[]{"a", "a", "b"});
        cmp(diffs, "arrayToCommaDelimitedString", "StringUtils", "arrayToCommaDelimitedString",
                new Class<?>[]{Object[].class}, (Object) new Object[]{"a", "b"});
        cmp(diffs, "arrayToDelimitedString", "StringUtils", "arrayToDelimitedString",
                new Class<?>[]{Object[].class, String.class}, new Object[]{"a", "b"}, "-");
        cmp(diffs, "collectionToCommaDelimitedString", "StringUtils", "collectionToCommaDelimitedString",
                new Class<?>[]{Collection.class}, new ArrayList<>(List.of("a", "b")));
        cmp(diffs, "toStringArray(Collection)", "StringUtils", "toStringArray",
                new Class<?>[]{Collection.class}, new ArrayList<>(List.of("a", "b")));
        cmp(diffs, "parseLocaleString", "StringUtils", "parseLocaleString",
                new Class<?>[]{String.class}, "zh_CN");
        cmp(diffs, "pathEquals", "StringUtils", "pathEquals",
                new Class<?>[]{String.class, String.class}, "/a/b", "/a//b");
        cmp(diffs, "applyRelativePath", "StringUtils", "applyRelativePath",
                new Class<?>[]{String.class, String.class}, "/a/b/c", "d");
        assertTrue(diffs.length() == 0, "差异：\n" + head(diffs));
    }

    @Test
    @DisplayName("StringUtils.substringMatch 边界索引与旧实现一致")
    void stringUtilsSubstringMatch() throws Exception {
        StringBuilder diffs = new StringBuilder();
        for (Object[] a : new Object[][]{
                {"abcdef", 0, "abc"}, {"abcdef", 3, "def"}, {"abcdef", 2, "cde"},
                {"abcdef", 6, ""}, {"abcdef", 7, "x"}, {"abc", 0, "abcd"}}) {
            cmp(diffs, "substringMatch(" + a[0] + "," + a[1] + "," + a[2] + ")", "StringUtils", "substringMatch",
                    new Class<?>[]{CharSequence.class, int.class, CharSequence.class}, a);
        }
        assertTrue(diffs.length() == 0, "差异：\n" + head(diffs));
    }

    // ---------------- RandomUtil（形状断言，非值比对） ----------------

    @Test
    @DisplayName("RandomUtil 形状断言：长度/字符集/范围符合，且不返回 null")
    void randomUtilShape() throws Exception {
        for (int n : new int[]{0, 1, 8, 32}) {
            assertEquals(n, RandomUtil.nextString(n).length(), "nextString(" + n + ") 长度");
            assertEquals(n, RandomUtil.nextIntAsStringByLength(n).length(), "nextIntAsStringByLength(" + n + ") 长度");
            assertEquals(n, RandomUtil.getRandomNameByLength(n).length(), "getRandomNameByLength(" + n + ") 长度");
            assertEquals(n, RandomUtil.getRandomPasswordByLength(n).length(), "getRandomPasswordByLength(" + n + ") 长度");
            assertEquals(n, RandomUtil.getAppId(n).length(), "getAppId(" + n + ") 长度");
        }
        for (int i = 0; i < 20; i++) {
            int v = RandomUtil.nextInt(5, 10);
            assertTrue(v >= 5 && v <= 10, "nextInt(min,max) 应在范围内，实际=" + v);
            int v2 = RandomUtil.nextInt(3);
            assertTrue(v2 >= 0 && v2 < 3, "nextInt(n) 应在 [0,n)，实际=" + v2);
            assertNotNull(RandomUtil.nextColor());
            assertNotNull(RandomUtil.nextColor(0, 255));
            assertNotNull(RandomUtil.nextChar());
        }
        String cn = RandomUtil.getChinese(4);
        assertEquals(4, cn.length(), "getChinese(4) 应为 4 个字符");
        assertTrue(cn.matches("[\\u4e00-\\u9fa5]{4}"), "getChinese 应仅含中文字符，实际=" + cn);
    }

    // ---------------- 辅助 ----------------

    /**
     * 旧侧 AntPathMatcher 实例。
     * 注意：AntPathMatcher 只有【无参构造】，trimTokens 通过 setter 设置。
     */
    private static Object newOldMatcher(boolean trimTokens) throws Exception {
        Class<?> c = Class.forName("cn.eova.common.utils.util.AntPathMatcher", true, isolated);
        Object m = c.getConstructor().newInstance();
        c.getMethod("setTrimTokens", boolean.class).invoke(m, trimTokens);
        return m;
    }

    /** 新侧 AntPathMatcher 实例（同样经 setter 设置 trimTokens） */
    private static AntPathMatcher newMatcher(boolean trimTokens) {
        AntPathMatcher m = new AntPathMatcher();
        m.setTrimTokens(trimTokens);
        return m;
    }

    /** 旧侧 containsConstant(enumValues, constant) */
    private static Object oldContainsConstant(String constant) throws Exception {
        return invokeOldContainsConstant(2, constant, null);
    }

    /** 旧侧 containsConstant(enumValues, constant, caseSensitive) */
    private static Object oldContainsConstant3(String constant, boolean caseSensitive) throws Exception {
        return invokeOldContainsConstant(3, constant, caseSensitive);
    }

    /** 按参数个数定位旧侧 containsConstant 重载并调用 */
    private static Object invokeOldContainsConstant(int arity, String constant, Boolean caseSensitive)
            throws Exception {
        Class<?> c = Class.forName("cn.eova.common.utils.util.ObjectUtils", true, isolated);
        Class<?> e = Class.forName("cn.eova.common.utils.util.UtilUtilsGoldenTest$Sample", true, isolated);
        for (Method m : c.getMethods()) {
            if (!m.getName().equals("containsConstant") || m.getParameterCount() != arity) {
                continue;
            }
            return arity == 2
                    ? m.invoke(null, e.getEnumConstants(), constant)
                    : m.invoke(null, e.getEnumConstants(), constant, caseSensitive);
        }
        throw new NoSuchMethodException("containsConstant/" + arity);
    }

    /** 在旧实例上按已解析 Method 调用，异常渲染为 "throw <类名>" */
    private static Object invokeOn(Object target, Method m, Object... args) {
        try {
            return m.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "throw " + e.getCause().getClass().getName();
        } catch (Exception e) {
            return "throw " + e.getClass().getName();
        }
    }

    /** 在新实例上按方法名调用，异常渲染同 invokeOn —— 必须与旧侧对称 */
    private static Object invokeOn(Object target, String method, Class<?>[] types, Object... args) {
        try {
            Method m = target.getClass().getMethod(method, types);
            return invokeOn(target, m, args);
        } catch (Exception e) {
            return "throw " + e.getClass().getName();
        }
    }

    private static Object callOld(String simpleName, String method, Class<?>[] types, Object... args)
            throws Exception {
        Method m = Class.forName("cn.eova.common.utils.util." + simpleName, true, isolated).getMethod(method, types);
        return invoke(m, args);
    }

    private static Object callNew(String simpleName, String method, Class<?>[] types, Object... args)
            throws Exception {
        Method m = Class.forName("cn.eova.common.utils.util." + simpleName).getMethod(method, types);
        return invoke(m, args);
    }

    private static Object invoke(Method m, Object... args) throws Exception {
        try {
            return m.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "throw " + e.getCause().getClass().getName();
        }
    }

    private static String render(Object v) {
        if (v instanceof Object[] a) {
            return Arrays.toString(a);
        }
        if (v instanceof byte[] a) {
            return Arrays.toString(a);
        }
        return String.valueOf(v);
    }

    private static void cmp(StringBuilder diffs, String label, String simpleName, String method,
                            Class<?>[] types, Object... args) throws Exception {
        String o = render(callOld(simpleName, method, types, args));
        String n = render(callNew(simpleName, method, types, args));
        if (!o.equals(n)) {
            diffs.append("  ").append(label).append(": 旧=").append(o).append(" 新=").append(n).append('\n');
        }
    }

    private static String q(String s) {
        return s == null ? "null" : "\"" + s.replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }

    private static String head(StringBuilder diffs) {
        String[] lines = diffs.toString().split("\n");
        return lines.length <= 15 ? diffs.toString()
                : String.join("\n", Arrays.copyOfRange(lines, 0, 15)) + "\n  ... 共 " + lines.length + " 行";
    }
}
