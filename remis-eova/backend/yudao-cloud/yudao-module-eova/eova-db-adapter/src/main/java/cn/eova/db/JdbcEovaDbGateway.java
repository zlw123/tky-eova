/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
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

    /** 事务中绑定的连接；非事务时为 null */
    private final ThreadLocal<Connection> txConnection = new ThreadLocal<>();

    /** 连接作用域内的操作（允许抛 SQLException） */
    @FunctionalInterface
    private interface SqlWork<T> {
        /** 在给定连接上执行 */
        T apply(Connection conn) throws SQLException;
    }

    /**
     * 构造网关
     *
     * @param dataSource 数据源
     */
    public JdbcEovaDbGateway(DataSource dataSource) {
        this.dataSource = dataSource;
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
        return withConnection(conn -> {
            List<EovaRecord> out = new ArrayList<>();
            try (PreparedStatement ps = bind(conn, sql, paras);
                 ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                while (rs.next()) {
                    EovaRecord r = new EovaRecord();
                    for (int i = 1; i <= n; i++) {
                        // 列名小写化：对应 CaseInsensitiveContainerFactory(true)
                        r.set(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                    }
                    // 从库读出的行不算"已修改"
                    r.getModifyFlag().clear();
                    out.add(r);
                }
            }
            return out;
        }, "查询失败: " + sql);
    }

    // ---------------- 写操作 ----------------

    /** 插入一行 */
    @Override
    public boolean save(String table, EovaRecord record) {
        Map<String, Object> cols = record.getColumns();
        StringBuilder names = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : cols.entrySet()) {
            if (names.length() > 0) {
                names.append(", ");
                marks.append(", ");
            }
            names.append(e.getKey());
            marks.append('?');
            args.add(e.getValue());
        }
        String sql = "insert into " + table + " (" + names + ") values (" + marks + ")";
        return update(sql, args.toArray()) > 0;
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
        return update("delete from " + table + " where id = ?", id) > 0;
    }

    /** 执行更新/DDL；返回受影响行数 */
    @Override
    public int update(String sql, Object... paras) {
        return withConnection(conn -> {
            try (PreparedStatement ps = bind(conn, sql, paras)) {
                return ps.executeUpdate();
            }
        }, "执行失败: " + sql);
    }

    // ---------------- 事务 ----------------

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
