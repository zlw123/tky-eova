/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.config;

import cn.eova.common.utils.db.SqlUtil;
import com.alibaba.druid.DbType;
import com.alibaba.druid.util.JdbcUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EovaDataSource}（可移植核心）与 {@link SqlUtil#getSequence} 的等价判据。
 *
 * <p><b>DbType 推导的 oracle 就是 druid 本身：</b>旧 {@code create()} 用的是
 * {@code JdbcUtils.getDbTypeRaw(url, driver)}，本判据在测试里<b>独立调用同一个 druid 工具</b>
 * 再与 {@code register()} 的结果比对 —— 避免把映射表誊抄进测试（R36/R43 同类风险）。
 *
 * <p><b>本判据把计划里的两条约束变成可执行断言：</b>
 * <ol>
 *   <li><b>URL 方案硬约束</b>：KingbaseES 必须用 {@code jdbc:kingbase8://}。
 *       换成 {@code jdbc:postgresql://} 会让 druid 推导出 {@code postgresql}，
 *       EOVA 随之切到 {@code PostgreSqlDialect} 并尝试加载 PG 的 Mod 方言，
 *       与 DES-001 选定的 mysql 兼容模式冲突。</li>
 *   <li><b>Kingbase 走默认 MySQL 方言</b>（SP3-a 结论）：{@code DbType.kingbase}
 *       不在 {@code buildDialect} 的任一分支里，故落默认分支。此断言是<b>回归守卫</b> ——
 *       若将来有人"顺手"给 kingbase 加一个方言分支，这里会红。</li>
 * </ol>
 *
 * <p>acceptanceProfile: golden-eova-datasource
 */
class EovaDataSourceGoldenTest {

    @BeforeEach
    void setUp() {
        EovaDataSource.clear();
    }

    @Test
    @DisplayName("DbType 推导：与 druid 直查一致（同一工具、同一版本）")
    void dbTypeDerivationMatchesDruid() {
        Map<String, String> urls = new LinkedHashMap<>();
        urls.put("mysql", "jdbc:mysql://127.0.0.1:3306/demo");
        urls.put("kingbase8", "jdbc:kingbase8://127.0.0.1:54321/demo");
        urls.put("kingbase", "jdbc:kingbase://127.0.0.1:54321/demo");
        urls.put("postgresql", "jdbc:postgresql://127.0.0.1:5432/demo");
        urls.put("oracle", "jdbc:oracle:thin:@127.0.0.1:1521:orcl");
        urls.put("dm", "jdbc:dm://127.0.0.1:5236/demo");
        urls.put("sqlserver", "jdbc:sqlserver://127.0.0.1:1433;databaseName=demo");
        urls.put("unknown", "jdbc:foo://127.0.0.1/x");

        int compared = 0;
        for (Map.Entry<String, String> e : urls.entrySet()) {
            String ds = e.getKey();
            DbType viaRegister = EovaDataSource.register(ds, e.getValue(), null);
            DbType viaDruid = JdbcUtils.getDbTypeRaw(e.getValue(), null);
            assertEquals(viaDruid, viaRegister,
                    "[" + ds + "] register 的推导结果应与 druid 直查一致");
            compared++;
        }
        // 真实 URL 的具体取值（与 SP3-a 实测一致）—— 独立断言，防止 druid 升级后静默漂移
        assertEquals(DbType.mysql, EovaDataSource.getDbType("mysql"));
        assertEquals(DbType.kingbase, EovaDataSource.getDbType("kingbase8"),
                "jdbc:kingbase8:// 应推导为 DbType.kingbase");
        assertEquals(DbType.postgresql, EovaDataSource.getDbType("postgresql"));
        assertEquals(DbType.oracle, EovaDataSource.getDbType("oracle"));
        assertEquals(DbType.dm, EovaDataSource.getDbType("dm"));
        assertEquals(DbType.sqlserver, EovaDataSource.getDbType("sqlserver"));
        assertNull(EovaDataSource.getDbType("unknown"), "未识别的 URL 应推导为 null");

        System.out.println("[数据源] DbType 推导与 druid 直查一致（" + compared + " 个 URL）");
    }

    @Test
    @DisplayName("未注册数据源返回 null；null 参与的等值比较不得抛异常")
    void unregisteredDataSourceReturnsNull() {
        assertNull(EovaDataSource.getDbType("never_registered"),
                "未注册的数据源应返回 null（旧实现即 HashMap.get）");
        assertNull(EovaDataSource.getDbType(null), "null 数据源名应返回 null");
        // BaseModel.save() 的写法即 getDbType(ds) == DbType.oracle —— null 时必须安全为 false
        assertTrue(EovaDataSource.getDbType("never_registered") != DbType.oracle,
                "null 与 DbType.oracle 比较应为 false，不得 NPE");
    }

    @Test
    @DisplayName("方言族选择：MySQL 为默认分支；kingbase/dm 落默认（回归守卫）")
    void dialectFamilySelection() {
        assertEquals(EovaDataSource.DialectFamily.ORACLE,
                EovaDataSource.baseDialectFamily(DbType.oracle));
        assertEquals(EovaDataSource.DialectFamily.POSTGRESQL,
                EovaDataSource.baseDialectFamily(DbType.postgresql));
        assertEquals(EovaDataSource.DialectFamily.SQLSERVER,
                EovaDataSource.baseDialectFamily(DbType.sqlserver));

        // 回归守卫（SP3-a 结论）：kingbase 不在分支里 → 默认 MySQL 方言
        assertEquals(EovaDataSource.DialectFamily.MYSQL,
                EovaDataSource.baseDialectFamily(DbType.kingbase),
                "kingbase 必须落默认 MySQL 方言 —— 与 DES-001 的 mysql 兼容模式一致；"
                        + "若此处变红，说明有人给 kingbase 加了方言分支，需重新评估兼容模式");
        // dm 同样不在第一段链里；其基础方言只有在商业 Mod 加载成功时才被替换为 DmDialect
        assertEquals(EovaDataSource.DialectFamily.MYSQL,
                EovaDataSource.baseDialectFamily(DbType.dm),
                "dm 的基础方言是 MySQL —— DmDialect 仅在 Mod 加载成功时替换");
        assertEquals(EovaDataSource.DialectFamily.MYSQL,
                EovaDataSource.baseDialectFamily(null),
                "null DbType 应落默认分支");

        System.out.println("[数据源] 方言族选择与 buildDialect 一致（kingbase/dm/null → 默认 MySQL）");
    }

    @Test
    @DisplayName("商业 Mod 方言映射：oracle/postgresql/sqlserver/dm 有分支，kingbase/mysql 无")
    void businessDialectModMapping() {
        assertEquals("oracle", EovaDataSource.businessDialectModName(DbType.oracle));
        assertEquals("postgresql", EovaDataSource.businessDialectModName(DbType.postgresql));
        assertEquals("sqlserver", EovaDataSource.businessDialectModName(DbType.sqlserver));
        assertEquals("dm", EovaDataSource.businessDialectModName(DbType.dm));
        assertNull(EovaDataSource.businessDialectModName(DbType.kingbase),
                "kingbase 没有 Mod 方言分支 —— 这正是它留在 MySQL 方言的原因");
        assertNull(EovaDataSource.businessDialectModName(DbType.mysql));
        assertNull(EovaDataSource.businessDialectModName(null));
    }

    @Test
    @DisplayName("URL 方案硬约束回归：jdbc:kingbase8:// 走 MySQL 路径，jdbc:postgresql:// 会切方言")
    void urlSchemeConstraint() {
        EovaDataSource.register("kb", "jdbc:kingbase8://host:54321/eova_meta", "com.kingbase8.Driver");
        EovaDataSource.register("pg", "jdbc:postgresql://host:54321/eova_meta", "com.kingbase8.Driver");

        assertEquals(DbType.kingbase, EovaDataSource.getDbType("kb"));
        assertEquals(DbType.postgresql, EovaDataSource.getDbType("pg"),
                "同一条 KingbaseES 连接串写成 postgresql 方案会被推导为 postgresql");

        assertEquals(EovaDataSource.DialectFamily.MYSQL, EovaDataSource.baseDialectFamily(
                EovaDataSource.getDbType("kb")), "kingbase8 方案 → 默认 MySQL 方言");
        assertEquals(EovaDataSource.DialectFamily.POSTGRESQL, EovaDataSource.baseDialectFamily(
                EovaDataSource.getDbType("pg")), "postgresql 方案 → PostgreSqlDialect（与兼容模式冲突）");

        System.out.println("[数据源] URL 方案约束已固化为回归断言："
                + "jdbc:kingbase8:// → MySQL 方言；jdbc:postgresql:// → PostgreSqlDialect");
    }

    @Test
    @DisplayName("SqlUtil.getSequence：按数据源类型生成序列表达式（经 EovaConst.SEQ_ 传递验证 port）")
    void getSequenceFollowsDbType() {
        EovaDataSource.register("my", "jdbc:mysql://127.0.0.1:3306/demo", null);
        EovaDataSource.register("or", "jdbc:oracle:thin:@127.0.0.1:1521:orcl", null);
        EovaDataSource.register("pg", "jdbc:postgresql://127.0.0.1:5432/demo", null);
        EovaDataSource.register("kb", "jdbc:kingbase8://127.0.0.1:54321/demo", null);

        assertNull(SqlUtil.getSequence("my", "eova_user"),
                "MySQL 无序列概念，应返回 null");
        assertNull(SqlUtil.getSequence("kb", "eova_user"),
                "kingbase 落默认分支，同样返回 null（与 MySQL 一致）");

        String oracle = SqlUtil.getSequence("or", "eova_user");
        String pg = SqlUtil.getSequence("pg", "eova_user");
        assertTrue(oracle.endsWith(".nextval"), "Oracle 序列表达式应以 .nextval 结尾：" + oracle);
        assertTrue(oracle.contains("eova_user"), "Oracle 序列表达式应含表名：" + oracle);
        assertEquals(String.format("nextval('%s'::regclass)", EovaConst.SEQ_ + "eova_user"), pg,
                "PostgreSQL 序列表达式形态应与旧实现一致（含 EovaConst.SEQ_ 前缀）");

        System.out.println("[数据源] SqlUtil.getSequence 一致：Oracle=" + oracle
                + "；PG=" + pg + "；MySQL/Kingbase=null（SEQ_=" + EovaConst.SEQ_ + "）");
    }

    /**
     * **方言族三件套必须真的被注册**（第 305 轮 · 真缺陷 P2 回归锁）。
     *
     * <p><b>缺陷现场</b>：旧栈 {@code EovaDataSource#buildDialect} 的末尾三行是注册副作用 ——
     * {@code EovaConfig.addQueryDialect(ds, queryDialect)}、{@code addConvertor(ds, convertor)}、
     * {@code DefineDialectFactory.addDialect(ds, defineDialect)}；本轮之前的 port 只保留了
     * "方言族选择"（{@code baseDialectFamily}/{@code businessDialectModName}），
     * <b>把这三行丢了</b> ⇒ {@code EovaConfig.queryDialectMap} 恒空 ⇒
     * {@code WidgetManager#buildQueryCondition} 在**带查询条件**的表格查询上 NPE
     * （服务端栈实测：{@code NullPointerException ... getQueryDialect(String) is null at WidgetManager.java:327}）
     * ⇒ 接口返回「查询条件错误, 请检查查询条件!」⇒ 页面"有接口、有数据、页面空"。</p>
     *
     * <p>实测对照（同一请求 {@code POST /api/table/query/eova_field_code?page=1&limit=99999&sort=&biz=meta_eidt}
     * + {@code {"object_code":"meta_product"}}）：旧栈 200 ok / count=14 / 14 行；
     * 修前新栈 200 {@code state=fail}；修后新栈 200 ok / count=14 / 14 行。</p>
     */
    @Test
    @DisplayName("方言族三件套注册：逐 ds 落到 EovaConfig/DefineDialectFactory（缺则带条件查询必 NPE）")
    void dialectFamilyIsRegisteredPerDataSource() {
        cn.eova.sql.dql.dialect.QueryDialect before = cn.eova.config.EovaConfig.getQueryDialect("__golden_ds__");
        cn.eova.core.type.Convertor beforeCv = cn.eova.config.EovaConfig.getConvertor("__golden_ds__");
        cn.eova.sql.ddl.dialect.DefineDialect beforeDd = cn.eova.sql.ddl.DefineDialectFactory.getDialect("__golden_ds__");
        try {
            cn.eova.config.EovaDataSource.registerDialectFamily("__golden_ds__", com.alibaba.druid.DbType.mysql);

            cn.eova.sql.dql.dialect.QueryDialect qd = cn.eova.config.EovaConfig.getQueryDialect("__golden_ds__");
            cn.eova.core.type.Convertor cv = cn.eova.config.EovaConfig.getConvertor("__golden_ds__");
            cn.eova.sql.ddl.dialect.DefineDialect dd = cn.eova.sql.ddl.DefineDialectFactory.getDialect("__golden_ds__");
            assertNotNull(qd, "QueryDialect 必须被注册（否则带条件的查询会 NPE）");
            assertNotNull(cv, "Convertor 必须被注册");
            assertNotNull(dd, "DefineDialect 必须被注册");
            assertTrue(qd instanceof cn.eova.sql.dql.dialect.MysqlQueryDialect,
                    "MySQL 数据源落默认分支（与旧 buildDialect 的默认值一致）");
            assertTrue(dd instanceof cn.eova.sql.ddl.dialect.MysqlDefineDialect,
                    "DefineDialect 同理落默认分支");

            // 反空断言：注册必须**按 ds 建条目**，不得共用同一个 key（否则多数据源会互相覆盖）
            cn.eova.config.EovaDataSource.registerDialectFamily("__golden_ds2__", com.alibaba.druid.DbType.mysql);
            assertNotNull(cn.eova.config.EovaConfig.getQueryDialect("__golden_ds2__"),
                    "另一个 ds 也必须有自己的条目");
            assertNotSame(cn.eova.config.EovaConfig.getQueryDialect("__golden_ds__"),
                    cn.eova.config.EovaConfig.getQueryDialect("__golden_ds2__"),
                    "两个 ds 的 QueryDialect 不得是同一个实例被复用为同一 key");
        } finally {
            cn.eova.config.EovaConfig.addQueryDialect("__golden_ds__", before);
            cn.eova.config.EovaConfig.addQueryDialect("__golden_ds2__", null);
        }
    }

    /**
     * **注册动作必须挂在 ds 注册这一步上**（第 305 轮 · 判据首版是**空跑**，被变异纪律抓出）。
     *
     * <p>判据口径：直接驱动 {@code registerOne(...)}（{@code create()} 的循环体，已抽出以便判据驱动），
     * 断言"注册完一个 ds 之后，该 ds 的方言三件套都已在位"。
     * 不得写成"跑一遍 {@code create()} 再看结果" —— 单测环境没有 {@code db.datasource} 配置，
     * 那种写法会**静默空跑**（本判据首版即如此：8/8 全绿，但 M1 变异抓不到，属假绿）。</p>
     */
    @Test
    @DisplayName("registerOne：注册一个 ds 之后其方言族即刻在位（旧栈在 ds 循环里调 buildDialect 的同一点）")
    void registerOneRegistersDialect() {
        String ds = "__golden_one__";
        try {
            cn.eova.config.EovaDataSource.registerOne(ds, "jdbc:mysql://127.0.0.1:3306/golden", "u", "p",
                    "com.mysql.cj.jdbc.Driver", "log4j",
                    new cn.eova.compat.jfinal.config.LegacyPlugins());
            assertNotNull(cn.eova.config.EovaConfig.getQueryDialect(ds),
                    "registerOne 之后必须有 QueryDialect（否则该 ds 上带条件的查询必 NPE）");
            assertNotNull(cn.eova.config.EovaConfig.getConvertor(ds), "registerOne 之后必须有 Convertor");
            assertNotNull(cn.eova.sql.ddl.DefineDialectFactory.getDialect(ds), "registerOne 之后必须有 DefineDialect");
            assertTrue(cn.eova.config.EovaConfig.getQueryDialect(ds)
                            instanceof cn.eova.sql.dql.dialect.MysqlQueryDialect,
                    "MySQL 数据源落默认分支（与旧 buildDialect 默认值一致）");
        } finally {
            cn.eova.config.EovaConfig.addQueryDialect(ds, null);
            cn.eova.config.EovaConfig.addConvertor(ds, null);
        }
    }
}
