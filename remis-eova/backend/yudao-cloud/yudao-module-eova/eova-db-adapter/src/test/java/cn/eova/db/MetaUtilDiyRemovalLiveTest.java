/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.eova.common.Ds;
import cn.eova.config.EovaDataSource;
import cn.eova.core.meta.MetaUtil;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **`MetaUtil` 两个"查库"方法的行为判据（第 215 轮）** —— 真实 baseline MySQL + 造数据。
 *
 * <p><b>来源：</b>r176 覆盖扫描把 `MetaUtil` 列为候选；r180 已判其**纯映射**两个方法
 * （`getDataType`/`getFormType`，跨制品 600 条）；r212 recon 查明剩下三个查库方法里，
 * `removeUserDiy`/`removeRoleDiy` **只动 `eova_diy`**（基线该表 0 行）⇒ 风险低、可造数据判；
 * `addVirtualObject` 会写 `eova_object`/`eova_field` 参考数据表 ⇒ 单独立项，**不在本判据内**。
 *
 * <p><b>判什么（逐条读实现得出，且都**可判别** —— 退化成"整表删除"或"什么都不删"都会红）：</b>
 * <ol>
 *   <li>{@code removeUserDiy}：`diyField` 里**仍存在于 `fields`** 的字段**不删**（`en` 命中即跳过）；</li>
 *   <li>{@code removeUserDiy}：`uid != null` ⇒ **只删该用户**的行；`uid == null` ⇒ 该字段**所有用户**的行都删；</li>
 *   <li>{@code removeRoleDiy} `type == 1`（角色**白名单**）⇒ 删 `uid not in (该角色的用户)` ⇒
 *       **该角色用户的行保留、其他人的行删除**；</li>
 *   <li>{@code removeRoleDiy} `type == 2`（角色**黑名单**）⇒ 删 `uid in (该角色的用户)` ⇒
 *       **该角色用户的行删除、其他人的行保留**；</li>
 *   <li>{@code removeRoleDiy} `type == 2` 且角色**没有对应用户** ⇒ 实现里是 {@code return}
 *       （不是 continue）⇒ **该字段及后续字段一个都不删**（既有行为，判据钉住它）。</li>
 * </ol>
 *
 * <p><b>判别性设计（r170 口径）：</b>每条用例都插入**目标行 + 对照组行**，
 * 断言"该删的删了、该留的留着"；只断言"删掉了"的弱断言在实现退化成宽删时也会绿。
 *
 * <p><b>数据与清理：</b>所有造的数据都用 `object_code` 前缀 {@code r215}，
 * teardown {@code delete from eova_diy where object_code like 'r215%'} ⇒ **baseline 恢复原状**
 * （该表基线本就是 0 行）。角色相关的用户 id 取自**真实库**（`select id from eova_user where rid = ?`），
 * 对照组用一个必然不属于该角色的合成 uid（{@code 999999}）。
 *
 * <p><b>为何落在 eova-db-adapter：</b>判据要用 JdbcEovaDbGateway（db-adapter 制品）与真实 MySQL
 * 驱动（该模块 test 作用域依赖），eova-core 两者都不可见。
 *
 * <p><b>跳过口径（R77）：</b>baseline 不可达 ⇒ 逐条跳过（报告 {@code skipped=N}），不得记为通过。
 * 本版只跑 MySQL；Kingbase 侧的同构判据待补（两库 `eova_diy` 列名一致，但布尔/整型列差异需单独确认）。
 */
class MetaUtilDiyRemovalLiveTest {

    private static final String URL = System.getProperty("eova.mysql.meta.url",
            "jdbc:mysql://127.0.0.1:13306/eova_meta?useUnicode=true&characterEncoding=UTF-8"
                    + "&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
                    + "&connectTimeout=3000");
    private static final String DB_USER = System.getProperty("eova.mysql.user", "root");
    private static final String DB_PWD = System.getProperty("eova.mysql.pwd", "root");

    /** 造数据的 object_code 前缀（teardown 按它清理） */
    private static final String P = "r215";
    /** 对照用的"不属于任何角色"的合成 uid */
    private static final int OUTSIDER = 999999;

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
        cleanup();
    }

    @AfterEach
    void tearDown() {
        try {
            cleanup();
        } catch (Exception ignore) {
            // 清理失败不应掩盖真实失败
        }
        EovaDataSource.clear();
        EovaGateways.clear();
    }

    /** 清掉本判据造的所有行（baseline 该表原本为空） */
    private static void cleanup() throws Exception {
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             Statement st = c.createStatement()) {
            st.executeUpdate("delete from eova_diy where object_code like '" + P + "%'");
        }
    }

    /** 造一行 eova_diy（列全部给值，避免 NOT NULL 约束） */
    private static void insertDiy(String objectCode, String en, int uid) throws Exception {
        String sql = "insert into eova_diy (id, object_code, en, uid, cn, num, width, is_open, defaulter, type)"
                + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, 99000000 + Math.abs((objectCode + en + uid).hashCode() % 100000));
            ps.setString(2, objectCode);
            ps.setString(3, en);
            ps.setInt(4, uid);
            ps.setString(5, "标签" + en);
            ps.setInt(6, 1);
            ps.setInt(7, 100);
            ps.setInt(8, 1);
            ps.setInt(9, 0);
            ps.setInt(10, 0);
            ps.executeUpdate();
        }
    }

    /** 该 object_code 下现存的行，渲染为 "en/uid" 集合（按 en,uid 排序，便于逐字断言） */
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

    /** 取真实库里某角色的用户 id（角色来自真实数据，不用合成值） */
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

    /** 造一个"字段记录"（removeUserDiy 的 fields 入参只读 en） */
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
    @DisplayName("★ removeUserDiy：仍在 fields 里的字段不删；不在的只删指定 uid")
    void removeUserDiySkipsPresentFieldsAndRespectsUid() throws Exception {
        String obj = P + "_user";
        insertDiy(obj, "f1", 21501);
        insertDiy(obj, "f2", 21501);
        insertDiy(obj, "f2", 21502);
        assertEquals(Arrays.asList("f1/21501", "f2/21501", "f2/21502"), rows(obj), "前置：三行都在");

        MetaUtil.removeUserDiy(obj, fieldsWith("f1"), Arrays.asList("f1", "f2"), 21501);

        // f1 仍在 fields 里 ⇒ 不删（尽管它在 diyField 里）；f2 不在 ⇒ 只删 uid=21501 的那行
        assertEquals(Arrays.asList("f1/21501", "f2/21502"), rows(obj),
                "★ 应只删 (f2,21501)：f1 因仍在 fields 中而保留，f2 的另一用户行也必须保留");
    }

    @Test
    @DisplayName("★ removeUserDiy：uid=null 时删该字段所有用户的行")
    void removeUserDiyWithNullUidDeletesAllUsers() throws Exception {
        String obj = P + "_user_null";
        insertDiy(obj, "f1", 21501);
        insertDiy(obj, "f1", 21502);
        insertDiy(obj, "ok", 21501);

        MetaUtil.removeUserDiy(obj, new ArrayList<EovaRecord>(), Arrays.asList("f1"), null);

        assertEquals(Arrays.asList("ok/21501"), rows(obj), "★ f1 的两行都应被删，ok 不受影响");
    }

    @Test
    @DisplayName("★ removeRoleDiy type=1（白名单）：删其他用户，保留该角色用户")
    void removeRoleDiyWhitelistKeepsRoleUsers() throws Exception {
        String obj = P + "_role_w";
        List<Integer> roleUsers = userIdsOfRid(1);
        Assumptions.assumeTrue(!roleUsers.isEmpty(), "基线里 rid=1 必须有用户");
        insertDiy(obj, "f1", roleUsers.get(0));
        insertDiy(obj, "f1", OUTSIDER);

        MetaUtil.removeRoleDiy(obj, "f1", "1", 1);

        assertEquals(Arrays.asList("f1/" + roleUsers.get(0)), rows(obj),
                "★ 白名单语义：角色 1 的用户保留，非该角色（999999）的行被删");
    }

    @Test
    @DisplayName("★ removeRoleDiy type=2（黑名单）：删该角色用户，保留其他用户")
    void removeRoleDiyBlacklistDeletesRoleUsers() throws Exception {
        String obj = P + "_role_b";
        List<Integer> roleUsers = userIdsOfRid(1);
        Assumptions.assumeTrue(!roleUsers.isEmpty(), "基线里 rid=1 必须有用户");
        insertDiy(obj, "f1", roleUsers.get(0));
        insertDiy(obj, "f1", OUTSIDER);

        MetaUtil.removeRoleDiy(obj, "f1", "1", 2);

        assertEquals(Arrays.asList("f1/" + OUTSIDER), rows(obj),
                "★ 黑名单语义：角色 1 的用户被删，非该角色的行保留");
    }

    @Test
    @DisplayName("★ removeRoleDiy type=2 且角色无用户：既有 return 语义 ⇒ 一行都不删")
    void removeRoleDiyBlacklistWithNoMatchingRoleDeletesNothing() throws Exception {
        String obj = P + "_role_none";
        insertDiy(obj, "f1", 21501);
        insertDiy(obj, "f2", 21502);

        // 用一个必然没有任何用户的 rid（基线 eova_user 的 rid 最大远小于此值）
        MetaUtil.removeRoleDiy(obj, "f1,f2", "987654", 2);

        assertEquals(Arrays.asList("f1/21501", "f2/21502"), rows(obj),
                "★ 实现里 type=2 且无对应用户时是 return（不是 continue）⇒ 两个字段都不删");
    }
}
