/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.sql.ddl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.core.api.ApiInterceptor;
import cn.eova.core.api.ApiRouterHandler;
import cn.eova.core.api.ApiRoutes;
import cn.eova.db.EovaDbGateway;
import cn.eova.db.EovaGateways;
import cn.eova.sql.ddl.dialect.MysqlDefineDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ApiRoutes}(23) 与 {@code DataDefine}(93)（第 65 轮 port）的判据。
 *
 * <p>两者都是"行为集中在少数几条委托链上"的单元，故判据直接走**端到端链路**：
 * {@code ApiRoutes.add} 必须把路径压到 {@code /router} 之下；
 * {@code DataDefine.create()/drop()} 必须经"方言工厂 → 生成 DDL → EovaModUtil → 网关 batch"
 * 这条完整链（顺带覆盖第 64 轮新增的 {@code batch} 接缝）。</p>
 */
class DataDefineAndApiRoutesGoldenTest {

    /** 被路由登记的控制器 */
    public static class ProbeCtrl extends LegacyController {
    }

    /** 记录 batch 调用的网关替身 */
    static final class Probe {
        final List<List<String>> batches = new ArrayList<>();

        @SuppressWarnings("unchecked")
        void record(Object[] batchArgs) {
            batches.add(new ArrayList<>((List<String>) batchArgs[0]));
        }
    }

    /**
     * 建记录式网关。
     *
     * @param probe 记录器
     * @return 替身
     */
    private static EovaDbGateway gateway(Probe probe) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "batch":
                    probe.record((Object[]) args);
                    return new int[((List<?>) args[0]).size()];
                case "update":
                    return 1;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (EovaDbGateway) Proxy.newProxyInstance(
                DataDefineAndApiRoutesGoldenTest.class.getClassLoader(),
                new Class<?>[]{EovaDbGateway.class}, h);
    }

    @Test
    @DisplayName("ApiRoutes：add 必须把路径压到 /router 之下；config 只挂 ApiInterceptor")
    void apiRoutesPrefixesAndRegistersInterceptor() {
        ApiRoutes routes = new ApiRoutes();
        assertEquals(0, routes.getInterceptors().length, "构造后尚无拦截器");

        routes.config();
        assertEquals(1, routes.getInterceptors().length, "config() 只挂一个拦截器");
        assertTrue(routes.getInterceptors()[0] instanceof ApiInterceptor,
                "挂的必须是 ApiInterceptor（旧实现如此）");

        routes.add("/user", ProbeCtrl.class);
        routes.add("/role", ProbeCtrl.class);
        assertEquals(2, routes.getRouteItemList().size());
        assertEquals("/router/user", routes.getRouteItemList().get(0).getControllerPath(),
                "所有注册路径必须被强制加上 /router 前缀（契约）");
        assertEquals("/router/role", routes.getRouteItemList().get(1).getControllerPath());
        assertEquals(ApiRouterHandler.ROUTER, "/router", "ROUTER 常量值属契约");
    }

    /** DataDefine 探针 */
    static class ProbeDefine extends DataDefine {

        @Override
        protected String getDs() {
            return "eova";
        }

        @Override
        protected List<DefineTable> createTable() {
            List<DefineColumn> cols = new ArrayList<>();
            cols.add(new DefineColumn("id", DefineTable.NUMBER, 11, 0, "主键"));
            return List.of(new DefineTable("探针表", "t_probe", "id", cols));
        }

        @Override
        protected Set<String> dropTable() {
            return Set.of("t_probe");
        }
    }

    @Test
    @DisplayName("DataDefine：create/drop 走完 方言工厂→DDL→EovaModUtil→网关 batch 全链")
    void dataDefineDrivesDdlThroughGateway() {
        Probe probe = new Probe();
        EovaGateways.register("eova", gateway(probe));
        try {
            DefineDialectFactory.addDialect("eova", new MysqlDefineDialect());
            ProbeDefine define = new ProbeDefine();

            // ① 建表
            define.create();
            assertEquals(1, probe.batches.size(), "create() 应恰好下发一批 SQL");
            List<String> createSqls = probe.batches.get(0);
            assertEquals(1, createSqls.size(), "一张表一条 DDL");
            String createSql = createSqls.get(0).toLowerCase();
            assertTrue(createSql.contains("create table"), "必须是建表语句：" + createSql);
            assertTrue(createSql.contains("t_probe"), "必须含表名：" + createSql);

            // ② 删表
            probe.batches.clear();
            define.drop();
            assertEquals(1, probe.batches.size(), "drop() 应恰好下发一批 SQL");
            String dropSql = probe.batches.get(0).get(0).toLowerCase();
            assertTrue(dropSql.contains("drop table"), "必须是删表语句：" + dropSql);
            assertTrue(dropSql.contains("t_probe"));

            // ③ 只生成、不下发（generateXxxDDL 仅打印）
            probe.batches.clear();
            define.generateCreateDDL();
            define.generateDropDDL();
            assertEquals(0, probe.batches.size(), "generateXxxDDL 只输出、不得执行");
        } finally {
            EovaGateways.clear();
        }
    }
}
