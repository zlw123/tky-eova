/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.kit.JsonKit} + {@code com.jfinal.json.JFinalJsonKit}
 * 的等价 port（阶段 1 `S-JSON` 切片）。
 *
 * <p><b>为什么必须 port（R40）：</b>新栈用的 {@code com.jfinal:enjoy:5.3.0} <b>整个不提供</b>
 * {@code com.jfinal.kit.JsonKit}（EOVA 调用 26 处）。更要紧的是序列化<b>不是 fastjson</b>：
 * <pre>
 *   JsonKit.toJson(Object)
 *     -&gt; com.jfinal.json.Json.getJson()          // 静态初始化即 new JFinalJsonFactory()
 *     -&gt; MixedJson / JFinalJson
 *     -&gt; JFinalJsonKit                            // 手写序列化器，30+ 类型处理器
 * </pre>
 * 按 R5，序列化行为直接决定 {@code state,msg,data} envelope，故必须逐处理器等价。
 *
 * <p>ported from: com.jfinal.kit.JsonKit + com.jfinal.json.JFinalJsonKit（第三方制品）
 * <br>source artifact: com.jfinal:jfinal:5.2.6
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08（锁定上述制品版本）
 *
 * <p><b>类型分派顺序（照抄 JFinalJsonKit 构造器中的注册顺序，顺序即语义）：</b>
 * {@code String} → {@code Number}(Integer/Long/Double/Float/其它) → {@code Boolean} →
 * {@code Character} → {@code Enum} → {@code Date}(Timestamp/Time/其它) →
 * {@code Temporal}(LocalDateTime/LocalDate/LocalTime) → 记录容器 → {@code Map} →
 * {@code Collection} → 数组 → {@code Enumeration} → {@code Iterator} → {@code Iterable} → 兜底。
 * <b>注意 {@code java.sql.Time}/{@code Timestamp} 是按精确类注册的，先于 {@code java.util.Date} 命中</b>，
 * 故它们不会走日期时间格式；而 {@code java.sql.Date} 未注册，向上命中 {@code Date}，
 * 走完整 {@code yyyy-MM-dd HH:mm:ss}（实测输出 {@code "2019-09-20 00:00:00"}）。
 *
 * <p><b>本单元为部分 port，明确未包含：</b>
 * <ol>
 *   <li>{@code parse(String, Class)} —— jfinal 反序列化入口。EOVA 仅在
 *       {@code EovaOption:83/97} 使用 2 处（均解析为 {@code Kv}）。
 *       本类<b>刻意不声明该方法</b>：声明了却在内部留空才是 stub；
 *       不声明会让 port `EovaOption` 时编译期报错，从而强制补做。</li>
 *   <li>{@code toJson(Object, int maxBufferSize)} —— EOVA 无调用方，同样不声明。</li>
 *   <li>{@code JFinalJsonKit} 的 {@code treatModelAsBean} / {@code skipNullValueField} /
 *       {@code modelAndRecordFieldNameConverter} 三个可配置开关 —— EOVA 未配置，按默认行为实现。</li>
 * </ol>
 *
 * <p><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li><b>转义集合是特定的三段</b>：{@code c <= 31} ∪ {@code [127,159]} ∪ {@code [8192,8447]}，
 *       其中 7 个字符用短形式（{@code \" \\ \b \f \n \r \t}），其余走 U+XXXX 转义（十六进制<b>大写</b>、
 *       零填充到 4 位）。<b>中文不被转义</b>（CJK 起点 19968 不在三段内），
 *       但 U+2000–U+20FF 会被转义 —— 空转义集合会让输出<b>变成非法 JSON</b>（裸控制字符）。</li>
 *   <li>日期时间一律用 {@code yyyy-MM-dd HH:mm:ss}（{@code Json.defaultDatePattern} 默认值），
 *       而<b>不是</b> {@code Date.toString()}；{@code LocalDate} 用 {@code yyyy-MM-dd}，
 *       {@code LocalTime}/{@code java.sql.Time} 用 {@code HH:mm:ss}。</li>
 *   <li>嵌套 {@code Map}/{@code Collection}/数组按<b>结构</b>展开，不倒成字符串。</li>
 *   <li>键序<b>不承诺</b>（§3.8 第 3 条：SP6 实测旧实现 toJson 键序非插入序，故键序非契约）。</li>
 *   <li>{@code null} 值序列化为 {@code null}，不跳过。</li>
 * </ol>
 */
public final class LegacyJsonKit {

    /** 日期时间格式（对应 {@code Json.defaultDatePattern} 的默认值） */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 纯日期格式（{@code LocalDate} 专用） */
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    /** 纯时间格式（{@code LocalTime} 与 {@code java.sql.Time} 专用） */
    private static final String TIME_PATTERN = "HH:mm:ss";

    /** 日期格式化器（SimpleDateFormat 非线程安全，按线程持有） */
    private static final ThreadLocal<SimpleDateFormat> DATE_TIME_FMT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat(DATE_TIME_PATTERN));

    /** 纯时间格式化器 */
    private static final ThreadLocal<SimpleDateFormat> TIME_FMT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat(TIME_PATTERN));

    /** LocalDateTime 格式化器（不可变、线程安全） */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    private LegacyJsonKit() {
    }

    /**
     * 对象 → JSON 字符串（与 jfinal 5.2.6 {@code JsonKit.toJson(Object)} 等价）
     */
    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    /**
     * JSON 字符串 → 对象（等价于 jfinal {@code JsonKit.parse(String, Class)}）。
     *
     * <p><b>为什么用 fastjson 是忠实的、而非"降级"：</b>
     * 实测 jfinal 的 {@code Json.getJson()} 返回 {@code MixedJson}，而
     * <b>{@code MixedJson.parse} 与 {@code MixedJson.toJson} 走的是两条不同路径</b>：
     * <ul>
     *   <li>{@code toJson} → <b>JFinalJson</b>（手写序列化器，本类已逐处理器固化）</li>
     *   <li>{@code parse} → <b>{@code FastJson.parse}</b> → {@code JSON.parseObject(json, type)}</li>
     * </ul>
     * 故反序列化侧直接用 fastjson 与旧栈<b>同构</b>。这条不对称（写用手写器、读用 fastjson）
     * 是旧实现的既有形态，不应"统一"成同一套。
     *
     * <p><b>本方法是刻意延后补上的：</b>{@code S-JSON} 切片首版<b>不声明</b>它，
     * 以便 port 消费者时在<b>编译期</b>报错、强制补做，而不是运行期静默失败。
     * 消费者（{@code Menu.getMenuConfig}）现已到位，故补齐。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 反序列化结果
     */
    public static <T> T parse(String json, Class<T> type) {
        return com.alibaba.fastjson.JSON.parseObject(json, type);
    }

    /** 按 JFinalJsonKit 的注册顺序分派并写出 */
    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof String || value instanceof Character) {
            appendString(sb, value.toString());
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            // Integer/Long/Double/Float/BigInteger/BigDecimal/Short/Byte 与 Boolean 均原样输出
            sb.append(value);
            return;
        }
        if (value instanceof Enum) {
            appendString(sb, ((Enum<?>) value).name());
            return;
        }
        // 精确类优先：Time / Timestamp 先于 Date 命中
        if (value instanceof Time) {
            sb.append('"').append(TIME_FMT.get().format((Time) value)).append('"');
            return;
        }
        if (value instanceof Timestamp) {
            sb.append('"').append(DATE_TIME_FMT.get().format((Timestamp) value)).append('"');
            return;
        }
        if (value instanceof Date) {
            // java.sql.Date 也落在此分支（未单独注册），故输出带 00:00:00
            sb.append('"').append(DATE_TIME_FMT.get().format((Date) value)).append('"');
            return;
        }
        if (value instanceof LocalDateTime) {
            // 直接按 pattern 格式化，不经 Date 中转（避免系统时区引入偏差）
            sb.append('"').append(((LocalDateTime) value).format(DATE_TIME_FORMATTER)).append('"');
            return;
        }
        if (value instanceof LocalDate) {
            sb.append('"').append(((LocalDate) value)).append('"');
            return;
        }
        if (value instanceof LocalTime) {
            sb.append('"').append(((LocalTime) value)).append('"');
            return;
        }
        if (value instanceof Temporal) {
            // 其它 Temporal 类型：jfinal 未给专门处理器，与兜底一致
            appendString(sb, value.toString());
            return;
        }
        if (value instanceof JsonColumns) {
            writeMap(sb, ((JsonColumns) value).jsonColumns());
            return;
        }
        if (value instanceof Map) {
            writeMap(sb, (Map<?, ?>) value);
            return;
        }
        if (value instanceof Collection) {
            writeIterable(sb, ((Collection<?>) value).iterator());
            return;
        }
        if (value instanceof Object[]) {
            writeIterable(sb, java.util.Arrays.asList((Object[]) value).iterator());
            return;
        }
        if (value instanceof Enumeration) {
            writeIterable(sb, new Iterator<Object>() {
                @Override
                public boolean hasNext() {
                    return ((Enumeration<?>) value).hasMoreElements();
                }

                @Override
                public Object next() {
                    return ((Enumeration<?>) value).nextElement();
                }
            });
            return;
        }
        if (value instanceof Iterator) {
            writeIterable(sb, (Iterator<?>) value);
            return;
        }
        if (value instanceof Iterable) {
            writeIterable(sb, ((Iterable<?>) value).iterator());
            return;
        }
        // 兜底：按字符串写出（JFinalJsonKit$UnknownToJson）
        appendString(sb, value.toString());
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            write(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeIterable(StringBuilder sb, Iterator<?> it) {
        sb.append('[');
        boolean first = true;
        while (it.hasNext()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            write(sb, it.next());
        }
        sb.append(']');
    }

    /** 写出带引号并转义的字符串 */
    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        escape(s, sb);
        sb.append('"');
    }

    /**
     * 字符串转义：与 {@code JFinalJsonKit.escape} 逐字符等价。
     *
     * <p>转义集合为三段并集，7 个常用字符用短形式，其余用 U+XXXX 转义。
     * 注意 {@code >= 127} 的字符并非一律转义 —— 只有 {@code [127,159]} 与 {@code [8192,8447]}
     * 两段才转义，故中文（CJK）原样输出。
     */
    static void escape(String s, StringBuilder sb) {
        if (s == null) {
            return;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c <= 31 || (c >= 127 && c <= 159) || (c >= 8192 && c <= 8447)) {
                        String hex = Integer.toHexString(c).toUpperCase();
                        sb.append("\\u");
                        for (int k = hex.length(); k < 4; k++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
    }

    /**
     * 可被 JSON 序列化器按"记录容器"处理的类型（对应 jfinal 的 {@code RecordToJson}）。
     *
     * <p>存在理由：{@code eova-compat} 不能反向依赖 {@code eova-db-adapter}，
     * 故不能直接引用 {@code EovaRecord}。由记录容器实现本接口，
     * 使<b>嵌套在 Kv/Map 里</b>的记录也能按结构展开，而非被兜底成字符串。
     */
    public interface JsonColumns {

        /**
         * 返回参与序列化的列（键值对）
         */
        Map<String, Object> jsonColumns();
    }
}
