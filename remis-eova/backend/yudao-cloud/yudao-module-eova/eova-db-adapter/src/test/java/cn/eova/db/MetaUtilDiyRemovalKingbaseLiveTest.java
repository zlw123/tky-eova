/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import cn.eova.common.Ds;
import cn.eova.config.EovaDataSource;
import cn.eova.core.meta.MetaUtil;
import cn.eova.db.EovaRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **`MetaUtil.removeUserDiy`/`removeRoleDiy` 判据的 KingbaseES 版（第 234 轮）** —— 造数据、跨库对称。
 *
 * <p>与 MySQL 版（`MetaUtilDiyRemovalLiveTest`）同源的五条契约：① `removeUserDiy` 对"仍在 `fields` 中"
 * 的字段不删、其余只删该 uid；② `uid == null` ⇒ 删该字段所有用户的行；③ `removeRoleDiy` `type=1`
 * （白名单）⇒ 删 `uid not in (角色用户)`（角色用户保留）；④ `type=2`（黑名单）⇒ 删角色用户；⑤ `type=2`
 * 且角色无用户 ⇒ 实现是 `return`（非 `continue`）⇒ 两个字段都不删（既有行为）。
 *
 * <p><b>★ 跨库造数据的类型处理（r234 recon 实测）</b>：`eova_diy` 在两库的列名一致，但
 * `is_open` 是 **MySQL `tinyint(1)` / Kingbase `boolean`**（r173 同族陷阱）；其余可选列
 * （`type/num/width/is_open/defaulter`）**两库均可空且有默认值** ⇒ 本判据**只插 NOT NULL 列**
 * （`id/uid/object_code/en/cn`），从而**绕开类型差异**、同一份插入语句两库通用。
 *
 * <p><b>清理与自证</b>：造的行 `object_code` 前缀 `r234`；teardown 按前缀删除并**断言计数为 0**
 * （该表两库基线均为 0 行）。纪律：清理条件不得依赖被测实现产出的命名（r230 教训）。
 * 判别性：每条用例都插**目标行 + 对照组行**（r170 口径）。
 *
 * <p><b>跳过口径（R77）</b>：Kingbase 不可达 ⇒ 逐条跳过，不得记为通过。
 */
class MetaUtilDiyRemovalKingbaseLiveTest {

    private static final String URL = System.getProperty("eova.kingbase.meta.url",
            "jdbc:kingbase8://base.platform:54321/eova_meta");
    private static final String DB_USER = System.getProperty("eova.kingbase.user", "system");
    private static final String DB_PWD = System.getProperty("eova.kingbase.pwd", "Rmtlwrm@@2026");
    private static final String P = "r234";
    private static final int OUTSIDER = 999999;

    private static boolean reachable() {
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD)) {
            return c.isValid(3);
        } catch (Throwable e) {
            return false;
        }
    }

    private static final boolean BASELINE_UP = reachable();

    /** 极简直连 DataSource（Kingbase 无随包实现可依赖） */
    private static DataSource dataSource() {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(URL, DB_USER, DB_PWD);
            }

            @Override
            public Connection getConnection(String u, String p) throws SQLException {
                return DriverManager.getConnection(URL, u, p);
            }

            @Override
            public PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(PrintWriter out) {
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
        };
    }

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(BASELINE_UP,
                "Kingbase baseline 不可达 ⇒ 逐条跳过（不得记为通过）：" + URL);
        EovaDataSource.register(Ds.EOVA, URL, "com.kingbase8.Driver");
        EovaGateways.register(Ds.EOVA, new JdbcEovaDbGateway(dataSource(), Ds.EOVA));
        cleanup();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanup();
        assertEquals(0, count(), "★ 清理后不得残留 r234 行（自证 baseline 已复原）");
        EovaDataSource.clear();
        EovaGateways.clear();
    }

    /** 按前缀清掉本判据造的行（不依赖被测实现产出的任何命名） */
    private static void cleanup() throws Exception {
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             Statement st = c.createStatement()) {
            st.executeUpdate("delete from eova_diy where object_code like '" + P + "%'");
        }
    }

    private static int count() throws Exception {
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select count(*) from eova_diy where object_code like '" + P + "%'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 只插 NOT NULL 列（两库通用；可选列有默认值 ⇒ 绕开 is_open 的 int/boolean 差异） */
    private static void insertDiy(String objectCode, String en, int uid) throws Exception {
        String sql = "insert into eova_diy (id, uid, object_code, en, cn) values (?, ?, ?, ?, ?)";
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, 23400000 + Math.abs((objectCode + en + uid).hashCode() % 100000));
            ps.setInt(2, uid);
            ps.setString(3, objectCode);
            ps.setString(4, en);
            ps.setString(5, "标签" + en);
            ps.executeUpdate();
        }
    }

    /** 该 object_code 下现存行，渲染为 "en/uid" 列表（按 en,uid 排序） */
    private static List<String> rows(String objectCode) throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             PreparedStatement ps = c.prepareStatement(
                     "select en, uid from eova_diy where object_code = ? order by en, uid")) {
            ps.setString(1, objectCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("en") + "/" + rs.getInt("uid"));
                }
            }
        }
        return out;
    }

    /** 取真实库里某角色的用户 id */
    private static List<Integer> userIdsOfRid(int rid) throws Exception {
        List<Integer> ids = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             PreparedStatement ps = c.prepareStatement("select id from eova_user where rid = ? order by id")) {
            ps.setInt(1, rid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /** 造"字段记录"（removeUserDiy 的 fields 只读 en） */
    private static List<EovaRecord> fieldsWith(String... ens) {
        List<EovaRecord> out = new ArrayList<>();
        for (String en : ens) {
            EovaRecord r = new EovaRecord();
            r.set("en", en);
            out.add(r);
        }
        return out;
    }

    @Test
    @DisplayName("★ removeUserDiy：仍在 fields 里的不删；不在的只删指定 uid（Kingbase）")
    void removeUserDiySkipsPresentFieldsAndRespectsUid() throws Exception {
        String obj = P + "_user";
        insertDiy(obj, "f1", 23401);
        insertDiy(obj, "f2", 23401);
        insertDiy(obj, "f2", 23402);
        assertEquals(Arrays.asList("f1/23401", "f2/23401", "f2/23402"), rows(obj), "前置：三行都在");

        MetaUtil.removeUserDiy(obj, fieldsWith("f1"), Arrays.asList("f1", "f2"), 23401);

        assertEquals(Arrays.asList("f1/23401", "f2/23402"), rows(obj),
                "★ 只删 (f2,23401)：f1 仍在 fields 中故保留，f2 的另一用户行也须保留");
    }

    @Test
    @DisplayName("★ removeUserDiy：uid=null 删该字段所有用户的行（Kingbase）")
    void removeUserDiyWithNullUidDeletesAllUsers() throws Exception {
        String obj = P + "_user_null";
        insertDiy(obj, "f1", 23401);
        insertDiy(obj, "f1", 23402);
        insertDiy(obj, "ok", 23401);

        MetaUtil.removeUserDiy(obj, new ArrayList<EovaRecord>(), Arrays.asList("f1"), null);

        assertEquals(Arrays.asList("ok/23401"), rows(obj), "★ f1 两行都应删，ok 不受影响");
    }

    @Test
    @DisplayName("★ removeRoleDiy type=1（白名单）：角色用户保留、他人删（Kingbase）")
    void removeRoleDiyWhitelistKeepsRoleUsers() throws Exception {
        String obj = P + "_role_w";
        List<Integer> roleUsers = userIdsOfRid(1);
        Assumptions.assumeTrue(!roleUsers.isEmpty(), "基线里 rid=1 必须有用户");
        insertDiy(obj, "f1", roleUsers.get(0));
        insertDiy(obj, "f1", OUTSIDER);

        MetaUtil.removeRoleDiy(obj, "f1", "1", 1);

        assertEquals(Arrays.asList("f1/" + roleUsers.get(0)), rows(obj),
                "★ 白名单：角色 1 用户保留，999999 的行被删");
    }

    @Test
    @DisplayName("★ removeRoleDiy type=2（黑名单）：角色用户删、他人留（Kingbase）")
    void removeRoleDiyBlacklistDeletesRoleUsers() throws Exception {
        String obj = P + "_role_b";
        List<Integer> roleUsers = userIdsOfRid(1);
        Assumptions.assumeTrue(!roleUsers.isEmpty(), "基线里 rid=1 必须有用户");
        insertDiy(obj, "f1", roleUsers.get(0));
        insertDiy(obj, "f1", OUTSIDER);

        MetaUtil.removeRoleDiy(obj, "f1", "1", 2);

        assertEquals(Arrays.asList("f1/" + OUTSIDER), rows(obj),
                "★ 黑名单：角色 1 用户被删，非该角色的行保留");
    }

    @Test
    @DisplayName("★ removeRoleDiy type=2 且角色无用户：既有 return 语义 ⇒ 一行都不删（Kingbase）")
    void removeRoleDiyBlacklistWithNoMatchingRoleDeletesNothing() throws Exception {
        String obj = P + "_role_none";
        insertDiy(obj, "f1", 23401);
        insertDiy(obj, "f2", 23402);

        MetaUtil.removeRoleDiy(obj, "f1,f2", "987654", 2);

        assertEquals(Arrays.asList("f1/23401", "f2/23402"), rows(obj),
                "★ type=2 且无对应用户时是 return（不是 continue）⇒ 两个字段都不删");
    }

    @Test
    @DisplayName("★ 清理自证：teardown 后本判据的行必须为 0（前置条件）")
    void cleanupIsProvable() throws Exception {
        insertDiy(P + "_probe", "f1", 23401);
        assertTrue(count() > 0, "前置：插入后应有行");
        cleanup();
        assertEquals(0, count(), "★ 按前缀清理后必须为 0（不依赖被测实现的命名）");
    }
}
