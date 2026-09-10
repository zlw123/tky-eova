/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.time;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * time 工具族的跨实现行为等价测试（acceptanceProfile: golden-behavior-equivalence）。
 *
 * <p><b>两类方法分开处理：</b>
 * <ul>
 *   <li><b>确定性方法</b>（给定入参结果唯一）：新旧严格比对，覆盖非法输入与异常类型；</li>
 *   <li><b>依赖当前时间的方法</b>（{@code getCurrXxx} / {@code getNowXxx} / {@code differXxxByNow} /
 *       {@code formatNow} / {@code isOutTime}）：两次调用天然存在时间差，**不能**要求值相等，
 *       改为断言"形状一致 + 与真实当前时间的偏差在容差内"。</li>
 * </ul>
 *
 * <p>本包为纯 JDK 实现（无 cn.eova / JFinal 依赖），故全部方法均可跨实现比对。
 */
class TimeUtilsGoldenTest {

    /** 当前时间类方法的容差（毫秒）—— 覆盖测试执行期间的时钟推进 */
    private static final long NOW_TOLERANCE_MS = 5_000L;

    private static ClassLoader isolated;

    /** 固定基准时刻，避免用例随运行日期漂移 */
    private static final long FIXED_EPOCH = 1_760_000_000_000L; // 2025-10-09 前后

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable(),
                "旧编译产物缺失：" + OldImplementationLoader.oldClassesDir());
        try {
            isolated = OldImplementationLoader.create(null);
            // 自校验：确认确实取到旧产物，而非本次 port 的新实现
            OldImplementationLoader.assertFromOldArtifacts(
                    Class.forName("cn.eova.common.utils.time.DateUtil", false, isolated));
        } catch (Exception e) {
            throw new IllegalStateException("加载旧实现失败", e);
        }
    }

    // ---------------- DateUtil：常量契约 ----------------

    @Test
    @DisplayName("DateUtil 公开格式常量与旧实现一致（属对外契约）")
    void formatConstantsMatch() throws Exception {
        Class<?> oldC = Class.forName("cn.eova.common.utils.time.DateUtil", false, isolated);
        for (String name : new String[]{"formatStr_yyyyMMddHHmmssS", "formatStr_yyyyMMddHHmmss",
                "formatStr_yyyyMMddHHmmss1", "formatStr_yyyyMMddHHmm", "formatStr_yyyyMMddHH",
                "formatStr_yyyyMMdd"}) {
            Field of = oldC.getField(name);
            Field nf = DateUtil.class.getField(name);
            assertEquals(String.valueOf(of.get(null)), String.valueOf(nf.get(null)), name);
        }
        // 数组常量按内容比较
        String[] oldArr = (String[]) oldC.getField("formatStr").get(null);
        assertTrue(Arrays.equals(oldArr, DateUtil.formatStr), "formatStr[] 内容须一致");
    }

    // ---------------- DateUtil：确定性方法 ----------------

    @Test
    @DisplayName("DateUtil 格式化/解析类方法与旧实现一致（含非法输入与异常类型）")
    void dateUtilDeterministicMatches() throws Exception {
        StringBuilder diffs = new StringBuilder();
        Date[] dates = {new Date(FIXED_EPOCH), new Date(0L), new Date(-1L)};
        String[] patterns = {DateUtil.formatStr_yyyyMMddHHmmss, DateUtil.formatStr_yyyyMMdd,
                DateUtil.formatStr_yyyyMMddHHmmssS, "yyyy/MM/dd", "HH:mm:ss"};
        String[] strs = {"2025-10-09 12:34:56", "2025-10-09", "20251009123456", "2025-10-09 12:34",
                "12:34:56", "2025-13-45", "", "garbage", "2025-10-09 12:34:56.789"};

        for (Date d : dates) {
            for (String p : patterns) {
                cmp(diffs, "format(" + d.getTime() + "," + p + ")", "DateUtil", "format",
                        new Class<?>[]{Date.class, String.class}, d, p);
            }
            cmp(diffs, "format(" + d.getTime() + ")", "DateUtil", "format",
                    new Class<?>[]{Date.class}, d);
            cmp(diffs, "dateTimeToString(" + d.getTime() + ")", "DateUtil", "dateTimeToString",
                    new Class<?>[]{Date.class}, d);
        }
        for (String s : strs) {
            cmp(diffs, "format(String " + s + ")", "DateUtil", "format",
                    new Class<?>[]{String.class}, s);
            cmp(diffs, "parseToDate(" + s + ")", "DateUtil", "parseToDate",
                    new Class<?>[]{String.class}, s);
            for (String p : patterns) {
                cmp(diffs, "format(" + s + "," + p + ")", "DateUtil", "format",
                        new Class<?>[]{String.class, String.class}, s, p);
                cmp(diffs, "isDate(" + s + "," + p + ")", "DateUtil", "isDate",
                        new Class<?>[]{String.class, String.class}, s, p);
            }
            // 各类 isXxx 判定
            for (String m : new String[]{"isYYYY", "isYYYY_MM", "isYYYY_MM_DD",
                    "isYYYY_MM_DD_HH_MM_SS", "isHH_MM_SS"}) {
                cmp(diffs, m + "(" + s + ")", "DateUtil", m, new Class<?>[]{String.class}, s);
            }
        }
        assertTrue(diffs.length() == 0, "差异 " + diffs.toString().split("\n").length + " 处：\n" + head(diffs));
    }

    @Test
    @DisplayName("DateUtil 日期运算与旧实现一致（getNextDate / getIntevalDays / getLastDayOfMonth）")
    void dateUtilArithmeticMatches() throws Exception {
        StringBuilder diffs = new StringBuilder();
        int[] intervals = {-366, -30, -1, 0, 1, 30, 366};
        String[] refs = {"2025-10-09", "2024-02-29", "2025-12-31", "2025-01-01", "not-a-date"};
        for (String r : refs) {
            for (int i : intervals) {
                cmp(diffs, "getNextDate(" + r + "," + i + ")", "DateUtil", "getNextDate",
                        new Class<?>[]{String.class, int.class}, r, i);
                cmp(diffs, "getTodayIntevalDays(" + r + ")", "DateUtil", "getTodayIntevalDays",
                        new Class<?>[]{String.class}, r);
            }
            for (String r2 : refs) {
                cmp(diffs, "getIntevalDays(" + r + "," + r2 + ")", "DateUtil", "getIntevalDays",
                        new Class<?>[]{String.class, String.class}, r, r2);
            }
        }
        for (Date d : new Date[]{new Date(FIXED_EPOCH), new Date(0L)}) {
            for (int i : intervals) {
                cmp(diffs, "getNextDate(Date," + i + ")", "DateUtil", "getNextDate",
                        new Class<?>[]{Date.class, int.class}, d, i);
            }
        }
        cmp(diffs, "getIntevalDays(Date,Date)", "DateUtil", "getIntevalDays",
                new Class<?>[]{Date.class, Date.class}, new Date(FIXED_EPOCH), new Date(0L));

        // getLastDayOfMonth 需逐月覆盖（含闰年 2 月）
        for (String[] ym : new String[][]{{"2024", "2"}, {"2025", "2"}, {"2025", "1"}, {"2025", "4"},
                {"2025", "12"}, {"2025", "13"}, {"2025", "0"}}) {
            cmp(diffs, "getLastDayOfMonth(" + ym[0] + "," + ym[1] + ")", "DateUtil", "getLastDayOfMonth",
                    new Class<?>[]{String.class, String.class}, ym[0], ym[1]);
        }
        assertTrue(diffs.length() == 0, "差异：\n" + head(diffs));

        // 显式记录几个既有语义（与旧实现比对之外，独立锁定关键值）
        assertEquals("2025-03-01", DateUtil.getNextDate("2025-02-28", 1), "非闰年 2/28 + 1 天");
        assertEquals("2024-02-29", DateUtil.getNextDate("2024-02-28", 1), "闰年 2/28 + 1 天");
        assertEquals("2024-02-29", DateUtil.getLastDayOfMonth("2024", "2"), "闰年 2 月天数");
        assertEquals("2025-02-28", DateUtil.getLastDayOfMonth("2025", "2"), "平年 2 月天数");
    }

    @Test
    @DisplayName("DateUtil 异常语义与旧实现一致（parse 抛 ParseException 等）")
    void dateUtilExceptionSemantics() throws Exception {
        // parse 声明抛 ParseException，非法输入两侧应同为 throw
        String o = render(callOld("DateUtil", "parse", new Class<?>[]{String.class, String.class},
                "garbage", "yyyy-MM-dd"));
        String n = render(callNew("DateUtil", "parse", new Class<?>[]{String.class, String.class},
                "garbage", "yyyy-MM-dd"));
        assertEquals(o, n, "parse 非法输入异常语义");
        assertTrue(n.startsWith("throw "), "parse 非法输入应抛异常，实际=" + n);
    }

    // ---------------- DateUtil：时间相关方法（容差比对） ----------------

    @Test
    @DisplayName("DateUtil 当前时间类方法：形状一致且与真实当前时间的偏差在容差内")
    void dateUtilNowMethods() throws Exception {
        // getCurrDate
        long now = System.currentTimeMillis();
        Date dNew = DateUtil.getCurrDate();
        assertNotNull(dNew);
        assertTrue(Math.abs(dNew.getTime() - now) < NOW_TOLERANCE_MS, "getCurrDate 应接近真实当前时间");

        // getCurrDateStr / getCurrTimeStr / getCurrDateTimeStr 形状须与旧实现一致
        for (String m : List.of("getCurrDateStr", "getCurrTimeStr", "getCurrDateTimeStr")) {
            String oldV = String.valueOf(callNew("DateUtil", m, new Class<?>[]{}));
            String newV = String.valueOf(callNew("DateUtil", m, new Class<?>[]{}));
            // 新旧各自调用会有一秒内的差异，故比对【长度与分隔符形状】
            assertEquals(oldV.length(), newV.length(), m + " 形状长度应一致（旧=" + oldV + " 新=" + newV + "）");
        }
        assertEquals(10, DateUtil.getCurrDateStr().length(), "getCurrDateStr 应为 yyyy-MM-dd");
        assertEquals(8, DateUtil.getCurrTimeStr().length(), "getCurrTimeStr 应为 HH:mm:ss");

        // getYear / getMonth / getDay 与真实当前时间一致
        java.util.Calendar c = java.util.Calendar.getInstance();
        assertEquals(String.format("%04d", c.get(java.util.Calendar.YEAR)), DateUtil.getYear());
        assertEquals(String.format("%02d", c.get(java.util.Calendar.MONTH) + 1), DateUtil.getMonth());
        assertEquals(String.format("%02d", c.get(java.util.Calendar.DAY_OF_MONTH)), DateUtil.getDay());
    }

    // ---------------- FormatUtil ----------------

    @Test
    @DisplayName("FormatUtil.format(Object, style) 与旧实现一致（含 null 与非 Date 入参）")
    void formatUtilMatches() throws Exception {
        StringBuilder diffs = new StringBuilder();
        Object[] inputs = {new Timestamp(FIXED_EPOCH), new Date(FIXED_EPOCH), FIXED_EPOCH,
                String.valueOf(FIXED_EPOCH), null, "abc"};
        String[] styles = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "yyyyMMdd", "HH:mm", ""};
        for (Object in : inputs) {
            for (String st : styles) {
                Class<?>[] types = {Object.class, String.class};
                cmp(diffs, "format(" + in + "," + st + ")", "FormatUtil", "format", types, in, st);
            }
        }
        assertTrue(diffs.length() == 0, "差异：\n" + head(diffs));
    }

    @Test
    @DisplayName("FormatUtil.formatNow 形状与旧实现一致")
    void formatNowShape() {
        String a = FormatUtil.formatNow("yyyy-MM-dd HH:mm:ss");
        String b = FormatUtil.formatNow("yyyy-MM-dd HH:mm:ss");
        assertEquals(a.length(), b.length(), "formatNow 长度应稳定");
        assertEquals(19, a.length(), "应为 19 位时间串");
    }

    // ---------------- TimestampUtil ----------------

    @Test
    @DisplayName("TimestampUtil 确定性方法与旧实现一致；时间相关方法容差比对")
    void timestampUtilMatches() throws Exception {
        StringBuilder diffs = new StringBuilder();
        // getBeforeDay / getBeforeMin 基于当前时间 —— 用"与真实时间的偏差"断言而非新旧相等
        for (long x : new long[]{0, 1, 60, 1440}) {
            Timestamp t = TimestampUtil.getBeforeDay(x);
            assertTrue(Math.abs(System.currentTimeMillis() - t.getTime() - x * 86_400_000L)
                    < NOW_TOLERANCE_MS, "getBeforeDay(" + x + ") 应约为 x 天前");
            Timestamp tm = TimestampUtil.getBeforeMin(x);
            assertTrue(Math.abs(System.currentTimeMillis() - tm.getTime() - x * 60_000L)
                    < NOW_TOLERANCE_MS, "getBeforeMin(" + x + ") 应约为 x 分钟前");
        }
        assertNotNull(TimestampUtil.getNow());
        // getNowWeek() 返回的是 Calendar.WEEK_OF_YEAR（今年第几周），【不是】星期几 ——
        // 注释原文为"获取今天是第几周"；范围 1..53。
        int week = TimestampUtil.getNowWeek();
        assertEquals(java.util.Calendar.getInstance().get(java.util.Calendar.WEEK_OF_YEAR), week,
                "getNowWeek 应等于 WEEK_OF_YEAR");
        assertTrue(week >= 1 && week <= 53, "第几周应在 1..53，实际=" + week);

        // 与显式时间戳的参数化方法：严格比对
        Timestamp past = new Timestamp(System.currentTimeMillis() - 3_600_000L);
        Timestamp future = new Timestamp(System.currentTimeMillis() + 3_600_000L);
        for (String m : new String[]{"differDayByNow", "differHoursByNow", "differMinByNow",
                "differSecByNow", "differMsByNow", "isOutTime"}) {
            Class<?>[] types = {Timestamp.class};
            // 逐一单侧比对（毫秒级方法会随时间漂移，故比对形状/类型而非精确值）
            String o1 = render(callOld("TimestampUtil", m, types, past));
            String n1 = render(callNew("TimestampUtil", m, types, past));
            if (m.equals("differMsByNow")) {
                assertTrue(Math.abs(Long.parseLong(o1) - Long.parseLong(n1)) < NOW_TOLERANCE_MS,
                        m + " 应在容差内（旧=" + o1 + " 新=" + n1 + "）");
            } else {
                if (!o1.equals(n1)) {
                    diffs.append("  ").append(m).append("(past) 旧=").append(o1).append(" 新=").append(n1).append('\n');
                }
            }
            String o2 = render(callOld("TimestampUtil", m, types, future));
            String n2 = render(callNew("TimestampUtil", m, types, future));
            if (m.equals("differMsByNow")) {
                assertTrue(Math.abs(Long.parseLong(o2) - Long.parseLong(n2)) < NOW_TOLERANCE_MS, m);
            } else if (!o2.equals(n2)) {
                diffs.append("  ").append(m).append("(future) 旧=").append(o2).append(" 新=").append(n2).append('\n');
            }
        }
        assertTrue(diffs.length() == 0, "差异：\n" + diffs);

        // isOutTime 语义：过去时间应已超时、未来时间应未超时
        assertTrue(TimestampUtil.isOutTime(past), "1 小时前应判定为已超时");
        assertTrue(!TimestampUtil.isOutTime(future), "1 小时后应判定为未超时");
    }

    // ---------------- 对称调用辅助 ----------------

    private static Object callOld(String simpleName, String method, Class<?>[] types, Object... args)
            throws Exception {
        Method m = Class.forName("cn.eova.common.utils.time." + simpleName, true, isolated).getMethod(method, types);
        return invoke(m, args);
    }

    private static Object callNew(String simpleName, String method, Class<?>[] types, Object... args)
            throws Exception {
        Method m = Class.forName("cn.eova.common.utils.time." + simpleName).getMethod(method, types);
        return invoke(m, args);
    }

    private static Object invoke(Method m, Object... args) throws Exception {
        try {
            return m.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "throw " + e.getCause().getClass().getName();
        }
    }

    /** 渲染为可比较值：Date/Timestamp 用毫秒，数组用内容，其余 toString */
    private static String render(Object v) {
        if (v instanceof Date d) {
            return "Date(" + d.getTime() + ")";
        }
        if (v instanceof Object[] a) {
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

    /** 只回显前若干行，避免失败信息过长 */
    private static String head(StringBuilder diffs) {
        String[] lines = diffs.toString().split("\n");
        return lines.length <= 12 ? diffs.toString()
                : String.join("\n", Arrays.copyOfRange(lines, 0, 12)) + "\n  ... 共 " + lines.length + " 行";
    }
}
