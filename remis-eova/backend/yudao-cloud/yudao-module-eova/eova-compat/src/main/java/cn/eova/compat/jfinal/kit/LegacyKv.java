/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.kit.Kv} 的等价 port（阶段 1 `S-JSON` 切片）。
 *
 * <p><b>为什么必须 port：</b>新栈的 {@code com.jfinal:enjoy:5.3.0} <b>确实</b>提供
 * {@code com.jfinal.kit.Kv}（§2.1 据此判断"无需引入 jfinal jar"），但两版 API
 * <b>并不相同</b>，属 R37/R40 所述的"同名类跨制品漂移"：
 * <ul>
 *   <li>jfinal 5.2.6 有 {@code toJson()}；<b>enjoy 5.3.0 将其移除</b>；
 *       EOVA 的 {@code BaseSharedMethod:27}、{@code SseKit:127}、
 *       {@code MenuController:100}、{@code EovaOption:101} 都在调 {@code kv.toJson()} ——
 *       直接用 enjoy 版会<b>编译期报错</b>。</li>
 *   <li>enjoy 5.3.0 额外覆写了 {@code remove(Object)} 并协变返回 {@code Kv}
 *       （5.2.6 无此覆写，继承 {@code HashMap.remove}）。</li>
 * </ul>
 * 故 {@code Kv} 必须固化，不得依赖 classpath 解析结果。
 *
 * <p>ported from: com.jfinal.kit.Kv（第三方制品，非 EOVA 源码）
 * <br>source artifact: com.jfinal:jfinal:5.2.6
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>覆盖度：5.2.6 全部公开方法（完整 port，无裁剪）
 *
 * <p><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>{@code getXxx} 全部委托 {@link LegacyTypeKit}（即 5.2.6 的 {@code TypeKit}），
 *       故异常类型与消息与旧实现逐字一致。</li>
 *   <li>{@code isTrue(key)} 与 {@code isFalse(key)} 对 {@code null}/缺键<b>均返回 false</b>
 *       （两者都先判空再调 {@code TypeKit.toBoolean}），是<b>对称</b>的。
 *       但非 null 且无法转布尔的值（如 {@code "abc"}）会抛 {@code ClassCastException}。</li>
 *   <li>{@code keep(String...)} 是<b>原地修改</b>：先把命中的键收进临时 Kv，
 *       再 {@code clear()} + {@code putAll()}；传入 {@code null} 或空数组等价于
 *       {@code clear()}。返回 {@code this}，不是新对象。</li>
 *   <li>{@code toMap()} 返回 {@code this} 本身（同一实例），不是副本。</li>
 *   <li>{@code equals} 仅在对方也是 {@code Kv} 时才可能为真
 *       （{@code o instanceof Kv && super.equals(o)}）—— 与普通 {@code Map} 比较恒 false。</li>
 *   <li>底层是 {@code HashMap}，故<b>键序为哈希序</b>（非插入序）。</li>
 *   <li>{@code toJson()} 走 {@link LegacyJsonKit}（等价于旧的
 *       {@code Json.getJson().toJson(this)}）。</li>
 * </ol>
 */
@SuppressWarnings("rawtypes")
public class LegacyKv extends HashMap {

    private static final long serialVersionUID = -3001700934716894073L;

    public LegacyKv() {
    }

    /**
     * 建含一个键值对的 Kv
     */
    public static LegacyKv of(Object key, Object value) {
        return new LegacyKv().set(key, value);
    }

    /**
     * 建含一个键值对的 Kv（{@code of} 的别名）
     */
    public static LegacyKv by(Object key, Object value) {
        return new LegacyKv().set(key, value);
    }

    /**
     * 建空 Kv
     */
    public static LegacyKv create() {
        return new LegacyKv();
    }

    /**
     * 设置键值对并返回 this
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public LegacyKv set(Object key, Object value) {
        // 走原始类型 put：旧实现基于原始 HashMap，允许非 String 键原样存入；
        // 若此处改为 (String) 强转，非 String 键会抛 ClassCastException 而旧实现不会
        ((Map) this).put(key, value);
        return this;
    }

    /**
     * 值非空白串时才设置
     */
    public LegacyKv setIfNotBlank(Object key, String value) {
        if (isNotBlank(value)) {
            set(key, value);
        }
        return this;
    }

    /**
     * 值非 null 时才设置
     */
    public LegacyKv setIfNotNull(Object key, Object value) {
        if (value != null) {
            set(key, value);
        }
        return this;
    }

    /**
     * 批量并入一个 Map
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public LegacyKv set(Map map) {
        ((Map) this).putAll(map);
        return this;
    }

    /**
     * 批量并入另一个 Kv
     */
    public LegacyKv set(LegacyKv kv) {
        ((Map) this).putAll((Map) kv);
        return this;
    }

    /**
     * 删除键并返回 this
     */
    public LegacyKv delete(Object key) {
        super.remove(key);
        return this;
    }

    /**
     * 取值为指定类型；缺键或值为 null 时返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getAs(Object key) {
        return (T) get(key);
    }

    /**
     * 取值为指定类型；缺键或值为 null 时返回默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAs(Object key, T defaultValue) {
        Object value = get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 取值经函数转换；缺键或值为 null 时返回 null
     */
    public <T> T getAs(Object key, Function<Object, T> func) {
        Object value = get(key);
        return value != null ? func.apply(value) : null;
    }

    /**
     * 取值经函数转换；缺键或值为 null 时返回默认值
     */
    public <T> T getAs(Object key, T defaultValue, Function<Object, T> func) {
        Object value = get(key);
        return value != null ? func.apply(value) : defaultValue;
    }

    /**
     * 取字符串；null 值返回 null
     */
    public String getStr(Object key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 取 Integer；委托 5.2.6 TypeKit 语义
     */
    public Integer getInt(Object key) {
        return LegacyTypeKit.toInt(get(key));
    }

    /**
     * 取 Long；委托 5.2.6 TypeKit 语义
     */
    public Long getLong(Object key) {
        return LegacyTypeKit.toLong(get(key));
    }

    /**
     * 取 BigDecimal；委托 5.2.6 TypeKit 语义
     */
    public BigDecimal getBigDecimal(Object key) {
        return LegacyTypeKit.toBigDecimal(get(key));
    }

    /**
     * 取 Double；委托 5.2.6 TypeKit 语义
     */
    public Double getDouble(Object key) {
        return LegacyTypeKit.toDouble(get(key));
    }

    /**
     * 取 Float；委托 5.2.6 TypeKit 语义
     */
    public Float getFloat(Object key) {
        return LegacyTypeKit.toFloat(get(key));
    }

    /**
     * 取 Number；委托 5.2.6 TypeKit 语义
     */
    public Number getNumber(Object key) {
        return LegacyTypeKit.toNumber(get(key));
    }

    /**
     * 取 Boolean；委托 5.2.6 TypeKit 语义（对 Integer 抛 ClassCastException）
     */
    public Boolean getBoolean(Object key) {
        return LegacyTypeKit.toBoolean(get(key));
    }

    /**
     * 取 Date；委托 5.2.6 TypeKit 语义
     */
    public Date getDate(Object key) {
        return LegacyTypeKit.toDate(get(key));
    }

    /**
     * 取 LocalDateTime；委托 5.2.6 TypeKit 语义
     */
    public LocalDateTime getLocalDateTime(Object key) {
        return LegacyTypeKit.toLocalDateTime(get(key));
    }

    /**
     * 取字符串；null 值返回默认值
     */
    public String getStr(Object key, String defaultValue) {
        Object value = get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 取 Integer；null 值返回默认值
     */
    public Integer getInt(Object key, Integer defaultValue) {
        Object value = get(key);
        return value != null ? LegacyTypeKit.toInt(value) : defaultValue;
    }

    /**
     * 取 Long；null 值返回默认值
     */
    public Long getLong(Object key, Long defaultValue) {
        Object value = get(key);
        return value != null ? LegacyTypeKit.toLong(value) : defaultValue;
    }

    /**
     * 取 BigDecimal；null 值返回默认值
     */
    public BigDecimal getBigDecimal(Object key, BigDecimal defaultValue) {
        Object value = get(key);
        return value != null ? LegacyTypeKit.toBigDecimal(value) : defaultValue;
    }

    /**
     * 取 Double；null 值返回默认值
     */
    public Double getDouble(Object key, Double defaultValue) {
        Object value = get(key);
        return value != null ? LegacyTypeKit.toDouble(value) : defaultValue;
    }

    /**
     * 取 Float；null 值返回默认值
     */
    public Float getFloat(Object key, Float defaultValue) {
        Object value = get(key);
        return value != null ? LegacyTypeKit.toFloat(value) : defaultValue;
    }

    /**
     * 取 Number；null 值返回默认值
     */
    public Number getNumber(Object key, Number defaultValue) {
        Object value = get(key);
        return value != null ? LegacyTypeKit.toNumber(value) : defaultValue;
    }

    /**
     * 取 Boolean；null 值返回默认值
     */
    public Boolean getBoolean(Object key, Boolean defaultValue) {
        Object value = get(key);
        return value != null ? LegacyTypeKit.toBoolean(value) : defaultValue;
    }

    /**
     * 取 Date；null 值返回默认值
     */
    public Date getDate(Object key, Date defaultValue) {
        Object value = get(key);
        return value != null ? LegacyTypeKit.toDate(value) : defaultValue;
    }

    /**
     * 取 LocalDateTime；null 值返回默认值
     */
    public LocalDateTime getLocalDateTime(Object key, LocalDateTime defaultValue) {
        Object value = get(key);
        return value != null ? LegacyTypeKit.toLocalDateTime(value) : defaultValue;
    }

    /**
     * 值是否非 null
     */
    public boolean notNull(Object key) {
        return get(key) != null;
    }

    /**
     * 值是否为 null
     */
    public boolean isNull(Object key) {
        return get(key) == null;
    }

    /**
     * 字符串值是否非空白（null 视为空白）
     */
    public boolean notBlank(Object key) {
        return isNotBlank(getStr(key));
    }

    /**
     * 字符串值是否为空白（null 视为空白）
     */
    public boolean isBlank(Object key) {
        return isBlankStr(getStr(key));
    }

    /**
     * 值是否为真；值缺失或为 null 时返回 false（不抛异常）。
     *
     * <p>注意：非 null 但无法转成布尔的值（如 {@code "abc"}）会由
     * {@link LegacyTypeKit#toBoolean} 抛 {@code ClassCastException} —— 旧实现如此。
     */
    public boolean isTrue(Object key) {
        Object value = get(key);
        return value != null && LegacyTypeKit.toBoolean(value).booleanValue();
    }

    /**
     * 值是否为假；值缺失或为 null 时返回 false。与 {@code isTrue} <b>对称</b>。
     */
    public boolean isFalse(Object key) {
        Object value = get(key);
        return value != null && !LegacyTypeKit.toBoolean(value).booleanValue();
    }

    /**
     * 序列化为 JSON（等价于旧的 {@code Json.getJson().toJson(this)}）
     */
    public String toJson() {
        return LegacyJsonKit.toJson(this);
    }

    /**
     * 仅当对方也是 Kv 时才比较内容（与普通 Map 比较恒 false）
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof LegacyKv && super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * 只保留给定键（<b>原地修改</b>），传 null 或空数组等价于 clear
     */
    public LegacyKv keep(String... keys) {
        if (keys != null) {
            LegacyKv kv = create();
            for (String key : keys) {
                if (containsKey(key)) {
                    ((Map) kv).put(key, get(key));
                }
            }
            clear();
            ((Map) this).putAll((Map) kv);
        } else {
            clear();
        }
        return this;
    }

    /**
     * 以 Map 形态返回（<b>就是 this 本身</b>，不是副本）
     */
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> toMap() {
        return (Map<K, V>) this;
    }

    /** 非空白串判定（对应 5.2.6 的 StrKit.notBlank：非 null 且含非空白字符） */
    private static boolean isNotBlank(String s) {
        return !isBlankStr(s);
    }

    /** 空白串判定（对应 5.2.6 的 StrKit.isBlank：null 视为空白；字符 <= ' ' 视为空白） */
    private static boolean isBlankStr(String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > ' ') {
                return false;
            }
        }
        return true;
    }
}
