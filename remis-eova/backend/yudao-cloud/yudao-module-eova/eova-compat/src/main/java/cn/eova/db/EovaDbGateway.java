/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.util.List;

import javax.sql.DataSource;

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
     * 取本网关底层的 {@link DataSource}。
     *
     * <p><b>存在理由（DES-DB-OWNERSHIP-R2 §4）：</b>绝大多数调用方应走网关的高层语义
     * （{@code find}/{@code update}/{@code tx}），但<b>表结构自省</b>需要原始
     * {@code Connection} 才能取 {@code DatabaseMetaData}（表名、列、注释），
     * 这是元数据模块自动生成 {@code eova_field} 的基础。
     * 旧实现经 jfinal {@code DbKit.getConfig(ds).getDataSource()} 取得同一能力。</p>
     *
     * <p><b>边界（须由判据固定）：</b>业务代码<b>不得</b>用本方法绕开网关语义；
     * 仅 {@code DsUtil} 这类自省路径使用。</p>
     *
     * @return 底层数据源
     */
    DataSource dataSource();

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
     * 按<b>指定主键列</b>查询（对应 jfinal {@code DbPro.findById(String tableName, String primaryKey, Object idValue)}）。
     *
     * <p><b>SQL 形态取自旧制品实测</b>（直接调用 jfinal 5.2.6 的
     * {@code MysqlDialect.forDbFindById} 取值，而非照 javap 注释拼）：</p>
     * <pre>
     * select * from `表` where `主键` = ?
     * </pre>
     * 多条主键（逗号分隔）时为 {@code where `k1` = ? and `k2` = ?}；
     * <b>表名与各主键都会 trim</b>。列名一律反引号引用（保留字安全）。
     *
     * @param table        表名（会 trim）
     * @param primaryKey   主键列名，多个以逗号分隔（每段会 trim）
     * @param idValue      主键值
     * @return 命中行；无命中返回 {@code null}
     */
    EovaRecord findById(String table, String primaryKey, Object idValue);

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
     * 查询<b>单列</b>并取<b>首行</b>值（对应 jfinal {@code DbPro.queryColumn}）。
     *
     * <p><b>语义逐条取自旧字节码</b>（jfinal 5.2.6 {@code DbPro.queryColumn(String, Object[])}）：
     * <pre>
     * List&lt;T&gt; list = query(sql, paras);
     * if (list.size() &gt; 0) {
     *     T t = list.get(0);
     *     if (t instanceof Object[]) throw new ActiveRecordException("Only ONE COLUMN can be queried.");
     *     return t;
     * }
     * return null;
     * </pre>
     * 注意两点（均属既有语义，不得"顺手修正"）：
     * <ol>
     *   <li>抛错分支是 {@code t instanceof Object[]} —— 因为 {@code query} 在<b>列数 &gt; 1</b> 时
     *       每行给的是<b>整行 {@code Object[]}</b>；故多列 SQL 会在此处抛错。</li>
     *   <li>{@code "Only ONE COLUMN can be queried."} 这条消息属 <b>本方法</b>，
     *       <b>不在</b> {@code query} 里（{@code query} 是<b>按列数分支</b>，不抛错）。</li>
     * </ol>
     *
     * <p><b>为什么用 default 方法：</b>本方法完全由 {@link #query} 派生，
     * 在接口上给唯一实现可保证<b>所有实现行为一致</b>，且不必让每个实现重复这段分支逻辑。</p>
     *
     * @param sql   查询语句
     * @param paras 参数
     * @param <T>   列值类型
     * @return 首行首列值；无命中返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    default <T> T queryColumn(String sql, Object... paras) {
        List<T> list = query(sql, paras);
        if (!list.isEmpty()) {
            T t = list.get(0);
            if (t instanceof Object[]) {
                throw new EovaActiveRecordException("Only ONE COLUMN can be queried.");
            }
            return t;
        }
        return null;
    }

    /**
     * 查询<b>单列首行</b>并转成字符串（对应 jfinal {@code DbPro.queryStr}）。
     *
     * <p><b>旧字节码语义：</b>{@code T t = queryColumn(sql, paras); return t != null ? t.toString() : null;}
     * —— 经 {@code toString()} 转换（<b>不是</b>强制类型转换），故日期/数值等类型会带上其
     * {@code toString} 形态。该行为属既有语义，不得改为按类型格式化。</p>
     *
     * @param sql   查询语句
     * @param paras 参数
     * @return 首行首列的字符串形态；无命中返回 {@code null}
     */
    default String queryStr(String sql, Object... paras) {
        Object t = queryColumn(sql, paras);
        return t != null ? t.toString() : null;
    }

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
     * 批量执行多条 SQL（对应 jfinal {@code DbPro.batch(List<String> sqlList, int batchSize)}）。
     *
     * <p><b>逐条取自旧字节码的语义：</b></p>
     * <ol>
     *   <li>{@code sqlList} 为 null 或空 ⇒ 返回<b>长度为 0</b> 的数组；</li>
     *   <li>{@code batchSize < 1} ⇒ 抛
     *       {@code IllegalArgumentException("The batchSize must more than 0.")}；</li>
     *   <li>按 {@code batchSize} 分块 {@code addBatch} + {@code executeBatch}，
     *       <b>非事务状态下每块执行完即 {@code commit}</b>（⇒ 批与批之间<b>不</b>是一个原子单元，
     *       中途失败只回滚当前块）——这是旧实现的可观测语义，不得"顺手"改成整体一次提交；</li>
     *   <li>返回数组长度固定为 {@code sqlList.size()}，各块的 {@code executeBatch()} 结果
     *       <b>压平到数组前部</b>（不足处留 0）。</li>
     * </ol>
     *
     * @param sqlList   待执行 SQL 列表
     * @param batchSize 每批条数（必须 &gt; 0）
     * @return 各行影响数（长度 = sqlList.size()）
     */
    int[] batch(List<String> sqlList, int batchSize);

    /**
     * 按缓存查询多行（对应 jfinal {@code DbPro.findByCache(cacheName, key, sql, paras)}）。
     *
     * <p>缓存键由 {@code (cacheName, key)} 决定；命中则直接返回缓存值，未命中执行查询并回填。</p>
     *
     * @param cacheName 缓存名
     * @param key       缓存键
     * @param sql       查询语句
     * @param paras     参数
     * @return 结果集（无命中为空列表）
     */
    List<EovaRecord> findByCache(String cacheName, Object key, String sql, Object... paras);

    /**
     * 生成分页 SQL（对应 jfinal {@code MysqlDialect.forPaginate(pageNumber, pageSize, sql)}）。
     *
     * <p><b>行为取自旧制品实测</b>（直接调用 jfinal 5.2.6 的
     * {@code MysqlDialect.forPaginate} 打印结果）：</p>
     * <ul>
     *   <li>{@code pageNumber=1, pageSize=100} ⇒ {@code select * from t limit 0, 100}</li>
     *   <li>{@code pageNumber=0, pageSize=100} ⇒ {@code select * from t limit -100, 100}
     *       —— 偏移量为 {@code (pageNumber-1)*pageSize}，<b>pageNumber=0 时是负数</b>；
     *       这是旧实现的既有行为（该 SQL 在 MySQL 上实际不可执行），本接缝<b>原样保留</b>，
     *       不得"顺手修正"成 0 —— 否则等于改变 EOVA 该路径的既有语义。</li>
     * </ul>
     *
     * <p><b>不做 trim</b>：入参 SQL 原样拼接（实测 {@code forPaginate(1,5,"  select 1  ")}
     * 产出 {@code "  select 1   limit 0, 5"}）。</p>
     *
     * @param pageNumber 页码（旧实现允许 &lt; 1）
     * @param pageSize   每页条数
     * @param sql        SQL（StringBuilder 形态与旧签名一致）
     * @return 分页 SQL 文本
     */
    String forPaginate(int pageNumber, int pageSize, StringBuilder sql);

    /**
     * 按列名批量执行同一条 SQL（对应 jfinal
     * {@code DbPro.batch(String sql, String columns, List modelOrRecordList, int batchSize)}）。
     *
     * <p><b>逐条取自旧字节码：</b></p>
     * <ol>
     *   <li>{@code recordList} 为 null/空 ⇒ 返回长度 0 的数组；</li>
     *   <li>首元素必须是 Record（本接缝只支持 Record）⇒ 否则
     *       {@code IllegalArgumentException("The element in list must be Model or Record.")}；</li>
     *   <li>{@code batchSize < 1} ⇒
     *       {@code IllegalArgumentException("The batchSize must more than 0.")}；</li>
     *   <li>{@code columns} 以 {@code ,} 分隔且<b>逐段 trim</b>，按该顺序把每条记录的列值绑定到
     *       {@code sql} 的 {@code ?} 上；</li>
     *   <li>每 {@code batchSize} 条 {@code executeBatch} 一次，<b>非事务状态下每批提交</b>
     *       （与 {@link #batch(List, int)} 同构）；结果压平到长度 = 记录数的数组前部。</li>
     * </ol>
     *
     * @param sql        含 {@code ?} 占位符的语句（如 {@code update t set num = ? where id = ?}）
     * @param columns    列名，逗号分隔（顺序即绑定顺序）
     * @param recordList 记录列表
     * @param batchSize  每批条数（必须 &gt; 0）
     * @return 各行影响数（长度 = recordList.size()）
     */
    int[] batch(String sql, String columns, List<EovaRecord> recordList, int batchSize);

    /**
     * 批量保存模型（对应 jfinal {@code DbPro.batchSave(List&lt;? extends Model&gt;, int)}）。
     *
     * <p><b>语义：</b>把 {@code models} 逐条按 {@code ModelSqlBuilder.forModelSave} 生成
     * insert 语句执行，返回逐行的影响数数组；空列表返回长度 0 的数组；
     * {@code batchSize < 1} 抛
     * {@code IllegalArgumentException("The batchSize must more than 0.")}（与旧实现一致）。</p>
     *
     * <p><b>已声明的适配（1 处，需在验收时留意）：</b>旧实现把逐条 insert 组进
     * JDBC batch，并<b>在非事务状态下每 {@code batchSize} 条提交一次</b>；
     * 本实现逐条执行（复用现有 {@code update} 的连接作用域）。
     * 二者的可观测差异仅出现在<b>事务之外</b>（提交粒度：逐条 vs 逐批）。
     * EOVA 全树唯一调用点 {@code AuthController:169} 处于 {@code @Before(Tx.class)} 之内，
     * 此时两者都在同一事务连接上执行、由外层统一提交 ⇒ <b>该场景下等价</b>。
     * 若将来出现事务外的大批量调用，需改为真正的组批实现（已记入文档待办）。</p>
     *
     * @param models    待保存模型（非空列表）
     * @param batchSize 每批条数（必须 &gt; 0）
     * @return 各行影响数（长度 = models.size()）
     */
    int[] batchSave(List<? extends EovaModel<?>> models, int batchSize);

    /**
     * 事务执行；抛出异常则回滚，正常返回则提交
     */
    <T> T tx(Atom<T> atom);

    /**
     * 当前线程是否已处于本网关的事务中（旧 {@code Config.getThreadLocalConnection() != null}）。
     *
     * <p><b>为什么需要它：</b>jfinal 的事务拦截器 {@code Tx} 的行为在"最外层"与"嵌套"
     * 两种情形下<b>不同</b> —— 最外层负责提交/回滚并<b>吞掉</b>
     * {@code NestedTransactionHelpException}（静默回滚），嵌套层则只参与外层事务、
     * 并把该异常<b>向上传播</b>，交由最外层回滚。
     * 若接缝无法区分这两种情形，嵌套时的语义就会反转（内层吞掉 → 外层照常提交）。</p>
     *
     * <p>默认实现返回 {@code false}（供测试替身使用）；真实实现由
     * {@code JdbcEovaDbGateway} 依据其线程绑定连接回答。</p>
     *
     * @return 是处于事务中
     */
    default boolean inTransaction() {
        return false;
    }
}
