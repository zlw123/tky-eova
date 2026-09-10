/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import cn.eova.compat.cache.CacheService;
import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import cn.eova.compat.table.EovaTableMapping;
import cn.eova.compat.table.TableMetadata;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * ActiveRecord 模型等价物：替代 jfinal 5.2.6 的
 * {@code com.jfinal.plugin.activerecord.Model<M>}，供 EOVA 的
 * {@code BaseModel} 与 18 个模型类使用（阶段 1 `D-MODEL` 前置 2）。
 *
 * <p>ported from: com.jfinal.plugin.activerecord.Model + IRow（语义等价重实现）
 * <br>source artifact: com.jfinal:jfinal:5.2.6
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 *
 * <p><b>设计：属性容器复用已固化的 {@link EovaRecord}</b> ——
 * 旧 {@code Model} 的取值器与其 {@code Record} 同源（都走 TypeKit），
 * 而 {@code EovaRecord} 的语义已由
 * {@code RecordSemanticsGoldenTest}（114 条）与 {@code LegacyTypeKitGoldenTest}（540 条）钉死，
 * 故此处直接持有并委托，不重复实现一遍。
 *
 * <p><b>实测确认的语义（据 jfinal 5.2.6 完整方法体，非片段推断 —— R43）：</b>
 * <ol>
 *   <li>{@link #set(String, Object)} 先校验列存在（旧实现调
 *       {@code _getTable().hasColumnLabel(k)}），列不存在抛
 *       {@link EovaActiveRecordException}，消息为
 *       {@code The attribute name does not exist: "k"}；随后写值<b>并记 modifyFlag</b>。</li>
 *   <li>{@link #put(String, Object)} 只写值：<b>不校验列、不记 modifyFlag</b>。</li>
 *   <li>{@link #delete()} 取主键值，为 null 时抛
 *       {@link EovaActiveRecordException}，消息为 {@code Primary key <pk> can not be null}（注意 key 与主键名之间<b>有空格</b>，实测旧侧如此）。</li>
 *   <li>{@link #deleteById(Object)} 对 null 抛
 *       {@code IllegalArgumentException}，消息为 {@code idValue can not be null}。</li>
 *   <li>{@link #dao()} 返回 this 并标记该实例可作 DAO 使用（EOVA 惯用
 *       {@code public static final X dao = new X().dao()}）。</li>
 *   <li>{@link #_getConfigName()} 返回该模型<b>注册时所属的数据源名</b> ——
 *       EOVA 双数据源，丢掉此维度会跨库写错。</li>
 * </ol>
 *
 * <p><b>刻意保留的既有语义：</b>
 * <ul>
 *   <li>{@code set} 与 {@code put} 的不对称（校验与 modifyFlag）原样保留 ——
 *       这是对外可观测差异，不能"统一"。</li>
 *   <li>未注册映射的模型：旧 {@code TableMapping.getTable} 返回 null，
 *       故 {@code delete()} 会 NPE。本实现同样让 {@code _getTable()} 返回 null。</li>
 * </ul>
 *
 * <p><b>本类明确未实现（列为待做，不静默留空）：</b>
 * {@code save()} / {@code update()} 的 SQL 生成与 null 过滤细节（需与旧实现逐列比对，
 * 单独一轮）、{@code save}/{@code update} 之外的 {@code deleteByIds}、
 * {@code findByIdLoadColumns}、{@code SqlPara} 系列、{@code getShort}/{@code getByte}/
 * {@code getTimestamp}/{@code getBytes}（{@code EovaRecord} 尚未提供这四个 getter）。
 * 不声明它们会让 port 模型类时<b>编译期报错</b>，从而强制补做。
 */
public abstract class EovaModel<M extends EovaModel<M>> implements Serializable {

    private static final long serialVersionUID = -1051928120476005929L;

    /** 属性容器（取值语义已固化为 EovaRecord） */
    private final EovaRecord attrs = new EovaRecord();

    /** 是否经 dao() 标记为 DAO 实例 */
    private boolean daoFlag;

    /** 默认持久化网关（单数据源场景）；启动时注入 */
    private static volatile EovaDbGateway gateway;

    /**
     * 按数据源名注册的网关：EOVA 是双数据源（eova / 用户业务库），
     * 而 {@code BaseModel} 的 {@code execute}/{@code isExist} 等都按
     * {@code _getConfigName()} 决定操作哪个库，故网关必须能按数据源名解析。
     */
    private static final Map<String, EovaDbGateway> gateways = new java.util.LinkedHashMap<>();

    /** 缓存实现；启动时注入（未注入时缓存方法明确报错） */
    private static volatile CacheService cacheService;

    /**
     * 注入持久化网关（启动时由容器调用）
     */
    public static void setGateway(EovaDbGateway gw) {
        gateway = gw;
    }

    /**
     * 按数据源名注册网关（启动时由容器对每个数据源各注入一个）
     *
     * @param configName 数据源名（与 {@code EovaTableMapping} 注册时一致）
     * @param gw         该数据源的网关
     */
    public static void setGateway(String configName, EovaDbGateway gw) {
        synchronized (gateways) {
            gateways.put(configName, gw);
        }
    }

    /**
     * 清空数据源网关注册（仅供测试隔离）
     */
    public static void clearGateways() {
        synchronized (gateways) {
            gateways.clear();
        }
        gateway = null;
    }

    /**
     * 注入缓存实现（启动时由容器调用）
     */
    public static void setCacheService(CacheService cs) {
        cacheService = cs;
    }

    /**
     * 取本模型应用哪个网关：优先按 {@code _getConfigName()}（本模型绑定的数据源）解析，
     * 未注册该数据源时回落到默认网关。
     */
    /**
     * 按【数据源名】显式取网关。
     *
     * <p>用于旧代码里 {@code Db.use(<ds>).xxx(...)} 这种<b>显式指定数据源</b>的写法 ——
     * 它不依赖模型自身的映射归属，故不能简单用 {@link #gw()}（后者按 {@code _getConfigName()} 解析）。
     * 若该数据源未注册，回落到默认网关并保持旧实现的"用错数据源就会出错"的可见性。
     *
     * @param configName 数据源名
     * @return 该数据源的网关
     */
    protected EovaDbGateway gw(String configName) {
        synchronized (gateways) {
            EovaDbGateway byDs = gateways.get(configName);
            if (byDs != null) {
                return byDs;
            }
        }
        return gw();
    }

    protected EovaDbGateway gw() {
        String ds = _getConfigName();
        if (ds != null) {
            synchronized (gateways) {
                EovaDbGateway byDs = gateways.get(ds);
                if (byDs != null) {
                    return byDs;
                }
            }
        }
        EovaDbGateway g = gateway;
        if (g == null) {
            throw new IllegalStateException(
                    "EovaModel 未注入 EovaDbGateway（数据源=" + ds + "）");
        }
        return g;
    }

    private static CacheService cache() {
        CacheService c = cacheService;
        if (c == null) {
            throw new IllegalStateException("EovaModel 未注入 CacheService");
        }
        return c;
    }

    // ———————————————————————— 模板与绑定 ————————————————————————

    /**
     * 标记本实例为 DAO 并返回（EOVA 惯用 {@code public static final X dao = new X().dao()}）
     */
    @SuppressWarnings("unchecked")
    public M dao() {
        this.daoFlag = true;
        return (M) this;
    }

    /**
     * 是否已标记为 DAO
     */
    public boolean isDao() {
        return daoFlag;
    }

    /**
     * 取实际使用的模型类（供反射构造查询结果）
     */
    protected Class<?> _getUsefulClass() {
        return getClass();
    }

    /**
     * 未注入网关时的报错入口，供 diagnostic 使用
     */
    public static boolean isConfigured() {
        return gateway != null;
    }

    // ———————————————————————— 属性容器 ————————————————————————

    /**
     * 取属性 Map（对应 jfinal {@code Model._getAttrs()}）。
     *
     * <p><b>可见性与返回类型都必须与 jfinal 一致</b>：jfinal 是
     * {@code protected Map<String, Object>}，而 EOVA 代码会在它上面直接调 Map 方法 ——
     * 例如 {@code Button.queryButtons} 里的
     * {@code list.get(0)._getAttrs().containsKey("btnset")}。
     * 本方法最初写成 {@code public EovaRecord}（返回自定义容器），
     * 结果逐字节 port 的 {@code Button} <b>编译失败</b>。
     * 同 R25 的教训：等价物的<b>名字、可见性、返回类型</b>都是契约的一部分。
     *
     * <p>返回的是<b>可变视图</b>（与 jfinal 一致，不做防御性拷贝）。
     */
    protected Map<String, Object> _getAttrs() {
        return attrs.getColumns();
    }

    /**
     * 取属性条目集（对应 jfinal {@code Model._getAttrsEntrySet()}）
     */
    public Set<Map.Entry<String, Object>> _getAttrsEntrySet() {
        return attrs.getColumns().entrySet();
    }

    /**
     * 取属性容器（本实现内部使用的强类型视图；与 {@link #_getAttrs()} 返回同一份数据）
     */
    public EovaRecord attrs() {
        return attrs;
    }

    /**
     * 属性名数组
     */
    public String[] _getAttrNames() {
        return attrs.getColumnNames().toArray(new String[0]);
    }

    /**
     * 属性值数组（与 {@link #_getAttrNames()} 同序）
     */
    public Object[] _getAttrValues() {
        Set<String> names = attrs.getColumnNames();
        Object[] out = new Object[names.size()];
        int i = 0;
        for (String n : names) {
            out[i++] = attrs.getObject(n);
        }
        return out;
    }

    /**
     * 批量设置属性（对应旧 {@code _setAttrs}）
     */
    @SuppressWarnings("unchecked")
    public M _setAttrs(Map<String, Object> map) {
        if (map != null) {
            for (Map.Entry<String, Object> e : map.entrySet()) {
                set(e.getKey(), e.getValue());
            }
        }
        return (M) this;
    }

    /**
     * 已修改字段集合（决定 update 提交哪些列）。
     *
     * <p>可见性与旧实现一致，为 {@code protected}（外部不可直接调用，子类可用）。
     */
    protected Set<String> _getModifyFlag() {
        return attrs.getModifyFlag();
    }

    /**
     * 取本模型绑定的数据源名；未注册映射时返回 null
     */
    public String _getConfigName() {
        return EovaTableMapping.me().getConfigName(_getUsefulClass());
    }

    /**
     * 取本模型绑定的表元数据；未注册映射时返回 null（与旧实现一致）
     */
    public TableMetadata _getTable() {
        return EovaTableMapping.me().getTable(_getUsefulClass());
    }

    /**
     * 切换数据源（对应旧 {@code Model.use(configName)}）；切换后的取值不受影响，
     * 但持久化操作应经 {@code BaseModel} 显式使用该数据源名
     */
    @SuppressWarnings("unchecked")
    public M use(String configName) {
        EovaTableMapping.me().addMapping(configName, _getUsefulClass(), _getTable());
        return (M) this;
    }

    // ———————————————————————— 取值 ————————————————————————

    /**
     * 取原始值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String column) {
        return (T) attrs.getObject(column);
    }

    /**
     * 取原始值；null 时返回默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String column, T defaultValue) {
        Object v = attrs.getObject(column);
        return v != null ? (T) v : defaultValue;
    }

    /**
     * 取值经函数转换；null 时返回 null
     */
    public <T> T get(String column, Function<Object, T> func) {
        Object v = attrs.getObject(column);
        return v != null ? func.apply(v) : null;
    }

    /**
     * 取值经函数转换；null 时返回默认值
     */
    public <T> T get(String column, T defaultValue, Function<Object, T> func) {
        Object v = attrs.getObject(column);
        return v != null ? func.apply(v) : defaultValue;
    }

    /** 取字符串 */
    public String getStr(String column) {
        return attrs.getStr(column);
    }

    /** 取 Integer */
    public Integer getInt(String column) {
        return attrs.getInt(column);
    }

    /** 取 Long */
    public Long getLong(String column) {
        return attrs.getLong(column);
    }

    /** 取 BigInteger */
    public java.math.BigInteger getBigInteger(String column) {
        return attrs.getBigInteger(column);
    }

    /** 取 BigDecimal */
    public java.math.BigDecimal getBigDecimal(String column) {
        return attrs.getBigDecimal(column);
    }

    /** 取 Double */
    public Double getDouble(String column) {
        return attrs.getDouble(column);
    }

    /** 取 Float */
    public Float getFloat(String column) {
        return attrs.getFloat(column);
    }

    /** 取 Number */
    public Number getNumber(String column) {
        return attrs.getNumber(column);
    }

    /** 取 Boolean */
    public Boolean getBoolean(String column) {
        return attrs.getBoolean(column);
    }

    /** 取 Date */
    public java.util.Date getDate(String column) {
        return attrs.getDate(column);
    }

    /** 取 LocalDateTime */
    public java.time.LocalDateTime getLocalDateTime(String column) {
        return attrs.getLocalDateTime(column);
    }

    /** 取 java.sql.Time */
    public java.sql.Time getTime(String column) {
        return attrs.getTime(column);
    }

    /**
     * 属性个数
     */
    public int size() {
        return attrs.size();
    }

    /**
     * 以 Map 形态返回属性（副本）
     */
    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(attrs.getColumns());
    }

    /**
     * 转为记录容器（对应旧 {@code Model.toRecord()}）
     */
    public EovaRecord toRecord() {
        EovaRecord r = new EovaRecord();
        r.setColumns(new LinkedHashMap<>(attrs.getColumns()));
        return r;
    }

    /**
     * 序列化为 JSON（与旧 {@code Model.toJson()} 等价，走同一序列化器）
     */
    public String toJson() {
        return LegacyJsonKit.toJson(attrs.getColumns());
    }

    // ———————————————————————— 设值 ————————————————————————

    /**
     * 设置属性：<b>先校验列存在</b>，列不存在时抛 {@link EovaActiveRecordException}
     * （替代旧实现的 {@code ActiveRecordException}，消息保持一致），随后写值<b>并记 modifyFlag</b>。
     *
     * <p>消息与旧实现逐字一致：{@code The attribute name does not exist: "column"}。
     * 异常类型由 {@code ActiveRecordException} 改名为 {@link EovaActiveRecordException}
     * （已实测 EOVA 对该类型零引用，见该类注释）。
     */
    @SuppressWarnings("unchecked")
    public M set(String column, Object value) {
        TableMetadata table = _getTable();
        if (table != null && !table.hasColumnLabel(column)) {
            throw new EovaActiveRecordException(
                    "The attribute name does not exist: \"" + column + "\"");
        }
        attrs.set(column, value);
        return (M) this;
    }

    /**
     * 设置属性但<b>不校验列、不记 modifyFlag</b>（对应旧 {@code Model.put}）
     */
    @SuppressWarnings("unchecked")
    public M put(String column, Object value) {
        attrs.put(column, value);
        return (M) this;
    }

    /**
     * 批量 put（对应旧 {@code Model.put(Map)}）
     */
    @SuppressWarnings("unchecked")
    public M put(Map<String, Object> map) {
        if (map != null) {
            for (Map.Entry<String, Object> e : map.entrySet()) {
                attrs.put(e.getKey(), e.getValue());
            }
        }
        return (M) this;
    }

    /**
     * 值非 null 时 set，否则 put（对应旧 {@code Model.setOrPut}）
     */
    @SuppressWarnings("unchecked")
    public M setOrPut(String column, Object value) {
        if (value != null) {
            set(column, value);
        } else {
            put(column, value);
        }
        return (M) this;
    }

    /**
     * 删除指定属性
     */
    @SuppressWarnings("unchecked")
    public M remove(String column) {
        attrs.remove(column);
        return (M) this;
    }

    /**
     * 删除多个属性
     */
    @SuppressWarnings("unchecked")
    public M remove(String... columns) {
        if (columns != null) {
            for (String c : columns) {
                attrs.remove(c);
            }
        }
        return (M) this;
    }

    /**
     * 移除值为 null 的属性（对应旧 {@code Model.removeNullValueAttrs}）
     */
    @SuppressWarnings("unchecked")
    public M removeNullValueAttrs() {
        attrs.getColumns().entrySet().removeIf(e -> e.getValue() == null);
        return (M) this;
    }

    /**
     * 只保留给定属性
     */
    @SuppressWarnings("unchecked")
    public M keep(String... columns) {
        if (columns == null) {
            attrs.clear();
            return (M) this;
        }
        Set<String> keys = new java.util.LinkedHashSet<>();
        for (String c : columns) {
            if (attrs.hasColumn(c)) {
                keys.add(c.toLowerCase());
            }
        }
        attrs.getColumns().keySet().retainAll(keys);
        attrs.getModifyFlag().retainAll(keys);
        return (M) this;
    }

    /**
     * 清空全部属性
     */
    @SuppressWarnings("unchecked")
    public M clear() {
        attrs.clear();
        return (M) this;
    }

    // ———————————————————————— 查询 ————————————————————————

    /**
     * 按 SQL 查询并转成模型列表
     */
    public List<M> find(String sql, Object... paras) {
        return toModels(gw().find(sql, paras));
    }

    /**
     * 按 SQL 查询（无参）
     */
    public List<M> find(String sql) {
        return toModels(gw().find(sql));
    }

    /**
     * 查询全部
     */
    public List<M> findAll() {
        TableMetadata table = _getTable();
        if (table == null) {
            throw new IllegalStateException(
                    "模型未注册映射：" + _getUsefulClass().getName());
        }
        return toModels(gw().find("select * from " + table.getName()));
    }

    /**
     * 查首行
     */
    public M findFirst(String sql, Object... paras) {
        return toModel(gw().findFirst(sql, paras));
    }

    /**
     * 查首行（无参）
     */
    public M findFirst(String sql) {
        return toModel(gw().findFirst(sql));
    }

    /**
     * 按主键查
     */
    public M findById(Object idValue) {
        TableMetadata table = _getTable();
        if (table == null) {
            throw new IllegalStateException(
                    "模型未注册映射：" + _getUsefulClass().getName());
        }
        return toModel(gw().findById(table.getName(), idValue));
    }

    /**
     * 分页查询
     */
    public EovaPage<M> paginate(int pageNumber, int pageSize, String select,
                                String sqlExceptSelect, Object... paras) {
        EovaPage<EovaRecord> page =
                gw().paginate(pageNumber, pageSize, select, sqlExceptSelect, paras);
        return new EovaPage<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow(),
                toModels(page.getList()));
    }

    /**
     * 分页查询（无参）
     */
    public EovaPage<M> paginate(int pageNumber, int pageSize, String select,
                                String sqlExceptSelect) {
        return paginate(pageNumber, pageSize, select, sqlExceptSelect, new Object[0]);
    }

    // ———————————————————————— 缓存查询 ————————————————————————

    /**
     * 带缓存的查询（缓存未命中则查库并回填）
     */
    @SuppressWarnings("unchecked")
    public List<M> findByCache(String cacheName, Object key, String sql, Object... paras) {
        Object cached = cache().get(cacheName, key);
        if (cached != null) {
            return (List<M>) cached;
        }
        List<M> list = find(sql, paras);
        cache().put(cacheName, key, list);
        return list;
    }

    /**
     * 带缓存的查询（无参）
     */
    public List<M> findByCache(String cacheName, Object key, String sql) {
        return findByCache(cacheName, key, sql, new Object[0]);
    }

    /**
     * 带缓存的查首行
     */
    public M findFirstByCache(String cacheName, Object key, String sql, Object... paras) {
        List<M> list = findByCache(cacheName, key, sql, paras);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 带缓存的查首行（无参）
     */
    public M findFirstByCache(String cacheName, Object key, String sql) {
        return findFirstByCache(cacheName, key, sql, new Object[0]);
    }

    /**
     * 带缓存的分页查询（缓存未命中则分页查库并回填）
     */
    @SuppressWarnings("unchecked")
    public EovaPage<M> paginateByCache(String cacheName, Object key, int pageNumber, int pageSize,
                                       String select, String sqlExceptSelect, Object... paras) {
        Object cached = cache().get(cacheName, key);
        if (cached != null) {
            return (EovaPage<M>) cached;
        }
        EovaPage<M> page = paginate(pageNumber, pageSize, select, sqlExceptSelect, paras);
        cache().put(cacheName, key, page);
        return page;
    }

    /**
     * 带缓存的分页查询（无参）
     */
    public EovaPage<M> paginateByCache(String cacheName, Object key, int pageNumber, int pageSize,
                                       String select, String sqlExceptSelect) {
        return paginateByCache(cacheName, key, pageNumber, pageSize, select, sqlExceptSelect,
                new Object[0]);
    }

    // ———————————————————————— 保存 / 更新 ————————————————————————

    /**
     * 插入当前记录。
     *
     * <p>与旧 {@code Model.save()} 同构：
     * <ol>
     *   <li>{@code filter(FILTER_BY_SAVE)} —— 旧 {@code Model.filter(int)} 是<b>空方法</b>，
     *       故本实现不调用任何过滤钩子（{@code BaseModel} 也未覆写）；</li>
     *   <li>SQL 由 {@link ModelSqlBuilder#forModelSave} 按 MysqlDialect 规则生成
     *       （只取已有属性、跳过非表列、null 照常写入、反引号引用）；</li>
     *   <li>取回自增主键并写回模型（可观测行为）；</li>
     *   <li>清空 modifyFlag。</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    public boolean save() {
        TableMetadata table = _getTable();
        if (table == null) {
            throw new IllegalStateException("模型未注册映射：" + _getUsefulClass().getName());
        }
        ModelSqlBuilder.Sql sql = ModelSqlBuilder.forModelSave(table, attrs.getColumns());
        Object key = gw().insertReturningKey(sql.sql(), sql.paras());
        String[] pks = table.getPrimaryKey();
        if (key != null && pks.length > 0) {
            attrs.set(pks[0], key);
        }
        attrs.getModifyFlag().clear();
        return true;
    }

    /**
     * 更新当前记录。
     *
     * <p>与旧 {@code Model.update()} 同构：
     * <ol>
     *   <li>{@code filter(FILTER_BY_UPDATE)} —— 旧实现为空方法，故不调用；</li>
     *   <li><b>modifyFlag 为空时直接返回 false</b>（不执行任何 SQL）；</li>
     *   <li>主键值为 null 时抛 {@link EovaActiveRecordException}，消息与旧实现逐字一致：
     *       {@code You can't update model without Primary Key, <pk> can not be null.}；</li>
     *   <li>只更新 modifyFlag 中的列，跳过主键与非表列。</li>
     * </ol>
     */
    public boolean update() {
        TableMetadata table = _getTable();
        if (table == null) {
            throw new IllegalStateException("模型未注册映射：" + _getUsefulClass().getName());
        }
        if (attrs.getModifyFlag().isEmpty()) {
            return false;
        }
        String[] pks = table.getPrimaryKey();
        Object idValue = attrs.getObject(pks[0]);
        if (idValue == null) {
            throw new EovaActiveRecordException("You can't update model without Primary Key, "
                    + pks[0] + " can not be null.");
        }
        ModelSqlBuilder.Sql sql = ModelSqlBuilder.forModelUpdate(
                table, attrs.getColumns(), attrs.getModifyFlag(), idValue);
        boolean ok = gw().update(sql.sql(), sql.paras()) > 0;
        attrs.getModifyFlag().clear();
        return ok;
    }

    // ———————————————————————— 删除 ————————————————————————

    /**
     * 按主键删除当前记录。
     *
     * <p>主键值为 null 时抛 {@link EovaActiveRecordException}，消息与旧实现一致：
     * {@code Primary key<主键名> can not be null}。
     */
    public boolean delete() {
        TableMetadata table = _getTable();
        String[] pk = table.getPrimaryKey();
        Object idValue = attrs.getObject(pk[0]);
        if (idValue == null) {
            throw new EovaActiveRecordException("Primary key " + pk[0] + " can not be null");
        }
        return gw().deleteById(table.getName(), idValue);
    }

    /**
     * 按主键值删除。
     *
     * <p>idValue 为 null 时抛 {@link IllegalArgumentException}，消息与旧实现一致：
     * {@code idValue can not be null}。
     */
    public boolean deleteById(Object idValue) {
        if (idValue == null) {
            throw new IllegalArgumentException("idValue can not be null");
        }
        TableMetadata table = _getTable();
        if (table == null) {
            throw new IllegalStateException(
                    "模型未注册映射：" + _getUsefulClass().getName());
        }
        return gw().deleteById(table.getName(), idValue);
    }

    // ———————————————————————— 内部 ————————————————————————

    /** 记录 → 模型 */
    @SuppressWarnings("unchecked")
    protected M toModel(EovaRecord record) {
        if (record == null) {
            return null;
        }
        try {
            M m = (M) _getUsefulClass().getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Object> e : record.getColumns().entrySet()) {
                m.put(e.getKey(), e.getValue());
            }
            return m;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法构造模型实例：" + _getUsefulClass().getName(), e);
        }
    }

    /** 记录列表 → 模型列表 */
    protected List<M> toModels(List<EovaRecord> records) {
        List<M> out = new ArrayList<>(records.size());
        for (EovaRecord r : records) {
            out.add(toModel(r));
        }
        return out;
    }

    @Override
    public String toString() {
        return _getUsefulClass().getSimpleName() + ":" + attrs;
    }
}
