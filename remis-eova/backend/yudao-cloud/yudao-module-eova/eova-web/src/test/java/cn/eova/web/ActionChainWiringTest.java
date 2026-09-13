/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyTx;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **分发器接线**判据（第 305 轮 · 真缺陷 P1 的接线侧回归锁）。
 *
 * <p>为什么必须有这一层：{@code LegacyInterceptorManager} 自己的判据只能证明"给定注解能装配"；
 * 而本轮真实缺陷的形态是 <b>装配器正确、但接线处没调用它</b>。实测该形态（M3）在只有装配器判据时
 * <b>未被捕获</b>。故这里直接驱动 {@code LegacyDispatcher#buildActionChain(...)}
 * —— 接线一旦退回"只拼全局 + 路由级"，本判据立刻红。</p>
 */
class ActionChainWiringTest {

    /**
     * 取 action 的链（走生产接线代码本身）
     *
     * @param controllerClass 控制器类
     * @param methodName      方法名
     * @return 链
     * @throws Exception 反射失败
     */
    private static LegacyInterceptor[] chain(Class<? extends cn.eova.compat.jfinal.core.LegacyController> controllerClass,
                                            String methodName) throws Exception {
        Method method = controllerClass.getMethod(methodName);
        return LegacyDispatcher.buildActionChain(List.of(), new LegacyInterceptor[0], controllerClass, method);
    }

    @Test
    @DisplayName("接线：MenuController#add 经分发器链构建后含 LegacyTx（方法级 @LegacyBefore 真的生效）")
    void dispatcherWiresMethodLevelLegacyTx() throws Exception {
        LegacyInterceptor[] chain = chain(cn.eova.core.menu.MenuController.class, "add");
        assertTrue(Arrays.stream(chain).anyMatch(i -> i.getClass() == LegacyTx.class),
                "分发器必须把方法级 @LegacyBefore(LegacyTx.class) 并进链 —— 否则回滚标记直穿成 500");
    }

    @Test
    @DisplayName("反例（防恒真）：未标注事务的 action 不得被套上 LegacyTx")
    void dispatcherDoesNotAddTxToUnannotated() throws Exception {
        LegacyInterceptor[] chain = chain(cn.eova.core.menu.MenuController.class, "icon");
        assertFalse(Arrays.stream(chain).anyMatch(i -> i.getClass() == LegacyTx.class),
                "未标注的 action 不该有 LegacyTx");
    }

    @Test
    @DisplayName("接线：类级 @LegacyBefore(AdminInterceptor.class) 经分发器后生效（安全守卫）")
    void dispatcherWiresClassLevelBefore() throws Exception {
        LegacyInterceptor[] chain = chain(cn.eova.core.admin.AdminController.class, "su");
        assertTrue(Arrays.stream(chain)
                        .anyMatch(i -> i.getClass() == cn.eova.core.admin.AdminInterceptor.class),
                "AdminController 的类级守卫必须经分发器生效");
    }
}
