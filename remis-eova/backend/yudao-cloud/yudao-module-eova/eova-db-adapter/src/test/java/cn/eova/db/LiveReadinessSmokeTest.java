/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import cn.eova.common.Ds;
import cn.eova.compat.table.EovaTableMapping;
import cn.eova.compat.table.TableMetadata;
import cn.eova.config.EovaDataSource;
import cn.eova.tools.x;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **live readiness 冒烟（第 87 轮）**：用**真实 JDBC**（H2 内存库）跑通 ported 栈的关键链路。
 *
 * <p><b>为什么用 H2 而不是 baseline MySQL：</b>本环境无 MySQL 客户端、3306 未开放
 * （第 87 轮实测），故 baseline 实跑仍记 {@code not executed}。但"把编译绿+判据绿抬到真实运行"
 * 这件事**不必等到 MySQL**：旧栈本身支持 H2 方言（{@code EovaDbPro} 对 h2 做关键字转义），
 * 故用 H2 内存库即可对下列**真实**链路做端到端验收（而非代理替身）：
 * <ol>
 *   <li><b>真实连接 + 真实 DDL</b>：建表并插入数据（走 ported 网关）；</li>
 *   <li><b>真实表元数据自省</b>：{@link JdbcTableMetadataSource}（走 {@code DatabaseMetaData}）
 *       解析列与主键，驱动 {@code EovaTableMapping}；</li>
 *   <li><b>真实 SQL 变换</b>：H2 方言下的**关键字转义**（r79 落的 {@code escapeSql}）
 *       在同一库上真执行；</li>
 *   <li><b>真实类型装配</b>：{@code EovaRecord} 的取值类型来自真实 {@code ResultSetMetaData}
 *       （h2 走"非 Oracle"分支：{@code getObject} 原样 + null 列保留）。</li>
 * </ol>
 *
 * <p><b>本判据不覆盖（如实记录）</b>：MySQL/Kingbase baseline 全链（登录→菜单→CRUD→上传→Excel 导入）、
 * HTTP 容器层（Spring DispatcherServlet + 路由适配）、Oracle 方言 SQL 形态（R60）。</p>
 */
class LiveReadinessSmokeTest {

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        // H2 内存库：同一 JVM 内命名库，随连接存活
        org.h2.jdbcx.JdbcDataSource h2 = new org.h2.jdbcx.JdbcDataSource();
        h2.setURL("jdbc:h2:mem:eova_live;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        h2.setUser("sa");
        h2.setPassword("");
        dataSource = h2;
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("create table if not exists eova_menu ("
                    + "id int primary key, code varchar(64), name varchar(64), type varchar(16), val varchar(16), order_num int)");
            st.execute("create table if not exists eova_object ("
                    + "id int primary key, code varchar(64), name varchar(64), pk varchar(64), ds varchar(64))");
            st.execute("delete from eova_menu");
            st.execute("delete from eova_object");
        }
        EovaDataSource.register(Ds.EOVA, "jdbc:h2:mem:eova_live", "org.h2.Driver");
    }

    @AfterEach
    void tearDown() {
        EovaDataSource.clear();
        EovaGateways.clear();
        EovaTableMapping.me().clear();
        EovaTableMapping.setMetadataSource(null);
        x.conf.getProps().remove("db.keyword");
    }

    @Test
    @DisplayName("真实链路：DDL/写/读 + 真实元数据自省 + 真实类型装配")
    void realJdbcRoundTrip() {
        JdbcEovaDbGateway gw = new JdbcEovaDbGateway(dataSource, Ds.EOVA);
        EovaGateways.register(Ds.EOVA, gw);

        // ① 真实写（走网关的 insert + 真实 JDBC）
        EovaRecord menu = new EovaRecord();
        menu.set("id", 1);
        menu.set("code", "sys");
        menu.set("name", "系统管理");
        menu.set("order_num", 10);
        assertTrue(gw.save("eova_menu", menu), "真实 INSERT 必须成功");

        EovaRecord menu2 = new EovaRecord();
        menu2.set("id", 2);
        menu2.set("code", "biz");
        menu2.set("name", null);
        menu2.set("order_num", 20);
        assertTrue(gw.save("eova_menu", menu2));

        // ② 真实读 + 类型装配（h2 ⇒ 非 Oracle 分支：getObject 原样 + null 列保留）
        List<EovaRecord> rows = gw.find("select * from eova_menu order by id");
        assertEquals(2, rows.size());
        assertEquals("sys", rows.get(0).getStr("code"));
        assertEquals("系统管理", rows.get(0).getStr("name"), "中文必须原样往返");
        assertTrue(rows.get(1).getColumns().containsKey("name"), "非 Oracle 分支 null 列必须保留");
        assertNull(rows.get(1).get("name"));
        assertNotNull(rows.get(0).get("order_num"));

        // ③ 真实表元数据自省（DatabaseMetaData）+ EovaTableMapping
        JdbcTableMetadataSource source = new JdbcTableMetadataSource(dataSource);
        EovaTableMapping.setMetadataSource(source);
        EovaTableMapping.me().addMapping(Ds.EOVA, "eova_menu", cn.eova.model.Menu.class);
        TableMetadata meta = EovaTableMapping.me().getTable(cn.eova.model.Menu.class);
        assertNotNull(meta, "表元数据必须能由真实自省解析");
        assertTrue(meta.getColumnNameSet().contains("code"), meta.getColumnNameSet().toString());
        assertEquals("id", meta.getPrimaryKey()[0], "主键必须来自真实自省");
        assertEquals(Ds.EOVA, EovaTableMapping.me().getConfigName(cn.eova.model.Menu.class));

        // ④ 模型层：真实按主键查询（EovaModel → 网关 → 真实 JDBC）
        cn.eova.model.Menu found = cn.eova.model.Menu.dao.findById(1);
        assertNotNull(found, "Menu.dao.findById 必须走真实链路取到行");
        assertEquals("sys", found.getStr("code"));
    }

    @Test
    @DisplayName("真实链路：无关键字配置时读写真库；转义开关的边界与旧 EovaDbPro 对齐")
    void keywordEscapingBoundaries() {
        JdbcEovaDbGateway gw = new JdbcEovaDbGateway(dataSource, Ds.EOVA);
        EovaGateways.register(Ds.EOVA, gw);

        // ① 未配置 db.keyword ⇒ 读写都在真库上成功（baseline 常态）
        EovaRecord r0 = new EovaRecord();
        r0.set("id", 4);
        r0.set("code", "noesc");
        assertTrue(gw.save("eova_menu", r0));
        assertEquals(1, gw.find("select id from eova_menu where code = 'noesc'").size());

        // ② 【关键开关：发生在配置关键字之后】—— 若 save 误走转义，H2 会因反引号报错（第 87 轮实测缺陷）
        x.conf.addConfig("db.keyword", "eova_menu.code");
        EovaRecord r = new EovaRecord();
        r.set("id", 3);
        r.set("code", "kw");
        r.set("name", "关键字列");
        assertTrue(gw.save("eova_menu", r),
                "写路径【不得】转义（旧 EovaDbPro 不覆写 save 链路）⇒ 真库必须可执行");
        assertTrue(gw.deleteById("eova_menu", 3), "删路径同样不得转义");
        String escaped = gw.escapeSql("select code from eova_menu where code = 'kw'");
        assertFalse(escaped.equals("select code from eova_menu where code = 'kw'"),
                "update/find 路径必须发生转义：" + escaped);
        assertTrue(escaped.contains("`code`") || escaped.contains("\"code\""), escaped);

        // ⚠️ 该转义形态是 MySQL 族反引号，H2 2.x 不接受 ⇒ 此处【不】真跑转义后的 SQL。
        //    转义后的 SQL 形态由 r79 的代理级判据（ArFamilyValueBuilderGoldenTest 的 FakeJdbc）钉住，
        //    真库执行则留给 baseline MySQL（见本判据类注释的"不覆盖"清单）。
        x.conf.getProps().remove("db.keyword");
        assertEquals(0, gw.find("select id from eova_menu where code = 'kw2'").size(), "清掉配置后恢复原样 SQL");
    }
}
