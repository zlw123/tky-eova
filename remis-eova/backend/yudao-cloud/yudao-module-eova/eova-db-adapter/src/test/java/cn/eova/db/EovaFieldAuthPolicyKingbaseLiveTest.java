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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import cn.eova.common.Ds;
import cn.eova.config.EovaDataSource;
import cn.eova.config.EovaFieldAuth;
import cn.eova.model.User;
import cn.eova.tools.x;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **`EovaFieldAuth` 字段权限判据的第二 baseline 版（KingbaseES，第 222 轮）**。
 *
 * <p><b>为什么单独一类而非参数化：</b>MySQL 版（{@code EovaFieldAuthPolicyLiveTest}）覆盖 6 条
 * 含企业维度的完整契约；本类只取其中**最可判别且与库类型无关**的四条，跑在第二个 baseline 上，
 * 用于回答"同一份 ported 逻辑在两套 baseline 上行为是否一致"。断言刻意**不做全量对称**
 * （不假装覆盖企业分组/单企业重载那两条）—— 那两条的跨库等价由 MySQL 版 + r218 的结构比对承担。
 *
 * <p><b>条数：4 条</b>：① 门控关闭 ⇒ 不加载（哨兵键）；② `type=1` 白名单：未授权 rid 禁用、已授权不禁用；
 * ③ `adminRole` 内的角色跳过白名单；④ `field="a,b"` 多字段都入禁用集。
 *
 * <p><b>两库结构同构（r218 实测）</b>：`id/company_id/type/object/field/auth` 六列在 MySQL 与
 * Kingbase 上名称与 NOT NULL 约束一致（仅 `int↔integer`、`varchar(50)↔varchar` 之类的等价差异）
 * ⇒ 造数据语句可共用。
 *
 * <p><b>静态状态纪律（R76 族）</b>：`adminRole`/`fieldAuths` 为 public static ⇒ 快照清空 + tearDown 复位。
 * <p><b>跳过口径（R77）</b>：Kingbase 不可达 ⇒ 逐条跳过，不得记为通过。
 */
class EovaFieldAuthPolicyKingbaseLiveTest {

    private static final String URL = System.getProperty("eova.kingbase.meta.url",
            "jdbc:kingbase8://base.platform:54321/eova_meta");
    private static final String DB_USER = System.getProperty("eova.kingbase.user", "system");
    private static final String DB_PWD = System.getProperty("eova.kingbase.pwd", "Rmtlwrm@@2026");
    private static final String P = "r222";

    private Set<String> adminRoleSnapshot;
    private Map<Integer, List<EovaRecord>> fieldAuthsSnapshot;

    private static boolean reachable() {
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD)) {
            return c.isValid(3);
        } catch (Throwable e) {
            return false;
        }
    }

    private static final boolean BASELINE_UP = reachable();

    /** 极简直连 DataSource（Kingbase 无随包 DataSource 实现可依赖；判据只需"给连接"） */
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
        adminRoleSnapshot = new HashSet<>(EovaFieldAuth.adminRole);
        fieldAuthsSnapshot = new HashMap<>(EovaFieldAuth.fieldAuths);
        EovaFieldAuth.adminRole.clear();
        EovaFieldAuth.fieldAuths.clear();
        cleanup();
    }

    @AfterEach
    void tearDown() {
        try {
            cleanup();
        } catch (Exception ignore) {
            // 清理失败不应掩盖真实失败
        }
        EovaFieldAuth.adminRole.clear();
        EovaFieldAuth.adminRole.addAll(adminRoleSnapshot);
        EovaFieldAuth.fieldAuths.clear();
        EovaFieldAuth.fieldAuths.putAll(fieldAuthsSnapshot);
        x.conf.getProps().remove("eova.field.auth");
        EovaDataSource.clear();
        EovaGateways.clear();
    }

    /** 清掉本判据造的行（该表两库基线均为 0 行） */
    private static void cleanup() throws Exception {
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             Statement st = c.createStatement()) {
            st.executeUpdate("delete from eova_field_auth where object like '" + P + "%'");
        }
    }

    /** 造一行字段授权策略 */
    private static void insertAuth(int id, int companyId, int type, String object, String field, String auth)
            throws Exception {
        String sql = "insert into eova_field_auth (id, company_id, type, object, field, auth)"
                + " values (?, ?, ?, ?, ?, ?)";
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, companyId);
            ps.setInt(3, type);
            ps.setString(4, object);
            ps.setString(5, field);
            ps.setString(6, auth);
            ps.executeUpdate();
        }
    }

    /** 造用户（getDisableFields 读 id / rid / company_id） */
    private static User user(int id, int rid, int companyId) {
        User u = new User();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", id);
        attrs.put("rid", rid);
        attrs.put("company_id", companyId);
        u._setAttrs(attrs);
        return u;
    }

    @Test
    @DisplayName("★ 门控：eova.field.auth=false 时 authReload 不加载任何东西（Kingbase）")
    void gateOffMeansNothingIsLoaded() throws Exception {
        insertAuth(2220001, 1, 1, P + "_g", "f1", "7");
        EovaFieldAuth.fieldAuths.put(999, new ArrayList<EovaRecord>());
        x.conf.addConfig("eova.field.auth", "false");
        EovaFieldAuth.authReload(0);
        assertTrue(EovaFieldAuth.fieldAuths.containsKey(999), "★ 哨兵键必须还在（门控被忽略则被覆盖）");
        assertFalse(EovaFieldAuth.fieldAuths.containsKey(1), "门控关闭时企业 1 不得被加载");
    }

    @Test
    @DisplayName("★ type=1 角色白名单：未授权 rid 禁用、已授权不禁用（Kingbase）")
    void roleWhitelistDisablesUnauthorizedRole() throws Exception {
        insertAuth(2220002, 1, 1, P + "_w", "f1", "7");
        x.conf.addConfig("eova.field.auth", "true");
        EovaFieldAuth.authReload(0);
        assertFalse(EovaFieldAuth.getDisableFields(user(222, 7, 1)).contains(P + "_w.f1"),
                "rid=7 已授权 ⇒ 不得禁用");
        assertTrue(EovaFieldAuth.getDisableFields(user(222, 8, 1)).contains(P + "_w.f1"),
                "★ rid=8 未授权 ⇒ 必须禁用（Kingbase 上与 MySQL 行为一致）");
    }

    @Test
    @DisplayName("★ adminRole 内的角色跳过白名单限制（Kingbase）")
    void adminRoleBypassesWhitelist() throws Exception {
        insertAuth(2220003, 1, 1, P + "_adm", "f1", "7");
        x.conf.addConfig("eova.field.auth", "true");
        EovaFieldAuth.authReload(0);
        assertTrue(EovaFieldAuth.getDisableFields(user(222, 8, 1)).contains(P + "_adm.f1"),
                "前置：rid=8 未授权 ⇒ 先确认被禁用");
        EovaFieldAuth.adminRole.add("8");
        assertFalse(EovaFieldAuth.getDisableFields(user(222, 8, 1)).contains(P + "_adm.f1"),
                "★ 加入 adminRole 后必须不再禁用");
    }

    @Test
    @DisplayName("★ field='a,b' 多字段都入禁用集（Kingbase）")
    void multiFieldIsSplit() throws Exception {
        insertAuth(2220004, 1, 1, P + "_multi", "f1,f2", "7");
        x.conf.addConfig("eova.field.auth", "true");
        EovaFieldAuth.authReload(0);
        Set<String> disabled = EovaFieldAuth.getDisableFields(user(222, 8, 1));
        assertTrue(disabled.contains(P + "_multi.f1"), "★ 第一个字段必须入集：" + disabled);
        assertTrue(disabled.contains(P + "_multi.f2"), "★ 第二个字段必须入集：" + disabled);
    }
}
