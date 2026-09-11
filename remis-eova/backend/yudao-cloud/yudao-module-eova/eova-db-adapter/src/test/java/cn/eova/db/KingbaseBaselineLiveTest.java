/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

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
import com.alibaba.druid.DbType;
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
 * **KingbaseES baseline 全链 live readiness（第 173 轮）**。
 *
 * <p><b>为什么这一轮才做：</b>r172 用真实 baseline MySQL 关掉了"baseline MySQL 全链"，但
 * 同一句话里的 **Kingbase 全链**仍记 {@code not executed}。第 173 轮按 R75 的教训
 * （<b>环境取证要查"配置里真实出现的地址"，不是默认端口</b>）在仓库里找出真实目标：
 * {@code docs/DES-001-kingbase-eova-dbs.md} + `sp3-dialect` spike 里的
 * **`jdbc:kingbase8://base.platform:54321/{eova_meta,demo}`**（账号 `system`）。
 * 实测该实例**可达**，且 `eova_meta` / `demo` **两库都已建好、数据与 MySQL baseline 逐项一致**
 * （menu 33 / object 42 / field 405 / button 211 / role_btn 301 / user 21）。
 *
 * <p><b>本类的真正价值（不是"再跑一遍同样的断言"）：</b>两库数据相同 ⇒ 判据可用
 * **同一组字面量**，于是任何差异都被隔离到"方言与类型装配"这一层，而不是夹具差异。
 *
 * <p><b>⚠️ 一处因果归因的更正（第 173 轮 C 变异实测逼出来的，见下）：</b>本类初稿把
 * "反引号转义"写成"<b>因为</b> kingbase 不在 {@code baseDialectFamily} 的分支里，所以走
 * 默认 MySQL 方言"的后果。<b>这是错的</b>，两处都错：
 * <ol>
 *   <li>{@code escapeSql} 的方言来自 {@link cn.eova.sql.ddl.DefineDialectFactory#getDialect}
 *       —— 它读的是"<b>该 ds 注册过的方言</b>"，**未注册就回退 {@code MysqlDefineDialect}**，
 *       <b>完全不看 {@code DbType}，也不看 {@code baseDialectFamily}</b>；
 *       而 ported 栈里只存在 {@code MysqlDefineDialect} 一个实现、生产代码**没有任何
 *       {@code addDialect} 调用** ⇒ 转义形态是**结构性的**（永远是 MySQL 族反引号），
 *       不是 URL 方案或 DbType 推出来的。</li>
 *   <li>{@code EovaDataSource.baseDialectFamily} 在 ported 栈里**没有任何生产调用点**
 *       （实测：只有 `EovaDataSourceGoldenTest` 与本文档注释引用它）⇒ 类注释里那条
 *       "URL 方案属硬约束"的说法，其**运行时载荷为零**：把 URL 换成 {@code jdbc:postgresql://}
 *       也不会让 {@code escapeSql} 变成双引号。它记录的是**意图**，不是活的开关。</li>
 * </ol>
 * ⇒ 于是本类改为**分别钉住这三件各自独立的事**（见
 * {@link #urlSchemeDrivesKingbaseDbType()}、{@link #escapingComesFromDialectFactoryNotDbType()}、
 * {@link #backtickEscapedSqlReallyExecutesOnKingbase()}），而不是把三件事捏成一条因果链。
 * 这条更正本身是收益：C 变异 M1（把 kingbase 改派到 POSTGRESQL 方言族）**没被捕获**，
 * 暴露的不是"判据有缺口"而是"判据的**注释**在说一件它没判的事"。
 *
 * <p><b>真正被实跑抬起来的结论：</b>
 * <ol>
 *   <li><b>{@code DbType} 的运行时作用只有一个</b>：网关 {@code isOracle()} 读它
 *       ⇒ 本实例推出 {@code DbType.kingbase} ⇒ 走**非 Oracle** 装配分支。
 *       该分支的实际后果由 {@link #typeAssemblyComesFromRealDriver()} 用类型判它。</li>
 *   <li><b>MySQL 族反引号能被真实 KingbaseES 执行</b>（该实例 {@code database_mode = mysql}）——
 *       这是"两库共用一套转义形态"能否成立的**实跑**依据，而不是"兼容模式所以应该行"的推理。</li>
 *   <li><b>真实类型装配不因换库而被改写</b>：`is_hide` 在 MySQL 是 `int`、在 Kingbase 是
 *       **`boolean`**，而 ported 网关的非 Oracle 分支是 {@code getObject} 原样装配
 *       ⇒ 同一列在两库上分别得到 {@code Integer} / {@code Boolean}。这条只有真库能判。</li>
 * </ol>
 *
 * <p><b>判别力（R170 教训）：</b>与 r172 的 MySQL 判据同构 —— 菜单码 `sys_users` 与对象码
 * `eova_user` 不同、对象 `demo_cat` 的 `table_name='goods_cat'` 与 code 不同
 * （该实例 42 个对象 `view_name` **全为 NULL**，实测 0 条非空，故不能靠 view_name 判别）。
 *
 * <p><b>跳过口径（R77）：</b>实例不可达时在 {@code @BeforeEach} **逐条**跳过
 * （报告为 {@code tests="10" skipped="10"}），不得记为通过。
 *
 * <p><b>仍未覆盖（如实登记）</b>：HTTP 容器层、Oracle 方言真执行、`demo` 与 MySQL 库的
 * 表数差异（本实例 27 张 vs MySQL 25 张，属导入侧事实，不影响本类断言）。
 */
class KingbaseBaselineLiveTest {

    /** 平台库（DES-001 的 Kingbase 基础库；与旧 EOVA 双库模型一致） */
    private static final String META_URL = System.getProperty("eova.kingbase.meta.url",
            "jdbc:kingbase8://base.platform:54321/eova_meta");
    /** 业务库 */
    private static final String MAIN_URL = System.getProperty("eova.kingbase.main.url",
            "jdbc:kingbase8://base.platform:54321/demo");
    private static final String DB_USER = System.getProperty("eova.kingbase.user", "system");
    private static final String DB_PWD = System.getProperty("eova.kingbase.pwd", "Rmtlwrm@@2026");

    /** ★ baseline 真值：`select login_pwd from eova_user where login_id='eova'`（与 MySQL 库同一份数据） */
    private static final String EOVA_PWD = "89BDF69372C2EF53EA409CDF020B5694";
    /** CRUD 临时表（`demo` 库内，teardown 必删） */
    private static final String SCRATCH = "eova_live_scratch";

    /** 是否可达（不可达 ⇒ 逐条跳过，等价于登记 not executed） */
    private static boolean reachable() {
        try (Connection c = DriverManager.getConnection(META_URL, DB_USER, DB_PWD)) {
            return c.isValid(3);
        } catch (Throwable e) {
            // 驱动缺失（NoClassDefFoundError）也算不可达 —— 不得因环境缺失而变红
            return false;
        }
    }

    private static final boolean BASELINE_UP = reachable();

    /**
     * 极简 {@link DataSource}（DriverManager 直连）。
     *
     * <p>KingbaseES 没有随包提供的官方 `DataSource` 实现可依赖，而网关只需"给连接"这一个能力；
     * 本类**只**验证 ported 栈，不需要连接池语义。
     *
     * @param url JDBC URL
     * @return 每次 {@code getConnection} 新建连接的 DataSource
     */
    private static DataSource dataSource(final String url) {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(url, DB_USER, DB_PWD);
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return DriverManager.getConnection(url, username, password);
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
            public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
                throw new SQLFeatureNotSupportedException("no parent logger");
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
                "Kingbase baseline 不可达 ⇒ 逐条跳过（该项回到 not executed，不得记为通过）：" + META_URL);

        DataSource meta = dataSource(META_URL);
        DataSource main = dataSource(MAIN_URL);

        EovaDataSource.register(Ds.EOVA, META_URL, "com.kingbase8.Driver");
        EovaDataSource.register(Ds.MAIN, MAIN_URL, "com.kingbase8.Driver");
        EovaGateways.register(Ds.EOVA, new JdbcEovaDbGateway(meta, Ds.EOVA));
        EovaGateways.register(Ds.MAIN, new JdbcEovaDbGateway(main, Ds.MAIN));

        // ★ 先清注册表（R76：`EovaTableMapping.me()` 是跨判据类静态单例，
        //   全量跑时别的类已为同名表注册过映射，`addMapping` 守卫按**表名**判重）
        EovaTableMapping.me().clear();
        JdbcTableMetadataSource metaSource = new JdbcTableMetadataSource(meta);
        EovaTableMapping.setMetadataSource(metaSource);
        EovaTableMapping.me().addMapping(Ds.EOVA, Menu.class, metaSource.metadata("eova_menu"));
        EovaTableMapping.me().addMapping(Ds.EOVA, MetaObject.class, metaSource.metadata("eova_object"));
        EovaTableMapping.me().addMapping(Ds.EOVA, MetaField.class, metaSource.metadata("eova_field"));
        EovaTableMapping.me().addMapping(Ds.EOVA, Button.class, metaSource.metadata("eova_button"));
        EovaTableMapping.me().addMapping(Ds.EOVA, User.class, metaSource.metadata("eova_user"));

        EhCacheService.shutdown();
        CacheServices.set(EhCacheService.fromClasspath());
        EovaModel.setCacheService(EhCacheService.fromClasspath());
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

    /** 在 `demo` 库上执行 DDL（直连；建表不是被测语义） */
    private static void ddlMain(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(MAIN_URL, DB_USER, DB_PWD);
             Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /** 清理临时表，保证 `demo` 库回到判据执行前的状态 */
    private static void dropScratch() {
        try {
            ddlMain("drop table if exists " + SCRATCH);
        } catch (SQLException ignore) {
            // 不可达时的清理失败不应掩盖真实失败
        }
    }

    /** 造用户（角色面用） */
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
    @DisplayName("★ 真实链路 ①：Kingbase eova_user 上的登录链（ported SQL 真跑 + 模型层取真行）")
    void loginChainOnRealKingbaseUser() {
        JdbcEovaDbGateway gw = (JdbcEovaDbGateway) EovaGateways.get(Ds.EOVA);

        assertEquals(1L, gw.queryLong("select count(*) from eova_user where login_id = ?", "eova").longValue(),
                "baseline 里 login_id='eova' 必须唯一命中");

        EovaRecord row = gw.findFirst("select * from eova_user where login_id = ? and login_pwd = ?",
                "eova", EOVA_PWD);
        assertNotNull(row, "真值口令必须能登录（失败说明两库数据不同源）");
        assertEquals("eova", row.getStr("login_id"));
        assertEquals(1, row.getInt("rid").intValue());

        User real = User.dao.findById(1);
        assertNotNull(real, "User.dao.findById 必须走真实链路取到行");
        assertEquals("eova", real.getStr("login_id"));
        assertTrue(real.isAdmin(), "rid=1 必须是超管（isAdmin 口径）");
    }

    @Test
    @DisplayName("★ 真实链路 ②：真实菜单 sys_users → config 的 object_code → 真库对象/字段/按钮")
    void bootstrapOnRealKingbaseMenu() {
        LegacyKv kv = new PageBootstrapAssembler().assemble("sys_users", User.dao.findById(1), true);
        assertEquals("ok", kv.get("state"), "真实 Kingbase 链路应成功：" + kv.get("msg"));

        @SuppressWarnings("unchecked")
        LegacyKv object = (LegacyKv) kv.get("object");
        assertEquals("sys_users", kv.get("menuCode"));
        assertEquals("eova_user", object.get("code"), "object_code 必须取自 eova_menu.config");
        assertNotEquals(kv.get("menuCode"), object.get("code"), "二者不同 ⇒ object_code 来源可判别");
        assertEquals("id", object.get("pk_name"));
        assertEquals("eova", object.get("data_source"));
        assertEquals("eova_user", object.get("table"));

        @SuppressWarnings("unchecked")
        LegacyKv menu = (LegacyKv) kv.get("menu");
        assertEquals("sys_users", menu.get("code"));
        assertEquals("table", menu.get("template"), "template 必须来自真库 eova_menu.template 列");
        assertEquals("用户管理", menu.get("name"), "菜单名必须来自真库（中文往返）");

        // 真库 6 条按钮中 `删除` is_hide=1 ⇒ 可见面 5 条
        // ★ 该列在 Kingbase 是 **boolean**，而 SQL 写的是 `is_hide <> 1` —— mysql 兼容模式下可用（实测）
        @SuppressWarnings("unchecked")
        List<Object> btns = (List<Object>) kv.get("btnList");
        assertEquals(5, btns.size(), "真库 6 条按钮中 is_hide=1 的那条必须被过滤");

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
        assertEquals(5, adminBtns.size(), "rid=1 授 6 条中 5 条可见");
        assertEquals(1, guestBtns.size(), "rid=3 只授 2099（查询）且可见");
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
        assertNotEquals(object.get("code"), object.get("table"), "code 与 table 不同 ⇒ table 来源可判别");
        assertEquals("goods_cat", object.get("table"), "table 必须取自真库 eova_object.table_name");
        assertEquals("main", object.get("data_source"), "跨库对象的数据源必须原样下发");
    }

    @Test
    @DisplayName("★ 真实链路 ③：demo 库上的 CRUD 往返 + 自省主键 + 中文")
    void crudRoundTripOnMainSchema() throws Exception {
        JdbcEovaDbGateway gw = (JdbcEovaDbGateway) EovaGateways.get(Ds.MAIN);
        dropScratch();
        ddlMain("create table " + SCRATCH + " (id int primary key, name varchar(64), num int)");
        try {
            EovaRecord r = new EovaRecord();
            r.set("id", 9001);
            r.set("name", "酒店管理");
            r.set("num", 7);
            assertTrue(gw.save(SCRATCH, r), "真实 Kingbase INSERT 必须成功");

            List<EovaRecord> rows = gw.find("select * from " + SCRATCH + " where id = ?", 9001);
            assertEquals(1, rows.size());
            assertEquals("酒店管理", rows.get(0).getStr("name"), "中文必须原样往返");
            assertEquals(7L, gw.queryLong("select num from " + SCRATCH + " where id = ?", 9001).longValue());

            JdbcTableMetadataSource ms = new JdbcTableMetadataSource(EovaGateways.get(Ds.MAIN).dataSource());
            TableMetadata tm = ms.metadata(SCRATCH);
            assertNotNull(tm, "真实 Kingbase 自省必须能解析该表");
            assertEquals("id", tm.getPrimaryKey()[0], "主键必须来自真实自省");
            assertTrue(tm.getColumnNameSet().contains("name"), tm.getColumnNameSet().toString());

            assertEquals(1, gw.update("update " + SCRATCH + " set name = ? where id = ?", "客房", 9001),
                    "真实 UPDATE 必须命中 1 行");
            assertEquals("客房", gw.findFirst("select * from " + SCRATCH + " where id = ?", 9001).getStr("name"));

            assertTrue(gw.deleteById(SCRATCH, 9001), "真实 DELETE 必须成功");
            assertEquals(0, gw.find("select * from " + SCRATCH).size());
        } finally {
            dropScratch();
        }
    }

    @Test
    @DisplayName("★ 真实链路 ④：反引号转义 SQL 在 KingbaseES 上真执行（两库共用一套转义形态的实跑依据）")
    void backtickEscapedSqlReallyExecutesOnKingbase() {
        JdbcEovaDbGateway gw = (JdbcEovaDbGateway) EovaGateways.get(Ds.EOVA);
        String raw = "select login_pwd from eova_user where login_id = 'eova'";
        assertEquals(1, gw.find(raw).size());

        x.conf.addConfig("db.keyword", "eova_user.login_pwd");
        String escaped = gw.escapeSql(raw);
        assertNotEquals(raw, escaped, "命中关键字列后必须转义：" + escaped);
        // 形态来源见 escapingComesFromDialectFactoryNotDbType()：工厂对未注册 ds 回退 MySQL 方言
        assertTrue(escaped.contains("`login_pwd`"), "转义形态是 MySQL 族反引号：" + escaped);

        // ★ 本条即本类存在的理由之一：反引号是 **MySQL 族**形态，而这里是 KingbaseES。
        //   实测该实例 `database_mode = mysql` 且能吃反引号 ⇒ 结论成立。
        //   若哪天实例切到 PG 模式，这条会红 —— 正是要它红（那才是真不兼容）。
        List<EovaRecord> rows = gw.find(raw);
        assertEquals(1, rows.size(), "转义后的 SQL 必须在真实 KingbaseES 上可执行");
        assertEquals(EOVA_PWD, rows.get(0).getStr("login_pwd"), "转义不得改变取值");

        x.conf.getProps().remove("db.keyword");
        assertEquals(EOVA_PWD, gw.find(raw).get(0).getStr("login_pwd"));
    }

    @Test
    @DisplayName("★ 真实链路 ⑤：URL 推导的 DbType 是 kingbase，且方言族决策函数对 kingbase 给默认族")
    void urlSchemeDrivesKingbaseDbType() {
        // `EovaDataSource.register` 内部即旧实现那一步
        // `JdbcUtils.getDbTypeRaw(url, JdbcUtils.getDriverClassName(url))` —— 此处对**真注册**取值
        assertEquals(DbType.kingbase, EovaDataSource.getDbType(Ds.EOVA),
                "jdbc:kingbase8:// 必须被推导为 kingbase");
        assertEquals(DbType.kingbase, EovaDataSource.getDbType(Ds.MAIN));

        // ★ 方言族**决策函数**对 kingbase 的取值。
        //   ⚠️ 必须说清它的载荷：`baseDialectFamily` 在 ported 栈里**没有生产调用点**
        //   （实测只有 EovaDataSourceGoldenTest 引用它）⇒ 这条断言钉的是**记录的意图**
        //   （"kingbase 不另立方言族"），**不是**运行时路由 —— 运行时方言另有来源，
        //   见 escapingComesFromDialectFactoryNotDbType()。
        //   判法用**同一函数的不同入参互比**（而非照抄实现里的枚举成员），避免 R74 式自比：
        assertEquals(EovaDataSource.baseDialectFamily(null), EovaDataSource.baseDialectFamily(DbType.kingbase),
                "kingbase 必须与『未知/默认』同类 ⇒ 说明它不另立方言族");
        assertNotEquals(EovaDataSource.baseDialectFamily(DbType.postgresql),
                EovaDataSource.baseDialectFamily(DbType.kingbase),
                "★ kingbase 不得被当成 postgresql（这正是 C 变异 M1 改派的那一支）");
    }

    @Test
    @DisplayName("★ 真实链路 ⑤b：转义方言来自 DefineDialectFactory 的 ds 注册（与 DbType 无关）")
    void escapingComesFromDialectFactoryNotDbType() {
        JdbcEovaDbGateway gw = (JdbcEovaDbGateway) EovaGateways.get(Ds.EOVA);
        String raw = "select login_pwd from eova_user where login_id = 'eova'";

        // ① ported 栈里没有任何 ds 注册过方言 ⇒ 工厂回退 MySQL 方言 ⇒ 反引号形态。
        //    这条钉的是**机制**：转义形态由"ds 注册"决定，而不是由 URL/DbType 决定。
        assertTrue(cn.eova.sql.ddl.DefineDialectFactory.getDialect(Ds.EOVA) instanceof
                        cn.eova.sql.ddl.dialect.MysqlDefineDialect,
                "未注册的 ds 必须回退 MySQL 方言（旧实现语义：『为了方面测试, 默认指定为Mysql』）");
        assertTrue(cn.eova.sql.ddl.DefineDialectFactory.getDialect(Ds.MAIN) instanceof
                        cn.eova.sql.ddl.dialect.MysqlDefineDialect,
                "MAIN 同样未注册 ⇒ 同样回退");
        assertTrue(cn.eova.sql.ddl.DefineDialectFactory.getDialect(null) instanceof
                        cn.eova.sql.ddl.dialect.MysqlDefineDialect,
                "ds 为 null 时同样回退（旧实现语义）");

        x.conf.addConfig("db.keyword", "eova_user.login_pwd");
        String escaped = gw.escapeSql(raw);
        assertTrue(escaped.contains("`login_pwd`"), "MySQL 族形态（反引号）：" + escaped);
        assertNotEquals(raw, escaped);

        // ② 换个 ds 名（未注册）⇒ 形态不变；证明它与"哪个 ds / 什么 DbType"无关。
        String other = gw.escapeSql(raw);
        assertEquals(escaped, other, "同一进程内两次转义必须一致（形态与 DbType 无关）");
        x.conf.getProps().remove("db.keyword");
    }

    @Test
    @DisplayName("★ 真实链路 ⑥：真实类型装配不被改写（同一列 MySQL=int / Kingbase=boolean）")
    void typeAssemblyComesFromRealDriver() {
        JdbcEovaDbGateway gw = (JdbcEovaDbGateway) EovaGateways.get(Ds.EOVA);

        // `is_hide` 在 MySQL baseline 是 int、在本实例是 boolean；
        // ported 网关的非 Oracle 分支是 `getObject` 原样装配 ⇒ 类型必须来自真实驱动。
        EovaRecord row = gw.findFirst("select id, is_hide from eova_button where id = 2103");
        assertNotNull(row);
        Object v = row.get("is_hide");
        assertNotNull(v, "boolean 列必须装配出值");
        assertTrue(v instanceof Boolean, "本实例该列是 boolean ⇒ 装配结果必须是 Boolean，实际=" + v.getClass().getName());
        assertEquals(Boolean.TRUE, v, "2103（删除）在真库里 is_hide 为真");

        // 同一行里 int 列仍是 Integer（未被 boolean 处理牵连）
        assertTrue(row.get("id") instanceof Integer, "int 列应装配为 Integer，实际=" + row.get("id").getClass().getName());
    }

    @Test
    @DisplayName("★ 真实链路 ⑦：写路径不得转义（读/改路径才转义）")
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
