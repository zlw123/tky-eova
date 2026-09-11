/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.eova.compat.table.TableMetadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **`EovaMysqlDialect`(18) 的待验收实跑（第 175 轮）** —— 该单元登记为 `NOT_PORTED`，
 * 其待验收原文是：
 *
 * <blockquote>在一次 acceptanceProfile 实跑里，核对 `JdbcTableMetadataSource` 与旧路径
 * （`select * from \`eova_field\` where false` + `ResultSetMetaData`）在**同一 baseline 库**上
 * 返回【相同的列序与列名集合】。当前状态：not executed（缺 baseline 库连接）。
 * **在完成该实跑前，本项不得标记 verified。**</blockquote>
 *
 * <p><b>为什么这条能判、且该判：</b>被不 port 的不是"一个没用的类"，而是**一条既有缺陷规避**——
 * 旧 `EovaMysqlDialect` 只是把 jfinal `TableBuilder` 的自省 SQL 换成
 * {@code select * from `t` where false}（原注释：修 8.0.26+ 与 5.1.30 并存时 `where 1 = 2`
 * 导致的异常/阻塞）。新栈的自省改走 `DatabaseMetaData`，**不再执行该 SQL** ⇒ 换路径之后，
 * "两条路径给出的列序与列名集合是否一致"就成了一条**必须实跑才能回答**的等价性问题
 * （列序尤其要紧：旧 `Model.save()` 生成的 insert 列序就来源于自省结果）。
 *
 * <p><b>本判据不去"port 那个类"，而是把它的待验收做成可执行的判据</b>（R53 的口径：
 * 降级必须带可执行验收条件）。三路互证：
 * <ol>
 *   <li><b>旧路径</b>：逐字复现旧方言产出的 SQL（含反引号与 `where false`）并在**真 MySQL** 上执行，
 *       读 {@code ResultSetMetaData} 的列名与顺序；</li>
 *   <li><b>新路径</b>：{@code JdbcTableMetadataSource}（走 {@code DatabaseMetaData}）；</li>
 *   <li><b>独立第三方</b>：{@code information_schema.columns} 的 {@code ORDINAL_POSITION}
 *       —— 用来确认"顺序"不是两条路径一起错成同一个样。</li>
 * </ol>
 *
 * <p><b>跳过口径（R77）：</b>baseline 不可达时在 {@code @BeforeEach} 逐条跳过，
 * 报告为 {@code tests="2" skipped="2"}，不得记为通过。
 *
 * <p><b>未覆盖（如实登记）</b>：旧栈的 {@code TableBuilder} 本体无法在 Java 17 新栈里执行
 * （生产代码不得引用 jfinal），故本判据复现的是**该 SQL 形态 + ResultSetMetaData 读取**这一
 * 可观测部分；`where 1 = 2` 在 8.0.26+/5.1.30 上的历史异常形态需对应版本环境，本机无。
 */
class MysqlDialectSelfInspectionEquivalenceTest {

    private static final String META_URL = System.getProperty("eova.mysql.meta.url",
            "jdbc:mysql://127.0.0.1:13306/eova_meta?useUnicode=true&characterEncoding=UTF-8"
                    + "&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
                    + "&connectTimeout=3000");
    private static final String DB_USER = System.getProperty("eova.mysql.user", "root");
    private static final String DB_PWD = System.getProperty("eova.mysql.pwd", "root");

    /** 真实 baseline 里的表（取自 eova_meta 库，覆盖元数据表与业务表两种形态） */
    private static final String[] TABLES = {
            "eova_field", "eova_object", "eova_menu", "eova_user", "eova_button", "eova_role_btn",
    };

    private static boolean reachable() {
        try (Connection c = DriverManager.getConnection(META_URL, DB_USER, DB_PWD)) {
            return c.isValid(3);
        } catch (Throwable e) {
            return false;
        }
    }

    private static final boolean BASELINE_UP = reachable();

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(BASELINE_UP,
                "baseline MySQL 不可达 ⇒ 逐条跳过（该项回到 not executed，不得记为通过）：" + META_URL);
    }

    /**
     * 旧方言 `forTableBuilderDoBuild` 的 SQL 形态（逐字：反引号 + where false）。
     *
     * @param tableName 表名
     * @return 旧路径会执行的 SQL
     */
    private static String legacySql(String tableName) {
        // 逐字取自旧 EovaMysqlDialect.forTableBuilderDoBuild：
        //   return "select * from `" + tableName + "` where false";
        return "select * from `" + tableName + "` where false";
    }

    /** 旧路径：执行旧方言 SQL 并从 ResultSetMetaData 读列名（顺序即结果集顺序） */
    private static List<String> legacyColumns(Connection c, String table) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(legacySql(table))) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                out.add(md.getColumnName(i));
            }
        }
        return out;
    }

    /**
     * 极简直连 {@link javax.sql.DataSource}（判据只需"给连接"这一个能力）。
     *
     * <p>{@code JdbcTableMetadataSource} 的构造器只接 DataSource，而本判据要复用同一条
     * 真实连接做三路互证 ⇒ 用一个每次新建连接的直连实现即可（无池语义需求）。
     */
    private static final class DirectDataSource implements javax.sql.DataSource {
        private final String url;

        DirectDataSource(String url) {
            this.url = url;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, DB_USER, DB_PWD);
        }

        @Override
        public Connection getConnection(String u, String p) throws SQLException {
            return DriverManager.getConnection(url, u, p);
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            throw new java.sql.SQLFeatureNotSupportedException("no parent logger");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    /** 第三方独立来源：information_schema 的 ORDINAL_POSITION 顺序 */
    private static List<String> infoSchemaColumns(Connection c, String table) throws SQLException {
        List<String> out = new ArrayList<>();
        String sql = "select column_name from information_schema.columns"
                + " where table_schema = database() and table_name = ? order by ordinal_position";
        try (java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("★ 待验收实跑：旧路径 SQL 与 JdbcTableMetadataSource 的列序、列名集合逐表一致")
    void legacyPathAndMetadataSourceAgree() throws Exception {
        // 先确认"旧路径 SQL"确实能在真库上执行 —— 这正是该方言存在的理由
        try (Connection c = DriverManager.getConnection(META_URL, DB_USER, DB_PWD)) {
            assertTrue(legacySql("eova_field").equals("select * from `eova_field` where false"),
                    "旧方言产出的 SQL 形态必须逐字保持（反引号 + where false）");
            assertTrue(!legacyColumns(c, "eova_field").isEmpty(),
                    "旧路径 SQL 必须能在真实 MySQL 上执行（where false 返回 0 行，但仍给出列元数据）");

            JdbcTableMetadataSource source = new JdbcTableMetadataSource(new DirectDataSource(META_URL));
            for (String table : TABLES) {
                List<String> oldOrder = legacyColumns(c, table);
                List<String> thirdParty = infoSchemaColumns(c, table);
                TableMetadata meta = source.metadata(table);
                assertNotNull(meta, "自省必须解析出表：" + table);

                List<String> newOrder = List.of(meta.columns());
                Set<String> newSet = meta.getColumnNameSet();

                // ① 列序：新路径（ORDINAL_POSITION）必须与旧路径（结果集顺序）一致
                assertEquals(oldOrder, newOrder, "【列序必须一致】表=" + table
                        + "\n  旧路径(RSMD)=" + oldOrder + "\n  新路径(自省)=" + newOrder);

                // ② 列名集合：一致
                assertEquals(new HashSet<>(oldOrder), newSet, "【列名集合必须一致】表=" + table);

                // ③ 第三方互证：顺序确实等于库里的 ORDINAL_POSITION，而不是两条路径一起错
                assertEquals(thirdParty, newOrder, "【顺序须与 information_schema 的 ORDINAL_POSITION 一致】表=" + table);
                assertEquals(thirdParty.size(), newSet.size(), "列数一致：" + table);
            }
        }
    }

    @Test
    @DisplayName("★ 该等价性是「可判别」的：列序确实是库序而非字母序（baseline 上二者不同）")
    void orderingClaimIsDiscriminable() throws Exception {
        try (Connection c = DriverManager.getConnection(META_URL, DB_USER, DB_PWD)) {
            JdbcTableMetadataSource source = new JdbcTableMetadataSource(new DirectDataSource(META_URL));
            String[] cols = source.metadata("eova_field").columns();
            List<String> asIs = List.of(cols);
            List<String> sorted = new ArrayList<>(asIs);
            sorted.sort(String::compareTo);

            // 若 baseline 的列序恰好等于字母序，上面的判据就无法区分"取库序"与"排序"两种实现
            assertTrue(!asIs.equals(sorted),
                    "★ 本表列序必须与字母序不同，否则『列序一致』这条断言不可判别（R170 口径）。"
                            + "实际列序=" + asIs);
            assertTrue(asIs.contains("id") && asIs.indexOf("id") == 0,
                    "eova_field 的首列应为 id（真实库序）：" + asIs);
        }
    }
}
