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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import cn.eova.common.Ds;
import cn.eova.compat.cache.CacheServices;
import cn.eova.compat.cache.EhCacheService;
import cn.eova.compat.table.EovaTableMapping;
import cn.eova.config.EovaDataSource;
import cn.eova.core.menu.MenuUtil;
import cn.eova.model.Button;
import cn.eova.model.Menu;
import cn.eova.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **`MenuUtil.buildMenu` 判据的 KingbaseES 版（第 227 轮）** —— 真实数据、**只读不造数据**。
 *
 * <p>与 MySQL 版（`MenuUtilBuildMenuLiveTest`）同源，回答"同一份 ported 菜单树逻辑在两套
 * baseline 上行为是否一致"。**★ 双向闭包断言从第一版就写全**（r225/r226 教训：只写单向时，
 * "丢掉祖先"这类退化会让结果更小、子集关系照样成立 ⇒ 变异不可捕获）。
 *
 * <p>四条：① 无授权 ⇒ 返回 `null`（非空列表）；② 授权项不丢；
 * ③ **集合相等**：结果 ==（授权项 ∪ 其祖先链）—— 双向；④ `url` 改写为 `link` 且 `url` 已移除。
 *
 * <p>祖先关系由判据侧独立读 `eova_menu` 的 `parent_id` 计算（哨兵：`parent_id == 0` 终止），
 * 不经 dao ⇒ 属集合成员校验而非照抄算法。跳过口径 R77；静态类状态纪律见 MySQL 版。
 */
class MenuUtilBuildMenuKingbaseLiveTest {

    private static final String URL = System.getProperty("eova.kingbase.meta.url",
            "jdbc:kingbase8://base.platform:54321/eova_meta");
    private static final String DB_USER = System.getProperty("eova.kingbase.user", "system");
    private static final String DB_PWD = System.getProperty("eova.kingbase.pwd", "Rmtlwrm@@2026");
    private static final int RID_WITH_AUTH = 1;
    private static final int RID_WITHOUT_AUTH = 999999;

    private static boolean reachable() {
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD)) {
            return c.isValid(3);
        } catch (Throwable e) {
            return false;
        }
    }

    private static final boolean BASELINE_UP = reachable();

    /** 极简直连 DataSource（Kingbase 无随包实现可依赖；判据只需"给连接"） */
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
    void setUp() {
        Assumptions.assumeTrue(BASELINE_UP,
                "Kingbase baseline 不可达 ⇒ 逐条跳过（不得记为通过）：" + URL);
        DataSource ds = dataSource();
        EovaDataSource.register(Ds.EOVA, URL, "com.kingbase8.Driver");
        EovaGateways.register(Ds.EOVA, new JdbcEovaDbGateway(ds, Ds.EOVA));

        JdbcTableMetadataSource metaSource = new JdbcTableMetadataSource(ds);
        EovaTableMapping.setMetadataSource(metaSource);
        EovaTableMapping.me().clear();
        EovaTableMapping.me().addMapping(Ds.EOVA, Menu.class, metaSource.metadata("eova_menu"));
        EovaTableMapping.me().addMapping(Ds.EOVA, Button.class, metaSource.metadata("eova_button"));

        EhCacheService.shutdown();
        CacheServices.set(EhCacheService.fromClasspath());
        EovaModel.setCacheService(EhCacheService.fromClasspath());
        cn.eova.service.biz.init();
    }

    @AfterEach
    void tearDown() {
        CacheServices.clear();
        EhCacheService.shutdown();
        EovaDataSource.clear();
        EovaGateways.clear();
        EovaTableMapping.me().clear();
        EovaTableMapping.setMetadataSource(null);
    }

    /** 造用户（buildMenu 只读 rid） */
    private static User user(int rid) {
        User u = new User();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", 1);
        attrs.put("name", "u");
        attrs.put("rid", rid);
        u._setAttrs(attrs);
        return u;
    }

    /** 判据侧独立读 `eova_menu` 的 (id → parent_id)（不经 dao） */
    private static Map<Integer, Integer> parentIds() throws Exception {
        Map<Integer, Integer> out = new HashMap<>();
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             PreparedStatement ps = c.prepareStatement("select id, parent_id from eova_menu");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getInt(1), rs.getInt(2));
            }
        }
        return out;
    }

    /** 判据侧独立算"授权项 ∪ 全部祖先"（哨兵 parent_id == 0 终止） */
    private static Set<Integer> closure(List<Integer> authorized, Map<Integer, Integer> parents) {
        Set<Integer> out = new HashSet<>();
        for (Integer id : authorized) {
            out.add(id);
            Integer cur = parents.get(id);
            int guard = 0;
            while (cur != null && cur != 0 && guard++ < 100) {
                out.add(cur);
                cur = parents.get(cur);
            }
        }
        return out;
    }

    private static Set<Integer> resultIds(List<Menu> menus) {
        Set<Integer> out = new HashSet<>();
        for (Menu m : menus) {
            out.add(m.getInt("id"));
        }
        return out;
    }

    @Test
    @DisplayName("★ 无授权菜单时 buildMenu 返回 null（Kingbase，非空列表）")
    void noAuthorizationReturnsNull() {
        assertNull(MenuUtil.buildMenu(user(RID_WITHOUT_AUTH)),
                "★ 该角色无授权记录 ⇒ 契约是返回 null");
    }

    @Test
    @DisplayName("★ 授权项一个都不能丢（Kingbase）")
    void authorizedItemsAreKept() {
        List<Integer> ids = Button.dao.queryMenuIdByRid(RID_WITH_AUTH);
        assertFalse(ids.isEmpty(), "前置：Kingbase 基线里该角色必须有授权菜单");

        List<Menu> menus = MenuUtil.buildMenu(user(RID_WITH_AUTH));
        assertNotNull(menus, "有授权 ⇒ 不得返回 null");
        Set<Integer> got = resultIds(menus);
        for (Integer id : ids) {
            assertTrue(got.contains(id), "★ 授权菜单 " + id + " 必须出现；实际=" + got);
        }
    }

    @Test
    @DisplayName("★ 集合相等：结果 == 授权项 ∪ 其祖先链（双向断言，Kingbase）")
    void closureEqualsAuthorizedPlusAncestors() throws Exception {
        List<Integer> ids = Button.dao.queryMenuIdByRid(RID_WITH_AUTH);
        Map<Integer, Integer> parents = parentIds();
        Set<Integer> allowed = closure(ids, parents);

        List<Menu> menus = MenuUtil.buildMenu(user(RID_WITH_AUTH));
        assertNotNull(menus);
        Set<Integer> got = resultIds(menus);

        // 方向一：不多给（不得出现既未授权、也非授权项祖先的菜单）
        Set<Integer> unexpected = new HashSet<>(got);
        unexpected.removeAll(allowed);
        assertEquals(new HashSet<Integer>(), unexpected, "★ 不得引入未授权项：" + unexpected);

        // 方向二（r225/r226 教训：单向断言会漏掉"丢祖先"这类退化）：不许少给
        Set<Integer> missing = new HashSet<>(allowed);
        missing.removeAll(got);
        assertEquals(new HashSet<Integer>(), missing, "★ 授权项及其祖先链必须全部保留：" + missing);

        assertFalse(got.contains(0), "0 是哨兵，不得作为菜单 id 出现");
    }

    @Test
    @DisplayName("★ url 改写为 link 且 url 已移除（Kingbase）")
    void urlIsRewrittenToLink() {
        List<Menu> menus = MenuUtil.buildMenu(user(RID_WITH_AUTH));
        assertNotNull(menus);
        assertFalse(menus.isEmpty(), "有授权 ⇒ 结果不应为空");
        for (Menu m : menus) {
            assertTrue(m._getAttrs().containsKey("link"), "★ 每项须有 link：id=" + m.getInt("id"));
            assertFalse(m._getAttrs().containsKey("url"), "★ url 须已移除：id=" + m.getInt("id"));
        }
    }
}
