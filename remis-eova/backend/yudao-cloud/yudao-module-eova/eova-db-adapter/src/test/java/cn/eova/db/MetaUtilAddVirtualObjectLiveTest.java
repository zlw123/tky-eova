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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.eova.common.Ds;
import cn.eova.compat.cache.CacheServices;
import cn.eova.compat.cache.EhCacheService;
import cn.eova.compat.table.EovaTableMapping;
import cn.eova.config.EovaDataSource;
import cn.eova.core.meta.MetaUtil;
import cn.eova.model.Button;
import cn.eova.model.Menu;
import cn.eova.model.MetaField;
import cn.eova.model.MetaObject;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * **`MetaUtil.addVirtualObject` 行为判据（第 229 轮）** —— 真实 baseline MySQL + **唯一 scratch code**。
 *
 * <p><b>来源</b>：r176 扫描列为零覆盖候选 → r178 确认非死类 → **r228 recon 查明写入面并定出"可证完备"的清理配方**
 * （这是敢动参考数据表的前提）：
 * <ul>
 *   <li>`code = "v_" + code` → `MetaObject.dao.getTemplate()` → `eo.save()` 写 **`eova_object` 一行**
 *       （`code=v_&lt;code&gt;`、`table_name=virtual`、`view_sql=&lt;sql&gt;`、`data_source=&lt;ds&gt;`）；</li>
 *   <li>`select … from` 之间的列按逗号切分 → 逐列 `MetaField.dao.getTemplate()` → `ei.save()` 写
 *       **`eova_field` N 行**（`object_code=v_&lt;code&gt;`、`num` 从 **10** 递增、`en`/`cn`=列名或别名、
 *       `type=文本框`、`is_show=1`、`width=100`）；**别名规则：列内含空格时取最后一段**
 *       （`(sum1-sum2) total` → `total`；注意 `select` 段被 `toLowerCase()` ⇒ 别名是小写）。</li>
 * </ul>
 *
 * <p><b>清理配方（唯一 scratch code + 自证）</b>：teardown 只删 `object_code/code = 'v_r229_scratch'`
 * 的行，并**断言两处计数为 0**；setUp 里也先清一次（防上次中途失败留残留）。
 *
 * <p><b>★ 顺带钉住一处既有缺陷（r228 发现，与 r181 的不可达分支同族）</b>：
 * `i1 = indexOf("select") + 6` ⇒ 无 `select` 时 `i1 = 5`（不是 -1）⇒
 * `if (i1 == -1) throw "缺少select关键字…"` **永不可达**；实际会走到 `substring(5, i2)` 抛
 * `StringIndexOutOfBoundsException`。判据分别钉住：缺 `from` 判**可达**的中文文案；缺 `select` 判**实际行为**
 * （不得"顺手修"成可达）。
 *
 * <p><b>跳过口径（R77）</b>：baseline 不可达 ⇒ 逐条跳过，不得记为通过。
 */
class MetaUtilAddVirtualObjectLiveTest {

    private static final String URL = System.getProperty("eova.mysql.meta.url",
            "jdbc:mysql://127.0.0.1:13306/eova_meta?useUnicode=true&characterEncoding=UTF-8"
                    + "&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
                    + "&connectTimeout=3000");
    private static final String DB_USER = System.getProperty("eova.mysql.user", "root");
    private static final String DB_PWD = System.getProperty("eova.mysql.pwd", "root");
    /** scratch code：函数内部会写成 `v_r229_scratch` */
    private static final String SCRATCH = "r229_scratch";
    private static final String OBJ = "v_" + SCRATCH;

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
        // addVirtualObject 走 MetaObject/MetaField 两个 dao，另加 Menu/Button 保持与本类其它接缝一致
        EovaTableMapping.me().addMapping(Ds.EOVA, MetaObject.class, metaSource.metadata("eova_object"));
        EovaTableMapping.me().addMapping(Ds.EOVA, MetaField.class, metaSource.metadata("eova_field"));
        EovaTableMapping.me().addMapping(Ds.EOVA, Menu.class, metaSource.metadata("eova_menu"));
        EovaTableMapping.me().addMapping(Ds.EOVA, Button.class, metaSource.metadata("eova_button"));

        EhCacheService.shutdown();
        CacheServices.set(EhCacheService.fromClasspath());
        EovaModel.setCacheService(EhCacheService.fromClasspath());
        cn.eova.service.biz.init();
        cleanup();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanup();
        // ★ 自证 baseline 已复原（本判据是少数会写参考数据表的判据，必须自己证明清理干净）
        assertEquals(0, count("eova_object", "code", OBJ), "清理后 eova_object 不得残留 scratch 行");
        assertEquals(0, count("eova_field", "object_code", OBJ), "清理后 eova_field 不得残留 scratch 行");
        CacheServices.clear();
        EhCacheService.shutdown();
        EovaDataSource.clear();
        EovaGateways.clear();
        EovaTableMapping.me().clear();
        EovaTableMapping.setMetadataSource(null);
    }

    /** 删掉本判据可能留下的 scratch 行（幂等） */
    private static void cleanup() throws Exception {
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             Statement st = c.createStatement()) {
            st.executeUpdate("delete from eova_field where object_code = '" + OBJ + "'");
            st.executeUpdate("delete from eova_object where code = '" + OBJ + "'");
        }
    }

    /** 计数（判据侧独立 JDBC，不经 dao） */
    private static int count(String table, String col, String value) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             PreparedStatement ps = c.prepareStatement(
                     "select count(*) from " + table + " where " + col + " = ?")) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** 读 scratch 对象行（判据侧独立 JDBC） */
    private static Map<String, String> objectRow() throws Exception {
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             PreparedStatement ps = c.prepareStatement(
                     "select code, name, table_name, view_sql, data_source from eova_object where code = ?")) {
            ps.setString(1, OBJ);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, String> m = new HashMap<>();
                m.put("code", rs.getString("code"));
                m.put("name", rs.getString("name"));
                m.put("table_name", rs.getString("table_name"));
                m.put("view_sql", rs.getString("view_sql"));
                m.put("data_source", rs.getString("data_source"));
                return m;
            }
        }
    }

    /** 读 scratch 字段行的 (num, en, cn, type, is_show, width)，按 num 升序 */
    private static List<String> fieldRows() throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PWD);
             PreparedStatement ps = c.prepareStatement(
                     "select num, en, cn, type, is_show, width from eova_field"
                             + " where object_code = ? order by num")) {
            ps.setString(1, OBJ);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getInt("num") + "/" + rs.getString("en") + "/" + rs.getString("cn")
                            + "/" + rs.getString("type") + "/" + rs.getInt("is_show") + "/" + rs.getInt("width"));
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("★ 正常 SQL：写 object 一行 + 按列数写 field 行（别名取最后一段、num 从 10 起）")
    void buildsVirtualObjectAndFields() throws Exception {
        String sql = "select a, b as bb, (sum1-sum2) total from some_table";
        MetaUtil.addVirtualObject(sql, SCRATCH, "虚拟对象", "eova");

        Map<String, String> obj = objectRow();
        assertNotNull(obj, "★ 必须写入 eova_object 一行（code=v_" + SCRATCH + "）");
        assertEquals(OBJ, obj.get("code"));
        assertEquals("虚拟对象", obj.get("name"), "name 应原样写入");
        assertEquals("virtual", obj.get("table_name"), "★ table_name 固定为 virtual");
        assertEquals(sql, obj.get("view_sql"), "★ view_sql 应等于入参 SQL");
        assertEquals("eova", obj.get("data_source"), "★ data_source 应等于入参 ds");

        List<String> fields = fieldRows();
        assertEquals(3, fields.size(), "★ 三列应写三行字段：" + fields);
        // 别名规则：列内含空格取最后一段（b as bb -> bb；(sum1-sum2) total -> total）；
        // 且 select 段经 toLowerCase() ⇒ 断言用小写
        assertEquals("10/a/a/文本框/1/100", fields.get(0), "第一列");
        assertEquals("11/bb/bb/文本框/1/100", fields.get(1), "★ 别名应取最后一段（bb）");
        assertEquals("12/total/total/文本框/1/100", fields.get(2), "★ 表达式列应取最后一段（total）");
    }

    @Test
    @DisplayName("★ 缺 from：抛异常且文案为『缺少from关键字』（该守卫可达）")
    void missingFromIsReportedWithChineseMessage() throws Exception {
        try {
            MetaUtil.addVirtualObject("select a, b", SCRATCH, "n", "eova");
            fail("缺 from 应抛异常");
        } catch (Exception e) {
            assertTrue(String.valueOf(e.getMessage()).contains("缺少from关键字"),
                    "★ 文案应含『缺少from关键字』，实际=" + e.getMessage());
        }
        assertEquals(0, count("eova_object", "code", OBJ), "失败时不得写入 object 行");
    }

    @Test
    @DisplayName("★ 缺 select：既有守卫【不可达】，实际行为是 StringIndexOutOfBounds（不得顺手修）")
    void missingSelectGuardIsUnreachable() throws Exception {
        Exception thrown = null;
        try {
            MetaUtil.addVirtualObject("x from y", SCRATCH, "n", "eova");
        } catch (Exception e) {
            thrown = e;
        }
        assertNotNull(thrown, "缺 select 时必然抛异常（但不是那条中文守卫）");
        assertFalse(String.valueOf(thrown.getMessage()).contains("缺少select关键字"),
                "★ 该中文守卫不可达（i1 = -1 + 6 = 5，永不等于 -1）：实际=" + thrown);
        assertTrue(thrown instanceof StringIndexOutOfBoundsException,
                "★ 实际行为应是 substring(5, i2) 越界，实际类型=" + thrown.getClass().getName());
    }
}
