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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.eova.common.Ds;
import cn.eova.compat.cache.CacheServices;
import cn.eova.compat.cache.EhCacheService;
import cn.eova.compat.table.EovaTableMapping;
import cn.eova.config.EovaDataSource;
import cn.eova.core.menu.MenuUtil;
import cn.eova.model.Button;
import cn.eova.model.Menu;
import cn.eova.model.User;
import com.mysql.cj.jdbc.MysqlDataSource;
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
 * **`MenuUtil.buildMenu` 行为判据（第 224 轮）** —— 真实 baseline MySQL + **真实角色数据**（无需造数据）。
 *
 * <p>来源：r176 扫描列为零覆盖候选 → r178 确认非死类 → **r179 判定"不可用跨制品比对"**（它查库 +
 * 父链闭包递归 ⇒ 跨制品需把旧 jfinal 栈接到库）→ **r223 recon 读全文定出 5 条可判别契约**。
 *
 * <p><b>语义（读实现得出）</b>：`menus = Menu.dao.queryMenu()`；`ids = Button.dao.queryMenuIdByRid(rid)`；
 * **`x.isEmpty(ids)` ⇒ 返回 `null`（不是空列表！）**；对每个授权 id 递归收集**祖先链**
 * （`parent_id == 0` 为终止哨兵、0 不入集）；遍历时 `m.put("link", m.getUrl())` + `m.remove("url")`，
 * 祖先链命中保留，否则不在 `ids` 里则 `it.remove()`；最后 `I18NBuilder.models(menus, "name")`。
 *
 * <p><b>断言（均用真实数据；祖先关系由判据侧独立走 `parent_id` 链验证 —— 属集合成员校验，非照抄算法）</b>：
 * <ol>
 *   <li><b>无授权 ⇒ `null`</b>（合成 rid，`eova_role_btn` 无记录）★ 与"返回空列表"是两种契约；</li>
 *   <li><b>授权项不丢</b>：`queryMenuIdByRid(1)` 的每个 id 都在结果里；</li>
 *   <li><b>不引入未授权项</b>：结果里每个 id 要么在 `ids` 里、要么是某授权项的**祖先**
 *       （同时覆盖 `parent_id == 0` 顶层目录的哨兵边界：未授权且无授权后代的顶层目录必须被移除）；</li>
 *   <li><b>`url`→`link` 改写</b>：结果每项都有 `link`，且 `url` 已被移除。</li>
 * </ol>
 *
 * <p><b>前置接缝</b>照 r172 配方（`EovaDataSource`/`EovaGateways`/`EovaTableMapping`(Menu+Button)/
 * `EhCacheService`+`CacheServices`+`EovaModel.setCacheService`/`biz.init()`）。
 *
 * <p><b>跳过口径（R77）</b>：baseline 不可达 ⇒ 逐条跳过，不得记为通过。
 */
class MenuUtilBuildMenuLiveTest {

    private static final String URL = System.getProperty("eova.mysql.meta.url",
            "jdbc:mysql://127.0.0.1:13306/eova_meta?useUnicode=true&characterEncoding=UTF-8"
                    + "&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
                    + "&connectTimeout=3000");
    private static final String DB_USER = System.getProperty("eova.mysql.user", "root");
    private static final String DB_PWD = System.getProperty("eova.mysql.pwd", "root");

    /** 基线上确实有授权菜单的角色（r172 实测 rid=1 有 6 条按钮授权） */
    private static final int RID_WITH_AUTH = 1;
    /** 合成 rid：`eova_role_btn` 里必然没有记录 ⇒ 用来判"无授权"分支 */
    private static final int RID_WITHOUT_AUTH = 999999;

    private static boolean reachable() {
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD)) {
            return c.isValid(3);
        } catch (Throwable e) {
            return false;
        }
    }

    private static final boolean BASELINE_UP = reachable();

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(BASELINE_UP,
                "baseline MySQL 不可达 ⇒ 逐条跳过（不得记为通过）：" + URL);
        MysqlDataSource meta = new MysqlDataSource();
        meta.setURL(URL);
        meta.setUser(DB_USER);
        meta.setPassword(DB_PWD);
        EovaDataSource.register(Ds.EOVA, URL, "com.mysql.cj.jdbc.Driver");
        EovaGateways.register(Ds.EOVA, new JdbcEovaDbGateway(meta, Ds.EOVA));

        JdbcTableMetadataSource metaSource = new JdbcTableMetadataSource(meta);
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

    /** 造用户（`buildMenu` 只读 rid） */
    private static User user(int rid) {
        User u = new User();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", 1);
        attrs.put("name", "u");
        attrs.put("rid", rid);
        u._setAttrs(attrs);
        return u;
    }

    /** 判据侧独立读 `eova_menu` 的 (id → parent_id)，用于自己的祖先链计算（不经 dao） */
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

    /** 判据侧独立计算"授权 id 及其全部祖先"集合（哨兵：parent_id == 0 终止） */
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

    private static List<Integer> resultIds(List<Menu> menus) {
        List<Integer> out = new ArrayList<>();
        for (Menu m : menus) {
            out.add(m.getInt("id"));
        }
        return out;
    }

    @Test
    @DisplayName("★ 无授权菜单时 buildMenu 返回 null（不是空列表）")
    void noAuthorizationReturnsNull() {
        List<Menu> menus = MenuUtil.buildMenu(user(RID_WITHOUT_AUTH));
        assertNull(menus, "★ 该角色在 eova_role_btn 无记录 ⇒ 契约是返回 null");
    }

    @Test
    @DisplayName("★ 授权项一个都不能丢")
    void authorizedItemsAreKept() {
        List<Integer> ids = Button.dao.queryMenuIdByRid(RID_WITH_AUTH);
        assertFalse(ids.isEmpty(), "前置：基线里该角色必须有授权菜单");

        List<Menu> menus = MenuUtil.buildMenu(user(RID_WITH_AUTH));
        assertNotNull(menus, "有授权 ⇒ 不得返回 null");
        Set<Integer> got = new HashSet<>(resultIds(menus));

        for (Integer id : ids) {
            assertTrue(got.contains(id), "★ 授权菜单 " + id + " 必须出现在结果里；实际=" + got);
        }
    }

    @Test
    @DisplayName("★ 不引入未授权项：结果 ⊆（授权项 ∪ 其祖先链）—— 含 parent_id=0 顶层哨兵")
    void noUnauthorizedEntryIsIntroduced() throws Exception {
        List<Integer> ids = Button.dao.queryMenuIdByRid(RID_WITH_AUTH);
        Map<Integer, Integer> parents = parentIds();
        Set<Integer> allowed = closure(ids, parents);

        List<Menu> menus = MenuUtil.buildMenu(user(RID_WITH_AUTH));
        assertNotNull(menus);
        Set<Integer> got = new HashSet<>(resultIds(menus));

        Set<Integer> unexpected = new HashSet<>(got);
        unexpected.removeAll(allowed);
        assertEquals(new HashSet<Integer>(), unexpected,
                "★ 结果里不得出现既未授权、也不是授权项祖先的菜单：" + unexpected);

        // ★ 反方向（r226 补）：授权项的**祖先链必须都保留**。
        //   只写单向断言时，"丢掉祖先"这类退化会让结果更小、子集关系照样成立
        //   ⇒ 变异不会被捕获（r225 实测 M2/M5 漏网即此因）。两个方向合起来
        //   才是旧实现的契约：**结果 == 授权项 ∪ 其祖先链**。
        Set<Integer> missing = new HashSet<>(allowed);
        missing.removeAll(got);
        assertEquals(new HashSet<Integer>(), missing,
                "★ 授权项及其祖先链必须全部保留：" + missing);

        // 顶层哨兵边界：parent_id == 0 的菜单若要出现，必须自己是授权项或授权项的祖先（已由上面覆盖）
        assertFalse(got.contains(0), "0 是哨兵（parent_id==0 终止），不得作为菜单 id 出现");
    }

    @Test
    @DisplayName("★ url 被改写为 link，且 url 键已移除")
    void urlIsRewrittenToLink() {
        List<Menu> menus = MenuUtil.buildMenu(user(RID_WITH_AUTH));
        assertNotNull(menus);
        assertFalse(menus.isEmpty(), "有授权 ⇒ 结果不应为空");
        for (Menu m : menus) {
            assertTrue(m._getAttrs().containsKey("link"),
                    "★ 每一项都必须有 link（由 url 改写而来）：" + m._getAttrs().keySet());
            assertFalse(m._getAttrs().containsKey("url"),
                    "★ url 键必须被移除：id=" + m.getInt("id"));
        }
    }
}
