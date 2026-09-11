/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import com.alibaba.druid.DbType;
import cn.eova.tools.x;
import cn.eova.sql.ddl.DefineDialectFactory;
import cn.eova.sql.ddl.dialect.DefineDialect;
import cn.eova.config.EovaDataSource;
import cn.eova.common.utils.db.SqlUtil;
import javax.sql.DataSource;
import java.sql.Connection;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyIAtom;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyNestedTransactionHelpException;
import cn.eova.compat.table.EovaTableMapping;
import cn.eova.compat.table.TableMetadata;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 JDBC 的 {@link EovaDbGateway} 实现（阶段 1 首选实现）。
 *
 * <p><b>为什么先用 JDBC 而非 MyBatis：</b>本实现的目标是<b>尽快建立可与 golden 逐条比对的等价物</b>。
 * MyBatis 接入（§4 规划）可在接口不变的前提下替换本类，属后续单元。
 *
 * <p><b>与旧实现的语义对齐点（均来自 SP6 实测 golden）：</b>
 * <ol>
 *   <li>结果集列名<b>统一小写</b>（对应 {@code CaseInsensitiveContainerFactory(true)}）；</li>
 *   <li>从库读出的行<b>不计入 modifyFlag</b>；{@code update} 只提交 modifyFlag 中的字段，提交后清空；</li>
 *   <li>{@code find} 无命中返回<b>空列表</b>；{@code findFirst}/{@code findById} 返回 <b>null</b>；</li>
 *   <li>{@code deleteById} 未命中返回 <b>false</b>；</li>
 *   <li>{@code paginate} 越界页返回空列表且 {@code isFirstPage=false}；</li>
 *   <li>{@code tx} 抛异常即回滚。</li>
 * </ol>
 *
 * <p>ported from: com.jfinal.plugin.activerecord.Db（语义等价重实现，非逐行 port）
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 */
public class JdbcEovaDbGateway implements EovaDbGateway {

    private final DataSource dataSource;

    /**
     * 数据源名（可为 null）。
     *
     * <p>第 79 轮加入：旧栈 {@code EovaDbPro}（{@code extends DbPro}）的方言专属行为
     * （Oracle 序列、PG 主键强转、dm/h2 的关键字转义）都以 {@code this.getConfig().getName()}
     * 为入口。新栈没有 jfinal 的 Config，故由宿主在构造时把 ds 名交给网关；
     * <b>未提供时这些行为不启用</b>（等价于旧栈"拿不到方言"）。</p>
     */
    private final String ds;

    /** 事务中绑定的连接；非事务时为 null */
    private final ThreadLocal<Connection> txConnection = new ThreadLocal<>();

    /** 连接作用域内的操作（允许抛 SQLException） */
    @FunctionalInterface
    private interface SqlWork<T> {
        /** 在给定连接上执行 */
        T apply(Connection conn) throws SQLException;
    }

    /**
     * 构造网关（不带数据源名 ⇒ 不启用方言专属行为）。
     *
     * @param dataSource 数据源
     */
    public JdbcEovaDbGateway(DataSource dataSource) {
        this(dataSource, null);
    }

    /**
     * 构造网关（携带数据源名 ⇒ 启用 Oracle 序列 / 关键字转义等旧 EovaDbPro 行为）。
     *
     * @param dataSource 数据源
     * @param ds         数据源名（对应旧 {@code Config.getName()}），可为 null
     */
    public JdbcEovaDbGateway(DataSource dataSource, String ds) {
        this.dataSource = dataSource;
        this.ds = ds;
    }

    /**
     * 取数据源名。
     *
     * @return 数据源名；未提供时为 null
     */
    public String ds() {
        return ds;
    }

    // ---------------- 查询 ----------------

    /**
     * 取底层数据源（供 {@code DsUtil} 的表结构自省使用）。
     *
     * <p>本类即数据源的所有者（构造期注入），故直接返回字段；不做任何包装或延迟解析 ——
     * 若在此处"顺手"包一层，自省拿到的将不是真正的连接池。</p>
     *
     * @return 构造期注入的数据源
     */
    @Override
    public DataSource dataSource() {
        return dataSource;
    }

    // ---------------- 查询 ----------------

    /** 查询多行；列名小写化 */
    @Override
    public List<EovaRecord> find(String sql, Object... paras) {
        return queryRecords(sql, paras);
    }

    /** 查询首行；无命中返回 null */
    @Override
    public EovaRecord findFirst(String sql, Object... paras) {
        List<EovaRecord> list = queryRecords(sql, paras);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 按主键查询（主键列名固定 id）；无命中返回 null */
    @Override
    public EovaRecord findById(String table, Object id) {
        return findFirst("select * from " + table + " where id = ?", id);
    }

    /** 分页查询；越界页返回空列表 */
    @Override
    public EovaPage<EovaRecord> paginate(int pageNumber, int pageSize,
                                         String select, String sqlExceptSelect, Object... paras) {
        EovaRecord cnt = findFirst("select count(*) as cnt " + sqlExceptSelect, paras);
        int totalRow = cnt == null ? 0 : cnt.getInt("cnt");
        int offset = (pageNumber - 1) * pageSize;
        List<EovaRecord> list = offset < 0 ? new ArrayList<>()
                : queryRecords(select + " " + sqlExceptSelect + " limit ? offset ?", append(paras, pageSize, offset));
        return new EovaPage<>(pageNumber, pageSize, totalRow, list);
    }

    /** 执行查询并装配为 EovaRecord 列表 */
    /** 记录级查询（私有辅助；与接口的"单列查询"query 区分，故另起名） */
    private List<EovaRecord> queryRecords(String sql, Object... paras) {
        // 旧 EovaDbPro.find/query 都先过关键字转义（save/delete 不过）
        final String escaped = escapeSql(sql);
        return withConnection(conn -> {
            List<EovaRecord> out = new ArrayList<>();
            try (PreparedStatement ps = bind(conn, escaped, paras);
                 ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                // 旧栈的分支：Oracle 数据源由 EovaOracleDialect 挂 OracleRecordBuilder，
                // 走"精准类型"装配（NUMBER 分档 / CLOB/BLOB / 业务转换器，且 null 列不入 map）；
                // 其余方言走 jfinal 默认 RecordBuilder（getObject 原样 + null 也入 map）。
                boolean oracle = isOracle();
                String[] labels = null;
                int[] types = null;
                if (oracle) {
                    labels = new String[n + 1];
                    types = new int[n + 1];
                    EovaRecordValueBuilder.buildLabelNamesAndTypes(md, labels, types);
                    for (int i = 1; i <= n; i++) {
                        // 列名小写化：对应 CaseInsensitiveContainerFactory(true)
                        labels[i] = labels[i].toLowerCase();
                    }
                }
                while (rs.next()) {
                    EovaRecord r = new EovaRecord();
                    if (oracle) {
                        EovaRecordValueBuilder.buildValue(ds, rs, md, n, labels, types, r.getColumns());
                    } else {
                        for (int i = 1; i <= n; i++) {
                            // 列名小写化：对应 CaseInsensitiveContainerFactory(true)
                            r.set(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                        }
                    }
                    // 从库读出的行不算"已修改"
                    r.getModifyFlag().clear();
                    out.add(r);
                }
            }
            return out;
        }, "查询失败: " + escaped);
    }

    /**
     * 是否 Oracle 数据源（旧 {@code EovaDataSource.getDbType(ds) == DbType.oracle}）。
     *
     * @return true 表示 Oracle
     */
    private boolean isOracle() {
        return ds != null && EovaDataSource.getDbType(ds) == DbType.oracle;
    }

    /**
     * SQL 关键字转义（逐字节等价旧 {@code EovaDbPro.escape(Config, String)}）。
     *
     * <p><b>旧实现的调用点只有 {@code find}/{@code query}/{@code update} 三个受保护重载</b>
     * （{@code save}/{@code delete} 家族<b>不</b>转义）—— 本网关据此只在读/改路径调用，
     * 见 {@link #update(String, Object...)} 与各查询入口。</p>
     *
     * @param sql 原始 SQL
     * @return 转义后的 SQL（未命中任何条件时原样返回）
     */
    String escapeSql(String sql) {
        if (ds == null) {
            return sql;
        }
        // 获取DDL方言
        DefineDialect dd = DefineDialectFactory.getDialect(ds);
        DbType dbType = EovaDataSource.getDbType(ds);

        // 系统关键字转义
        if (dbType == DbType.dm) {
            String[] fields = {"eova_dict.object", "dicts.object"};
            for (String field : fields) {
                sql = SqlUtil.escapeSqlKeyword(dd, sql, field);
            }
        } else if (dbType == DbType.h2) {
            String[] fields = {"eova_dict.value", "eova_config.value", "eova_widget.value", "dicts.value"};
            for (String field : fields) {
                sql = SqlUtil.escapeSqlKeyword(dd, sql, field);
            }
        }

        // 用户自定义数据源关键字
        String s = x.conf.get("db.keyword");
        if (!x.isEmpty(s)) {
            String[] fields = s.split(",");
            for (String field : fields) {
                sql = SqlUtil.escapeSqlKeyword(dd, sql, field);
            }
        }

        return sql;
    }

    // ---------------- 写操作 ----------------

    /** 插入一行 */
    @Override
    public boolean save(String table, EovaRecord record) {
        return insert(table, null, record);
    }

    /**
     * INSERT 骨架（含 Oracle 序列表达式内联）。
     *
     * <p><b>为什么序列要"内联"而不是当参数绑定（第 79 轮实测 jfinal 字节码）：</b>
     * {@code OracleDialect.forDbSave} 的规则是 —— 某列的值是 {@code String}、
     * <b>是主键列</b>、且以 {@code ".nextval"} 结尾 ⇒ 把该字符串<b>原样拼进 SQL</b>
     * （得到 {@code values (?, seq_x.nextval)}），否则才写 {@code ?} 并加入参数表。
     * 若把 {@code seq_x.nextval} 当参数绑定，Oracle 会把它当<b>字面量字符串</b>处理 ——
     * 这正是旧栈"主键为空则自动填序列"能生效的机制。</p>
     *
     * @param table      表名
     * @param primaryKey 主键列（可逗号分隔；null/空表示不做序列内联）
     * @param record     记录
     * @return 是否成功
     */
    private boolean insert(String table, String primaryKey, EovaRecord record) {
        Map<String, Object> cols = record.getColumns();
        List<String> pKeys = new ArrayList<>();
        if (primaryKey != null) {
            for (String k : primaryKey.split(",")) {
                pKeys.add(k.trim().toLowerCase());
            }
        }
        StringBuilder names = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : cols.entrySet()) {
            if (names.length() > 0) {
                names.append(", ");
                marks.append(", ");
            }
            names.append(e.getKey());
            Object v = e.getValue();
            if (v instanceof String && pKeys.contains(e.getKey().toLowerCase())
                    && ((String) v).endsWith(".nextval")) {
                // 序列表达式：原样拼进 SQL（不进参数表）—— 与 OracleDialect.forDbSave 一致
                marks.append(v);
            } else {
                marks.append('?');
                args.add(v);
            }
        }
        String sql = "insert into " + table + " (" + names + ") values (" + marks + ")";
        // 旧 EovaDbPro 不覆写 save 链路 ⇒ 插入不转义（见 execute 的说明）
        return execute(false, sql, args.toArray()) > 0;
    }

    /** 按默认主键 id 更新（仅提交已修改字段） */
    @Override
    public boolean update(String table, EovaRecord record) {
        return update(table, "id", record);
    }

    /** 按指定主键更新；仅提交 modifyFlag 字段，提交后清空该标记 */
    @Override
    public boolean update(String table, String primaryKey, EovaRecord record) {
        String pk = primaryKey.toLowerCase();
        Set<String> flag = new LinkedHashSet<>(record.getModifyFlag());
        flag.remove(pk);
        if (flag.isEmpty()) {
            return false;
        }
        StringBuilder sets = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (String c : flag) {
            if (sets.length() > 0) {
                sets.append(", ");
            }
            sets.append(c).append(" = ?");
            args.add(record.getColumns().get(c));
        }
        args.add(record.getColumns().get(pk));
        String sql = "update " + table + " set " + sets + " where " + primaryKey + " = ?";
        boolean ok = update(sql, args.toArray()) > 0;
        // 与旧实现一致：提交后清空修改标记
        record.getModifyFlag().clear();
        return ok;
    }

    /** 按主键删除 */
    @Override
    public boolean delete(String table, EovaRecord record) {
        return deleteById(table, record.getColumns().get("id"));
    }

    /** 按主键删除；未命中返回 false */
    @Override
    public boolean deleteById(String table, Object id) {
        // 旧 EovaDbPro 不覆写 delete 链路 ⇒ 删除不转义
        return execute(false, "delete from " + table + " where id = ?", id) > 0;
    }

    /** 执行更新/DDL；返回受影响行数 */
    @Override
    public int update(String sql, Object... paras) {
        // 旧 EovaDbPro.update(Config, Connection, String, Object...) 会先做关键字转义
        return execute(true, sql, paras);
    }

    /**
     * 执行写语句（受控转义）。
     *
     * <p><b>为什么必须区分（第 87 轮 live readiness 冒烟抓出的真实缺陷）：</b>
     * 第 79 轮把 {@code EovaDbPro.escape} 的语义落进网关时，我让它作用在"读路径 + {@code update}"上，
     * 但<b>实现上 {@code save}/{@code delete} 都经由 {@code update(...)} 下发</b> ⇒ 它们也被转义了。
     * 旧实现里 {@code save} 直接走 {@code Dialect.forDbSave} + {@code executeUpdate}，
     * <b>不经过被覆写的 {@code update}</b> ⇒ 不转义。H2 上真跑时表现为
     * {@code insert into eova_menu (id, code, `value`)} 被反引号引坏而报错 —— 只有真库能暴露。</p>
     *
     * @param escape 是否做关键字转义（true 仅用于 {@code update}）
     * @param sql    SQL
     * @param paras  参数
     * @return 受影响行数
     */
    private int execute(boolean escape, String sql, Object... paras) {
        final String target = escape ? escapeSql(sql) : sql;
        return withConnection(conn -> {
            try (PreparedStatement ps = bind(conn, target, paras)) {
                return ps.executeUpdate();
            }
        }, "执行失败: " + target);
    }

    // ---------------- 事务 ----------------

    /**
     * 当前线程是否已处于本网关的事务中。
     *
     * <p>对应旧 jfinal 的 {@code Config.getThreadLocalConnection() != null} ——
     * {@code LegacyTx} 用它区分"最外层"与"嵌套层"，两者对
     * {@code NestedTransactionHelpException} 的处理<b>相反</b>：最外层回滚并吞掉，
     * 嵌套层向上传播、交由最外层回滚整个外层事务。</p>
     *
     * @return 已处于事务中
     */
    @Override
    public boolean inTransaction() {
        return txConnection.get() != null;
    }

    /**
     * 按缓存查询多行（对应 jfinal {@code DbPro.findByCache}）。委托 {@code EovaGateways} 的缓存查询。
     *
     * @param cacheName 缓存名
     * @param key       缓存键
     * @param sql       查询语句
     * @param paras     参数
     * @return 结果集
     */
    @Override
    public List<EovaRecord> findByCache(String cacheName, Object key, String sql, Object... paras) {
        return EovaGateways.findByCache(cacheName, key, sql, paras);
    }

    /**
     * 生成分页 SQL（对应 jfinal {@code MysqlDialect.forPaginate}）。
     *
     * <p>偏移量 = {@code (pageNumber-1) * pageSize}（与旧制品实测一致，pageNumber=0 时为负数），
     * 形态为 {@code <sql> limit <offset>, <pageSize>}。</p>
     *
     * @param pageNumber 页码
     * @param pageSize   每页条数
     * @param sql        SQL
     * @return 分页 SQL
     */
    @Override
    public String forPaginate(int pageNumber, int pageSize, StringBuilder sql) {
        int offset = (pageNumber - 1) * pageSize;
        // 【实测】旧实现【不 trim】入参 SQL：forPaginate(1,5,"  select 1  ")
        // 产出 "  select 1   limit 0, 5"（原样拼接）。我最初写成 trim 后拼接 —— 判据纠正。
        return sql.toString() + " limit " + offset + ", " + pageSize;
    }

    /**
     * 按列名批量执行同一条 SQL（对应 jfinal
     * {@code DbPro.batch(sql, columns, modelOrRecordList, batchSize)}）。
     *
     * <p>守卫与分块提交语义见接口 javadoc（逐条取自旧字节码）。</p>
     *
     * @param sql        含占位符的语句
     * @param columns    列名（逗号分隔，逐段 trim）
     * @param recordList 记录列表
     * @param batchSize  每批条数
     * @return 各行影响数
     */
    @Override
    public int[] batch(String sql, String columns, List<EovaRecord> recordList, int batchSize) {
        if (recordList == null || recordList.isEmpty()) {
            return new int[0];
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("The batchSize must more than 0.");
        }
        String[] cols = columns.split(",");
        for (int i = 0; i < cols.length; i++) {
            cols[i] = cols[i].trim();
        }
        boolean inTx = txConnection.get() != null;
        Connection conn = inTx ? txConnection.get() : null;
        Connection owned = null;
        try {
            if (conn == null) {
                owned = dataSource.getConnection();
                owned.setAutoCommit(false);
                conn = owned;
            }
            int[] result = new int[recordList.size()];
            int writeIdx = 0;
            int counter = 0;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (EovaRecord r : recordList) {
                    Map<String, Object> columnsMap = r.getColumns();
                    for (int i = 0; i < cols.length; i++) {
                        ps.setObject(i + 1, columnsMap.get(cols[i]));
                    }
                    ps.addBatch();
                    if (++counter >= batchSize) {
                        counter = 0;
                        int[] ret = ps.executeBatch();
                        if (!inTx) {
                            conn.commit();
                        }
                        for (int v : ret) {
                            result[writeIdx++] = v;
                        }
                    }
                }
                if (counter != 0) {
                    int[] ret = ps.executeBatch();
                    if (!inTx) {
                        conn.commit();
                    }
                    for (int v : ret) {
                        result[writeIdx++] = v;
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            if (owned != null) {
                try {
                    owned.rollback();
                } catch (SQLException ignored) {
                    // 回滚失败不掩盖原异常
                }
            }
            throw new IllegalStateException("批量执行失败", e);
        } finally {
            closeQuietly(owned);
        }
    }

    /**
     * 批量保存模型（对应 jfinal {@code DbPro.batchSave(List&lt;? extends Model&gt;, int)}）。
     *
     * <p>逐条按 {@code ModelSqlBuilder.forModelSave} 生成 insert 并执行；空列表返回空数组；
     * {@code batchSize < 1} 抛 {@code IllegalArgumentException}（消息与旧实现逐字一致）。
     * 关于"组批提交 vs 逐条提交"的已声明适配见接口 javadoc。</p>
     *
     * @param models    待保存模型
     * @param batchSize 每批条数
     * @return 各行影响数
     */
    @Override
    public int[] batchSave(List<? extends EovaModel<?>> models, int batchSize) {
        if (models == null || models.isEmpty()) {
            return new int[0];
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("The batchSize must more than 0.");
        }
        int[] result = new int[models.size()];
        int i = 0;
        for (EovaModel<?> m : models) {
            TableMetadata table = EovaTableMapping.me().getTable(m.getClass());
            ModelSqlBuilder.Sql sql = ModelSqlBuilder.forModelSave(table, m._getAttrs());
            result[i++] = update(sql.sql(), sql.paras());
        }
        return result;
    }

    /**
     * 指定主键列的插入（对应 jfinal {@code DbPro.save(tableName, primaryKey, record)}）。
     * 已声明适配见接口 javadoc（本网关按 id 列处理生成键）。
     *
     * @param table      表名
     * @param primaryKey 主键列名
     * @param record     记录
     * @return 是否成功
     */
    @Override
    public boolean save(String table, String primaryKey, EovaRecord record) {
        // 逐行等价旧 EovaDbPro.save：
        //   boolean isOracle = EovaDataSource.getDbType(ds) == DbType.oracle;
        //   if (isOracle && !primaryKey.contains(",") && record.get(primaryKey) == null)
        //       record.set(primaryKey, SqlUtil.getSequence(ds, table));
        boolean oracle = isOracle();
        if (oracle && !primaryKey.contains(",") && record.get(primaryKey) == null) {
            record.set(primaryKey, SqlUtil.getSequence(ds, table));
        }
        return insert(table, primaryKey, record);
    }

    /**
     * 按指定主键列删除（对应 jfinal {@code DbPro.deleteById(tableName, primaryKey, idValue)}）。
     *
     * <p>SQL 形态与旧制品实测一致：{@code delete from `表` where `主键` = ?}。</p>
     *
     * @param table      表名
     * @param primaryKey 主键列名（可逗号分隔）
     * @param idValue    主键值
     * @return 是否删除了行
     */
    @Override
    public boolean deleteById(String table, String primaryKey, Object idValue) {
        return delete(deleteByIdSql(table, primaryKey), idValue) > 0;
    }

    /**
     * 生成"按主键删除"的 SQL（对应旧制品 {@code MysqlDialect.forDbDeleteById} 的实测输出）。
     *
     * <p><b>为什么抽成包内静态方法：</b>它需要真实库才能端到端验证，而 SQL 形态本身是契约。
     * 抽出来判据才能在没有数据库时也把"表名/主键 trim、反引号、多主键用 {@code  and } 连接"
     * 逐字钉住 —— 第 74 轮实测：不抽出来时，"去掉 trim"的变异**逃过**了判据。</p>
     *
     * @param table      表名（会 trim）
     * @param primaryKey 主键列名（逗号分隔，各段 trim）
     * @return 删除 SQL
     */
    static String deleteByIdSql(String table, String primaryKey) {
        String[] pks = primaryKey.split(",");
        for (int i = 0; i < pks.length; i++) {
            pks[i] = pks[i].trim();
        }
        StringBuilder sql = new StringBuilder("delete from `").append(table.trim()).append("` where ");
        for (int i = 0; i < pks.length; i++) {
            if (i > 0) {
                sql.append(" and ");
            }
            sql.append('`').append(pks[i]).append("` = ?");
        }
        return sql.toString();
    }

    /**
     * 按指定主键列查询（对应 jfinal {@code DbPro.findById(tableName, primaryKey, idValue)}）。
     *
     * <p>SQL 形态与 jfinal 5.2.6 的 {@code MysqlDialect.forDbFindById} <b>实测输出</b>一致：
     * {@code select * from `表` where `主键` = ?}（表名与各主键 trim；多主键用 {@code  and } 连接）。
     * 主键个数与值个数不匹配时，旧实现抛
     * {@code IllegalArgumentException("primary key number must equals id value number")}。</p>
     *
     * @param table      表名
     * @param primaryKey 主键列名（可逗号分隔）
     * @param idValue    主键值
     * @return 命中行；无命中返回 null
     */
    @Override
    public EovaRecord findById(String table, String primaryKey, Object idValue) {
        return findFirst(findByIdSql(table, primaryKey), idValue);
    }

    /**
     * 生成"按主键查询"的 SQL（对应旧制品 {@code MysqlDialect.forDbFindById} 的实测输出）。
     *
     * <p><b>空格教训：</b>旧制品产出 {@code where `id` = ?}（{@code where} 与反引号之间<b>有空格</b>）。
     * 我最初照 javap 注释里的 `` ` where `` 字面拼接，漏掉了那个<b>尾随空格</b>，
     * 得到 {@code where`id`} —— 直到第 74 轮把 SQL 构造抽出来断言才暴露（javap 的注释显示会吃掉尾随空格，
     * 本工程在 {@code Captcha.toString} 上栽过同类问题）。</p>
     *
     * @param table      表名（会 trim）
     * @param primaryKey 主键列名（逗号分隔，各段 trim）
     * @return 查询 SQL
     */
    static String findByIdSql(String table, String primaryKey) {
        StringBuilder sql = new StringBuilder("select * from `")
                .append(table.trim()).append("` where ");
        String[] pks = primaryKey.split(",");
        for (int i = 0; i < pks.length; i++) {
            if (i > 0) {
                sql.append(" and ");
            }
            sql.append('`').append(pks[i].trim()).append("` = ?");
        }
        return sql.toString();
    }

    /**
     * 批量执行多条 SQL（对应 jfinal {@code DbPro.batch(List&lt;String&gt;, int)}）。
     *
     * <p>旧实现（字节码逐条读出）：空列表返回 {@code new int[0]}；{@code batchSize < 1}
     * 抛 {@code IllegalArgumentException("The batchSize must more than 0.")}；
     * 按 batchSize 分块 addBatch/executeBatch，<b>非事务时每块提交</b>；
     * 结果压平到长度 {@code sqlList.size()} 的数组前部。</p>
     *
     * @param sqlList   待执行 SQL
     * @param batchSize 每批条数
     * @return 各行影响数
     */
    @Override
    public int[] batch(List<String> sqlList, int batchSize) {
        if (sqlList == null || sqlList.isEmpty()) {
            return new int[0];
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("The batchSize must more than 0.");
        }
        boolean inTx = txConnection.get() != null;
        Connection bound = txConnection.get();
        if (inTx) {
            // 事务中：复用绑定连接，交由外层事务统一提交/回滚
            return doBatch(bound, sqlList, batchSize, result -> { }, true);
        }
        Connection c = null;
        try {
            c = dataSource.getConnection();
            final Connection conn = c;
            boolean auto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            int[] r = doBatch(conn, sqlList, batchSize, r2 -> { }, false);
            conn.setAutoCommit(auto);
            return r;
        } catch (SQLException e) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // 回滚失败不掩盖原异常
                }
            }
            throw new IllegalStateException("批量执行失败", e);
        } finally {
            closeQuietly(c);
        }
    }

    /**
     * 分块执行并压平结果。
     *
     * @param conn      连接
     * @param sqlList   SQL 列表
     * @param batchSize 每批条数
     * @param afterEach 每块执行后的回调（预留）
     * @param inTx      是否处于外层事务（为真则每块后不提交）
     * @return 影响数数组
     */
    private int[] doBatch(Connection conn, List<String> sqlList, int batchSize,
                          java.util.function.Consumer<int[]> afterEach, boolean inTx) {
        int[] result = new int[sqlList.size()];
        int writeIdx = 0;
        int counter = 0;
        try (Statement st = conn.createStatement()) {
            for (String sql : sqlList) {
                st.addBatch(sql);
                if (++counter >= batchSize) {
                    counter = 0;
                    int[] ret = st.executeBatch();
                    if (!inTx) {
                        conn.commit();
                    }
                    afterEach.accept(ret);
                    for (int v : ret) {
                        result[writeIdx++] = v;
                    }
                }
            }
            if (counter != 0) {
                int[] ret = st.executeBatch();
                if (!inTx) {
                    conn.commit();
                }
                afterEach.accept(ret);
                for (int v : ret) {
                    result[writeIdx++] = v;
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("批量执行失败", e);
        }
    }

    /**
     * 事务执行（布尔驱动版，对应 jfinal {@code DbPro.tx(IAtom)}）。
     *
     * <p>语义逐条取自旧字节码，见 {@link cn.eova.compat.jfinal.plugin.activerecord.LegacyIAtom}。
     * 关键点：<b>嵌套分支返回 false 时抛 {@link LegacyNestedTransactionHelpException}</b>
     * （消息逐字取自旧常量池），由最外层捕获后回滚并<b>静默返回 false</b>。</p>
     *
     * @param atom 事务体
     * @return 事务体返回值
     */
    @Override
    public boolean tx(LegacyIAtom atom) {
        if (txConnection.get() != null) {
            // 嵌套：并入当前事务（不提交、不关闭）
            try {
                if (atom.run()) {
                    return true;
                }
                throw new LegacyNestedTransactionHelpException(
                        "Notice the outer transaction that the nested transaction return false");
            } catch (SQLException e) {
                throw new EovaActiveRecordException(e.toString(), e);
            }
        }
        Connection c = null;
        try {
            c = dataSource.getConnection();
            c.setAutoCommit(false);
            txConnection.set(c);
            boolean result = atom.run();
            if (result) {
                c.commit();
            } else {
                c.rollback();
            }
            return result;
        } catch (LegacyNestedTransactionHelpException e) {
            // 旧实现：回滚 + logNothing + 返回 false（静默）
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // 回滚失败不掩盖原意
                }
            }
            return false;
        } catch (Throwable t) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // 回滚失败不掩盖原异常
                }
            }
            throw wrap(t);
        } finally {
            txConnection.remove();
            closeQuietly(c);
        }
    }

    /** 事务执行；异常回滚、正常提交 */
    @Override
    public <T> T tx(Atom<T> atom) {
        if (txConnection.get() != null) {
            // 已在事务中：并入当前事务
            try {
                return atom.run();
            } catch (Throwable t) {
                throw wrap(t);
            }
        }
        Connection c = null;
        try {
            c = dataSource.getConnection();
            c.setAutoCommit(false);
            txConnection.set(c);
            T result = atom.run();
            c.commit();
            return result;
        } catch (Throwable t) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // 回滚失败不掩盖原异常
                }
            }
            throw wrap(t);
        } finally {
            txConnection.remove();
            closeQuietly(c);
        }
    }

    // ---------------- 连接管理 ----------------

    /**
     * 连接作用域：事务中复用绑定连接（不关闭），否则独占取用并在结束时关闭。
     *
     * <p>这样写而不是包装 PreparedStatement —— 后者需要手写实现整个 JDBC 接口，易漏方法。
     */
    private <T> T withConnection(SqlWork<T> work, String errMsg) {
        Connection bound = txConnection.get();
        if (bound != null) {
            try {
                return work.apply(bound);
            } catch (SQLException e) {
                throw new IllegalStateException(errMsg, e);
            }
        }
        try (Connection c = dataSource.getConnection()) {
            return work.apply(c);
        } catch (SQLException e) {
            throw new IllegalStateException(errMsg, e);
        }
    }

    /** 绑定参数 */
    /**
     * 执行 insert 并取回自增生成的主键（对应旧实现
     * {@code prepareStatement(sql, RETURN_GENERATED_KEYS)} + {@code Dialect.getModelGeneratedKey}）。
     *
     * <p>旧 {@code Model.save()} 在插入后会把生成的主键写回模型，因此"插入后模型的 pk 被填充"
     * 是可观测行为，必须保留。无生成键时返回 null。
     *
     * @param sql   insert 语句
     * @param paras 参数
     * @return 生成的主键值；无则 null
     */
    @Override
    public int delete(String sql, Object... paras) {
        // 与 DbPro.delete 同构：就是 update 的执行路径（删除也是 update 语句）
        return update(sql, paras);
    }

    @Override
    public Long queryLong(String sql, Object... paras) {
        Number n = queryNumber(sql, paras);
        // 与 jfinal DbPro.queryLong 同构：非 null 才拆箱
        return n == null ? null : n.longValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> query(String sql, Object... paras) {
        return withConnection(conn -> {
            try (PreparedStatement ps = bind(conn, sql, paras);
                 ResultSet rs = ps.executeQuery()) {
                // 与 jfinal DbPro.query 逐分支同构（据【完整方法体】，非片段推断）：
                //   列数 > 1 → 每行一个 Object[]（整行）
                //   列数 = 1 → 每行的第 1 列值（标量）
                //   列数 = 0 → 空列表
                // 注意此处【不抛】"Only ONE COLUMN can be queried."——那条属 queryColumn。
                // （我先后猜过"多列抛异常"和"总是取首列"，两次都被判据纠正。）
                List<T> out = new java.util.ArrayList<>();
                int columnsCount = rs.getMetaData().getColumnCount();
                if (columnsCount > 1) {
                    while (rs.next()) {
                        Object[] row = new Object[columnsCount];
                        for (int i = 0; i < columnsCount; i++) {
                            row[i] = rs.getObject(i + 1);
                        }
                        out.add((T) row);
                    }
                } else if (columnsCount == 1) {
                    while (rs.next()) {
                        out.add((T) rs.getObject(1));
                    }
                }
                return out;
            }
        }, "查询失败: " + sql);
    }

    @Override
    public Number queryNumber(String sql, Object... paras) {
        return withConnection(conn -> {
            try (PreparedStatement ps = bind(conn, sql, paras);
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Object v = rs.getObject(1);
                if (v == null) {
                    return null;
                }
                if (v instanceof Number) {
                    return (Number) v;
                }
                return new java.math.BigDecimal(v.toString());
            }
        }, "查询数值失败: " + sql);
    }

    @Override
    public Object insertReturningKey(String sql, Object[] paras) {
        return withConnection(conn -> {
            try (PreparedStatement ps =
                         conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < paras.length; i++) {
                    ps.setObject(i + 1, paras[i]);
                }
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    return rs != null && rs.next() ? rs.getObject(1) : null;
                }
            }
        }, "插入失败: " + sql);
    }

    private static PreparedStatement bind(Connection conn, String sql, Object... paras) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < paras.length; i++) {
            ps.setObject(i + 1, paras[i]);
        }
        return ps;
    }

    /** 静默关闭连接并复位 autocommit */
    private static void closeQuietly(Connection c) {
        if (c != null) {
            try {
                c.setAutoCommit(true);
                c.close();
            } catch (SQLException ignored) {
                // 关闭失败不影响结果
            }
        }
    }

    /** 把 Throwable 规整为 RuntimeException（保留 cause） */
    private static RuntimeException wrap(Throwable t) {
        return t instanceof RuntimeException re ? re : new IllegalStateException(t.getMessage(), t);
    }

    /** 把参数数组追加若干参数 */
    private static Object[] append(Object[] paras, Object... extra) {
        Object[] out = new Object[paras.length + extra.length];
        System.arraycopy(paras, 0, out, 0, paras.length);
        System.arraycopy(extra, 0, out, paras.length, extra.length);
        return out;
    }
}
