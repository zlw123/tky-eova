/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.core.converter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.core.converter.TypeConverter} 的等价接缝
 * （含 {@code Converters} 中的内建转换器）。
 *
 * <p>{@code ported from} {@code com.jfinal.core.converter.TypeConverter} +
 * {@code com.jfinal.core.converter.Converters}（jfinal 5.2.6）。</p>
 *
 * <p><b>唯一调用方：</b>EOVA 的 {@code cn.eova.core.type.Convertor#rule} ——
 * 即"按元字段声明的 Java 类型把字符串转成目标类型"，全树只有这一处。
 * 但其映射表（{@code MysqlConvertor.mapping()}）覆盖 11+ 种 Java 类型，
 * 故转换器集合必须完整。</p>
 *
 * <p><b>语义来源：不是猜的，是【旧制品的行为矩阵】</b> ——
 * 用反射对旧 {@code TypeConverter.me().convert(Class, String)} 跑了
 * 14 种类型 × 13 个输入值的矩阵，本类逐格对齐
 * （判据 {@code LegacyTypeConverterGoldenTest} 用同一矩阵做跨实现比对）。
 * 矩阵实测出的几条关键行为：</p>
 * <ul>
 *   <li><b>空串对所有类型都返回 {@code null}</b>（不是空值/0/异常）；</li>
 *   <li>{@code String}：<b>trim 都不做</b>，原样返回（{@code " 12 "} → {@code " 12 "}）；
 *       但空串仍返回 null；</li>
 *   <li>{@code Boolean}：{@code "1"}/{@code "true"}（忽略大小写）→ true；
 *       {@code "0"}/{@code "false"} → false；<b>其余一律抛 {@code RuntimeException}</b>；</li>
 *   <li>数值类：先 trim 再 {@code parseXxx}，失败抛 <b>{@code NumberFormatException}</b>（原样上抛）；</li>
 *   <li>{@code java.util.Date}/{@code java.sql.Timestamp}/{@code java.sql.Date}：
 *       按 <b>长格式在前</b>的顺序（{@code yyyy-MM-dd HH:mm:ss}、
 *       {@code yyyy-MM-dd'T'HH:mm:ss}、{@code yyyy-MM-dd}）解析，失败抛 {@code ParseException}。
 *       <b>顺序错不得</b>：{@code SimpleDateFormat} 默认宽松解析，先试短格式会把
 *       {@code "2024-01-02 03:04:05"} 的时间部分悄悄丢掉；</li>
 *   <li>{@code java.sql.Time}：<b>{@code java.sql.Time.valueOf(s)}</b> ——
 *       故失败抛的是 {@code IllegalArgumentException} 而非 {@code ParseException}
 *       （该差异由行为矩阵实测确定）；</li>
 *   <li>{@code byte[]}：{@code s.getBytes()}（空串已被前面的 null 分支拦下）。</li>
 * </ul>
 *
 * <p><b>为什么用矩阵而不是通读 17 个转换器的字节码：</b>行为矩阵是<b>可执行的 oracle</b>
 * —— 它覆盖值域边界，且判据能重复比对；逐个读字节码既慢又容易漏掉边界。</p>
 */
public final class LegacyTypeConverter {

    /** 单类型转换器 */
    @FunctionalInterface
    public interface IConverter<T> {
        /**
         * 转换。
         *
         * @param s 字符串值
         * @return 目标值
         * @throws Exception 转换失败
         */
        T convert(String s) throws Exception;
    }

    private static final LegacyTypeConverter ME = new LegacyTypeConverter();

    private final Map<Class<?>, IConverter<?>> converterMap = new HashMap<>();

    /**
     * 私有构造：注册内建转换器。
     */
    private LegacyTypeConverter() {
        StringConverter str = new StringConverter();
        converterMap.put(String.class, str);
        converterMap.put(Boolean.class, new BooleanConverter());
        converterMap.put(Integer.class, s -> Integer.parseInt(s.trim()));
        converterMap.put(Long.class, s -> Long.parseLong(s.trim()));
        converterMap.put(Short.class, s -> Short.parseShort(s.trim()));
        converterMap.put(Double.class, s -> Double.parseDouble(s.trim()));
        converterMap.put(Float.class, s -> Float.parseFloat(s.trim()));
        converterMap.put(BigDecimal.class, s -> new BigDecimal(s.trim()));
        converterMap.put(BigInteger.class, s -> new BigInteger(s.trim()));
        // byte[]：矩阵实测会 trim（" 12 " → 2 字节）
        converterMap.put(byte[].class, s -> s.trim().getBytes());

        DateConverter dateConverter = new DateConverter();
        converterMap.put(java.util.Date.class, dateConverter);
        converterMap.put(java.sql.Date.class, s -> new java.sql.Date(dateConverter.convert(s).getTime()));
        converterMap.put(java.sql.Timestamp.class,
                s -> new java.sql.Timestamp(dateConverter.convert(s).getTime()));
        // Time：旧实现的四条实测行为（矩阵 oracle）——
        //   "03:04:05" ✔ 接受；"3:4:5" ✘；"2024-01-02" ✘；"2024-01-02 03:04:05" ✘，
        // 且失败形态为 IllegalArgumentException（不是 ParseException）。
        // 注意 java.sql.Time.valueOf 只满足前两条中的第一条 —— 它【接受】"3:4:5"，
        // 故旧实现不是裸 valueOf。与四条观测一致的规则是【严格 HH:mm:ss 校验】：
        // 下面据此实现。该规则系【据矩阵推断】（非读字节码得出），
        // 由 LegacyTypeConverterGoldenTest 的矩阵继续看守 —— 若将来发现更多输入形态
        // 与旧实现不符，矩阵会报出。
        converterMap.put(java.sql.Time.class, s -> {
            String v = s.trim();
            if (!v.matches("\\d{2}:\\d{2}:\\d{2}")) {
                throw new IllegalArgumentException("Can not parse to Time type of value: " + v);
            }
            return java.sql.Time.valueOf(v);
        });

        LocalDateConverter localDateConverter = new LocalDateConverter();
        converterMap.put(LocalDate.class, localDateConverter);
        converterMap.put(LocalDateTime.class, new LocalDateTimeConverter(localDateConverter));
    }

    /**
     * 取单例。
     *
     * @return 单例
     */
    public static LegacyTypeConverter me() {
        return ME;
    }

    /**
     * 取转换器表。
     *
     * @return 表
     */
    public Map<Class<?>, IConverter<?>> getConverterMap() {
        return converterMap;
    }

    /**
     * 把字符串转成目标类型。
     *
     * <p><b>空串一律返回 {@code null}</b>（矩阵实测行为，且对全部类型成立）。</p>
     *
     * @param type 目标类型
     * @param s    字符串值
     * @param <T>  目标类型
     * @return 转换结果；{@code s} 为 null 或空串时返回 null
     * @throws ParseException 转换失败
     */
    @SuppressWarnings("unchecked")
    public <T> T convert(Class<T> type, String s) throws ParseException {
        if (s == null || s.isEmpty()) {
            return null;
        }
        IConverter<?> converter = converterMap.get(type);
        if (converter == null) {
            throw new IllegalArgumentException("Can not convert to type : " + type.getName());
        }
        try {
            return (T) converter.convert(s);
        } catch (ParseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** String：原样返回（不 trim） */
    private static final class StringConverter implements IConverter<String> {
        @Override
        public String convert(String s) {
            return s;
        }
    }

    /** Boolean：1/true（忽略大小写）→ true；0/false → false；其余 RuntimeException */
    private static final class BooleanConverter implements IConverter<Boolean> {
        @Override
        public Boolean convert(String s) {
            s = s.toLowerCase();
            if ("1".equals(s) || "true".equals(s)) {
                return Boolean.TRUE;
            }
            if ("0".equals(s) || "false".equals(s)) {
                return Boolean.FALSE;
            }
            throw new RuntimeException("Can not parse to Boolean type of value: " + s);
        }
    }

    /** 日期：依次尝试三种格式 */
    private static final class DateConverter implements IConverter<java.util.Date> {

        // 【顺序要紧】必须"长格式在前"：SimpleDateFormat 默认宽松解析，
        // 若先试 "yyyy-MM-dd"，则 "2024-01-02 03:04:05" 会被它吃掉、时间部分被丢弃
        // （矩阵实测：旧实现得到 03:04:05，我先前的顺序得到 00:00:00）。
        private static final String[] PATTERNS = {
                "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd"};

        @Override
        public java.util.Date convert(String s) throws ParseException {
            String v = s.trim();
            ParseException last = null;
            for (String p : PATTERNS) {
                try {
                    return new SimpleDateFormat(p).parse(v);
                } catch (ParseException e) {
                    last = e;
                }
            }
            throw last;
        }
    }

    /** LocalDate：yyyy-MM-dd；也接受 10 位以上的日期时间串前缀 */
    private static final class LocalDateConverter implements IConverter<LocalDate> {
        @Override
        public LocalDate convert(String s) {
            String v = s.trim();
            if (v.length() > 10 && (v.charAt(10) == ' ' || v.charAt(10) == 'T')) {
                v = v.substring(0, 10);
            }
            return LocalDate.parse(v);
        }
    }

    /** LocalDateTime：yyyy-MM-dd HH:mm:ss 或 ISO（T 分隔） */
    private static final class LocalDateTimeConverter implements IConverter<LocalDateTime> {

        private final LocalDateConverter dateConverter;

        /**
         * 构造。
         *
         * @param dateConverter 日期转换器
         */
        LocalDateTimeConverter(LocalDateConverter dateConverter) {
            this.dateConverter = dateConverter;
        }

        @Override
        public LocalDateTime convert(String s) {
            String v = s.trim();
            if (v.length() <= 10) {
                return dateConverter.convert(v).atStartOfDay();
            }
            return LocalDateTime.parse(v.replace(' ', 'T'));
        }
    }

}
