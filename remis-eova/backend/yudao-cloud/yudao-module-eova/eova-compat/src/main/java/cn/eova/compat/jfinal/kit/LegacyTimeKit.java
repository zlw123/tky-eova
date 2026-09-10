/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * jfinal {@code com.jfinal.kit.TimeKit} 的等价 port（阶段 1 S03 支撑单元）。
 *
 * <p><b>为什么必须 port 而不是直接依赖：</b>
 * 旧系统运行在 {@code com.jfinal:jfinal:5.2.6}（Enjoy 内嵌于该 jar）之上；
 * 新栈为满足首页模板 {@code #renderOrElse} 与 {@code com.jfinal.kit.PathKit} 需求，
 * 必须使用独立制品 {@code com.jfinal:enjoy:5.3.0}（见 DES-002-R4 §2.1，SP2 实测逐字节相同）。
 * 两个制品<b>都导出</b> {@code com.jfinal.kit.*} 同名类，实际生效者取决于 classpath
 * 解析顺序 —— 这是<b>环境依赖行为</b>，不能作为迁移的等价性基准。
 *
 * <p>金标证据显示二者确实不同：对同一组输入，jfinal 5.2.6 的
 * {@code TypeKit.toDate("test11111")} 抛
 * {@code RuntimeException: java.text.ParseException: Unparseable date}，
 * 而 enjoy 5.3.0 的同名方法抛
 * {@code IllegalArgumentException: Invalid date string ...}。
 * 故此处把 5.2.6 的语义固化为项目自有类，使 Record 转换语义不再随制品漂移。
 *
 * <p>ported from: com.jfinal.kit.TimeKit（第三方制品，非 EOVA 源码）
 * <br>source artifact: com.jfinal:jfinal:5.2.6
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08（该 revision 锁定上述制品版本）
 * <br>参照物实证：{@code docs/.local/spikes/sp6-record-semantics/pom.xml} 声明 jfinal 5.2.6
 *
 * <p><b>本单元为部分 port，只覆盖 {@link LegacyTypeKit} 需要的方法：</b>
 * <ul>
 *   <li>已 port：{@code getDateTimeFormatter}、{@code getSimpleDateFormat}、
 *       {@code parse}、{@code parseLocalDateTime}、
 *       {@code toLocalDateTime(Date)}、
 *       {@code toDate(LocalDateTime|LocalDate|LocalTime)}</li>
 *   <li>未 port：{@code now*}、{@code format*}、{@code parseLocalDate}、
 *       {@code parseLocalTime}、{@code isAfter/isBefore/isEqual}、
 *       {@code toLocalDate/toLocalTime}、{@code toDate(LocalDate,LocalTime)}、{@code toLong}
 *       —— 阶段 1 port 范围内无调用方。后续若 EOVA 单元需要，须按同一口径逐个 port 并补金标。</li>
 * </ul>
 *
 * <p><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>{@code parse} 把 {@link ParseException} 包装为 {@code RuntimeException} 后抛出，
 *       而非直接抛受检异常（旧实现如此，调用方依赖其异常类型）。</li>
 *   <li>{@code SimpleDateFormat} 未设置 {@code setLenient(false)}，保持 JDK 默认宽松解析。</li>
 *   <li>{@code toLocalDateTime(Date)} 对 {@code java.sql.Date} 先转回 {@code java.util.Date}，
 *       规避 {@code java.sql.Date.toInstant()} 抛 {@code UnsupportedOperationException}。</li>
 * </ol>
 */
public final class LegacyTimeKit {

    /** 日期时间格式化器缓存（按 pattern） */
    private static final Map<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();

    /** 线程内 SimpleDateFormat 缓存（SimpleDateFormat 非线程安全，旧实现按线程缓存） */
    private static final ThreadLocal<HashMap<String, SimpleDateFormat>> TL =
            ThreadLocal.withInitial(HashMap::new);

    private LegacyTimeKit() {
    }

    /**
     * 取（并缓存）指定 pattern 的 DateTimeFormatter
     */
    public static DateTimeFormatter getDateTimeFormatter(String pattern) {
        DateTimeFormatter formatter = FORMATTERS.get(pattern);
        if (formatter == null) {
            formatter = DateTimeFormatter.ofPattern(pattern);
            FORMATTERS.put(pattern, formatter);
        }
        return formatter;
    }

    /**
     * 取（并缓存）当前线程的指定 pattern 的 SimpleDateFormat
     */
    public static SimpleDateFormat getSimpleDateFormat(String pattern) {
        SimpleDateFormat sdf = TL.get().get(pattern);
        if (sdf == null) {
            sdf = new SimpleDateFormat(pattern);
            TL.get().put(pattern, sdf);
        }
        return sdf;
    }

    /**
     * 按 pattern 解析为 Date；失败时包装为 RuntimeException（与旧实现一致）
     */
    public static Date parse(String dateString, String pattern) {
        try {
            return getSimpleDateFormat(pattern).parse(dateString);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 按 pattern 解析为 LocalDateTime；失败时抛出 DateTimeParseException（不包装）
     */
    public static LocalDateTime parseLocalDateTime(String dateString, String pattern) {
        return LocalDateTime.parse(dateString, getDateTimeFormatter(pattern));
    }

    /**
     * Date → LocalDateTime（系统时区）
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        // java.sql.Date.toInstant() 不受支持，先转为 java.util.Date
        if (date instanceof java.sql.Date) {
            date = new Date(date.getTime());
        }
        Instant instant = date.toInstant();
        ZoneId zone = ZoneId.systemDefault();
        return LocalDateTime.ofInstant(instant, zone);
    }

    /**
     * LocalDateTime → Date（系统时区）
     */
    public static Date toDate(LocalDateTime localDateTime) {
        ZoneId zone = ZoneId.systemDefault();
        Instant instant = localDateTime.atZone(zone).toInstant();
        return Date.from(instant);
    }

    /**
     * LocalDate → Date（当日零点，系统时区）
     */
    public static Date toDate(LocalDate localDate) {
        ZoneId zone = ZoneId.systemDefault();
        Instant instant = localDate.atStartOfDay().atZone(zone).toInstant();
        return Date.from(instant);
    }

    /**
     * LocalTime → Date（叠加当日日期，系统时区）
     */
    public static Date toDate(LocalTime localTime) {
        LocalDate localDate = LocalDate.now();
        LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime);
        ZoneId zone = ZoneId.systemDefault();
        Instant instant = localDateTime.atZone(zone).toInstant();
        return Date.from(instant);
    }
}
