/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.api.page;

import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import cn.eova.common.Ds;
import cn.eova.compat.cache.CacheServices;
import cn.eova.compat.cache.EhCacheService;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.compat.table.EovaTableMapping;
import cn.eova.config.EovaDataSource;
import cn.eova.db.EovaGateways;
import cn.eova.db.JdbcEovaDbGateway;
import cn.eova.db.EovaModel;
import cn.eova.db.JdbcTableMetadataSource;
import cn.eova.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 页面引导数据装配的**真实链路**判据（第 169 轮）—— H2 内存库 + 真实 JDBC。
 *
 * <p>为什么不能再只判纯映射：`assemble(menuCode, user, isQuery)` 的**查库路径**此前完全没有判据
 * （r129/r140/r154 只判了 `of(...)` 纯映射、未登录分支与"未注入接缝时响亮失败"）。
 * 而这条链路上每一环都可能静默错：菜单查不到、**元对象编码没从菜单配置里取**（旧 `index()` 的关键一步）、
 * 按角色查按钮拿错集合、缓存把两次调用串味。
 *
 * <p>链路（与旧 `AppController#index()`:70-96 逐条对应）：
 * <pre>
 * Menu.dao.findByCode("select * from eova_menu where code = ?")
 *   → menu.getMenuConfig().getStr("object_code")        // ★ 元对象编码由菜单配置推导
 *   → sm.meta.getMeta(code)（MetaObject + MetaField 两条查询）
 *   → Button.dao.queryByMenuCode(menuCode, rid)（eova_button × eova_role_btn 联查）
 * </pre>
 *
 * <p>未覆盖（如实登记）：baseline MySQL/Kingbase 全链、HTTP 容器层、Oracle 方言 SQL 形态（R60）。
 */
class PageBootstrapAssemblerDbTest {

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        org.h2.jdbcx.JdbcDataSource h2 = new org.h2.jdbcx.JdbcDataSource();
        h2.setURL("jdbc:h2:mem:eova_bootstrap;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        h2.setUser("sa");
        h2.setPassword("");
        dataSource = h2;

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("create table if not exists eova_menu (id int primary key, code varchar(64), name varchar(64),"
                    + " template varchar(32), config varchar(2048))");
            st.execute("create table if not exists eova_object (id int primary key, code varchar(64), name varchar(64),"
                    + " pk_name varchar(64), table_name varchar(64), view_name varchar(64), data_source varchar(32))");
            st.execute("create table if not exists eova_field (id int primary key, object_code varchar(64),"
                    + " name varchar(64), cn varchar(64), num int)");
            st.execute("create table if not exists eova_button (id int primary key, menu_code varchar(64),"
                    + " name varchar(64), ui varchar(512), is_base int, is_hide int, num int)");
            st.execute("create table if not exists eova_role_btn (id int primary key, rid int, bid int)");

            st.execute("delete from eova_menu");
            st.execute("delete from eova_object");
            st.execute("delete from eova_field");
            st.execute("delete from eova_button");
            st.execute("delete from eova_role_btn");

            // ★ 菜单的 config 列存 {"object_code":"meta_hotel"} —— 旧 index() 正是从它推导元对象编码
            st.execute("insert into eova_menu values (1, 'menu_hotel', '酒店管理', 'table',"
                    + " '{\"object_code\":\"meta_hotel\"}')");
            st.execute("insert into eova_object values (1, 'meta_hotel', '酒店', 'hotel_id', 'meta_hotel', 'meta_hotel_view', 'eova')");
            // ★ `view_name` 刻意与 `table_name` **不同**：否则 `table` 取自哪一列不可观测
            //   （r170 实测：view_name 为空时 `getView()` 会回退到表名 ⇒ "从 view 取" 与 "从 table 取" 等价）
            st.execute("insert into eova_field values (1, 'meta_hotel', 'name', '名称', 1)");

            // 两个角色各自的按钮：rid=1 有两条，rid=9 只有一条（DES-004 验收 2 的角色面）
            st.execute("insert into eova_button values (11, 'menu_hotel', '查询', '<button>x</button>', 1, 0, 1)");
            st.execute("insert into eova_button values (12, 'menu_hotel', '导出脚本', '/demo/test/btn.js', 0, 0, 2)");
            st.execute("insert into eova_button values (13, 'menu_hotel', '仅超管', 'eova-btn_error', 1, 0, 3)");
            st.execute("insert into eova_role_btn values (1, 1, 11)");
            st.execute("insert into eova_role_btn values (2, 1, 13)");
            st.execute("insert into eova_role_btn values (3, 1, 12)");
            st.execute("insert into eova_role_btn values (4, 9, 11)");
        }

        EovaDataSource.register(Ds.EOVA, "jdbc:h2:mem:eova_bootstrap", "org.h2.Driver");
        EovaGateways.register(Ds.EOVA, new JdbcEovaDbGateway(dataSource, Ds.EOVA));
        JdbcTableMetadataSource metaSource = new JdbcTableMetadataSource(dataSource);
        EovaTableMapping.setMetadataSource(metaSource);
        // ★ 模型 → 表/数据源的**绑定**必须显式建立（`EovaModel._getConfigName()` 走
        //   `EovaTableMapping.me().getConfigName(class)`，未注册时返回 null ⇒ 网关查不到
        //   "未注册数据源网关（数据源=null）"，本判据实测踩到）。
        EovaTableMapping.me().addMapping(Ds.EOVA, cn.eova.model.Menu.class, metaSource.metadata("eova_menu"));
        EovaTableMapping.me().addMapping(Ds.EOVA, cn.eova.model.MetaObject.class, metaSource.metadata("eova_object"));
        EovaTableMapping.me().addMapping(Ds.EOVA, cn.eova.model.MetaField.class, metaSource.metadata("eova_field"));
        EovaTableMapping.me().addMapping(Ds.EOVA, cn.eova.model.Button.class, metaSource.metadata("eova_button"));
        // 缓存接缝：`queryFisrtByCache`/`queryByCache` 需要（R49：单例可能持有已死的 CacheManager，故 shutdown 后重建）
        EhCacheService.shutdown();
        CacheServices.set(EhCacheService.fromClasspath());
        // ★ `EovaModel.queryFisrtByCache/queryByCache` 读的是**它自己的**静态槽（与 CacheServices 不同）：
        //   `EovaModel.setCacheService(CacheService)`（eova-compat/.../db/EovaModel.java:111）。
        //   只设 CacheServices 会得到 "EovaModel 未注入 CacheService"（本判据实测踩到）。
        EovaModel.setCacheService(EhCacheService.fromClasspath());
        // ★ 服务层静态槽由启动期 `biz.init()` 填充（`service/biz.java:44-53`，`meta = new MetaService()`）——
        //   判据里必须显式调一次，否则 `sm.meta` 为 null（本判据实测踩到：NPE 文案
        //   "Cannot invoke MetaService.getMeta(String) because sm.meta is null"）。
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

    /**
     * 造用户（超管判定走上游 `rid`：`User#isAdmin()` = `get("rid") == EovaConst.ADMIN_RID`）。
     *
     * @param rid 角色 id
     * @return 用户
     */
    private static User user(int rid) {
        User u = new User();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", 1);
        attrs.put("name", "admin");
        attrs.put("rid", rid);
        u._setAttrs(attrs);
        return u;
    }

    @Test
    @DisplayName("★ 真实链路：菜单 → config 里的 object_code → 元对象 → 按角色按钮")
    void assembleHitsRealChain() {
        LegacyKv kv = new PageBootstrapAssembler().assemble("menu_hotel", user(1), true);

        assertEquals("ok", kv.get("state"), "真实链路应成功：" + kv.get("msg"));

        // ★ object.code 必须等于**菜单 config 里写的** meta_hotel（而不是入参 menuCode）
        @SuppressWarnings("unchecked")
        LegacyKv object = (LegacyKv) kv.get("object");
        assertEquals("meta_hotel", object.get("code"));
        assertEquals("酒店", object.get("name"));
        assertEquals("hotel_id", object.get("pk_name"));
        assertEquals("eova", object.get("data_source"));
        assertEquals("meta_hotel", object.get("table"));

        @SuppressWarnings("unchecked")
        LegacyKv menu = (LegacyKv) kv.get("menu");
        assertEquals("menu_hotel", menu.get("code"));
        assertEquals("table", menu.get("template"), "template 是 SPA 的分派键，必须来自库");

        assertEquals("menu_hotel", kv.get("menuCode"));
        assertEquals(Boolean.TRUE, ((LegacyKv) kv.get("loginUser")).get("isAdmin"));
    }

    @Test
    @DisplayName("★ 角色面（DES-004 验收 2）：同一菜单、不同 rid ⇒ btnList 集合不同")
    void btnListDependsOnRole() {
        LegacyKv admin = new PageBootstrapAssembler().assemble("menu_hotel", user(1), true);
        LegacyKv normal = new PageBootstrapAssembler().assemble("menu_hotel", user(9), true);

        @SuppressWarnings("unchecked")
        List<Object> adminBtns = (List<Object>) admin.get("btnList");
        @SuppressWarnings("unchecked")
        List<Object> normalBtns = (List<Object>) normal.get("btnList");

        assertNotNull(adminBtns);
        assertEquals(3, adminBtns.size(), "rid=1 有三条授权按钮");
        assertEquals(1, normalBtns.size(), "rid=9 只有一条授权按钮");
        assertNotEquals(adminBtns.size(), normalBtns.size(), "角色面必须真的生效");
    }

    @Test
    @DisplayName("★ 两类 ui 原样下发（DES-004 验收 3）：HTML 片段与 .js 路径都保持字符串")
    void btnUiStaysString() {
        LegacyKv kv = new PageBootstrapAssembler().assemble("menu_hotel", user(1), true);
        @SuppressWarnings("unchecked")
        List<cn.eova.model.Button> btns = (List<cn.eova.model.Button>) kv.get("btnList");

        boolean sawHtml = false;
        boolean sawJs = false;
        for (cn.eova.model.Button b : btns) {
            String ui = b.getStr("ui");
            assertTrue(ui instanceof String, "ui 必须是字符串");
            sawHtml |= ui.startsWith("<button");
            sawJs |= ui.endsWith(".js");
        }
        assertTrue(sawHtml, "应有一条 HTML 片段形态的 ui");
        assertTrue(sawJs, "应有一条 .js 路径形态的 ui");
    }

    @Test
    @DisplayName("菜单不存在 ⇒ state='no' 且文案点名菜单（不是 NPE）")
    void missingMenuIsReported() {
        LegacyKv kv = new PageBootstrapAssembler().assemble("no_such_menu", user(1), true);
        assertEquals("no", kv.get("state"));
        assertTrue(String.valueOf(kv.get("msg")).contains("no_such_menu"));
    }
}
