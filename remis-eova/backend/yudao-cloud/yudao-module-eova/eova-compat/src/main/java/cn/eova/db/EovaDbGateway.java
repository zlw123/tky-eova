/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.util.List;

/**
 * EOVA 数据访问网关（DES-002-R4 §4：唯一允许接触 MyBatis/数据源/事务的边界）。
 *
 * <p><b>存在理由：</b>旧系统所有业务代码经 JFinal 的 {@code Db}/{@code Record} 访问数据；
 * §3.1 实测 111 个文件依赖该语义。把数据访问收敛到本接口后，
 * 上层不再直接依赖 JFinal，且可在接入 MyBatis 时只替换实现。
 *
 * <p><b>语义规格：</b>{@code docs/.local/ledger/record-semantics.golden.jsonl}（SP6 实测 128 条）。
 * 任何实现都必须通过该规格的逐条比对。
 *
 * <p><b>事务原子操作</b>
 */
public interface EovaDbGateway {

    /** 事务体 */
    @FunctionalInterface
    interface Atom<T> {
        /** 在事务中执行 */
        T run() throws Throwable;
    }

    /**
     * 查询多行
     *
     * @param sql   带 {@code ?} 占位符的 SQL
     * @param paras 参数
     * @return 结果集（无命中为空列表，不返回 null）
     */
    List<EovaRecord> find(String sql, Object... paras);

    /**
     * 查询首行
     *
     * @return 首行；无命中返回 {@code null}
     */
    EovaRecord findFirst(String sql, Object... paras);

    /**
     * 按主键查询（主键列名固定为 {@code id}，与旧实现一致）
     *
     * @return 命中行；无命中返回 {@code null}
     */
    EovaRecord findById(String table, Object id);

    /**
     * 分页查询
     *
     * @param pageNumber      页码（从 1 开始；越界返回空列表）
     * @param pageSize        每页条数
     * @param select          select 子句（如 {@code "select *"}）
     * @param sqlExceptSelect 其余 SQL（如 {@code "from users order by id"}）
     */
    EovaPage<EovaRecord> paginate(int pageNumber, int pageSize,
                                  String select, String sqlExceptSelect, Object... paras);

    /**
     * 插入一行
     *
     * @return 是否成功
     */
    boolean save(String table, EovaRecord record);

    /**
     * 按默认主键 {@code id} 更新（仅提交已修改字段）
     *
     * @return 是否成功
     */
    boolean update(String table, EovaRecord record);

    /**
     * 按指定主键更新
     *
     * @return 是否成功
     */
    boolean update(String table, String primaryKey, EovaRecord record);

    /**
     * 按主键删除
     *
     * @return 是否成功
     */
    boolean delete(String table, EovaRecord record);

    /**
     * 按主键删除
     *
     * @return 命中并删除返回 true；未命中返回 false
     */
    boolean deleteById(String table, Object id);

    /**
     * 执行 insert 并取回自增生成的主键（旧 {@code Model.save()} 依赖此能力把主键写回模型）
     *
     * @param sql   insert 语句
     * @param paras 参数
     * @return 生成的主键值；无则 null
     */
    Object insertReturningKey(String sql, Object[] paras);

    /**
     * 查询单值数值（对应 jfinal {@code DbPro.queryNumber}），供 {@code BaseModel.isExist} 使用
     *
     * @param sql   查询语句（如 {@code select count(*) from ...}）
     * @param paras 参数
     * @return 数值；无结果时 null
     */
    Number queryNumber(String sql, Object... paras);

    /**
     * 查询单个 Long（对应 jfinal {@code DbPro.queryLong}）：
     * 即 {@code queryNumber} 非 null 时取 {@code longValue()}，null 时返回 null
     *
     * @param sql   查询语句
     * @param paras 参数
     * @return Long；无结果时 null
     */
    Long queryLong(String sql, Object... paras);

    /**
     * 查询<b>单列</b>并返回该列所有行的值（对应 jfinal {@code DbPro.query}）。
     *
     * <p><b>实测旧行为：按列数分支（据 {@code DbPro.query} 完整方法体）：</b>
     * <ul>
     *   <li>列数 &gt; 1 → 每行一个 {@code Object[]}（<b>整行</b>）</li>
     *   <li>列数 = 1 → 每行的第 1 列<b>标量</b></li>
     *   <li>列数 = 0 → 空列表</li>
     * </ul>
     * 注意 {@code "Only ONE COLUMN can be queried."} 属 {@code DbPro.queryColumn}，
     * <b>不在</b> {@code DbPro.query} 里 —— 两者不可混同（我最初据片段误归因，已由判据纠正）。
     * 调用方 {@code Button} 用的是<b>单列</b> SQL，取标量后自行转 Integer
     * （源码注释："为了兼容Oracle 返回的List&lt;BigDecimal&gt;"）。
     *
     * @param sql   查询语句（必须只 select 一列）
     * @param paras 参数
     * @param <T>   列值类型
     * @return 第 1 列的所有行值
     */
    <T> List<T> query(String sql, Object... paras);

    /**
     * 执行更新/DDL 语句
     *
     * @return 受影响行数
     */
    int update(String sql, Object... paras);

    /**
     * 执行删除语句（对应 jfinal {@code DbPro.delete}）
     *
     * @param sql   删除语句
     * @param paras 参数
     * @return 受影响行数
     */
    int delete(String sql, Object... paras);

    /**
     * 事务执行；抛出异常则回滚，正常返回则提交
     */
    <T> T tx(Atom<T> atom);
}
