/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.service;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.eova.common.Ds;
import cn.eova.config.EovaConst;
import cn.eova.db.EovaDbGateway;
import cn.eova.db.EovaGateways;
import cn.eova.compat.cache.CacheService;
import cn.eova.common.base.BaseCache;
import cn.eova.model.Menu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code AuthService}（第 65 轮 port）的判据。
 *
 * <p><b>判据落点：</b>无库可跑 —— 用记录式网关替身回答查询，并把"缓存命中即不查库"
 * 这条<b>性能与语义双重契约</b>钉住（旧实现先取 {@code BaseCache} 的
 * {@code EovaConst.ALL_MENU}，命中则直接复制）。</p>
 */
class AuthServiceGoldenTest {

    /**
     * 自带的内存缓存服务。
     *
     * <p><b>为什么必须注入而不是用全局单例：</b>第 65 轮全量跑时本判据<b>三个用例全错</b>，
     * 报 "The CacheManager has been shut down" —— 同 JVM 里别的用例把 EhCache 单例关了
     * （R49 记录的全局单例问题）。判据依赖环境状态就会"单独跑绿、全量跑红"。
     * 注入自带实现后判据自洽，且顺带把"经 CacheService 读写"这条链钉住。</p>
     */
    static final class MemoryCache implements CacheService {

        /** 存储 */
        final Map<String, Map<Object, Object>> store = new java.util.HashMap<>();

        @Override
        public Object get(String cacheName, Object key) {
            Map<Object, Object> m = store.get(cacheName);
            return m == null ? null : m.get(key);
        }

        @Override
        public void put(String cacheName, Object key, Object value) {
            store.computeIfAbsent(cacheName, k -> new java.util.HashMap<>()).put(key, value);
        }

        @Override
        public void remove(String cacheName, Object key) {
            Map<Object, Object> m = store.get(cacheName);
            if (m != null) {
                m.remove(key);
            }
        }
    }

    /** 用例前的缓存实现（用后还原，避免影响同 JVM 的其他用例） */
    private CacheService originalService;

    /** 用例前注入自带缓存实现 */
    @BeforeEach
    void injectCache() {
        originalService = cn.eova.compat.cache.CacheServices.get();
        BaseCache.setCacheService(new MemoryCache());
    }

    /** 用例后还原缓存实现 */
    @AfterEach
    void restoreCache() {
        cn.eova.compat.cache.CacheServices.set(originalService);
    }

    /** 记录器 */
    static final class Probe {
        final List<String> sqls = new ArrayList<>();
        final List<Object[]> paras = new ArrayList<>();
        Object queryResult;
    }

    /**
     * 记录式网关替身。
     *
     * @param probe 记录器
     * @return 替身
     */
    private static EovaDbGateway gateway(Probe probe) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "query":
                    probe.sqls.add((String) args[0]);
                    probe.paras.add((Object[]) args[1]);
                    return probe.queryResult;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (EovaDbGateway) Proxy.newProxyInstance(AuthServiceGoldenTest.class.getClassLoader(),
                new Class<?>[]{EovaDbGateway.class}, h);
    }

    /**
     * 造一个菜单（只需 id 与 parent_id）。
     *
     * @param id       主键
     * @param parentId 父键
     * @param code     菜单编码
     * @return 菜单
     */
    private static Menu menu(int id, int parentId, String code) {
        Menu m = new Menu();
        m.set("id", id);
        m.set("parent_id", parentId);
        m.set("code", code);
        return m;
    }

    @Test
    @DisplayName("queryMenuCodeByRid：SQL 与参数按契约传给网关（含 ui='query' 过滤）")
    void queryMenuCodeByRidPassesSqlAndParam() {
        Probe probe = new Probe();
        probe.queryResult = List.of("menu_a", "menu_b");
        EovaGateways.register(Ds.EOVA, gateway(probe));
        try {
            List<String> codes = new AuthService().queryMenuCodeByRid(7);
            assertEquals(List.of("menu_a", "menu_b"), codes, "必须原样返回网关结果");
            assertEquals(1, probe.sqls.size());
            String sql = probe.sqls.get(0);
            assertTrue(sql.contains("eova_role_btn"), "SQL 必须查角色按钮授权表：" + sql);
            assertTrue(sql.contains("eova_button"), "SQL 必须 join 按钮表");
            assertTrue(sql.contains("b.ui = 'query'"), "只取查询类按钮（契约）：" + sql);
            assertEquals(7, ((Object[]) probe.paras.get(0))[0], "角色 ID 必须作为参数传入");
        } finally {
            EovaGateways.clear();
        }
    }

    @Test
    @DisplayName("getByParentId：缓存命中【不查库】，返回自身+全部后代，且是副本")
    void getByParentIdUsesCacheAndReturnsCopy() {
        Probe probe = new Probe();
        EovaGateways.register(Ds.EOVA, gateway(probe));
        try {
            // 预置缓存：1(根) -> 2 -> 3，另有 9 属别的树
            LinkedHashMap<Integer, Menu> all = new LinkedHashMap<>();
            all.put(1, menu(1, 0, "root"));
            all.put(2, menu(2, 1, "child"));
            all.put(3, menu(3, 2, "grand"));
            all.put(9, menu(9, 0, "other"));
            cn.eova.common.base.BaseCache.putSer(EovaConst.ALL_MENU, all);
            try {
                AuthService svc = new AuthService();
                Map<Integer, Menu> result = svc.getByParentId(1);

                assertEquals(0, probe.sqls.size(), "缓存命中时【不得查库】");
                assertEquals(List.of(1, 2, 3), new ArrayList<>(result.keySet()),
                        "必须返回自身 + 全部后代（深度优先、保序），不含别的树");
                assertNotSame(all, result, "必须返回副本（旧实现经 CloneUtil.clone）");
                assertFalse(result.containsKey(9), "不得混入其它父节点的子树");

                // 副本语义：改结果不影响缓存
                result.remove(1);
                assertEquals(4, all.size(), "缓存必须不受结果改动影响");
            } finally {
                cn.eova.common.base.BaseCache.delSer(EovaConst.ALL_MENU);
            }
        } finally {
            EovaGateways.clear();
        }
    }

    @Test
    @DisplayName("isExistsAuthByPidRid：角色授权的 menu_code 命中树内 code 才为真")
    void isExistsAuthByPidRidMatchesMenuCode() {
        Probe probe = new Probe();
        probe.queryResult = List.of("child");
        EovaGateways.register(Ds.EOVA, gateway(probe));
        try {
            LinkedHashMap<Integer, Menu> all = new LinkedHashMap<>();
            all.put(1, menu(1, 0, "root"));
            all.put(2, menu(2, 1, "child"));
            cn.eova.common.base.BaseCache.putSer(EovaConst.ALL_MENU, all);
            try {
                AuthService svc = new AuthService();
                assertTrue(svc.isExistsAuthByPidRid(1, 7), "授权码命中子树内 code ⇒ true");

                probe.queryResult = List.of("nope");
                assertFalse(svc.isExistsAuthByPidRid(1, 7), "授权码与树内 code 都不匹配 ⇒ false");
            } finally {
                cn.eova.common.base.BaseCache.delSer(EovaConst.ALL_MENU);
            }
        } finally {
            EovaGateways.clear();
        }
    }

    @Test
    @DisplayName("queryMenuByParentId：把子树 map 转成 List（顺序与 map 一致）")
    void queryMenuByParentIdFlattens() {
        EovaGateways.register(Ds.EOVA, gateway(new Probe()));
        try {
            LinkedHashMap<Integer, Menu> all = new LinkedHashMap<>();
            all.put(1, menu(1, 0, "root"));
            all.put(2, menu(2, 1, "child"));
            cn.eova.common.base.BaseCache.putSer(EovaConst.ALL_MENU, all);
            try {
                List<Menu> list = new AuthService().queryMenuByParentId(1);
                assertEquals(2, list.size());
                assertEquals("root", list.get(0).getStr("code"));
                assertEquals("child", list.get(1).getStr("code"));
            } finally {
                cn.eova.common.base.BaseCache.delSer(EovaConst.ALL_MENU);
            }
        } finally {
            EovaGateways.clear();
        }
    }

}
