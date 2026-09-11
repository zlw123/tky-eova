/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.interceptor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import cn.eova.aop.AopContext;
import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInvocation;
import cn.eova.compat.jfinal.core.LegacyAction;
import cn.eova.compat.jfinal.core.LegacyActionException;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.render.DefaultLegacyRenderFactory;
import cn.eova.compat.render.LegacyRenderManager;
import cn.eova.core.menu.MenuIntercept;
import cn.eova.db.EovaDbGateway;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;
import cn.eova.model.Menu;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ExceptionInterceptor}(92) 与 {@code MenuIntercept}(73)（第 69 轮 port）的判据。
 *
 * <p>重点钉住两条容易丢的契约：① <b>非 500 的 ActionException 直接渲染对应错误页、不落库</b>
 * （客户端错误不该污染异常日志表）；② 500 类异常按 <b>url+info 去重</b>：
 * 已有近期记录则 {@code num+1}，否则新增一行。</p>
 */
class ExceptionAndMenuInterceptGoldenTest {

    /** 记录器 */
    static final class Probe {
        final List<String> sqls = new ArrayList<>();
        final List<Object[]> paras = new ArrayList<>();

        /** queryLong 的返回值（模拟"已有近期记录"） */
        Long existingId;
    }

    /**
     * MenIntercept 用的探针控制器（AopContext 构造器需要 BaseController）。
     *
     * <p>与 {@link BoomCtrl} 分开是有意的：{@code ExceptionInterceptor} 的 500 分支会走
     * {@code ctrl instanceof BaseController} 再调 {@code getUser()}（需登录服务），
     * 故那里用普通 LegacyController 以避免引入无关依赖。</p>
     */
    public static class MenuCtrl extends cn.eova.common.base.BaseController {
    }

    /** 抛异常的探针控制器 */
    public static class BoomCtrl extends LegacyController {

        /** 本次要抛出的异常 */
        Throwable toThrow;

        /** 抛出 */
        public void act() throws Throwable {
            if (toThrow != null) {
                throw toThrow;
            }
        }
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
                case "queryLong":
                    probe.sqls.add((String) args[0]);
                    probe.paras.add((Object[]) args[1]);
                    return probe.existingId;
                case "update":
                    probe.sqls.add((String) args[0]);
                    probe.paras.add((Object[]) args[1]);
                    return 1;
                case "save":
                    probe.sqls.add("save:" + args[0]);
                    probe.paras.add(new Object[]{args[1]});
                    return true;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (EovaDbGateway) Proxy.newProxyInstance(
                ExceptionAndMenuInterceptGoldenTest.class.getClassLoader(),
                new Class<?>[]{EovaDbGateway.class}, h);
    }

    /**
     * 请求替身（只需 URI 与查询串）。
     *
     * @param uri   URI
     * @param query 查询串（可为 null）
     * @return 替身
     */
    private static HttpServletRequest request(String uri, String query) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getRequestURI":
                    return uri;
                case "getQueryString":
                    return query;
                case "getHeader":
                    return null;
                case "getRemoteAddr":
                    return "127.0.0.1";
                case "getAttribute":
                    return null;
                case "getParameter":
                    return null;
                case "getRequestURL":
                    return new StringBuffer("http://localhost" + uri);
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                ExceptionAndMenuInterceptGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, h);
    }

    /** 装默认渲染工厂（renderError 经它取渲染） */
    @BeforeAll
    static void installFactory() {
        LegacyRenderManager.setRenderFactory(new DefaultLegacyRenderFactory());
    }

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

    /**
     * 造一个"拦截器链 = [ExceptionInterceptor]，动作会抛指定异常"的调用。
     *
     * @param ctrl 控制器
     * @return 调用
     */
    private static LegacyInvocation invocation(LegacyController ctrl) {
        Method m;
        try {
            m = ctrl.getClass().getMethod("act");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        LegacyAction action = new LegacyAction("/c/a", "/c", ctrl.getClass(), m, "act",
                new LegacyInterceptor[]{new ExceptionInterceptor()}, "/view");
        return new LegacyInvocation(action, ctrl);
    }

    @Test
    @DisplayName("非 500 的 ActionException：渲染对应错误页并返回，【不落库】")
    void nonServerErrorRendersAndSkipsLogging() {
        Probe probe = new Probe();
        EovaGateways.register("eova", gateway(probe));
        try {
            BoomCtrl ctrl = new BoomCtrl();
            // 旧构造器签名是 (int, Render, String)：错误渲染经工厂取值（与旧栈一致）
            ctrl.toThrow = new LegacyActionException(404,
                    LegacyRenderManager.getRenderFactory().getErrorRender(404), "not found");
            ctrl.setHttpServletRequest(request("/x", null));
            // 旧语义：Controller.renderError(code) 本身【抛出】ActionException，
            // 由框架的 ActionHandler 渲染错误页 —— 而不是设置 render 字段。
            // 故这里断言"抛出 404 的 ActionException"，且【未落库】。
            LegacyActionException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                    LegacyActionException.class, () -> {
                        invocation(ctrl).invoke();
                    });
            assertEquals(404, thrown.getErrorCode(), "必须抛出对应状态码（由框架渲染错误页）");
            assertNotNull(thrown.getErrorRender(), "必须带错误渲染");
            assertEquals(0, probe.sqls.size(),
                    "客户端错误（非 500）不得写 eova_exception，实际：" + probe.sqls);
        } finally {
            EovaGateways.clear();
        }
    }

    @Test
    @DisplayName("500 类异常：已有近期同 url+info 记录时只做 num+1（不新增行）")
    void serverErrorDeduplicates() {
        Probe probe = new Probe();
        probe.existingId = 5L;
        EovaGateways.register("eova", gateway(probe));
        try {
            BoomCtrl ctrl = new BoomCtrl();
            ctrl.toThrow = new RuntimeException("boom-500");
            ctrl.setHttpServletRequest(request("/x", "a=1"));

            LegacyActionException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                    LegacyActionException.class, () -> invocation(ctrl).invoke());

            assertEquals(500, thrown.getErrorCode(), "500 分支以抛出 500 结束（由框架渲染错误页）");
            assertTrue(probe.sqls.get(0).startsWith("select max(id) from eova_exception"),
                    "先去重查询，实际：" + probe.sqls.get(0));
            assertTrue(probe.sqls.stream().anyMatch(s -> s.startsWith("update eova_exception set num = num+1")),
                    "命中近期记录时必须是 num+1，实际：" + probe.sqls);
            assertTrue(probe.sqls.stream().noneMatch(s -> s.startsWith("save:")),
                    "不得新增行");

            // url 必须带上查询串（且不超过 250 字符）
            Object url = probe.paras.get(0)[1];
            assertEquals("/x?a=1", url, "url = uri + '?' + queryString");
        } finally {
            EovaGateways.clear();
        }
    }

    @Test
    @DisplayName("500 类异常：无近期记录时写入新行（ip/uid/url/info 等字段）")
    void serverErrorInsertsWhenNoRecent() {
        Probe probe = new Probe();
        probe.existingId = null;
        EovaGateways.register("eova", gateway(probe));
        try {
            BoomCtrl ctrl = new BoomCtrl();
            ctrl.toThrow = new RuntimeException("first-time");
            ctrl.setHttpServletRequest(request("/y", null));

            LegacyActionException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                    LegacyActionException.class, () -> invocation(ctrl).invoke());
            assertEquals(500, thrown.getErrorCode());

            assertTrue(probe.sqls.stream().anyMatch(s -> s.equals("save:eova_exception")),
                    "无近期记录时必须新增一行，实际：" + probe.sqls);
            EovaRecord saved = (EovaRecord) probe.paras.get(probe.sqls.indexOf("save:eova_exception"))[0];
            assertEquals("/y", saved.get("url"), "url 必须写入（无查询串时不加 ?）");
            assertNull(saved.get("uid"), "非 BaseController 时 uid 为 null");
            assertNotNull(saved.get("info"));
        } finally {
            EovaGateways.clear();
        }
    }

    @Test
    @DisplayName("MenuIntercept.hideBefore：返回 null（不拦截）且不抛错")
    void menuInterceptHideBefore() throws Exception {
        MenuIntercept mi = new MenuIntercept();
        // AopContext 构造会取 BaseController.getUser()（经登录服务），故需初始化业务注册中心
        cn.eova.service.biz.init();
        MenuCtrl c = new MenuCtrl();
        c.setHttpServletRequest(request("/x", null));
        AopContext ac = new AopContext(c);
        ac.record = new EovaRecord();
        ac.record.set("id", 3);
        assertNull(mi.hideBefore(ac), "hideBefore 旧实现返回 null（不拦截）");
    }

    @Test
    @DisplayName("MenuIntercept：覆写面属于 MetaObjectIntercept 的回调契约")
    void menuInterceptOverridesContract() throws Exception {
        for (String name : new String[]{"hideBefore", "deleteBefore", "addSucceed",
                "deleteSucceed", "updateSucceed"}) {
            Method m = MenuIntercept.class.getDeclaredMethod(name, AopContext.class);
            assertEquals(String.class, m.getReturnType(), name + " 返回 String（回调契约）");
        }
        assertTrue(cn.eova.aop.MetaObjectIntercept.class.isAssignableFrom(MenuIntercept.class));
        Menu menu = new Menu();
        assertNotNull(menu);
    }
}
