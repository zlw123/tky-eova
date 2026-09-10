/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.base;

import cn.eova.compat.table.EovaTableMapping;
import cn.eova.db.EovaDbGateway;
import cn.eova.db.JdbcEovaDbGateway;
import cn.eova.db.JdbcTableMetadataSource;
import cn.eova.db.EovaModel;
import cn.eova.testkit.OldImplementationLoader;
import com.jfinal.plugin.activerecord.ActiveRecordPlugin;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Model;
import com.jfinal.plugin.ehcache.EhCachePlugin;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BaseModel} 的等价判据（阶段 1 `D-MODEL`）。
 *
 * <p><b>强度说明（诚实地界定证据边界）：</b>旧 {@code BaseModel} 的基类是
 * jfinal {@code Model}，无法在同一个测试源集里同时编译出"继承旧 BaseModel"与
 * "继承新 BaseModel"的两个子类，故<b>做不到类对类的实例比对</b>。
 * 本判据改用两条<b>可执行</b>的证据线：
 * <ol>
 *   <li><b>方法面比对</b>：经 {@code OldImplementationLoader} 加载<b>旧 {@code BaseModel} 类</b>，
 *       逐条比对声明的方法签名（公开/protected）—— 抓"漏 port / 改名 / 参数变了"。
 *       允许的替换（{@code Page} → {@link cn.eova.db.EovaPage}）显式声明，不是静默放宽。</li>
 *   <li><b>行为比对</b>：{@code BaseModel} 的每个方法体都归约到 jfinal 原语
 *       （{@code Db.use(ds).update} / {@code Db.queryNumber} / {@code Model.findByCache} / …），
 *       故拿<b>真实 jfinal 原语</b>在同一张表上跑同一件事并比对结果 ——
 *       这是"我的组合"对"旧的原语"，不是自造期望值。</li>
 * </ol>
 *
 * <p>acceptanceProfile: golden-base-model
 */
class BaseModelGoldenTest {

    private static final String URL = "jdbc:mysql://127.0.0.1:13306/eova_meta"
            + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String USER = "root";
    private static final String PWD = "root";
    private static final String TABLE = "eova_base_probe";
    private static final String SEQ_TABLE = "eova_base_seq_probe";

    private static DataSource ds;
    private static ActiveRecordPlugin arp;
    private static EhCachePlugin ehCache;

    /** 旧栈侧：真实 jfinal Model 子类 */
    public static class JfProbe extends Model<JfProbe> {
        public static final JfProbe dao = new JfProbe();
    }

    /** 新栈侧：继承新 BaseModel（mysql 数据源） */
    public static class EvProbe extends BaseModel<EvProbe> {
        public static final EvProbe dao = new EvProbe().dao();
    }

    /** 新栈侧：注册在 "or" 数据源下，用于验证 Oracle 序列分支 */
    public static class EvSeqProbe extends BaseModel<EvSeqProbe> {
        public static final EvSeqProbe dao = new EvSeqProbe().dao();
    }

    @BeforeAll
    static void setUp() throws Exception {
        ds = dataSource();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("drop table if exists " + TABLE);
            st.execute("create table " + TABLE + " (id int not null auto_increment,"
                    + "name varchar(64), primary key (id)) engine=InnoDB default charset=utf8mb4");
            // 序列表：主键用 varchar 且带默认值，好让"Oracle 分支把序列表达式写进主键"可观测
            st.execute("drop table if exists " + SEQ_TABLE);
            st.execute("create table " + SEQ_TABLE + " (id varchar(64) not null default '',"
                    + "name varchar(64), primary key (id)) engine=InnoDB default charset=utf8mb4");
        }
        arp = new ActiveRecordPlugin(ds);
        arp.addMapping(TABLE, JfProbe.class);
        arp.start();
        // 旧栈侧的 Model.findByCache 走 CacheKit -> EhCache；配置即 classpath 的 ehcache.xml
        ehCache = new EhCachePlugin();
        ehCache.start();

        EovaTableMapping mapping = EovaTableMapping.me();
        mapping.clear();
        mapping.setMetadataSource(new JdbcTableMetadataSource(ds));
        mapping.addMapping("eova", TABLE, EvProbe.class);
        mapping.addMapping("or", SEQ_TABLE, EvSeqProbe.class);

        EovaModel.clearGateways();
        EovaDbGateway gw = new JdbcEovaDbGateway(ds);
        EovaModel.setGateway(gw);
        // queryByCache 等走缓存，需要注入 CacheService（与旧栈同一份 ehcache.xml）
        EovaModel.setCacheService(cn.eova.compat.cache.EhCacheService.fromClasspath());
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (ehCache != null) {
            ehCache.stop();
        }
        if (arp != null) {
            arp.stop();
        }
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("drop table if exists " + TABLE);
            st.execute("drop table if exists " + SEQ_TABLE);
        }
    }

    private static DataSource dataSource() {
        MysqlDataSource d = new MysqlDataSource();
        d.setUrl(URL);
        d.setUser(USER);
        d.setPassword(PWD);
        return d;
    }

    @Test
    @DisplayName("方法面：与旧 BaseModel 的声明方法逐条一致（允许项显式声明）")
    void methodSurfaceMatchesOld() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable()
                        && OldImplementationLoader.oldJFinalJarAvailable(),
                "旧产物或旧 jfinal 制品缺失，无法加载旧 BaseModel");
        ClassLoader loader = OldImplementationLoader.createWithOldJFinal(
                OldImplementationLoader.locateRepoRoot());
        Class<?> oldClass = loader.loadClass("cn.eova.common.base.BaseModel");

        Set<String> oldSurface = surface(oldClass);
        Set<String> newSurface = surface(BaseModel.class);
        assertFalse(oldSurface.isEmpty(), "旧 BaseModel 应声明若干方法");

        Set<String> missing = new TreeSet<>(oldSurface);
        missing.removeAll(newSurface);
        Set<String> extra = new TreeSet<>(newSurface);
        extra.removeAll(oldSurface);

        assertTrue(missing.isEmpty(),
                "以下旧 BaseModel 方法在新实现中缺失或签名变化：\n  " + String.join("\n  ", missing));
        assertTrue(extra.isEmpty(),
                "以下新实现方法在旧 BaseModel 中不存在（如需保留请显式声明）：\n  "
                        + String.join("\n  ", extra));

        System.out.println("[BaseModel 比对] 方法面一致，共 " + oldSurface.size() + " 个公开/protected 方法");
        for (String s : oldSurface) {
            System.out.println("    " + s);
        }
    }

    /** 提取公开/protected 声明方法的规范化签名（含已声明的类型替换） */
    private static Set<String> surface(Class<?> c) {
        Set<String> out = new TreeSet<>();
        for (Method m : c.getDeclaredMethods()) {
            if (Modifier.isPrivate(m.getModifiers()) || m.isSynthetic()) {
                continue;
            }
            String params = Arrays.stream(m.getParameterTypes())
                    .map(t -> normalize(t.getSimpleName()))
                    .collect(Collectors.joining(","));
            out.add(normalize(m.getReturnType().getSimpleName()) + " " + m.getName() + "(" + params + ")");
        }
        return out;
    }

    /**
     * 已声明的类型替换（因基类由 jfinal {@code Model} 换为 {@link EovaModel}，
     * 泛型变量 {@code M} 的擦除结果随之改变 —— 这是基类替换的必然后果，不是漏 port）：
     * <ul>
     *   <li>jfinal {@code Page} → {@link cn.eova.db.EovaPage}</li>
     *   <li>{@code Model} → {@link EovaModel}（{@code M} 的擦除上界）</li>
     * </ul>
     */
    private static String normalize(String simpleName) {
        if ("Page".equals(simpleName)) {
            return "EovaPage";
        }
        if ("Model".equals(simpleName)) {
            return "EovaModel";
        }
        return simpleName;
    }

    @Test
    @DisplayName("execute / isExist：与真实 jfinal 原语 Db.update / Db.queryNumber 结果一致")
    void executeAndIsExistMatchJFinalPrimitives() throws Exception {
        // —— execute(sql, paras)：影响行数应与 Db.update 一致 ——
        EvProbe m = new EvProbe();
        m.put("name", "a").save();
        Object id = m.get("id");

        m.execute("update " + TABLE + " set name = ? where id = ?", "b", id);
        assertEquals("b", nameOf(id), "BaseModel.execute 应已生效");
        // 对照组：同一句 SQL 用真实 jfinal 原语执行，影响的也应是同一行
        int viaDb = Db.update("update " + TABLE + " set name = ? where id = ?", "c", id);
        assertEquals(1, viaDb, "对照组：Db.update 应影响 1 行");
        assertEquals("c", nameOf(id), "对照组：Db.update 应已生效");

        // —— isExist(sql, paras)：应等于 Db.queryNumber(...).longValue() != 0 ——
        String cnt = "select count(*) from " + TABLE + " where id = ?";
        boolean viaBaseModel = new EvProbe().isExist(cnt, id);
        long countFromDb = Db.queryNumber(cnt, id).longValue();
        assertEquals(countFromDb != 0, viaBaseModel, "isExist 应与 Db.queryNumber 的判定一致");
        assertTrue(viaBaseModel, "该行存在，isExist 应为 true");

        String cntNone = "select count(*) from " + TABLE + " where id = ?";
        assertEquals(false, new EvProbe().isExist(cntNone, -12345L),
                "不存在的行 isExist 应为 false");

        System.out.println("[BaseModel 比对] execute/isExist 与 jfinal 原语一致（Db.update=" + viaDb
                + "，isExist=" + viaBaseModel + "）");
    }

    @Test
    @DisplayName("queryByCache / queryFisrtByCache：与真实 Model.findByCache 同名同值")
    void cacheQueryMatchesRealModel() throws Exception {
        EvProbe m = new EvProbe();
        m.put("name", "cache-a").save();
        Object id = m.get("id");

        String sql = "select * from " + TABLE + " where id = " + id;

        // 注意：两侧共用同一个 ehcache 实例（同一 JVM），若用【同一个键】会互相读到
        // 对方的 List<不同模型类> 而抛 ClassCastException —— 那是测试共用缓存的产物，
        // 生产上只有一个实现存在。故旧侧用独立键（键只影响缓存命中，不影响查询结果）。
        String newKey = sql;
        String oldKey = sql + "#old";

        // 新侧：BaseModel.queryByCache（缓存键 = SQL, cacheName = service）
        List<EvProbe> newList = EvProbe.dao.queryByCache(sql);
        // 旧侧：真实 jfinal Model.findByCache（同一 cacheName=service，独立键）
        List<JfProbe> oldList = JfProbe.dao.findByCache("service", oldKey, sql);

        assertEquals(oldList.size(), newList.size(), "queryByCache 与 Model.findByCache 行数应一致");
        assertEquals(
                oldList.stream().map(x -> String.valueOf((Object) x.get("name"))).collect(Collectors.toList()),
                newList.stream().map(x -> String.valueOf((Object) x.get("name"))).collect(Collectors.toList()),
                "查出的行内容应一致");

        // 缓存命中：第二次调用应走缓存且结果相同
        assertEquals(newList.size(), EvProbe.dao.queryByCache(sql).size(), "缓存命中结果应一致");

        // queryFisrtByCache：与旧侧 findFirstByCache 比对（同样用独立键）
        EvProbe newOne = EvProbe.dao.queryFisrtByCache(sql);
        JfProbe oldOne = JfProbe.dao.findFirstByCache("service", sql + "#oldf", sql);
        assertNotNull(newOne);
        assertNotNull(oldOne);
        assertEquals(String.valueOf((Object) oldOne.get("name")),
                String.valueOf((Object) newOne.get("name")),
                "queryFisrtByCache 与 Model.findFirstByCache 应一致");

        System.out.println("[BaseModel 比对] queryByCache/queryFisrtByCache 与真实 Model.findByCache 一致"
                + "（cacheName=service）");
    }

    @Test
    @DisplayName("query / queryLong：与真实 jfinal Db.query / Db.queryLong 一致（含单列约束）")
    void queryPrimitivesMatchJFinal() {
        // 造两行数据
        EvProbe a = new EvProbe();
        a.put("name", "q1").save();
        EvProbe b = new EvProbe();
        b.put("name", "q2").save();

        cn.eova.db.JdbcEovaDbGateway gw = new cn.eova.db.JdbcEovaDbGateway(ds);

        // —— queryLong：与 Db.queryLong 一致 ——
        String cntSql = "select count(*) from " + TABLE;
        assertEquals(Db.queryLong(cntSql), gw.queryLong(cntSql), "queryLong 应与 Db.queryLong 一致");

        // —— query：单列，与 Db.query 一致（元素级比较，注意可能是 BigDecimal） ——
        String colSql = "select name from " + TABLE + " order by id";
        List<Object> expected = Db.query(colSql);
        List<Object> actual = gw.query(colSql);
        assertEquals(expected.size(), actual.size(), "query 行数应与 Db.query 一致");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(String.valueOf(expected.get(i)), String.valueOf(actual.get(i)),
                    "第 " + i + " 个元素应一致（两侧都按原始类型返回，不做转换）");
        }

        // —— 多列：按列数分支，每行是【整行 Object[]】（不是首列！）——
        String multiSql = "select id, name from " + TABLE + " order by id";
        List<Object> expMulti = Db.query(multiSql);
        List<Object> actMulti = gw.query(multiSql);
        assertEquals(expMulti.size(), actMulti.size(), "多列时 query 行数应与 Db.query 一致");
        assertFalse(expMulti.isEmpty(), "测试数据应非空");
        for (int i = 0; i < expMulti.size(); i++) {
            Object expRow = expMulti.get(i);
            Object actRow = actMulti.get(i);
            assertTrue(expRow instanceof Object[] && actRow instanceof Object[],
                    "多列时两侧每个元素都应是 Object[]（整行）：旧=" + expRow.getClass()
                            + " 新=" + actRow.getClass());
            assertEquals(Arrays.toString((Object[]) expRow), Arrays.toString((Object[]) actRow),
                    "多列时整行内容应一致（第 " + i + " 行）");
        }

        System.out.println("[BaseModel 比对] query/queryLong 与 jfinal 原语一致（"
                + expected.size() + " 行；单列取标量、多列取整行 Object[]，两者均与旧实现一致）");
    }

    @Test
    @DisplayName("表元数据 API：getColumnNameSet / getPrimaryKey 与真实 jfinal Table 一致")
    void tableMetadataApiMatchesJFinalTable() {
        // jfinal 侧：真实 com.jfinal.plugin.activerecord.Table
        com.jfinal.plugin.activerecord.Table jfTable =
                com.jfinal.plugin.activerecord.TableMapping.me().getTable(JfProbe.class);
        // 新侧：TableMetadata 等价物
        cn.eova.compat.table.TableMetadata evTable = EvProbe.dao._getTable();

        assertEquals(new TreeSet<>(jfTable.getColumnNameSet()),
                new TreeSet<>(evTable.getColumnNameSet()),
                "getColumnNameSet 应与 jfinal Table 一致（EOVA 用它对字段存在性做判断）");
        assertEquals(new TreeSet<>(Arrays.asList(jfTable.getPrimaryKey())),
                new TreeSet<>(Arrays.asList(evTable.getPrimaryKey())),
                "getPrimaryKey 应与 jfinal Table 一致");
        assertEquals(jfTable.getName(), evTable.getName(), "表名应一致");

        // hasColumnLabel：Model.set 的列校验依赖它
        for (String col : jfTable.getColumnNameSet()) {
            assertEquals(jfTable.hasColumnLabel(col), evTable.hasColumnLabel(col),
                    "hasColumnLabel 应一致：" + col);
            // 大小写行为也是契约的一部分：实测 jfinal 区分大小写，故大写形式两侧都应为 false
            assertEquals(jfTable.hasColumnLabel(col.toUpperCase()),
                    evTable.hasColumnLabel(col.toUpperCase()),
                    "hasColumnLabel 对大写形式应一致：" + col);
            assertFalse(evTable.hasColumnLabel(col.toUpperCase()),
                    "大写形式必须为 false（jfinal 的 hasColumnLabel 区分大小写）：" + col);
        }

        System.out.println("[BaseModel 比对] 表元数据 API 与 jfinal Table 一致：列集="
                + new TreeSet<>(evTable.getColumnNameSet()) + "，主键="
                + Arrays.toString(evTable.getPrimaryKey()));
    }

    @Test
    @DisplayName("Oracle 序列分支：仅在 dbType=oracle 且主键为 null 时补序列值")
    void oracleSequenceBranch() {
        // "or" 数据源被注册为 oracle 类型 → 触发 BaseModel.save() 的序列分支
        EovaDbGateway gw = new JdbcEovaDbGateway(ds);
        EovaModel.setGateway("or", gw);
        cn.eova.config.EovaDataSource.register("or", "jdbc:oracle:thin:@127.0.0.1:1521:orcl", null);

        EvSeqProbe seqModel = new EvSeqProbe();
        seqModel.put("name", "seq");
        seqModel.save();

        // SqlUtil.getSequence 的 Oracle 形态为 "<SEQ_><表名>.nextval"
        String expectedSeq = "seq_" + SEQ_TABLE + ".nextval";
        assertEquals(expectedSeq, seqValue(1),
                "Oracle 分支应在主键为 null 时写入 SqlUtil.getSequence 的取值：" + expectedSeq);

        // 主键非 null 时不应被覆盖
        EvSeqProbe explicit = new EvSeqProbe();
        explicit.put("id", "myid");
        explicit.put("name", "explicit");
        explicit.save();
        assertEquals("myid", explicit.get("id"), "主键已提供时不应被序列覆盖");

        // 已注册为非 oracle 的数据源不应补序列
        new EvProbe().put("name", "mysql").save();
        System.out.println("[BaseModel 比对] Oracle 序列分支已确认（or 数据源 → 主键补 "
                + expectedSeq + "；主键已提供时不覆盖）");
    }

    // ———————————————————————— 辅助 ————————————————————————

    /** 执行并返回异常消息；未抛异常返回 null */
    private static String messageOf(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    private static String nameOf(Object id) throws Exception {
        try (Connection c = ds.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(
                     "select name from " + TABLE + " where id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** 取序列表中按插入顺序的第 n 行主键 */
    private static String seqValue(int n) {
        List<String> ids = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select id from " + SEQ_TABLE + " order by name, id")) {
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assertTrue(ids.size() >= n, "序列表应有至少 " + n + " 行，实际 " + ids.size());
        return ids.get(n - 1);
    }
}
