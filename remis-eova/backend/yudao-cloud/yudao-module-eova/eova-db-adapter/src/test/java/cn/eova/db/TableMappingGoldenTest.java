/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import cn.eova.compat.table.EovaTableMapping;
import cn.eova.compat.table.TableMetadata;
import cn.eova.compat.table.TableMetadataSource;
import cn.eova.testkit.OldImplementationLoader;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EovaTableMapping} + {@link JdbcTableMetadataSource} 的等价判据
 * （阶段 1 `D-MODEL` 前置 3）。
 *
 * <p><b>驱动源是契约制品，不是我誊抄的常量表：</b>
 * 映射清单从旧源码 {@code cn.eova.config.EovaConfig} <b>现场解析</b>
 * （该文件是全项目唯一的映射注册点），主键从<b>数据库元数据</b>现场解析。
 * 这样避免"把值抄进测试"—— 誊抄会让测试与契约一起漂移（R36/R43 同类风险）。
 *
 * <p><b>实测确认的旧行为（据完整方法体，非片段推断 —— R43）：</b>
 * <ul>
 *   <li>jfinal 5.2.6 的 {@code TableMapping.getTable(cls)} 方法体只有
 *       {@code modelToTableMap.get(cls)} 加一次泛型 {@code checkcast}：
 *       <b>未映射的类返回 null 而非抛异常</b>。故 EOVA 中未注册映射的模型调用
 *       {@code BaseModel.save()} 会在 {@code table.getPrimaryKey()[0]} 处 NPE。
 *       本判据钉住"返回 null"。</li>
 *   <li>{@code AutoBindModel} + {@code @TableBind} + classpath 扫描是<b>死代码</b>
 *       （全项目零引用），真实机制就是 {@code EovaConfig} 的 15 条显式 addMapping。
 *       故新实现<b>不需要</b> classpath 扫描 —— 这是本轮消掉的一块工作量。</li>
 * </ul>
 *
 * <p><b>本判据明确未覆盖的部分（已知缺口，不静默跳过）：</b>
 * "哪个模型类绑定到哪张表"的<b>运行期类身份绑定</b>要求 18 个模型类已 port
 * （它们都依赖尚未落地的 {@code BaseModel}/{@code Model}），故本轮只能验证
 * 【声明侧的对应关系】（从 EovaConfig 解析）与【表侧的一切】（表名、主键、机制）。
 * 该缺口由 {@link #classSideBindingDeferred()} 显式记录为断言，
 * 待模型类落地后必须补做，不得当作已完成。
 *
 * <p>acceptanceProfile: golden-table-mapping
 */
class TableMappingGoldenTest {

    private static final String URL = "jdbc:mysql://127.0.0.1:13306/eova_meta"
            + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai";

    private static final String USER = "root";
    private static final String PWD = "root";

    /** 匹配 {@code arp.addMapping("table", Model.class)} —— EovaConfig 中的两参形式 */
    private static final Pattern MAPPING =
            Pattern.compile("addMapping\\(\"([^\"]+)\",\\s*(\\w+)\\.class\\)");

    private EovaTableMapping mapping;
    private JdbcTableMetadataSource metaSource;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable(),
                "旧产物缺失，无法解析 EovaConfig");
        mapping = EovaTableMapping.me();
        mapping.clear();
        metaSource = new JdbcTableMetadataSource(dataSource());
        EovaTableMapping.setMetadataSource(metaSource);
    }

    private static MysqlDataSource dataSource() {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(URL);
        ds.setUser(USER);
        ds.setPassword(PWD);
        return ds;
    }

    @Test
    @DisplayName("声明侧保真：EovaConfig 的每条映射，其表名都真实存在且有主键")
    void declaredMappingsResolveToRealTables() throws Exception {
        List<String[]> declared = parseEovaConfigMappings();
        assertTrue(declared.size() >= 15,
                "旧 EovaConfig 应声明至少 15 条映射，实际 " + declared.size());

        Set<String> tables = new LinkedHashSet<>();
        Set<String> models = new LinkedHashSet<>();
        for (String[] pair : declared) {
            tables.add(pair[0]);
            models.add(pair[1]);
        }
        assertEquals(declared.size(), tables.size(),
                "表名不应重复（重复会触发旧实现的 'Model mapping already exists'）");
        assertEquals(declared.size(), models.size(), "模型名不应重复");

        for (String table : tables) {
            String[] pk = directMetadataPk(table);
            assertTrue(pk.length > 0,
                    "映射表 [" + table + "] 在库中应有主键 —— 空表示表名写错或表不存在");
            assertArrayEquals(pk, metaSource.metadata(table).primaryKeys(),
                    "表 [" + table + "] 经接缝解析的主键应与元数据直查一致");
        }
        System.out.println("[表映射] EovaConfig 声明 " + declared.size() + " 条映射（"
                + tables.size() + " 张表 / " + models.size() + " 个模型），全部在库中存在且有主键");
    }

    @Test
    @DisplayName("机制：注册→取表→未映射返回 null→重复注册报错→主键防御性拷贝")
    void mappingMechanism() throws Exception {
        // 经接缝解析的路径必须用【真实存在的表】，否则主键为空是正确行为而非缺陷
        String realTable = parseEovaConfigMappings().get(0)[0];
        mapping.addMapping(realTable, ModelA.class);
        mapping.addMapping(ModelB.class, new TableMetadata("t_b", new String[]{"id"}, new String[]{"id"}));

        TableMetadata a = mapping.getTable(ModelA.class);
        assertNotNull(a);
        assertEquals(realTable, a.getName());
        assertArrayEquals(directMetadataPk(realTable), a.primaryKeys(),
                "经接缝解析的主键应与元数据直查一致");

        TableMetadata b = mapping.getTable(ModelB.class);
        assertEquals("t_b", b.getName());
        assertArrayEquals(new String[]{"id"}, b.primaryKeys());

        // 未映射 → null（与旧实现一致，后续 save() 会 NPE）
        assertNull(mapping.getTable(UnmappedModel.class),
                "未映射的类应返回 null —— 这正是旧实现的行为");

        // 重复注册同一张表名 → 报错，消息与旧实现同构
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> mapping.addMapping(ModelB.class, new TableMetadata(realTable, new String[]{"id"}, new String[]{"id"})));
        assertTrue(e.getMessage().startsWith("Model mapping already exists :"),
                "异常消息应与旧实现同构，实际：" + e.getMessage());

        // 防御性拷贝：入参与返回值都不应能改到内部状态
        String[] pk = {"id"};
        mapping.addMapping(ModelC.class, new TableMetadata("t_c", pk, pk));
        pk[0] = "hacked";
        assertArrayEquals(new String[]{"id"}, mapping.getTable(ModelC.class).primaryKeys(),
                "构造后改动入参不应影响内部状态");
        String[] out = mapping.getTable(ModelC.class).primaryKeys();
        out[0] = "hacked2";
        assertArrayEquals(new String[]{"id"}, mapping.getTable(ModelC.class).primaryKeys(),
                "取出的数组应是副本");

        System.out.println("[表映射] 机制断言全通过（含未映射返回 null 与重复注册报错）");
    }

    @Test
    @DisplayName("列集与数据库元数据一致：Model.set 的列校验依赖它")
    void columnsComeFromMetadata() throws Exception {
        List<String[]> declared = parseEovaConfigMappings();
        for (String[] pair : declared) {
            String table = pair[0];
            String[] fromSource = metaSource.metadata(table).columns();
            String[] fromDirect = directMetadataColumns(table);
            assertArrayEquals(fromDirect, fromSource,
                    "表 [" + table + "] 的列集应与元数据直查一致（含 ORDINAL_POSITION 顺序）");
        }

        // hasColumn：Model.set 的列校验依赖它 —— 不存在的列必须为 false，否则 set 会放行非法列
        TableMetadata user = metaSource.metadata("eova_user");
        assertTrue(user.hasColumn("id"), "eova_user 应有 id 列");
        assertTrue(user.hasColumn("ID"), "列判定应大小写不敏感");
        assertTrue(user.hasColumn("login_id"), "eova_user 应有 login_id 列");
        assertFalse(user.hasColumn("no_such_column"), "不存在的列必须返回 false");
        assertFalse(user.hasColumn(null), "null 列名必须返回 false，不得抛异常");

        // 表不存在 → 空元数据（非 null），由调用方决定如何应对
        TableMetadata absent = metaSource.metadata("no_such_table_xyz");
        assertNotNull(absent, "表不存在时应返回空元数据而非 null");
        assertTrue(absent.isEmpty(), "不存在的表应返回空元数据");
        assertFalse(absent.hasColumn("id"), "空元数据的 hasColumn 应为 false");

        System.out.println("[表映射] " + declared.size()
                + " 张表的列集与元数据直查一致；hasColumn 边界（大小写/null/不存在列）已确认");
    }

    @Test
    @DisplayName("配置错误必须响亮失败：未注入或违约的 TableMetadataSource 不得静默产出空表")
    void misconfigurationFailsLoudly() {
        EovaTableMapping.setMetadataSource(null);
        try {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> mapping.addMapping("t_no_source", NoSourceModel.class));
            assertTrue(e.getMessage().contains("TableMetadataSource"),
                    "报错应指出缺少 TableMetadataSource，实际：" + e.getMessage());
        } finally {
            EovaTableMapping.setMetadataSource(metaSource);
        }

        TableMetadataSource broken = t -> null;
        EovaTableMapping.setMetadataSource(broken);
        try {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> mapping.addMapping("t_broken", BrokenSourceModel.class));
            assertTrue(e.getMessage().contains("返回 null"), "应报出违约，实际：" + e.getMessage());
        } finally {
            EovaTableMapping.setMetadataSource(metaSource);
        }
        System.out.println("[表映射] 未注入/违约的 TableMetadataSource 均响亮失败，无静默降级");
    }

    @Test
    @DisplayName("已知缺口：类身份绑定要等 18 个模型类落地后才能验证（显式记录，不静默跳过）")
    void classSideBindingDeferred() throws Exception {
        List<String[]> declared = parseEovaConfigMappings();
        List<String> notYetPorted = new ArrayList<>();
        List<String> loadableButStub = new ArrayList<>();
        for (String[] pair : declared) {
            Class<?> c = null;
            try {
                c = Class.forName("cn.eova.model." + pair[1]);
            } catch (ClassNotFoundException ignored) {
                // 未 port
            }
            // 判"已 port"必须要求它真的是 BaseModel 子类 —— 不能用"能加载"当判据：
            // 仓库里还有 stub 占位类，且本轮给 db-adapter 加了 eova-core 依赖后
            // 这些 stub 变得可加载，"可加载"与"已 port"就不再等价了。
            if (c != null && cn.eova.common.base.BaseModel.class.isAssignableFrom(c)) {
                continue;
            }
            if (c != null) {
                loadableButStub.add(pair[1] + "(stub)");
            }
            notYetPorted.add(pair[1]);
        }
        // 当模型类全部落地后，本断言会失败 —— 那正是要求补做类身份绑定的信号
        assertEquals(declared.size(), notYetPorted.size(),
                "仍有模型类未 port，故【类身份绑定】尚不能验证；"
                        + "待全部落地后必须补做。"
                        + "已 port（真为 BaseModel 子类）的：" + (declared.size() - notYetPorted.size())
                        + "；其中可加载但仍是 stub 的：" + loadableButStub);
        System.out.println("[表映射] 已知缺口已记录：类身份绑定待 " + notYetPorted.size()
                + " 个模型类 port 后验证（当前为显式断言，非静默跳过）");
    }

    // ———————————————————————— 辅助 ————————————————————————

    /** 现场解析旧 EovaConfig 中的映射声明 */
    private static List<String[]> parseEovaConfigMappings() throws Exception {
        Path src = OldImplementationLoader.locateRepoRoot()
                .resolve("meta-eova/eova/core/src/main/java/cn/eova/config/EovaConfig.java");
        String body = Files.readString(src, StandardCharsets.UTF_8);
        List<String[]> out = new ArrayList<>();
        Matcher m = MAPPING.matcher(body);
        while (m.find()) {
            out.add(new String[]{m.group(1), m.group(2)});
        }
        return out;
    }

    /** 独立于被测实现，直接查元数据取主键（按 KEY_SEQ 排序） */
    private static String[] directMetadataPk(String table) throws Exception {
        Map<Short, String> bySeq = new LinkedHashMap<>();
        try (Connection conn = dataSource().getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getPrimaryKeys(conn.getCatalog(), null, table)) {
                while (rs.next()) {
                    bySeq.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
                }
            }
        }
        List<Short> seqs = new ArrayList<>(bySeq.keySet());
        seqs.sort(null);
        List<String> out = new ArrayList<>();
        for (Short s : seqs) {
            out.add(bySeq.get(s));
        }
        return out.toArray(new String[0]);
    }

    /** 独立于被测实现，直接查元数据取列集（按 ORDINAL_POSITION 排序） */
    private static String[] directMetadataColumns(String table) throws Exception {
        Map<Integer, String> byPos = new LinkedHashMap<>();
        try (Connection conn = dataSource().getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, table, null)) {
                while (rs.next()) {
                    byPos.put(rs.getInt("ORDINAL_POSITION"), rs.getString("COLUMN_NAME"));
                }
            }
        }
        List<Integer> keys = new ArrayList<>(byPos.keySet());
        keys.sort(null);
        List<String> out = new ArrayList<>();
        for (Integer k : keys) {
            out.add(byPos.get(k));
        }
        return out.toArray(new String[0]);
    }

    /** 判据用的模型替身（各自独立，保证类身份可区分） */
    static class ModelA {
    }

    static class ModelB {
    }

    static class ModelC {
    }

    static class UnmappedModel {
    }

    static class NoSourceModel {
    }

    static class BrokenSourceModel {
    }
}
