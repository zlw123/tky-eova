/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.base;

import cn.eova.common.utils.db.SqlUtil;
import cn.eova.config.EovaDataSource;
import cn.eova.db.EovaModel;
import cn.eova.db.EovaPage;
import cn.eova.compat.table.TableMetadata;
import com.alibaba.druid.DbType;

import java.util.List;

/**
 * <p>ported from: cn.eova.common.base.BaseModel
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 *
 * <p><b>本单元为逐行对应 port（非逐字节）：</b>旧类的基类是
 * {@code com.jfinal.plugin.activerecord.Model}，新栈不存在该类，
 * 故基类替换为 {@link EovaModel}（语义等价物，已由 {@code EovaModelGoldenTest}
 * 对着真实 jfinal {@code Model} 验证）。方法体逐条保留，仅做底座必需替换：
 *
 * <table border="1">
 *   <caption>底座替换对照</caption>
 *   <tr><th>旧</th><th>新</th><th>说明</th></tr>
 *   <tr><td>{@code Db.use(ds).update(sql, paras)}</td><td>{@code gw().update(sql, paras)}</td>
 *       <td>数据源经 {@code _getConfigName()} 解析（双数据源）</td></tr>
 *   <tr><td>{@code DbKit.getConfig(getClass()).getName()}</td><td>{@code _getConfigName()}</td>
 *       <td>同名值：本模型注册时绑定的数据源名</td></tr>
 *   <tr><td>{@code Db.use(cfg).queryNumber(...)}</td><td>{@code gw().queryNumber(...)}</td>
 *       <td>网关已补 {@code queryNumber}，返回值同为 {@code Number}</td></tr>
 *   <tr><td>{@code TableMapping.me().getTable(getClass())}</td><td>{@code _getTable()}</td>
 *       <td>{@code EovaTableMapping}（已由 {@code TableMappingGoldenTest} 验证）</td></tr>
 *   <tr><td>{@code BaseCache}（EhCache）</td><td>{@link BaseCache}（经 {@link CacheService} 接缝）</td>
 *       <td>同一份 ehcache.xml，策略逐字节一致</td></tr>
 *   <tr><td>{@code Page<M>}</td><td>{@link EovaPage}</td><td>分页容器等价物</td></tr>
 *   <tr><td>{@code Model.save()}</td><td>{@code EovaModel.save()}</td>
 *       <td>SQL 由 {@code ModelSqlBuilder} 按 MysqlDialect 规则生成</td></tr>
 * </table>
 *
 * <p><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>{@code findByCache} 的两个覆写<b>只是转调 super</b>（旧实现中"按 base_ 前缀换 cacheName"
 *       的逻辑被注释掉了）——<b>保留该覆写与注释</b>，不"顺手复活"注释里的逻辑。</li>
 *   <li>{@code getByCache} 的缓存键为 {@code getClass().getSimpleName() + "_" + id}，
 *       使用 {@code BaseCache.SER}（service 缓存，TTL 1s）。</li>
 *   <li>{@code queryByCache} 直接把 SQL 当缓存键；带参版本把参数逐个拼进键
 *       （{@code key += "_" + obj.toString()}）——参数为 null 会 NPE，属既有行为。</li>
 *   <li>{@code isExist} 里 {@code Db.use(...).queryNumber(...).longValue()} 先拆箱再判
 *       {@code count != null}，故 {@code count != null} 是死条件 —— 原样保留。</li>
 *   <li>{@code save()} 在 Oracle 下的两处分支：先补序列值（仅当主键为 null），
 *       保存后再执行 {@code this.set(pk, this.get(pk))}（即"把主键值写回自身"，空操作语义）——
 *       <b>看似冗余但属既有行为，原样保留</b>。</li>
 *   <li>{@code x.isEmpty(list)} 用于 {@code queryFisrtByCache} 的空判断（来自 eova-tools）。</li>
 * </ol>
 */
public class BaseModel<M extends EovaModel<M>> extends EovaModel<M> {

    private static final long serialVersionUID = 1702469565872353932L;

    /**
     * 执行更新/DDL（旧实现：{@code Db.use(this._getConfig().getName()).update(sql, paras)}）
     */
    public void execute(String sql, Object... paras) {
        String ds = _getConfigName();
        gw().update(sql, paras);
    }

    /**
     * 执行更新/DDL（无参）
     */
    public void execute(String sql) {
        execute(sql, new Object[0]);
    }

    /**
     * 按缓存查询（旧实现仅转调 super，其"按 base_ 前缀换 cacheName"的逻辑在源码中被注释）
     */
    public List<M> findByCache(String cacheName, Object key, String sql) {
        // Base数据缓存30Min
        // if (sql.contains("from base_")) {
        // cacheName = BaseCache.BASE;
        // }
        return super.findByCache(cacheName, key, sql);
    }

    /**
     * 按缓存查询（带参）
     */
    public List<M> findByCache(String cacheName, Object key, String sql, Object... paras) {
        return super.findByCache(cacheName, key, sql, paras);
    }

    /**
     * 根据主键获取对象（带 service 缓存）
     *
     * @param id 主键
     * @return 模型或 null
     */
    @SuppressWarnings("unchecked")
    public M getByCache(Object id) {
        String key = this.getClass().getSimpleName() + "_" + id;
        // get by cache
        M me = (M) BaseCache.getSer(key);
        if (me == null) {
            // get by db
            me = super.findById(id);
            if (me != null) {
                // add to service cache
                BaseCache.putSer(key, me);
            }
        }
        return me;
    }

    /**
     * 查询自动缓存（以 SQL 作为缓存键）
     *
     * @param sql 查询语句
     * @return 模型列表
     */
    public List<M> queryByCache(String sql) {
        // 查询SQL作为Key值
        return findByCache(BaseCache.SER, sql, sql);
    }

    /**
     * 查询自动缓存（缓存键 = SQL + 各参数）
     *
     * @param sql   查询语句
     * @param paras 参数
     * @return 模型列表
     */
    public List<M> queryByCache(String sql, Object... paras) {
        // sql_xx_xx_xx
        String key = sql;
        for (Object obj : paras) {
            // 参数为 null 会 NPE —— 属既有行为
            key += "_" + obj.toString();
        }
        return findByCache(BaseCache.SER, key, sql, paras);
    }

    /**
     * 缓存查询第一项
     *
     * @param sql 查询语句
     * @return 模型或 null
     */
    public M queryFisrtByCache(String sql) {
        List<M> list = queryByCache(sql);
        if (isEmpty(list)) {
            return null;
        }
        return list.get(0);
    }

    /**
     * 缓存查询第一项（带参）
     *
     * @param sql   查询语句
     * @param paras 参数
     * @return 模型或 null
     */
    public M queryFisrtByCache(String sql, Object... paras) {
        List<M> list = queryByCache(sql, paras);
        if (isEmpty(list)) {
            return null;
        }
        return list.get(0);
    }

    /**
     * 缓存分页查询
     *
     * @param pageNumber      页码
     * @param pageSize        数量
     * @param select          查询前缀
     * @param sqlExceptSelect 查询条件
     * @return 分页结果
     */
    public EovaPage<M> pagerByCache(int pageNumber, int pageSize, String select,
                                    String sqlExceptSelect) {
        String key = select + sqlExceptSelect + "_" + pageNumber + "_" + pageSize;
        return super.paginateByCache(BaseCache.SER, key, pageNumber, pageSize, select,
                sqlExceptSelect);
    }

    /**
     * 缓存分页查询（带参）
     *
     * @param pageNumber      页码
     * @param pageSize        数量
     * @param select          查询前缀
     * @param sqlExceptSelect 查询条件
     * @param paras           SQL 参数
     * @return 分页结果
     */
    public EovaPage<M> pagerByCache(int pageNumber, int pageSize, String select,
                                    String sqlExceptSelect, Object... paras) {
        String key = select + sqlExceptSelect + "_" + pageNumber + "_" + pageSize;
        for (Object obj : paras) {
            key += "_" + obj.toString();
        }
        return super.paginateByCache(BaseCache.SER, key, pageNumber, pageSize, select,
                sqlExceptSelect, paras);
    }

    /**
     * 是否存在
     *
     * @param sql   查询语句
     * @param paras 参数
     * @return true 表示存在
     */
    public boolean isExist(String sql, Object... paras) {
        // 旧实现：DbKit.getConfig(this.getClass()).getName() —— 即本模型绑定的数据源名
        String configName = _getConfigName();
        Long count = gw().queryNumber(sql, paras).longValue();
        // count 已在上一行拆箱，故此处的 null 判断是死条件 —— 原样保留
        if (count != null && count != 0) {
            return true;
        }
        return false;
    }

    /**
     * 保存（Oracle 下自动补序列值）
     */
    @Override
    public boolean save() {
        TableMetadata table = _getTable();
        String pk = table.primaryKeys()[0];
        // Class<?> pkType = table.getColumnType(pk);

        String ds = _getConfigName();

        if (EovaDataSource.getDbType(ds) == DbType.oracle) {
            // 序列默认值
            if (this.get(pk) == null) {
                this.set(pk, SqlUtil.getSequence(ds, table.getName()));
            }
        }
        boolean isSave = super.save();
        if (EovaDataSource.getDbType(ds) == DbType.oracle) {
            this.set(pk, this.get(pk));
        }
        return isSave;
    }

    /**
     * 列表空判断（等价于 eova-tools 的 {@code x.isEmpty(list)}）
     */
    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
