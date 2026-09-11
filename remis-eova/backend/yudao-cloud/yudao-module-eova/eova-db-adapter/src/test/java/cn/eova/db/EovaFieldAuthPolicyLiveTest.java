/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.eova.common.Ds;
import cn.eova.config.EovaDataSource;
import cn.eova.config.EovaFieldAuth;
import cn.eova.model.User;
import cn.eova.tools.x;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **`EovaFieldAuth`（字段权限）行为判据（第 220 轮）** —— 真实 baseline MySQL + 造数据。
 *
 * <p>来源：r176 扫描列为候选 → r178 确认非死类 → r211/r218 recon 查明它读 `eova_field_auth`、
 * 受配置 `eova.field.auth`（默认 false）门控、持有 public static `adminRole`/`fieldAuths`，
 * 两库表结构**完全同构**（无可空/布尔差异）。
 *
 * <p>六条断言（读实现得出，均可判别）：① 门控关闭 ⇒ 不加载（哨兵键证明未被覆盖）；
 * ② `companyId=0` ⇒ 全量按 `company_id` 分组；③ `companyId!=0` ⇒ 不清空其它企业；
 * ④ `type=1` 角色白名单：rid 不在 `auth` ⇒ 禁用；⑤ **`adminRole` 跳过白名单**；
 * ⑥ `field="a,b"` ⇒ 两个字段都入集。
 *
 * <p>★ 静态状态纪律（R76 族）：`adminRole`/`fieldAuths` 是 public static 共享状态 ⇒
 * setUp 快照清空、tearDown 复位。只断言已读清的分支（type 2/5 支路 r218 截断未读全 ⇒ 不写期望值）。
 * 造的数据 `object` 前缀 `r219`，teardown 按前缀删（该表两库基线均为 0 行）。
 * `x.conf.addConfig` 第二参是 **String**（r219 编译错纠正）。
 *
 * <p>跳过口径（R77）：baseline 不可达 ⇒ 逐条跳过，不得记为通过。
 */
class EovaFieldAuthPolicyLiveTest {

    private static final String URL = System.getProperty("eova.mysql.meta.url",
            "jdbc:mysql://127.0.0.1:13306/eova_meta?useUnicode=true&characterEncoding=UTF-8"
                    + "&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
                    + "&connectTimeout=3000");
    private static final String DB_USER = System.getProperty("eova.mysql.user", "root");
    private static final String DB_PWD = System.getProperty("eova.mysql.pwd", "root");
    private static final String P = "r219";

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

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(BASELINE_UP,
                "baseline MySQL 不可达 ⇒ 逐条跳过（该项回到 not executed，不得记为通过）：" + URL);
        MysqlDataSource ds = new MysqlDataSource();
        ds.setURL(URL);
        ds.setUser(DB_USER);
        ds.setPassword(DB_PWD);
        EovaDataSource.register(Ds.EOVA, URL, "com.mysql.cj.jdbc.Driver");
        EovaGateways.register(Ds.EOVA, new JdbcEovaDbGateway(ds, Ds.EOVA));
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

    /** 清掉本判据造的行（该表基线为 0 行） */
    private static void cleanup() throws Exception {
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             Statement st = c.createStatement()) {
            st.executeUpdate("delete from eova_field_auth where object like '" + P + "%'");
        }
    }

    /** 造一行字段授权策略（两库该表各列均 NOT NULL，故全部给值） */
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

    /** 造用户：`getDisableFields` 读 id / rid / company_id */
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
    @DisplayName("★ 门控：eova.field.auth=false（默认）时 authReload 不加载任何东西")
    void gateOffMeansNothingIsLoaded() throws Exception {
        insertAuth(2190001, 1, 1, P + "_g", "f1", "7");
        EovaFieldAuth.fieldAuths.put(999, new ArrayList<EovaRecord>());
        x.conf.addConfig("eova.field.auth", "false");
        EovaFieldAuth.authReload(0);
        assertTrue(EovaFieldAuth.fieldAuths.containsKey(999),
                "★ 门控关闭时不得加载：哨兵键必须还在（被覆盖说明门控被忽略）");
        assertFalse(EovaFieldAuth.fieldAuths.containsKey(1), "门控关闭时企业 1 不得被加载");
    }

    @Test
    @DisplayName("★ companyId=0 全量加载并按 company_id 分组")
    void reloadAllGroupsByCompany() throws Exception {
        insertAuth(2190002, 1, 1, P + "_a", "f1", "7");
        insertAuth(2190003, 1, 1, P + "_b", "f2", "7");
        insertAuth(2190004, 2, 1, P + "_c", "f3", "7");
        x.conf.addConfig("eova.field.auth", "true");
        EovaFieldAuth.authReload(0);
        assertTrue(EovaFieldAuth.fieldAuths.containsKey(1), "必须按 company_id 分组：键 1 应在");
        assertTrue(EovaFieldAuth.fieldAuths.containsKey(2), "必须按 company_id 分组：键 2 应在");
        assertEquals(2, EovaFieldAuth.fieldAuths.get(1).size(), "企业 1 应有两行");
        assertEquals(1, EovaFieldAuth.fieldAuths.get(2).size(), "企业 2 应有一行");
    }

    @Test
    @DisplayName("★ companyId!=0 只更新该企业，不清空其它企业")
    void reloadOneCompanyKeepsOthers() throws Exception {
        insertAuth(2190005, 1, 1, P + "_d", "f1", "7");
        EovaFieldAuth.fieldAuths.put(9, new ArrayList<EovaRecord>());
        x.conf.addConfig("eova.field.auth", "true");
        EovaFieldAuth.authReload(1);
        assertTrue(EovaFieldAuth.fieldAuths.containsKey(9),
                "★ 单企业重载不得清空其它企业：哨兵键 9 必须还在");
        assertEquals(1, EovaFieldAuth.fieldAuths.get(1).size(), "企业 1 应被更新为 1 行");
    }

    @Test
    @DisplayName("★ type=1 角色白名单：rid 不在 auth ⇒ 禁用；在 ⇒ 不禁用")
    void roleWhitelistDisablesUnauthorizedRole() throws Exception {
        insertAuth(2190006, 1, 1, P + "_w", "f1", "7");
        x.conf.addConfig("eova.field.auth", "true");
        EovaFieldAuth.authReload(0);
        assertFalse(EovaFieldAuth.getDisableFields(user(219, 7, 1)).contains(P + "_w.f1"),
                "rid=7 已授权 ⇒ 不得进入禁用集");
        assertTrue(EovaFieldAuth.getDisableFields(user(219, 8, 1)).contains(P + "_w.f1"),
                "★ rid=8 未授权 ⇒ 必须进入禁用集");
    }

    @Test
    @DisplayName("★ adminRole 内的角色跳过白名单限制")
    void adminRoleBypassesWhitelist() throws Exception {
        insertAuth(2190007, 1, 1, P + "_adm", "f1", "7");
        x.conf.addConfig("eova.field.auth", "true");
        EovaFieldAuth.authReload(0);
        assertTrue(EovaFieldAuth.getDisableFields(user(219, 8, 1)).contains(P + "_adm.f1"),
                "前置：rid=8 未授权 ⇒ 先确认它确实被禁用");
        EovaFieldAuth.adminRole.add("8");
        assertFalse(EovaFieldAuth.getDisableFields(user(219, 8, 1)).contains(P + "_adm.f1"),
                "★ 角色进入 adminRole 后必须跳过白名单限制（不再禁用）");
    }

    @Test
    @DisplayName("★ field='a,b' 多字段 ⇒ obj.a 与 obj.b 都在禁用集里")
    void multiFieldIsSplit() throws Exception {
        insertAuth(2190008, 1, 1, P + "_multi", "f1,f2", "7");
        x.conf.addConfig("eova.field.auth", "true");
        EovaFieldAuth.authReload(0);
        Set<String> disabled = EovaFieldAuth.getDisableFields(user(219, 8, 1));
        assertTrue(disabled.contains(P + "_multi.f1"), "★ 多字段第一个必须进禁用集：" + disabled);
        assertTrue(disabled.contains(P + "_multi.f2"), "★ 多字段第二个也必须进禁用集：" + disabled);
    }
}
