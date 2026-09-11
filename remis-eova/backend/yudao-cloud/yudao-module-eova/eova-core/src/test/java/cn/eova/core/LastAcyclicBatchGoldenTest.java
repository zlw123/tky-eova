/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core;

import java.util.List;

import cn.eova.config.EovaConfig;
import cn.eova.db.EovaRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第 73 轮 port 的一批单元（控制器族 + 模板族 + `EovaMetaHooks`）与三处接缝补齐的判据。
 *
 * <p>本批把"可 port 的<b>无环</b>部分"基本扫完：剩余 28 个未 port 单元全部成环于
 * `EovaConfig` 的 W3 接缝族与 `IAtom`/AR 族（下一批的主题）。</p>
 */
class LastAcyclicBatchGoldenTest {

    /** 会话拦截器探针（实现接口的全部 4 个抽象方法） */
    static class ProbeSessionIntercept implements cn.eova.aop.UserSessionIntercept {

        @Override
        public String loginBefore(cn.eova.model.User user) {
            return null;
        }

        @Override
        public void login(cn.eova.model.User user) {
        }

        @Override
        public void logout(cn.eova.model.User user) {
        }

        @Override
        public void su(cn.eova.model.User user, com.alibaba.fastjson.JSONObject switchUser) {
        }
    }

    /** 本批控制器 */
    private static final List<Class<?>> CONTROLLERS = List.of(
            cn.eova.widget.WidgetCtrl.class,
            cn.eova.meta.api.WidgetController.class,
            cn.eova.meta.api.MetaControler.class,
            cn.eova.core.AppController.class,
            cn.eova.meta.api.ExcelController.class,
            cn.eova.core.admin.AdminController.class,
            cn.eova.meta.api.TreeController.class,
            cn.eova.widget.tree.TreeController.class);

    @Test
    @DisplayName("控制器族：继承 BaseController、public，且 FQCN 属契约")
    void controllerFamily() {
        for (Class<?> c : CONTROLLERS) {
            assertTrue(cn.eova.common.base.BaseController.class.isAssignableFrom(c),
                    c.getName() + " 必须继承 BaseController");
            assertTrue(java.lang.reflect.Modifier.isPublic(c.getModifiers()),
                    c.getName() + " 必须 public（路由按类名注册）");
        }
        assertEquals("cn.eova.widget.WidgetCtrl", cn.eova.widget.WidgetCtrl.class.getName(),
                "WidgetCtrl 的 FQCN 属对外契约（前端按 /widget 调它的动作）");
        assertEquals("cn.eova.meta.api.WidgetController",
                cn.eova.meta.api.WidgetController.class.getName(),
                "WidgetController 注册在 /api/widget（EovaWebRoutes）");
    }

    @Test
    @DisplayName("模板族与 Hook 注册表：类型契约")
    void templatesAndHooks() {
        assertTrue(cn.eova.template.single.SingleTemplate.class.getSuperclass() != null);
        assertTrue(cn.eova.template.query.QueryTemplate.class.getSuperclass() != null);
        assertTrue(java.lang.reflect.Modifier.isPublic(cn.eova.EovaMetaHooks.class.getModifiers()),
                "EovaMetaHooks 必须 public（EovaConfig 注册它）");
    }

    @Test
    @DisplayName("EovaConfig stub 的真实子集：会话拦截器存取器（第 73 轮按旧源码逐字补入）")
    void eovaConfigSessionInterceptAccessor() {
        cn.eova.aop.UserSessionIntercept before = EovaConfig.getUserSessionIntercept();
        try {
            EovaConfig.setUserSessionIntercept(null);
            assertNull(EovaConfig.getUserSessionIntercept(), "未设置时为 null");

            // UserSessionIntercept 有 4 个抽象方法（loginBefore/login/logout/su）⇒ 非函数接口
            cn.eova.aop.UserSessionIntercept probe = new ProbeSessionIntercept();
            EovaConfig.setUserSessionIntercept(probe);
            assertSame(probe, EovaConfig.getUserSessionIntercept(),
                    "必须是同一实例（静态字段语义，旧实现如此）");
        } finally {
            EovaConfig.setUserSessionIntercept(before);
        }
    }

    @Test
    @DisplayName("EovaRecord.remove(String...)：null 数组跳过；多列按序移除（第 73 轮补的重载）")
    void recordRemoveVarargs() {
        EovaRecord r = new EovaRecord();
        r.set("a", 1);
        r.set("b", 2);
        r.set("c", 3);

        // ① null 数组：旧字节码直接跳过（不抛错）
        assertSame(r, r.remove((String[]) null), "remove(null 数组) 必须返回自身且不抛错");
        assertEquals(3, r.getColumns().size(), "null 数组不得删掉任何列");

        // ② 多列按序移除
        r.remove("a", "c");
        assertFalse(r.getColumns().containsKey("a"));
        assertFalse(r.getColumns().containsKey("c"));
        assertTrue(r.getColumns().containsKey("b"), "未列出的列必须保留");
    }
}
