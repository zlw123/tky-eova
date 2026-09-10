/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.db;

import java.sql.Connection;
import java.sql.SQLException;

import cn.eova.db.EovaDbGateway;
import cn.eova.db.EovaGateways;
import cn.eova.db.JdbcEovaDbGateway;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code DsUtil} 与数据层所有权接缝的判据（DES-DB-OWNERSHIP-R2 §5）。
 *
 * <p><b>为什么必须单独做：</b>变异测试实测发现
 * —— 把 {@code EovaGateways.find(ds)} 偷偷改成 {@code get(ds)}（即"未注册就回落默认网关"）
 * 竟然<b>没有任何判据失败</b>。而这两者语义完全不同，且 {@code DsUtil} 的正确性
 * <b>正建立在 find 不回落之上</b>：
 * <pre>
 * EovaDbGateway gw = EovaGateways.find(ds);      // 精确查找
 * if (gw == null) throw new SQLException(ds + " datasrouce can not get config");
 * return gw.dataSource().getConnection();
 * </pre>
 * 若换成 {@code get(ds)}，未注册的数据源会<b>静默去连默认库</b> ——
 * 表结构自省会读错库，而且错得很安静。</p>
 *
 * <p>acceptanceProfile: golden-dsutil-connection</p>
 */
class DsUtilConnectionGoldenTest {

    private static final String URL = "jdbc:mysql://127.0.0.1:13306/eova_meta"
            + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai";

    private static JdbcEovaDbGateway gw;

    /**
     * 建一个可用的网关（同时作为 fallback 装入，供区分 find/get 语义用）。
     */
    @BeforeAll
    static void setUp() {
        try {
            MysqlDataSource ds = new MysqlDataSource();
            ds.setUrl(URL);
            ds.setUser("root");
            ds.setPassword("root");
            gw = new JdbcEovaDbGateway(ds);
            Number n = gw.queryNumber("select count(*) from eova_field", new Object[0]);
            Assumptions.assumeTrue(n != null && n.intValue() > 0, "baseline 不可用，跳过");
        } catch (Throwable t) {
            Assumptions.abort("baseline MySQL 不可用，跳过：" + t.getMessage());
        }
    }

    /**
     * 每个用例后清空注册表，避免影响其它判据。
     */
    @AfterEach
    void tearDown() {
        EovaGateways.clear();
    }

    /**
     * <b>核心判据：</b>{@code find} 精确查找（未注册返回 null），{@code get} 回落默认网关。
     *
     * <p>两者必须可区分 —— 这正是把 {@code find} 换成 {@code get} 会被抓出来的地方。</p>
     */
    @Test
    @DisplayName("find 不回落（未注册返回 null），get 回落默认网关 —— 两者语义必须可区分")
    void findDoesNotFallBackButGetDoes() {
        EovaGateways.setFallback(gw);
        EovaGateways.register("known", gw);

        // 已注册：两者一致
        assertSame(gw, EovaGateways.find("known"), "已注册时 find 应返回该网关");
        assertSame(gw, EovaGateways.get("known"), "已注册时 get 应返回该网关");

        // 未注册：语义必须不同
        assertNull(EovaGateways.find("never_registered"),
                "find 必须【不回落】：未注册返回 null。回落会让 DsUtil 静默连错库");
        assertSame(gw, EovaGateways.get("never_registered"),
                "get 必须【回落】默认网关（业务代码的单数据源友好语义）");

        // null 参数：find 返回 null（对齐 jfinal DbKit.getConfig(null) 查表结果）
        assertNull(EovaGateways.find(null), "find(null) 应为 null");
        assertSame(gw, EovaGateways.get(null), "get(null) 应直接用默认网关");
    }

    /**
     * 未注册数据源时，{@code DsUtil.getConnection} 必须抛出<b>逐字</b>的旧错误串。
     *
     * <p><b>注意 {@code datasrouce} 的拼写：</b>这是旧实现原文的拼写错误，
     * 属对外可见的错误消息，阶段 1 <b>原样保留</b>，不得"顺手修正"。</p>
     */
    @Test
    @DisplayName("未注册数据源：DsUtil.getConnection 抛 SQLException，错误串逐字保留（含原文拼写 datasrouce）")
    void unregisteredDataSourceThrowsVerbatimMessage() {
        EovaGateways.setFallback(gw);   // 即使有默认网关，也不能救未注册的 ds

        SQLException e = assertThrows(SQLException.class,
                () -> DsUtil.getConnection("never_registered"),
                "未注册数据源必须抛 SQLException（而不是静默回落默认库）");

        assertEquals("never_registered datasrouce can not get config", e.getMessage(),
                "错误串必须与旧实现逐字一致 —— 含原文拼写 datasrouce（不是 datasource）");
    }

    /**
     * 已注册数据源时，{@code DsUtil.getConnection} 返回<b>可用</b>连接
     * （表结构自省的真实路径）。
     *
     * @throws Exception 连接/元数据失败
     */
    @Test
    @DisplayName("已注册数据源：DsUtil.getConnection 返回可用连接，可读 DatabaseMetaData")
    void registeredDataSourceYieldsUsableConnection() throws Exception {
        EovaGateways.register("eova", gw);

        try (Connection c = DsUtil.getConnection("eova")) {
            assertNotNull(c, "必须返回连接");
            assertTrue(!c.isClosed(), "连接必须处于打开状态");
            // 真跑一次元数据读取 —— 这就是 DsUtil 存在的目的（表结构自省）
            String product = c.getMetaData().getDatabaseProductName();
            assertNotNull(product, "应能读到数据库产品名");
            assertTrue(product.toLowerCase().contains("mysql"),
                    "baseline 为 MySQL，实际读到：" + product);
        }
    }

    /**
     * {@code EovaDbGateway.dataSource()} 必须返回构造期注入的那个 DataSource 本身
     * （不做包装），否则自省拿到的不是真正的连接池。
     */
    @Test
    @DisplayName("EovaDbGateway.dataSource() 返回构造期注入的实例本身（不包装）")
    void dataSourceIsTheInjectedInstance() {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(URL);
        EovaDbGateway g = new JdbcEovaDbGateway(ds);
        assertSame(ds, g.dataSource(), "必须返回注入的同一实例，不得包装");
    }

}
