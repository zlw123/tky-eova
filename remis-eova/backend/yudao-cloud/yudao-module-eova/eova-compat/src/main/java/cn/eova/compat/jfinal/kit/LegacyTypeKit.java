/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Date;

/**
 * jfinal {@code com.jfinal.kit.TypeKit} 的等价 port（阶段 1 S03 支撑单元）。
 *
 * <p><b>存在理由：</b>旧系统运行在 {@code com.jfinal:jfinal:5.2.6} 之上，新栈使用
 * {@code com.jfinal:enjoy:5.3.0}；两者都导出 {@code com.jfinal.kit.TypeKit}，
 * 生效者取决于 classpath 解析顺序。金标实测二者语义有差异（见 {@link LegacyTimeKit} 类注释）。
 * EOVA 的 {@code Record} 取值 API 全部构建在本类语义之上，属对外可观测契约，
 * 故必须固化，不得依赖环境。
 *
 * <p>ported from: com.jfinal.kit.TypeKit（第三方制品，非 EOVA 源码）
 * <br>source artifact: com.jfinal:jfinal:5.2.6
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>覆盖度：5.2.6 全部 12 个 {@code to*} 公开方法（完整 port，无裁剪）
 *
 * <p><b>刻意保留的既有语义（均为实测契约，不是实现细节）：</b>
 * <ol>
 *   <li><b>非 {@code Number}/{@code String} 输入走裸 cast</b>：{@code toInt} 系列末尾是
 *       {@code (T) value}，对不兼容类型抛 JVM 生成的 {@code ClassCastException}
 *       （消息含 "is in module java.base of loader 'bootstrap'" 等 JDK 措辞）。</li>
 *   <li>{@code toBoolean} 只认 {@code Integer/Long/BigInteger/Byte/Short} 的 0/1；
 *       {@code Float}/{@code Double}/{@code BigDecimal} 或 Integer 的其它值
 *       <b>直接落到兜底 cast</b>，抛 {@code ClassCastException}。</li>
 *   <li>{@code toBoolean(String)} 中 {@code "true"} 大小写不敏感，而 {@code "1"} 大小写敏感
 *       （对数字串无差别，但保持原样以免行为漂移）。</li>
 *   <li>{@code toBigInteger(String)} 用 {@code new BigInteger(str)}，故非数字串抛
 *       {@code NumberFormatException: For input string: "..."}；
 *       不可改用 {@code BigDecimal} 中转（后者异常消息与解析宽度都不同）。</li>
 *   <li>{@code toDate(Object)} 对字符串按<b>长度分派</b> pattern（≤10 走 yyyy-MM-dd，
 *       &gt;16 走 yyyy-MM-dd HH:mm:ss，其余按冒号个数判断），长度 11–16 且无冒号时
 *       落到兜底 cast 抛 {@code ClassCastException}。</li>
 *   <li>{@code toNumber} 对非 {@code Number} 输入<b>总是返回 {@code Double}</b>：
 *       含 {@code '.'} 走 {@code Double.parseDouble}，否则走 {@code Long.parseLong} 再提升为 double。</li>
 *   <li>{@code toDate}/{@code toLocalDateTime} 的 {@code LocalTime} 分支使用
 *       {@code LocalDate.now()}，即"当日"，非确定性行为照旧保留。</li>
 * </ol>
 */
public final class LegacyTypeKit {

    /** 纯日期 pattern */
    private static final String datePattern = "yyyy-MM-dd";

    /** 纯日期长度（10） */
    private static final int dateLen = datePattern.length();

    /** 无秒日期时间 pattern */
    private static final String dateTimeWithoutSecondPattern = "yyyy-MM-dd HH:mm";

    /** 无秒日期时间长度（16） */
    private static final int dateTimeWithoutSecondLen = dateTimeWithoutSecondPattern.length();

    /** 完整日期时间 pattern */
    private static final String dateTimePattern = "yyyy-MM-dd HH:mm:ss";

    private LegacyTypeKit() {
    }

    /**
     * 取字符串：null 返回 null，否则 toString()
     */
    public static String toStr(Object s) {
        return s != null ? s.toString() : null;
    }

    /**
     * 取 Integer：Integer 直返，Number 取 intValue，否则按字符串解析，null 返回 null
     */
    public static Integer toInt(Object n) {
        if (n instanceof Integer) {
            return (Integer) n;
        }
        if (n instanceof Number) {
            return ((Number) n).intValue();
        }
        return n != null ? Integer.parseInt(n.toString()) : null;
    }

    /**
     * 取 Long：Long 直返，Number 取 longValue，否则按字符串解析，null 返回 null
     */
    public static Long toLong(Object n) {
        if (n instanceof Long) {
            return (Long) n;
        }
        if (n instanceof Number) {
            return ((Number) n).longValue();
        }
        return n != null ? Long.parseLong(n.toString()) : null;
    }

    /**
     * 取 Double：Double 直返，Number 取 doubleValue，否则按字符串解析，null 返回 null
     */
    public static Double toDouble(Object n) {
        if (n instanceof Double) {
            return (Double) n;
        }
        if (n instanceof Number) {
            return ((Number) n).doubleValue();
        }
        return n != null ? Double.parseDouble(n.toString()) : null;
    }

    /**
     * 取 BigDecimal：BigDecimal 直返，否则按字符串构造，null 返回 null
     */
    public static BigDecimal toBigDecimal(Object n) {
        if (n instanceof BigDecimal) {
            return (BigDecimal) n;
        }
        return n != null ? new BigDecimal(n.toString()) : null;
    }

    /**
     * 取 Float：Float 直返，Number 取 floatValue，否则按字符串解析，null 返回 null
     */
    public static Float toFloat(Object n) {
        if (n instanceof Float) {
            return (Float) n;
        }
        if (n instanceof Number) {
            return ((Number) n).floatValue();
        }
        return n != null ? Float.parseFloat(n.toString()) : null;
    }

    /**
     * 取 Short：Short 直返，Number 取 shortValue，否则按字符串解析，null 返回 null
     */
    public static Short toShort(Object n) {
        if (n instanceof Short) {
            return (Short) n;
        }
        if (n instanceof Number) {
            return ((Number) n).shortValue();
        }
        return n != null ? Short.parseShort(n.toString()) : null;
    }

    /**
     * 取 Byte：Byte 直返，Number 取 byteValue，否则按字符串解析，null 返回 null
     */
    public static Byte toByte(Object n) {
        if (n instanceof Byte) {
            return (Byte) n;
        }
        if (n instanceof Number) {
            return ((Number) n).byteValue();
        }
        return n != null ? Byte.parseByte(n.toString()) : null;
    }

    /**
     * 取 Boolean：只识别整数型 0/1 与字符串 true/false/1/0，其余抛 ClassCastException（与旧实现一致）
     */
    public static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            // 仅整数型参与 0/1 判定；Float/Double/BigDecimal 不参与，直接落兜底 cast
            if (value instanceof Integer || value instanceof Long || value instanceof BigInteger
                    || value instanceof Byte || value instanceof Short) {
                int intValue = ((Number) value).intValue();
                if (intValue == 1) {
                    return Boolean.TRUE;
                }
                if (intValue == 0) {
                    return Boolean.FALSE;
                }
            }
            return (Boolean) value;
        }
        if (value instanceof String) {
            String s = value.toString();
            // "true" 大小写不敏感；"1" 大小写敏感（照旧）
            if ("true".equalsIgnoreCase(s) || "1".equals(s)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(s) || "0".equals(s)) {
                return Boolean.FALSE;
            }
        }
        return (Boolean) value;
    }

    /**
     * 取 Number：Number 直返；否则含小数点按 Double 解析，否则按 Long 解析后提升为 Double；null 返回 null
     */
    public static Number toNumber(Object value) {
        if (value instanceof Number) {
            return (Number) value;
        }
        if (value == null) {
            return null;
        }
        String s = value.toString();
        return s.indexOf('.') != -1
                ? Double.parseDouble(s)
                : (double) Long.parseLong(s);
    }

    /**
     * 取 Date：Date 直返，Temporal 系列按类型转换，字符串按长度分派 pattern，其余抛 ClassCastException
     */
    public static Date toDate(Object value) {
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof Temporal) {
            if (value instanceof LocalDateTime) {
                return LegacyTimeKit.toDate((LocalDateTime) value);
            }
            if (value instanceof LocalDate) {
                return LegacyTimeKit.toDate((LocalDate) value);
            }
            if (value instanceof LocalTime) {
                return LegacyTimeKit.toDate((LocalTime) value);
            }
            if (value instanceof OffsetDateTime) {
                return LegacyTimeKit.toDate(((OffsetDateTime) value).toLocalDateTime());
            }
            if (value instanceof ZonedDateTime) {
                return LegacyTimeKit.toDate(((ZonedDateTime) value).toLocalDateTime());
            }
        }
        if (value instanceof String) {
            String s = (String) value;
            if (s.length() <= dateLen) {
                return LegacyTimeKit.parse(s, datePattern);
            }
            if (s.length() > dateTimeWithoutSecondLen) {
                return LegacyTimeKit.parse(s, dateTimePattern);
            }
            int indexOfColon = s.indexOf(':');
            if (indexOfColon == -1) {
                // 长度 11–16 且无冒号：落到兜底 cast（旧实现即如此，实测抛 ClassCastException）
                return (Date) value;
            }
            if (indexOfColon != s.lastIndexOf(':')) {
                return LegacyTimeKit.parse(s, dateTimePattern);
            }
            return LegacyTimeKit.parse(s, dateTimeWithoutSecondPattern);
        }
        return (Date) value;
    }

    /**
     * 取 LocalDateTime：LocalDateTime 直返，Date/LocalDate/LocalTime 按类型转换，字符串按长度分派，其余抛 ClassCastException
     */
    public static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Date) {
            return LegacyTimeKit.toLocalDateTime((Date) value);
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay();
        }
        if (value instanceof LocalTime) {
            // 旧实现取"当日"，非确定性行为照旧保留
            return LocalDateTime.of(LocalDate.now(), (LocalTime) value);
        }
        if (value instanceof String) {
            String s = (String) value;
            if (s.length() <= dateLen) {
                return LegacyTimeKit.parseLocalDateTime(s, datePattern);
            }
            if (s.length() > dateTimeWithoutSecondLen) {
                return LegacyTimeKit.parseLocalDateTime(s, dateTimePattern);
            }
            int indexOfColon = s.indexOf(':');
            if (indexOfColon == -1) {
                return (LocalDateTime) value;
            }
            if (indexOfColon != s.lastIndexOf(':')) {
                return LegacyTimeKit.parseLocalDateTime(s, dateTimePattern);
            }
            return LegacyTimeKit.parseLocalDateTime(s, dateTimeWithoutSecondPattern);
        }
        return (LocalDateTime) value;
    }
}
