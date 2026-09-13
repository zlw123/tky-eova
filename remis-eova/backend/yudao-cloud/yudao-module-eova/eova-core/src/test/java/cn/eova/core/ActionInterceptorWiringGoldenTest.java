/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core;

import java.lang.reflect.Method;
import java.util.Arrays;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInterceptorManager;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyTx;
import cn.eova.core.admin.AdminController;
import cn.eova.core.admin.AdminInterceptor;
import cn.eova.core.menu.MenuController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **生产控制器上的注解必须真的进拦截器链**（第 305 轮 · 真缺陷 P1 的端到端回归锁）。
 *
 * <p>兼容层自己的判据（{@code LegacyInterceptorManagerGoldenTest}）只能证明"给定注解能装配"；
 * 本判据证明"**真实控制器上的注解确实被读到了**" —— 两者缺一不可：
 * 装配器正确但接线处没调用它，缺陷照样在（本轮就是这种情况）。</p>
 *
 * <p><b>缺陷现场</b>：{@code LegacyDispatcher} 只拼"全局 + 路由级"⇒ 方法级
 * {@code @LegacyBefore(LegacyTx.class)}（全仓 24 处）与类级 {@code @LegacyBefore(AdminInterceptor.class)}
 * 全部惰性 ⇒ {@code GET /menu/add} 从旧栈的 fail JSON 变成 500
 * （回滚标记 {@code LegacyNestedTransactionHelpException} 无人吞）。</p>
 */
class ActionInterceptorWiringGoldenTest {

    /**
     * 取 action 的完整链（等价 {@code LegacyDispatcher} 的接线方式）
     *
     * @param controllerClass 控制器类
     * @param methodName      方法名
     * @return 拦截器链
     * @throws Exception 反射失败
     */
    private static LegacyInterceptor[] chainOf(Class<?> controllerClass, String methodName) throws Exception {
        Method method = controllerClass.getMethod(methodName);
        return LegacyInterceptorManager.buildControllerActionInterceptor(
                new LegacyInterceptor[0], new LegacyInterceptor[0],
                LegacyInterceptorManager.createControllerInterceptor(controllerClass),
                controllerClass, method);
    }

    /**
     * 链里是否含指定拦截器类
     *
     * @param chain 链
     * @param cls   拦截器类
     * @return 是否含
     */
    private static boolean has(LegacyInterceptor[] chain, Class<?> cls) {
        return Arrays.stream(chain).anyMatch(i -> i.getClass() == cls);
    }

    @Test
    @DisplayName("方法级 @LegacyBefore(LegacyTx.class)：MenuController.add / ButtonController 的 add 必须在链上（P1 回归锁）")
    void methodLevelLegacyTxIsWired() throws Exception {
        assertTrue(has(chainOf(MenuController.class, "add"), LegacyTx.class),
                "MenuController#add 的链里必须有 LegacyTx —— 缺了它，回滚标记会直穿成 500（旧栈是 fail JSON）");
        // ★ 用**实测的**方法名：ButtonController 的保存动作是 doAdd/doQuick（add 是渲染入口页的，无事务）
        assertTrue(has(chainOf(cn.eova.core.button.ButtonController.class, "doAdd"), LegacyTx.class),
                "ButtonController#doAdd 标注了 @LegacyBefore(LegacyTx.class)");
        assertTrue(has(chainOf(cn.eova.core.button.ButtonController.class, "doQuick"), LegacyTx.class),
                "ButtonController#doQuick 同理");
    }

    @Test
    @DisplayName("反例（防恒真）：未标注方法级 @Before 的 action 链里**不得**出现 LegacyTx")
    void unannotatedActionHasNoLegacyTx() throws Exception {
        // `MenuController#icon`（旧实现未标注 @Before）—— 若它也有 LegacyTx，说明判据看的是别的东西
        assertFalse(has(chainOf(MenuController.class, "icon"), LegacyTx.class),
                "未标注的 action 不该被套事务 —— 否则上一条判据可能是恒真的");
    }

    @Test
    @DisplayName("类级 @LegacyBefore(AdminInterceptor.class)：AdminController 的所有 action 都在守卫之下")
    void classLevelBeforeIsWired() throws Exception {
        LegacyInterceptor[] chain = LegacyInterceptorManager.createControllerInterceptor(AdminController.class);
        assertTrue(Arrays.stream(chain).anyMatch(i -> i instanceof AdminInterceptor),
                "AdminController 的类级 @LegacyBefore(AdminInterceptor.class) 必须生效（安全相关）");
        // 同一个类上取到的实例必须复用（旧 singletonMap 语义）
        assertSame(chain[0], LegacyInterceptorManager.createControllerInterceptor(AdminController.class)[0],
                "拦截器实例必须复用，不得每次新建");
    }
}
