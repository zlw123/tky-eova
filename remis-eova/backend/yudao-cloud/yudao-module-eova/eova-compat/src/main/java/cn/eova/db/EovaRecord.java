/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import cn.eova.compat.jfinal.kit.LegacyTypeKit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * EOVA 记录容器 —— JFinal {@code Record} 的等价物（阶段 1 S03 单元）。
 *
 * <p><b>等价性依据：</b>{@code docs/.local/ledger/record-semantics.golden.jsonl}
 * （SP6 探针从 JFinal 5.2.6 实测捕获 128 条证据）。本实现逐条对齐，不靠阅读源码推断。
 *
 * <p><b>关键契约（实测）：</b>
 * <ol>
 *   <li><b>键全程小写</b>：{@code set("MyKey", v)} 后键为 {@code mykey}；
 *       {@code get} 对任意大小写均可命中（对应 EOVA 默认
 *       {@code db.islowercase=true} -> {@code CaseInsensitiveContainerFactory(true)}）。</li>
 *   <li><b>类型转换委托 {@link LegacyTypeKit}</b>：即 jfinal 5.2.6 的
 *       {@code com.jfinal.kit.TypeKit} 语义，已固化为项目自有类。
 *       <b>刻意不直接调用环境的 {@code com.jfinal.kit.TypeKit}</b> —— 旧栈用
 *       jfinal 5.2.6 内嵌版，新栈用 enjoy 5.3.0，同名类语义有实测差异，
 *       生效者取决于 classpath 顺序，不能作为等价性基准。
 *       （{@code getInt} 对非数字串抛 {@code NumberFormatException}、
 *       对 null/缺列返回 {@code null}；{@code getBoolean} 对 Integer 抛 {@code ClassCastException}）。</li>
 *   <li><b>不保证键序</b>：SP6 实测 JFinal 的 {@code toJson} 键序亦非插入序，
 *       故本实现【不承诺】任何键序，调用方不得依赖。</li>
 * </ol>
 *
 * <p>ported from: com.jfinal.plugin.activerecord.Record（语义等价重实现，非逐行 port）
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 */
public class EovaRecord implements LegacyJsonKit.JsonColumns, java.io.Serializable {

    /**
     * 序列化版本号。
     *
     * <p><b>为什么必须 Serializable：</b>实测 ehcache 的 {@code service}（{@code BaseCache.SER}）
     * 与 {@code sys} 等 cache 开启了 {@code copyOnWrite}/{@code copyOnRead}，
     * EhCache 对这类 cache 的取值要求<b>必须可序列化</b>（否则抛
     * {@code CacheException: ... a Store will only accept Serializable values}）。
     * EOVA 的 {@code BaseModel.queryByCache} 正是往 {@code service} 缓存里放
     * {@code List<Model>}，故模型与其属性容器都必须可序列化 ——
     * 旧栈的 {@code Model}/{@code Record} 同样实现 {@code Serializable}。
     */
    private static final long serialVersionUID = -4217436621260204480L;

    /** 列数据；键统一小写（对应 CaseInsensitiveContainerFactory(true)） */
    private final Map<String, Object> columns = new LinkedHashMap<>();

    /** 已修改字段集（update 只提交该集合） */
    private final Set<String> modifyFlag = new LinkedHashSet<>();

    /** 归一化键：与旧实现一致，一律小写 */
    private static String norm(String column) {
        return column == null ? null : column.toLowerCase();
    }

    /**
     * 设置列值；键名会被归一化为小写，并记入 modifyFlag
     */
    public EovaRecord set(String column, Object value) {
        String k = norm(column);
        columns.put(k, value);
        modifyFlag.add(k);
        return this;
    }

    /**
     * 设置列值但<b>不记入 modifyFlag</b>（对应 jfinal {@code Model.put(k, v)} 语义）。
     *
     * <p>与 {@link #set(String, Object)} 的区别是契约性的，不是实现细节：
     * 实测 jfinal 5.2.6 中 {@code Model.set} 会记 modifyFlag（决定 update 提交哪些列），
     * 而 {@code Model.put} 只做 attrs.put。此外 {@code Model.set} 还会先在
     * {@code Table} 上校验列是否存在，`put` 不校验 —— 该校验在 {@code EovaModel} 一侧完成。
     *
     * @param column 列名（键会归一化为小写）
     * @param value  值
     */
    public EovaRecord put(String column, Object value) {
        columns.put(norm(column), value);
        return this;
    }

    /**
     * 取原始值；缺列返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String column) {
        return (T) columns.get(norm(column));
    }

    /**
     * 取原始值；缺列时返回默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String column, T defaultValue) {
        Object v = columns.get(norm(column));
        return v == null ? defaultValue : (T) v;
    }

    /**
     * 取对象值；与 {@link #get(String)} 等价（保留旧 API 形态）
     */
    public Object getObject(String column) {
        return columns.get(norm(column));
    }

    /**
     * 取对象值；缺列返回默认值
     */
    public Object getObject(String column, Object defaultValue) {
        Object v = columns.get(norm(column));
        return v == null ? defaultValue : v;
    }

    /**
     * 取字符串：非 null 值经 {@code toString()} 转换，null 返回 null
     */
    public String getStr(String column) {
        Object v = columns.get(norm(column));
        return v != null ? v.toString() : null;
    }

    /**
     * 取 Integer：委托 LegacyTypeKit（非数字串抛 NumberFormatException，null 返回 null）
     */
    public Integer getInt(String column) {
        return LegacyTypeKit.toInt(columns.get(norm(column)));
    }

    /**
     * 取 Long：委托 LegacyTypeKit
     */
    public Long getLong(String column) {
        return LegacyTypeKit.toLong(columns.get(norm(column)));
    }

    /**
     * 取 Double：委托 LegacyTypeKit
     */
    public Double getDouble(String column) {
        return LegacyTypeKit.toDouble(columns.get(norm(column)));
    }

    /**
     * 取 Float：委托 LegacyTypeKit
     */
    public Float getFloat(String column) {
        return LegacyTypeKit.toFloat(columns.get(norm(column)));
    }

    /**
     * 取 BigDecimal：委托 LegacyTypeKit
     */
    public BigDecimal getBigDecimal(String column) {
        return LegacyTypeKit.toBigDecimal(columns.get(norm(column)));
    }

    /**
     * 取 BigInteger：按旧实现的分支阶梯转换 ——
     * BigInteger 直返 / BigDecimal.toBigInteger / Number.longValue / String 走
     * {@code new BigInteger(str)} / 其余裸 cast。
     *
     * <p>注意：<b>不得</b>用 {@code LegacyTypeKit.toBigDecimal(...).toBigInteger()} 代替 ——
     * 后者对非数字串抛 {@code NumberFormatException: Character 这 is neither ...}，
     * 而旧实现对 {@code "这个"} 抛 {@code NumberFormatException: For input string: "这个"}。
     */
    public BigInteger getBigInteger(String column) {
        Object v = columns.get(norm(column));
        if (v instanceof BigInteger) {
            return (BigInteger) v;
        }
        if (v instanceof BigDecimal) {
            return ((BigDecimal) v).toBigInteger();
        }
        if (v instanceof Number) {
            return BigInteger.valueOf(((Number) v).longValue());
        }
        if (v instanceof String) {
            return new BigInteger((String) v);
        }
        return (BigInteger) v;
    }

    /**
     * 取 Boolean：委托 LegacyTypeKit（对 Integer 列抛 ClassCastException，与旧实现一致）
     */
    public Boolean getBoolean(String column) {
        return LegacyTypeKit.toBoolean(columns.get(norm(column)));
    }

    /**
     * 取 Number：委托 LegacyTypeKit
     */
    public Number getNumber(String column) {
        return LegacyTypeKit.toNumber(columns.get(norm(column)));
    }

    /**
     * 取 Date：委托 LegacyTypeKit（对不可解析字符串抛 RuntimeException 包装 ParseException）
     */
    public java.util.Date getDate(String column) {
        return LegacyTypeKit.toDate(columns.get(norm(column)));
    }

    /**
     * 取 java.sql.Time：<b>裸 cast</b>，不做任何转换（旧实现如此）。null 值 cast 后仍为 null。
     *
     * <p>注意：<b>不得</b>手写 {@code throw new ClassCastException(...)} 代替裸 cast ——
     * JVM 生成的 cast 异常消息（含 "is in module java.base of loader 'bootstrap'" 措辞）
     * 与手写消息不同，属对外可观测差异（金标已捕获该差异）。
     */
    public Time getTime(String column) {
        return (Time) columns.get(norm(column));
    }

    /**
     * 取 LocalDateTime：委托 LegacyTypeKit
     */
    public LocalDateTime getLocalDateTime(String column) {
        return LegacyTypeKit.toLocalDateTime(columns.get(norm(column)));
    }

    /**
     * 取全部列（返回内部视图；键为小写）
     */
    public Map<String, Object> getColumns() {
        return columns;
    }

    /**
     * 用给定 Map 批量设置列（键会被归一化）
     */
    public EovaRecord setColumns(Map<String, Object> attrs) {
        if (attrs != null) {
            attrs.forEach(this::set);
        }
        return this;
    }

    /**
     * 用另一个 Record 的列批量设置（对应 jfinal {@code Record.setColumns(Record)}）。
     *
     * <p>旧字节码：{@code return setColumns(record.getColumns());} —— 一条委托，无别的动作。</p>
     *
     * @param record 来源
     * @return 本对象
     */
    public EovaRecord setColumns(EovaRecord record) {
        return setColumns(record.getColumns());
    }

    /**
     * 用 Model 的属性批量设置（对应 jfinal {@code Record.setColumns(Model)}）。
     *
     * <p><b>本重载是第 63 轮由真实 port 逼出来的：</b>port {@code WidgetUtil} 时
     * {@code new Record().setColumns(model)} 编译失败，才发现本类漏了这个重载。
     * 旧字节码：{@code return setColumns(model._getAttrs());} ——
     * {@code Model._getAttrs()} 是 {@code protected}，jfinal 能调是因为二者同包；
     * 本实现的 {@code EovaRecord}/{@code EovaModel} 同在 {@code cn.eova.db}，同样可访问，
     * 故委托关系原样保留。</p>
     *
     * @param model 来源模型
     * @return 本对象
     */
    public EovaRecord setColumns(EovaModel<?> model) {
        return setColumns(model._getAttrs());
    }

    /**
     * 取列名数组（小写）。
     *
     * <p><b>返回类型必须与 jfinal 一致：{@code String[]}，不是 {@code Set<String>}。</b>
     * 旧字节码（jfinal 5.2.6 {@code Record.getColumnNames}）为：
     * <pre>
     * Set&lt;String&gt; set = getColumns().keySet();
     * return set.toArray(new String[set.size()]);
     * </pre>
     * 故顺序即 {@code getColumns()} 的键迭代顺序（{@code columns} 为插入序 Map）。</p>
     *
     * <p><b>这是一处【已修正的接缝缺陷】：</b>本方法此前返回 {@code Set<String>}，
     * 与旧实现签名不符，会让 EOVA 自己的两处调用编译失败 ——
     * {@code DbUtil:388}（{@code String[] names = r.getColumnNames();}）与
     * {@code WidgetManager:831}（{@code String[] cols = e.getColumnNames();}）。
     * 该缺陷此前被 {@code RecordSemanticsGoldenTest} 的
     * {@code accessor|getColumnNames} <b>排除项掩盖</b>：那条排除的正当理由只是
     * <b>键序不稳定</b>，却把<b>返回类型</b>这个真实契约一并排除了
     * （与"过度排除 {@code json|toJson*} 导致 {@code toJson} 从未被比对"同类）。
     * 现排除项已收窄到只排除顺序、保留类型比对。</p>
     *
     * @return 列名数组
     */
    public String[] getColumnNames() {
        Set<String> set = columns.keySet();
        return set.toArray(new String[set.size()]);
    }

    /**
     * 取已修改字段集合
     */
    public Set<String> getModifyFlag() {
        return modifyFlag;
    }

    /**
     * 移除指定列
     */
    public EovaRecord remove(String column) {
        String k = norm(column);
        columns.remove(k);
        modifyFlag.remove(k);
        return this;
    }

    /**
     * 清空全部列与修改标记
     */
    public EovaRecord clear() {
        columns.clear();
        modifyFlag.clear();
        return this;
    }

    /**
     * 移除值为 null 的列（旧实现同名方法）
     */
    public EovaRecord removeNullValueColumns() {
        columns.entrySet().removeIf(e -> e.getValue() == null);
        return this;
    }

    /**
     * 只保留给定列
     */
    public EovaRecord keep(String column) {
        String k = norm(column);
        columns.keySet().retainAll(Set.of(k));
        modifyFlag.retainAll(Set.of(k));
        return this;
    }

    /**
     * 是否包含指定列
     */
    public boolean hasColumn(String column) {
        return columns.containsKey(norm(column));
    }

    /**
     * 列数
     */
    public int size() {
        return columns.size();
    }

    /**
     * 批量设置列（兼容 Collection 形态，供上层适配调用）
     */
    public EovaRecord setColumns(Collection<String> keys, Collection<Object> values) {
        if (keys != null && values != null) {
            var ki = keys.iterator();
            var vi = values.iterator();
            while (ki.hasNext() && vi.hasNext()) {
                set(ki.next(), vi.next());
            }
        }
        return this;
    }

    /**
     * 输出为 JSON 字符串；与旧实现等价 —— 旧实现是 {@code JsonKit.toJson(getColumns())}，
     * 即把<b>列 Map</b>交给序列化器，故此处同样委托 {@link LegacyJsonKit}。
     *
     * <p><b>注意：</b>本方法<b>不得</b>手写 JSON 拼接。实测手写版本在 12 处与旧实现不一致，
     * 其中多条是<b>真 bug</b>：控制字符不转义会产出<b>非法 JSON</b>（裸换行）、
     * 日期走 {@code toString()} 而非 {@code yyyy-MM-dd HH:mm:ss}、
     * 嵌套 Map/List 被倒成带引号的字符串而丢失结构。
     *
     * <p>键序不承诺（§3.8 第 3 条：SP6 实测旧实现键序非插入序，故键序非契约）。
     */
    public String toJson() {
        return LegacyJsonKit.toJson(columns);
    }

    /**
     * 供 JSON 序列化器按"记录容器"处理（对应 jfinal 的 {@code RecordToJson}）。
     *
     * <p>存在理由：使<b>嵌套在 Kv/Map 里</b>的记录也能按结构展开，
     * 而非被序列化器的兜底分支倒成字符串。
     */
    @Override
    public Map<String, Object> jsonColumns() {
        return columns;
    }

    @Override
    public String toString() {
        return columns.toString();
    }
}
