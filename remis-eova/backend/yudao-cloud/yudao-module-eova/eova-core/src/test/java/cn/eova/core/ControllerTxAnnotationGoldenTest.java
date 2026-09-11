/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core;

import java.lang.reflect.Method;

import cn.eova.common.Ds;
import cn.eova.common.base.BaseController;
import cn.eova.compat.jfinal.aop.LegacyBefore;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyTx;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyTxConfig;
import cn.eova.core.button.ButtonController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ButtonController}(152) 与 {@code HomeController}(145)（第 68 轮 port）的判据。
 *
 * <p><b>本判据只钉"注解契约"与"基类契约"</b>，不驱动 HTTP：这两个控制器的动作体都要
 * 数据库与请求上下文，其实跑属 acceptanceProfile 范畴。而<b>事务注解是它们最容易
 * 被"顺手"改掉的部分</b>（改成 Spring {@code @Transactional}、或把 {@code @TxConfig}
 * 的数据源写错），一旦改了，事务边界与数据源都会变。</p>
 */
class ControllerTxAnnotationGoldenTest {

    /**
     * 取方法的 {@code @LegacyBefore} 声明的第一个拦截器类型。
     *
     * @param m 方法
     * @return 类型；无注解返回 null
     */
    private static Class<?> firstBefore(Method m) {
        LegacyBefore b = m.getAnnotation(LegacyBefore.class);
        return b == null || b.value().length == 0 ? null : b.value()[0];
    }

    @Test
    @DisplayName("ButtonController：doAdd/doQuick 的事务注解与数据源逐项一致")
    void buttonControllerTxContract() throws Exception {
        assertTrue(BaseController.class.isAssignableFrom(ButtonController.class),
                "控制器必须继承 BaseController");

        for (String name : new String[]{"doAdd", "doQuick"}) {
            Method m = ButtonController.class.getDeclaredMethod(name);
            assertEquals(LegacyTx.class, firstBefore(m),
                    name + " 必须声明 @Before(Tx.class)（事务拦截器）");
            LegacyTxConfig tc = m.getAnnotation(LegacyTxConfig.class);
            assertNotNull(tc, name + " 必须声明 @TxConfig");
            assertEquals(Ds.EOVA, tc.value(), name + " 的事务数据源必须是 eova");
        }
    }

    @Test
    @DisplayName("HomeController：带 @Before(Tx.class) 的动作其数据源为默认（无 @TxConfig）")
    void homeControllerTxContract() {
        assertTrue(BaseController.class.isAssignableFrom(HomeController.class),
                "必须继承 BaseController");

        int txCount = 0;
        for (Method m : HomeController.class.getDeclaredMethods()) {
            if (firstBefore(m) == LegacyTx.class) {
                txCount++;
                // 旧源码里这些动作【没有】@TxConfig ⇒ 走默认数据源（区别于 ButtonController）
                assertEquals(null, m.getAnnotation(LegacyTxConfig.class),
                        m.getName() + " 在旧源码中未声明 @TxConfig ⇒ 用默认数据源，不得补上");
            }
        }
        assertEquals(2, txCount, "HomeController 应有 2 个事务动作（旧源码实测），实际 " + txCount);
    }

    @Test
    @DisplayName("两个控制器的动作面非空（防判据空洞）")
    void actionsExist() {
        assertTrue(ButtonController.class.getDeclaredMethods().length >= 5,
                "ButtonController 动作数异常");
        // 实测：HomeController 有 3 个动作（menu/star/resort），另 3 个在旧源码里被注释掉
        assertTrue(HomeController.class.getDeclaredMethods().length >= 3,
                "HomeController 动作数异常");
    }
}
