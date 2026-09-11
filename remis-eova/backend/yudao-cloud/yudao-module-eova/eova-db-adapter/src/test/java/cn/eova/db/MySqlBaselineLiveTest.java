/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mysql.cj.jdbc.MysqlDataSource;
import cn.eova.api.page.PageBootstrapAssembler;
import cn.eova.common.Ds;
import cn.eova.compat.cache.CacheServices;
import cn.eova.compat.cache.EhCacheService;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.compat.table.EovaTableMapping;
import cn.eova.compat.table.TableMetadata;
import cn.eova.config.EovaDataSource;
import cn.eova.model.Button;
import cn.eova.model.Menu;
import cn.eova.model.MetaField;
import cn.eova.model.MetaObject;
import cn.eova.model.User;
import cn.eova.tools.x;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **baseline MySQL 全链 live readiness（第 172 轮）**。
 *
 * <p><b>为什么单起一个类：</b>{@link LiveReadinessSmokeTest}（r87）用 H2 内存库把
 * "编译绿 + 判据绿" 抬到真实 JDBC，但它在类注释里**如实登记了不覆盖项**：baseline
 * MySQL 全链（登录→菜单→CRUD）、以及 r79 落的关键字转义 —— 因为**转义形态是 MySQL 族反引号，
 * H2 2.x 不接受**，那条 SQL 当时只能由代理级判据（FakeJdbc）钉住形态，无法真跑。
 * 本类用**真实 baseline 库**把这两项补齐（第 172 轮实测：baseline MySQL 8.0.46 容器
 * `eova-baseline-mysql` 在 127.0.0.1:13306 上已就绪，schema 与旧 `dev.txt` 完全一致：
 * `eova`→`eova_meta`、`main`→`demo`）。
 *
 * <p><b>本类的价值在于"判据的来源"：</b>断言的期望值**不是构造的**，而是先查真库得到
 * 再写进判据（例如 `sys_users` 菜单 6 条按钮里 `删除` 的 `is_hide=1` ⇒ 可见面必须是 5 条）。
 * 因此任何一条失败都指向真实语义偏差，而不是夹具与代码互相迁就。
 *
 * <p><b>四段链路（与 r87 口径一致，改为真库）+ 两段 baseline 独有：</b>
 * <ol>
 *   <li><b>真实连接 + 真实登录查询</b>：旧 {@code UserIntercept}/{@code UserHook} 的
 *       `login_id` 判定 SQL、旧登录的 `login_id + login_pwd` 条件，都在真库上执行；
 *       `User.dao.findById` 走 `EovaModel → 网关 → MySQL` 取真实行；</li>
 *   <li><b>真实菜单 → 引导装配全链</b>：`eova_menu.config.object_code → eova_object →
 *       eova_field → eova_button × eova_role_btn` 五表联查，全部落在真库真数据上；</li>
 *   <li><b>真实 DDL/DML CRUD 往返</b>：在 baseline `demo` schema 上建临时表（teardown 必删）
 *       跑 insert/find/update/delete + utf8mb4 中文往返 + `DatabaseMetaData` 自省主键；</li>
 *   <li><b>MySQL 族反引号转义 SQL 真执行</b>：r87 明确"留给 baseline MySQL"的那条，本类真跑。</li>
 * </ol>
 *
 * <p><b>判别力（R120/R170 教训：夹具必须让被断言的分支可判别）：</b>
 * <ul>
 *   <li>菜单码 `sys_users` 与对象码 `eova_user` **不同** ⇒ "object_code 取自菜单 config"
 *       这一分支可判别（若误取入参 menuCode 立刻红）；</li>
 *   <li>对象 `demo_cat` 的 `table_name='goods_cat'` 与 `code` **不同** ⇒
 *       "table 取自库列" 可判别（baseline 里 42 个对象的 `view_name` **全为 NULL**，
 *       故不能靠 view_name 做该判别，改由这一对锚点承担）；</li>
 *   <li>角色面用真库不同的 rid（1 → 5 条可见按钮，3 → 1 条）。</li>
 * </ul>
 *
 * <p><b>跳过口径：</b>baseline 不可达时整类 `Assumptions` 跳过（不静默绿、不假绿）。
 * 跳过即等于该项回到 `not executed`，必须如实登记 —— 不得把跳过当通过。
 *
 * <p><b>仍未覆盖（如实登记）</b>：Kingbase baseline 全链、HTTP 容器层
 * （Spring DispatcherServlet + 路由适配）、Oracle 方言 SQL 真执行（本机无 Oracle）。
 */
class MySqlBaselineLiveTest {

    /** meta 库连接（旧 `dev.txt` 的 `eova.url`，端口/schema 与 baseline 完全一致） */
    private static final String META_URL = System.getProperty("eova.mysql.meta.url",
            "jdbc:mysql://127.0.0.1:13306/eova_meta?useUnicode=true&characterEncoding=UTF-8"
                    + "&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai"
                    + "&allowPublicKeyRetrieval=true&connectTimeout=3000");
    /** 业务库连接（旧 `dev.txt` 的 `main.url`） */
    private static final String MAIN_URL = System.getProperty("eova.mysql.main.url",
            "jdbc:mysql://127.0.0.1:13306/demo?useUnicode=true&characterEncoding=UTF-8"
                    + "&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai"
                    + "&allowPublicKeyRetrieval=true&connectTimeout=3000");
    private static final String DB_USER = System.getProperty("eova.mysql.user", "root");
    private static final String DB_PWD = System.getProperty("eova.mysql.pwd", "root");

    /** ★ baseline 真值：`select login_pwd from eova_user where login_id='eova'`（非判据构造值） */
    private static final String EOVA_PWD = "89BDF69372C2EF53EA409CDF020B5694";
    /** CRUD 临时表名（baseline `demo` schema 内，teardown 必删，保证 baseline 不被改动） */
    private static final String SCRATCH = "eova_live_scratch";

    /** baseline 是否可达（不可达 ⇒ 逐条跳过，等价于登记 not executed） */
    private static boolean reachable() {
        try (Connection c = DriverManager.getConnection(META_URL, DB_USER, DB_PWD)) {
            return c.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * ★ baseline 可达性（类加载时探一次）。
     *
     * <p><b>为什么放在 {@code @BeforeEach} 而【不是】{@code @BeforeAll}（第 172 轮实测）：</b>
     * {@code @BeforeAll} 里的 assumption 失败会让 surefire 报 {@code tests="0" skipped="0"} ——
     * 与"这个类根本没被发现/根本没跑"**无法区分**，正是本项目反复警惕的**假绿**形态
     * （空跑当通过）。放进 {@code @BeforeEach} 后，不可达时报告为
     * {@code tests="7" skipped="7"}，跳过是**显式可见**的。
     */
    private static final boolean BASELINE_UP = reachable();

    @BeforeEach
    void setUp() {
        // ★ 必须先于任何查库动作（下面的 addMapping 会走 DatabaseMetaData 真连库）
        Assumptions.assumeTrue(BASELINE_UP,
                "baseline MySQL 不可达 ⇒ 逐条跳过（该项回到 not executed，不得记为通过）：" + META_URL);

        MysqlDataSource meta = new MysqlDataSource();
        meta.setURL(META_URL);
        meta.setUser(DB_USER);
        meta.setPassword(DB_PWD);
        MysqlDataSource main = new MysqlDataSource();
        main.setURL(MAIN_URL);
        main.setUser(DB_USER);
        main.setPassword(DB_PWD);

        // 真实数据源登记：URL 决定方言（MySQL），关键字转义形态即由此而来
        EovaDataSource.register(Ds.EOVA, META_URL, "com.mysql.cj.jdbc.Driver");
        EovaDataSource.register(Ds.MAIN, MAIN_URL, "com.mysql.cj.jdbc.Driver");
        EovaGateways.register(Ds.EOVA, new JdbcEovaDbGateway(meta, Ds.EOVA));
        EovaGateways.register(Ds.MAIN, new JdbcEovaDbGateway(main, Ds.MAIN));

        // 真实表元数据自省（DatabaseMetaData）→ EovaTableMapping 绑定
        //
        // ★ 先清一次注册表：`EovaTableMapping.me()` 是**跨判据类的静态单例**，全量跑时
        //   `EovaModelGoldenTest` 已为表 `eova_user` 注册过映射（它绑的是另一个模型类），
        //   而 `addMapping` 的守卫**按表名判重** ⇒ 本类再为 `User` 绑同一张表会抛
        //   "Model mapping already exists : eova_user"。
        //   **这一条只有全量 `mvn clean test` 才暴露** —— 单跑 `-Dtest=MySqlBaselineLiveTest`
        //   时注册表干净、判据全绿（第 172 轮实测：单跑 7/7 绿，全量 82 条中 1 报错）。
        EovaTableMapping.me().clear();
        JdbcTableMetadataSource metaSource = new JdbcTableMetadataSource(meta);
        EovaTableMapping.setMetadataSource(metaSource);
        EovaTableMapping.me().addMapping(Ds.EOVA, Menu.class, metaSource.metadata("eova_menu"));
        EovaTableMapping.me().addMapping(Ds.EOVA, MetaObject.class, metaSource.metadata("eova_object"));
        EovaTableMapping.me().addMapping(Ds.EOVA, MetaField.class, metaSource.metadata("eova_field"));
        EovaTableMapping.me().addMapping(Ds.EOVA, Button.class, metaSource.metadata("eova_button"));
        EovaTableMapping.me().addMapping(Ds.EOVA, User.class, metaSource.metadata("eova_user"));

        // 缓存接缝（R49：单例可能持有已死的 CacheManager，故 shutdown 后重建）
        EhCacheService.shutdown();
        CacheServices.set(EhCacheService.fromClasspath());
        EovaModel.setCacheService(EhCacheService.fromClasspath());
        // 服务层静态槽由启动期 `biz.init()` 填充（`service/biz.java:44-53`）
        cn.eova.service.biz.init();
    }

    @AfterEach
    void tearDown() throws Exception {
        dropScratch();
        CacheServices.clear();
        EhCacheService.shutdown();
        EovaDataSource.clear();
        EovaGateways.clear();
        EovaTableMapping.me().clear();
        EovaTableMapping.setMetadataSource(null);
        x.conf.getProps().remove("db.keyword");
    }

    /** 在 baseline `demo` schema 上执行 DDL（直连，不经网关：建表不是被测语义） */
    private static void ddlMain(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(MAIN_URL, DB_USER, DB_PWD);
             Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /** 清理临时表，保证 baseline `demo` schema 回到判据执行前的状态 */
    private static void dropScratch() throws SQLException {
        try {
            ddlMain("drop table if exists " + SCRATCH);
        } catch (SQLException ignore) {
            // baseline 不可达时的清理失败不应掩盖真实失败
        }
    }

    /** 造用户（角色面用；`User#isAdmin()` 读 `rid`） */
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
    @DisplayName("★ 真实链路 ①：baseline eova_user 上的登录链（ported SQL 真跑 + 模型层取真行）")
    void loginChainOnRealBaselineUser() {
        JdbcEovaDbGateway gw = (JdbcEovaDbGateway) EovaGateways.get(Ds.EOVA);

        // 旧 UserIntercept/UserHook 的 login_id 判定 SQL（逐字同源，只在真库上跑）
        assertEquals(1L, gw.queryLong("select count(*) from eova_user where login_id = ?", "eova").longValue(),
                "baseline 里 login_id='eova' 必须唯一命中");

        // 旧登录链的真实条件：login_id + login_pwd
        EovaRecord row = gw.findFirst("select * from eova_user where login_id = ? and login_pwd = ?",
                "eova", EOVA_PWD);
        assertNotNull(row, "baseline 真值口令必须能登录（失败说明 baseline 数据与判据不同源）");
        assertEquals("eova", row.getStr("login_id"));
        assertEquals(1, row.getInt("rid").intValue());

        // 模型层：EovaModel → EovaTableMapping → 网关 → 真实 MySQL
        User real = User.dao.findById(1);
        assertNotNull(real, "User.dao.findById 必须走真实链路取到行");
        assertEquals("eova", real.getStr("login_id"));
        assertTrue(real.isAdmin(), "baseline rid=1 必须是超管（isAdmin 口径）");
    }

    @Test
    @DisplayName("★ 真实链路 ②：真实菜单 sys_users → config 的 object_code → 真库对象/字段/按钮")
    void bootstrapOnRealBaselineMenu() {
        User admin = User.dao.findById(1);
        LegacyKv kv = new PageBootstrapAssembler().assemble("sys_users", admin, true);

        assertEquals("ok", kv.get("state"), "真实基线链路应成功：" + kv.get("msg"));

        @SuppressWarnings("unchecked")
        LegacyKv object = (LegacyKv) kv.get("object");
        // ★ 判别点：菜单码与对象码在 baseline 里不同 ⇒ 若误用入参 menuCode 当 object_code，此处立刻红
        assertEquals("sys_users", kv.get("menuCode"));
        assertEquals("eova_user", object.get("code"), "object_code 必须取自 eova_menu.config");
        assertNotEquals(kv.get("menuCode"), object.get("code"), "二者不同 ⇒ object_code 的来源可判别");
        assertEquals("id", object.get("pk_name"), "pk_name 必须来自真库 eova_object.pk_name");
        assertEquals("eova", object.get("data_source"));
        assertEquals("eova_user", object.get("table"));

        @SuppressWarnings("unchecked")
        LegacyKv menu = (LegacyKv) kv.get("menu");
        assertEquals("sys_users", menu.get("code"));
        assertEquals("table", menu.get("template"), "template 必须来自真实 eova_menu.template 列");
        assertEquals("用户管理", menu.get("name"), "菜单名必须来自真库（utf8mb4 中文往返）");

        // 真实按钮面：eova_button 6 条，其中 `删除` is_hide=1 ⇒ 可见面 5 条
        // （真库实测：6 条全部授给 rid=1/2，rid=3 只授 2099；btnset 全为空串 ⇒ 分组分支不收缩集合）
        @SuppressWarnings("unchecked")
        List<Object> btns = (List<Object>) kv.get("btnList");
        assertEquals(5, btns.size(), "真库 6 条按钮中 is_hide=1 的那条必须被 queryByMenuCode 过滤");

        @SuppressWarnings("unchecked")
        LegacyKv loginUser = (LegacyKv) kv.get("loginUser");
        assertEquals(Boolean.TRUE, loginUser.get("isAdmin"));
    }

    @Test
    @DisplayName("★ 真实链路 ②b：角色面用真库不同 rid（1 → 5 条 / 3 → 1 条）")
    void bootstrapRoleFaceOnRealData() {
        LegacyKv admin = new PageBootstrapAssembler().assemble("sys_users", user(1), true);
        LegacyKv guest = new PageBootstrapAssembler().assemble("sys_users", user(3), true);
        assertEquals("ok", admin.get("state"), String.valueOf(admin.get("msg")));
        assertEquals("ok", guest.get("state"), String.valueOf(guest.get("msg")));

        @SuppressWarnings("unchecked")
        List<Object> adminBtns = (List<Object>) admin.get("btnList");
        @SuppressWarnings("unchecked")
        List<Object> guestBtns = (List<Object>) guest.get("btnList");
        assertEquals(5, adminBtns.size(), "baseline rid=1 授 6 条中 5 条可见");
        assertEquals(1, guestBtns.size(), "baseline rid=3 只授 2099（查询）且可见");
        assertNotEquals(adminBtns.size(), guestBtns.size(), "角色面必须真的生效");
    }

    @Test
    @DisplayName("★ 真实链路 ②c：跨 schema 对象（code=demo_cat / table_name=goods_cat，ds=main）")
    void crossSchemaObjectOnRealData() {
        LegacyKv kv = new PageBootstrapAssembler().assemble("meta_goods_style", User.dao.findById(1), true);
        assertEquals("ok", kv.get("state"), String.valueOf(kv.get("msg")));

        @SuppressWarnings("unchecked")
        LegacyKv object = (LegacyKv) kv.get("object");
        assertEquals("demo_cat", object.get("code"));
        // ★ 判别点：baseline 42 个对象的 view_name 全为 NULL（故 view_name 无法承担该判别），
        //   而这里的 table_name 与 code 不同 ⇒ "table 取自库列" 才真正可观测
        assertNotEquals(object.get("code"), object.get("table"), "该对象 code 与 table 不同 ⇒ table 来源可判别");
        assertEquals("goods_cat", object.get("table"), "table 必须取自真库 eova_object.table_name");
        assertEquals("main", object.get("data_source"), "跨 schema 对象的数据源必须原样下发");
    }

    @Test
    @DisplayName("★ 真实链路 ③：baseline demo schema 上的 CRUD 往返 + 自省主键 + utf8mb4 中文")
    void crudRoundTripOnMainSchema() throws Exception {
        JdbcEovaDbGateway gw = (JdbcEovaDbGateway) EovaGateways.get(Ds.MAIN);
        dropScratch();
        ddlMain("create table " + SCRATCH + " (id int primary key, name varchar(64), num int)");
        try {
            // 真写
            EovaRecord r = new EovaRecord();
            r.set("id", 9001);
            r.set("name", "酒店管理");
            r.set("num", 7);
            assertTrue(gw.save(SCRATCH, r), "真实 MySQL INSERT 必须成功");

            // 真读 + utf8mb4 中文往返
            List<EovaRecord> rows = gw.find("select * from " + SCRATCH + " where id = ?", 9001);
            assertEquals(1, rows.size());
            assertEquals("酒店管理", rows.get(0).getStr("name"), "中文必须原样往返（utf8mb4）");
            assertEquals(7L, gw.queryLong("select num from " + SCRATCH + " where id = ?", 9001).longValue());

            // 真实自省：DatabaseMetaData 取主键与列
            JdbcTableMetadataSource ms = new JdbcTableMetadataSource(EovaGateways.get(Ds.MAIN).dataSource());
            TableMetadata tm = ms.metadata(SCRATCH);
            assertNotNull(tm, "真实 MySQL 自省必须能解析该表");
            assertEquals("id", tm.getPrimaryKey()[0], "主键必须来自真实 MySQL 自省");
            assertTrue(tm.getColumnNameSet().contains("name"), tm.getColumnNameSet().toString());

            // 真改
            assertEquals(1, gw.update("update " + SCRATCH + " set name = ? where id = ?", "客房", 9001),
                    "真实 MySQL UPDATE 必须命中 1 行");
            assertEquals("客房", gw.findFirst("select * from " + SCRATCH + " where id = ?", 9001).getStr("name"));

            // 真删
            assertTrue(gw.deleteById(SCRATCH, 9001), "真实 MySQL DELETE 必须成功");
            assertEquals(0, gw.find("select * from " + SCRATCH).size());
        } finally {
            dropScratch();
        }
    }

    @Test
    @DisplayName("★ 真实链路 ④：MySQL 族反引号转义 SQL【真执行】（r87 遗留的 baseline 待办）")
    void backtickEscapedSqlReallyExecutes() {
        JdbcEovaDbGateway gw = (JdbcEovaDbGateway) EovaGateways.get(Ds.EOVA);
        String raw = "select login_pwd from eova_user where login_id = 'eova'";

        // 未配置关键字 ⇒ 原样 SQL 真跑
        assertEquals(1, gw.find(raw).size());

        // 配置关键字列 ⇒ 读路径必须发生转义（save/delete 不转义，此处只判读路径）
        x.conf.addConfig("db.keyword", "eova_user.login_pwd");
        String escaped = gw.escapeSql(raw);
        assertNotEquals(raw, escaped, "命中关键字列后必须转义：" + escaped);
        assertTrue(escaped.contains("`login_pwd`"), "MySQL 族形态必须是反引号列名：" + escaped);

        // ★ 本行即 r87 登记的"留给 baseline MySQL"的那条：转义后的 SQL 在真库上必须可执行。
        //   H2 2.x 不接受反引号，故当时只能判形态、无法真跑。
        List<EovaRecord> rows = gw.find(raw);
        assertEquals(1, rows.size(), "转义后的 SQL 必须在真实 MySQL 上可执行");
        assertEquals(EOVA_PWD, rows.get(0).getStr("login_pwd"), "转义不得改变取值");

        // 清掉配置后恢复原样 SQL，且结果一致（转义不改变语义）
        x.conf.getProps().remove("db.keyword");
        assertEquals(EOVA_PWD, gw.find(raw).get(0).getStr("login_pwd"));
    }

    @Test
    @DisplayName("★ 真实链路 ⑤：写路径【不得】转义（旧 EovaDbPro 只覆写读/改路径）")
    void writePathIsNotEscaped() throws Exception {
        JdbcEovaDbGateway gw = (JdbcEovaDbGateway) EovaGateways.get(Ds.MAIN);
        dropScratch();
        ddlMain("create table " + SCRATCH + " (id int primary key, name varchar(64), num int)");
        try {
            x.conf.addConfig("db.keyword", SCRATCH + ".name");
            EovaRecord r = new EovaRecord();
            r.set("id", 2);
            r.set("name", "关键字列");
            assertTrue(gw.save(SCRATCH, r), "写路径不得转义 ⇒ 真库必须可执行");
            assertTrue(gw.deleteById(SCRATCH, 2), "删路径不得转义 ⇒ 真库必须可执行");

            // 读路径转义后仍可执行（该表已由 keyword 配置覆盖）
            EovaRecord r2 = new EovaRecord();
            r2.set("id", 3);
            r2.set("name", "读路径");
            assertTrue(gw.save(SCRATCH, r2));
            assertTrue(gw.escapeSql("select name from " + SCRATCH).contains("`name`"));
            assertEquals("读路径", gw.find("select name from " + SCRATCH + " where id = 3").get(0).getStr("name"));
        } finally {
            dropScratch();
        }
    }
}
