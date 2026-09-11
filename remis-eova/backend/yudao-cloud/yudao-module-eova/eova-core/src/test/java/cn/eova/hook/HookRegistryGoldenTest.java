/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.hook;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import cn.eova.aop.AopContext;
import cn.eova.common.Ds;
import cn.eova.core.object.MetaFieldHook;
import cn.eova.db.EovaDbGateway;
import cn.eova.db.EovaGateways;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code HookRegistry}(82) / {@code EovaMetaHook}(66) / {@code MetaFieldHook}(51)（第 70 轮 port）的判据。
 *
 * <p>Hook 体系是 EOVA 元数据驱动扩展的骨架：{@code HookRegistry} 按（类型, 编码）登记 Hook，
 * 上层用 {@code getBiz}/{@code getAction}/{@code getMeta} 取出。它的键格式与"未命中返回 null"
 * 属对外契约（用户 mod 依赖它）。</p>
 */
class HookRegistryGoldenTest {

    /** 用例前的缓存实现（用后还原，避免依赖全局单例 —— R58） */
    private cn.eova.compat.cache.CacheService originalCache;

    /** 注入自带内存缓存 */
    @org.junit.jupiter.api.BeforeEach
    void injectCache() {
        originalCache = cn.eova.compat.cache.CacheServices.get();
        cn.eova.compat.cache.CacheServices.set(new cn.eova.testkit.MemoryCacheService());
    }

    /** 还原缓存实现 */
    @org.junit.jupiter.api.AfterEach
    void restoreCache() {
        cn.eova.compat.cache.CacheServices.set(originalCache);
    }

    /** 记录式网关替身 */
    static final class Probe {
        final List<String> sqls = new ArrayList<>();
        final List<Object[]> paras = new ArrayList<>();
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
                case "delete":
                case "update":
                    probe.sqls.add((String) args[0]);
                    probe.paras.add((Object[]) args[1]);
                    return 1;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (EovaDbGateway) Proxy.newProxyInstance(HookRegistryGoldenTest.class.getClassLoader(),
                new Class<?>[]{EovaDbGateway.class}, h);
    }

    /** 一个具体的 ActionHook 探针（真实签名见接口） */
    static class ProbeActionHook implements ActionHook {

        @Override
        public void invoke(cn.eova.compat.jfinal.core.LegacyController ctrl,
                           cn.eova.compat.jfinal.kit.LegacyKv kv) {
        }
    }

    /** 一个具体的 BizHook 探针 */
    static class ProbeBizHook implements BizHook {

        @Override
        public void invoke(cn.eova.compat.jfinal.kit.LegacyKv kv) {
        }
    }

    /** 一个具体的 EovaMetaHook 探针 */
    static class ProbeMetaHook implements EovaMetaHook {

        @Override
        public String invoke(EovaMetaHook.Action action, AopContext ac) {
            return action.name();
        }
    }

    @Test
    @DisplayName("HookRegistry：按（类型, 编码）登记与取出；未命中返回 null；可覆盖")
    void registerAndLookup() {
        java.util.Map<String, Hook> before = new java.util.HashMap<>(HookRegistry.hookMap);
        try {
            HookRegistry.hookMap.clear();

            ProbeActionHook act = new ProbeActionHook();
            ProbeBizHook biz = new ProbeBizHook();
            ProbeMetaHook meta = new ProbeMetaHook();

            // 注意：EovaHookType 的 ACTION/BIZ 在旧源码里【被注释掉】（不得恢复），
            // 故只能用实际存在的常量：FLOW_ACTION/DIY 等。
            HookRegistry.add(EovaHookType.FLOW_ACTION, "user.add", act);
            HookRegistry.add(EovaHookType.DIY, "user.add", biz);
            HookRegistry.addMeta("user", meta);

            assertSame(act, HookRegistry.getAction(EovaHookType.FLOW_ACTION, "user.add"),
                    "按类型取回同一实例");
            assertSame(biz, HookRegistry.getBiz(EovaHookType.DIY, "user.add"),
                    "不同类型下同编码互不干扰");
            assertSame(meta, HookRegistry.getMeta("user"), "按编码取 MetaHook（键为 META#code）");

            // 键格式钉死：type + "#" + code
            assertSame(act, HookRegistry.hookMap.get(EovaHookType.FLOW_ACTION + "#user.add"),
                    "登记键必须是 type#code 的拼接形式");

            assertNull(HookRegistry.getAction(EovaHookType.FLOW_ACTION, "nope"), "未命中返回 null");
            assertNull(HookRegistry.getMeta("nope"), "未命中返回 null");

            // 同键覆盖：后登记的生效
            ProbeActionHook act2 = new ProbeActionHook();
            HookRegistry.add(EovaHookType.FLOW_ACTION, "user.add", act2);
            assertSame(act2, HookRegistry.getAction(EovaHookType.FLOW_ACTION, "user.add"));
        } finally {
            HookRegistry.hookMap.clear();
            HookRegistry.hookMap.putAll(before);
        }
    }

    @Test
    @DisplayName("EovaMetaHook：是 Hook 的子接口（标签接口语义），且 HookRegistry 是抽象类需 config()")
    void metaHookTypeHierarchy() {
        assertTrue(Hook.class.isAssignableFrom(EovaMetaHook.class),
                "EovaMetaHook 必须继承 Hook（标签接口）");
        assertTrue(java.lang.reflect.Modifier.isAbstract(HookRegistry.class.getModifiers()),
                "HookRegistry 是抽象类（子类实现 config() 完成登记）");
        try {
            java.lang.reflect.Method config = HookRegistry.class.getDeclaredMethod("config");
            assertTrue(java.lang.reflect.Modifier.isAbstract(config.getModifiers()));
        } catch (NoSuchMethodException e) {
            throw new AssertionError("HookRegistry 必须声明抽象 config()", e);
        }
    }

    /**
     * 造一个"什么都不知道"的请求替身（getAttr/getCookie/getParameter 均返回 null）。
     *
     * @return 替身
     */
    private static jakarta.servlet.http.HttpServletRequest emptyRequest() {
        return (jakarta.servlet.http.HttpServletRequest) Proxy.newProxyInstance(
                HookRegistryGoldenTest.class.getClassLoader(),
                new Class<?>[]{jakarta.servlet.http.HttpServletRequest.class},
                (p2, m, args) -> {
                    switch (m.getName()) {
                        case "getRequestURL":
                            return new StringBuffer("http://localhost/x");
                        case "equals":
                            return p2 == args[0];
                        case "hashCode":
                            return System.identityHashCode(p2);
                        default:
                            return null;
                    }
                });
    }

    @Test
    @DisplayName("MetaFieldHook.deleteBefore：删除元字段前先清理字典表达式（SQL 逐字）")
    void metaFieldHookDeletesDictOptions() throws Exception {
        Probe probe = new Probe();
        EovaGateways.register(Ds.EOVA, gateway(probe));
        try {
            MetaFieldHook hook = new MetaFieldHook();
            // AopContext 构造会取 BaseController.getUser()（经登录服务与请求属性），
            // 故需初始化业务注册中心并给出请求上下文（R58/R69 同类教训）
            cn.eova.service.biz.init();
            cn.eova.common.base.BaseController ctrl = new cn.eova.common.base.BaseController();
            ctrl.setHttpServletRequest(emptyRequest());
            AopContext ac = new AopContext(ctrl);
            ac.record = new cn.eova.db.EovaRecord();
            ac.record.set("object_code", "eova_menu");
            ac.record.set("en", "name");
            assertNull(hook.deleteBefore(ac), "deleteBefore 返回 null（不拦断）");

            // 实测：它委托 sm.meta.deleteMetaField(code, en) ⇒ 三条删除（与 MetaService 契约一致）
            assertEquals(3, probe.sqls.size(), "应下发三条删除，实际：" + probe.sqls);
            assertEquals("delete from eova_field where object_code = ? and en = ?", probe.sqls.get(0));
            assertEquals("delete from eova_field_diy where object_code = ? and en = ?", probe.sqls.get(1));
            assertEquals("delete from eova_option where code like ?", probe.sqls.get(2));
            assertEquals("eova_menu", probe.paras.get(0)[0], "对象编码取自 record.object_code");
            assertEquals("name", probe.paras.get(0)[1], "字段名取自 record.en");
            assertEquals("dict_eova_menu_name%", probe.paras.get(2)[0],
                    "字典 like 参数为 dict_<object>_<field>%");
        } finally {
            EovaGateways.clear();
        }
    }
}
