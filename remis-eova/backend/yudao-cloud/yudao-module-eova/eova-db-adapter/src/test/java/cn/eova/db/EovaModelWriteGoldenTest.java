/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import cn.eova.compat.table.EovaTableMapping;
import com.jfinal.plugin.activerecord.ActiveRecordPlugin;
import com.jfinal.plugin.activerecord.Model;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EovaModel} 的 <b>save / update</b> 对真实 jfinal {@code Model} 的等价验证。
 *
 * <p><b>为什么用临时表：</b>save/update 会写库，不能拿 EOVA 基线表做实验
 * （基线数据是"元数据行不变"这一契约的一部分）。本判据自建自删
 * {@code eova_model_probe}，两边各写各的行，再<b>回读比对实际结果</b> ——
 * 比比对生成的 SQL 字符串更接近"可观测行为"。
 *
 * <p><b>判据覆盖的规则（来自 {@link ModelSqlBuilder} 的实测规格）：</b>
 * 只取已有属性、跳过非表列（不报错）、null 照常写入、
 * update 只看 modifyFlag 且跳过主键、modifyFlag 为空时提前返回 false、
 * 无主键时抛异常、save 后取回自增主键并写回模型。
 *
 * <p>acceptanceProfile: golden-eova-model-write
 */
class EovaModelWriteGoldenTest {

    private static final String URL = "jdbc:mysql://127.0.0.1:13306/eova_meta"
            + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String USER = "root";
    private static final String PWD = "root";
    private static final String TABLE = "eova_model_probe";

    private static DataSource ds;
    private static ActiveRecordPlugin arp;

    /** 旧栈侧（_getModifyFlag 是 protected，经子类暴露给判据） */
    public static class JfProbe extends Model<JfProbe> {
        public static final JfProbe dao = new JfProbe();

        /** 暴露 protected 的 _getModifyFlag 供判据比对 */
        public java.util.Set<String> modFlag() {
            return _getModifyFlag();
        }
    }

    /** 新栈侧（同样经子类暴露，保证两侧访问路径对称） */
    public static class EvProbe extends EovaModel<EvProbe> {
        public static final EvProbe dao = new EvProbe().dao();

        /** 暴露 protected 的 _getModifyFlag 供判据比对 */
        public java.util.Set<String> modFlag() {
            return _getModifyFlag();
        }
    }

    @BeforeAll
    static void setUp() throws Exception {
        ds = dataSource();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("drop table if exists " + TABLE);
            st.execute("create table " + TABLE + " ("
                    + "id int not null auto_increment,"
                    + "name varchar(64) default null,"
                    + "num int default null,"
                    + "memo varchar(64) default null,"
                    // flag 带【非空默认值】：这是"省略列"与"写入 NULL"唯一可区分的形态 ——
                    // 若列默认就是 NULL，两种写法结果相同，判据将不具判别力
                    + "flag varchar(16) default 'D',"
                    + "primary key (id)) engine=InnoDB default charset=utf8mb4");
        }
        arp = new ActiveRecordPlugin(ds);
        arp.addMapping(TABLE, JfProbe.class);
        arp.start();

        EovaTableMapping mapping = EovaTableMapping.me();
        mapping.clear();
        mapping.setMetadataSource(new JdbcTableMetadataSource(ds));
        mapping.addMapping("eova", TABLE, EvProbe.class);
        EovaModel.setGateway(new JdbcEovaDbGateway(ds));
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (arp != null) {
            arp.stop();
        }
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("drop table if exists " + TABLE);
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
    @DisplayName("save：null 照常写入、非表列被跳过、自增主键写回模型")
    void saveMatchesOldModel() throws Exception {
        // —— 旧侧 ——
        JfProbe oldM = new JfProbe();
        oldM.put("name", "旧");
        oldM.put("num", 1);
        oldM.put("memo", null);          // null 应照常写入
        oldM.put("flag", null);          // 带默认值的列写 null —— 用于区分"省略"与"写 NULL"
        oldM.put("no_such_col", "x");    // 非表列应被跳过（不报错）
        boolean oldOk = oldM.save();
        Object oldId = oldM.get("id");

        // —— 新侧 ——
        EvProbe newM = new EvProbe();
        newM.put("name", "新");
        newM.put("num", 1);
        newM.put("memo", null);
        newM.put("flag", null);
        newM.put("no_such_col", "x");
        boolean newOk = newM.save();
        Object newId = newM.get("id");

        assertEquals(oldOk, newOk, "save 返回值应一致");
        assertNotNull(oldId, "旧 save 后模型应带上自增主键");
        assertNotNull(newId, "新 save 后模型应带上自增主键（旧实现的可观测行为）");
        assertEquals(row(oldId)[0], "旧", "旧侧行内容");
        assertEquals(row(newId)[0], "新", "新侧行内容");

        // 两行的列值形态应一致：memo 为 null、num 为 1
        Map<String, Object> oldRow = rowMap(oldId);
        Map<String, Object> newRow = rowMap(newId);
        assertEquals(oldRow.get("num"), newRow.get("num"), "num 应一致");
        assertEquals(oldRow.get("memo"), newRow.get("memo"), "memo 应一致（null 照常写入）");
        assertEquals(null, newRow.get("memo"), "memo 应为 null —— 旧实现不过滤 null");
        // 判别力关键：flag 有默认值 'D'。若实现"过滤 null 而省略该列"，flag 会是 'D'；
        // 只有"null 照常写入"才会是 NULL。没有这一条，判据对 null 过滤不具判别力。
        assertEquals(oldRow.get("flag"), newRow.get("flag"), "flag 应一致");
        assertNull(newRow.get("flag"),
                "flag 应为 NULL 而不是默认值 'D' —— 证明 null 是【写入】的，不是被省略的");

        System.out.println("[EovaModel 写] save 一致：返回值=" + newOk + "，自增主键写回=" + newId
                + "，非表列被跳过，null 照常写入");
    }

    @Test
    @DisplayName("update：modifyFlag 为空返回 false；set 后只更新该列；无主键时异常消息一致")
    void updateMatchesOldModel() throws Exception {
        // 准备两行
        JfProbe o = new JfProbe();
        o.put("name", "o").put("num", 1).save();
        EvProbe n = new EvProbe();
        n.put("name", "n").put("num", 1).save();
        Object oldId = o.get("id");
        Object newId = n.get("id");

        // ① modifyFlag 为空 → 两侧都应返回 false 且不执行 SQL
        JfProbe o2 = JfProbe.dao.findById(oldId);
        EvProbe n2 = EvProbe.dao.findById(newId);
        assertFalse(o2.update(), "旧侧 modifyFlag 为空应返回 false");
        assertFalse(n2.update(), "新侧 modifyFlag 为空应返回 false");
        assertEquals("o", row(oldId)[0], "旧侧行不应被改动");
        assertEquals("n", row(newId)[0], "新侧行不应被改动");

        // ② set 后 update → 只更新该列
        o2.set("name", "o2");
        n2.set("name", "n2");
        assertEquals(o2.update(), n2.update(), "update 返回值应一致");
        assertEquals("o2", row(oldId)[0], "旧侧应更新");
        assertEquals("n2", row(newId)[0], "新侧应更新");
        assertEquals(rowMap(oldId).get("num"), rowMap(newId).get("num"), "未修改的列不应变化");

        // ③ update 后 modifyFlag 应被清空 → 再 update 返回 false
        assertFalse(o2.update(), "旧侧 update 后 modifyFlag 应已清空");
        assertFalse(n2.update(), "新侧 update 后 modifyFlag 应已清空");

        // ④ 无主键值 → 两侧异常消息应逐字一致
        String oldMsg = messageOf(() -> {
            JfProbe m = new JfProbe();
            m.set("name", "x");
            m.update();
        });
        String newMsg = messageOf(() -> {
            EvProbe m = new EvProbe();
            m.set("name", "x");
            m.update();
        });
        assertNotNull(oldMsg, "旧侧无主键 update 应抛异常");
        assertEquals(oldMsg, newMsg, "无主键 update 的异常消息应逐字一致");

        System.out.println("[EovaModel 写] update 一致：空 modifyFlag→false、只更新已改列、"
                + "update 后清 flag、无主键消息：" + newMsg);
    }

    @Test
    @DisplayName("delete：按主键删除后行消失，两侧一致")
    void deleteMatchesOldModel() throws Exception {
        JfProbe o = new JfProbe();
        o.put("name", "do").save();
        EvProbe n = new EvProbe();
        n.put("name", "dn").save();
        Object oldId = o.get("id");
        Object newId = n.get("id");

        assertEquals(o.delete(), n.delete(), "delete 返回值应一致");
        assertNull(rawRow(oldId), "旧侧行应已删除");
        assertNull(rawRow(newId), "新侧行应已删除");

        System.out.println("[EovaModel 写] delete 一致：按主键删除后两侧行均消失");
    }

    @Test
    @DisplayName("save 后 modifyFlag 应被清空（旧实现 clearModifyFlag）")
    void saveClearsModifyFlag() {
        JfProbe o = new JfProbe();
        o.put("name", "mf").save();
        assertTrue(o.modFlag().isEmpty(), "旧侧 save 后 modifyFlag 应清空");

        EvProbe n = new EvProbe();
        n.put("name", "mf").save();
        assertTrue(n.modFlag().isEmpty(), "新侧 save 后 modifyFlag 应清空");

        // 经 set（记 flag）后 save，flag 也应被清空
        EvProbe n2 = new EvProbe();
        n2.set("name", "mf2").save();
        assertTrue(n2.modFlag().isEmpty(), "新侧经 set 后 save，flag 也应清空");
    }

    // ———————————————————————— 辅助 ————————————————————————

    /** 读某行（按主键），返回 [name, num, memo]；不存在时返回 null */
    private static Object[] row(Object id) throws Exception {
        Object[] r = rawRow(id);
        assertNotNull(r, "行应存在：id=" + id);
        return r;
    }

    private static Object[] rawRow(Object id) throws Exception {
        try (Connection c = ds.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(
                     "select name, num, memo, flag from " + TABLE + " where id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Object[]{rs.getString(1), rs.getObject(2), rs.getObject(3),
                        rs.getString(4)};
            }
        }
    }

    private static Map<String, Object> rowMap(Object id) throws Exception {
        try (Connection c = ds.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(
                     "select name, num, memo, flag from " + TABLE + " where id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Object> m = new LinkedHashMap<>();
                if (rs.next()) {
                    m.put("name", rs.getString(1));
                    m.put("num", rs.getObject(2));
                    m.put("memo", rs.getObject(3));
                    m.put("flag", rs.getString(4));
                }
                return m;
            }
        }
    }

    private static String messageOf(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }
}
