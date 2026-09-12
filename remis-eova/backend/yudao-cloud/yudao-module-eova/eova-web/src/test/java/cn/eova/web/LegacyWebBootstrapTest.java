/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.util.ArrayList;
import java.util.List;

import cn.eova.compat.jfinal.config.LegacyJFinalBoot;
import cn.eova.compat.jfinal.config.LegacyRoutes;
import cn.eova.compat.table.EovaTableMapping;
import cn.eova.model.Button;
import cn.eova.model.Menu;
import cn.eova.model.User;
import cn.eova.tools.x;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **切片 S1 判据：Web 层启动装配可跑、路由表可枚举（第 245 轮）**。
 *
 * <p><b>为什么这么判（不猜路由字符串）</b>：S1 的目的是"证明旧引导序列能在 Spring 容器里被驱动，
 * 且路由表真的产出了"。若断言某条具体路由字符串，就变成"照抄我猜的路径"——
 * 那种断言在路径口径与旧栈一致时绿、不一致时红，但**不能区分"路由没建"与"路径写法不同"**。
 * 故本判据用三类**结构性/契约性**事实：
 * <ol>
 *   <li>引导真的完成：{@code boot.isStarted()}，且 {@link LegacyRoutes} 路由条目非空；</li>
 *   <li>路由条目**形态合法**：每条 {@code controllerPath} 非空白且以 {@code /} 开头（旧栈
 *       {@code processControllerPath} 的口径），条目数达到下限；</li>
 *   <li>**独立契约事实**（与路由写法无关）：{@code configPlugin} 注册的模型→表映射必须在位
 *       （{@code User→eova_user}、{@code Menu→eova_menu}、{@code Button→eova_button}）——
 *       它们正是 S2 登录/菜单链路要用的 dao 绑定。</li>
 * </ol>
 *
 * <p><b>环境纪律（R58）</b>：{@code x.conf} 与 {@code EovaTableMapping} 都是全局静态 ⇒ 逐项还原。
 *
 * <p><b>本判据不覆盖（如实登记）</b>：分发器/渲染/静态资源/会话契约/真浏览器 —— 属 S2–S5。
 * 因此**阶段 1 的「HTTP 容器层」在 S5 之前仍记 not executed**。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LegacyWebBootstrapTest {

    @Autowired
    private LegacyJFinalBoot boot;

    @AfterEach
    void tearDown() {
        for (String k : new String[]{"file.dir.base", "db.datasource", "eova.url", "eova.user",
                "eova.pwd", "eova.driver"}) {
            x.conf.getProps().remove(k);
        }
        EovaTableMapping.me().clear();
        EovaTableMapping.setMetadataSource(null);
    }

    @Test
    @DisplayName("★ S1：引导在 Spring 容器内完成，路由表可枚举且形态合法")
    void bootStartsInsideSpringAndRoutesAreEnumerable() {
        assertTrue(boot.isStarted(), "★ 旧引导序列必须已被驱动（isStarted）");

        LegacyRoutes routes = boot.getRoutes();
        assertNotNull(routes, "必须暴露路由表");
        List<LegacyRoutes.Route> items = routes.getRouteItemList();
        assertFalse(items.isEmpty(), "★ 路由表不得为空（否则 configRoute 未被调用）");

        List<String> paths = new ArrayList<>();
        for (LegacyRoutes.Route r : items) {
            String p = r.getControllerPath();
            paths.add(p);
            assertNotNull(p, "controllerPath 不得为 null");
            assertFalse(p.isBlank(), "★ controllerPath 不得为空白");
            assertTrue(p.startsWith("/"), "★ 旧口径下 controllerPath 必须以 / 开头，实际=" + p);
        }
        // ★ 旧栈实际只注册【两个控制器路径】/app 与 /：动作名是这两个控制器上的公开无参方法
        //   （r70 记录："动作名取自 AppController + IndexController 的公开无参方法"）。
        //   此前我按"数十条路径"拍了下限 10 —— 实测 2 条 ⇒ 改为断言**精确集合**（更有据、更强）。
        java.util.Collections.sort(paths);
        assertEquals(java.util.Arrays.asList("/", "/app"), paths,
                "★ 控制器路径集合必须恰为 {/,/app}（旧 configRoute 的注册面）：" + paths);

        // 动作面（S2 分发器要用的口径）：两个控制器上的公开无参方法数（此处**打印**，不用猜的数字做断言）
        int actions = 0;
        for (Class<?> c : new Class<?>[]{cn.eova.core.AppController.class, cn.eova.core.IndexController.class}) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isPublic(m.getModifiers()) && m.getParameterCount() == 0) {
                    actions++;
                }
            }
        }
        assertTrue(actions > 0, "★ 两个控制器上必须有公开无参方法（动作名来源），实际=" + actions);
        System.out.println("[S1] 控制器路径=" + paths + "，公开无参动作数=" + actions);
    }

    @Test
    @DisplayName("★ S1：configPlugin 的模型→表映射在位（S2 登录/菜单的 dao 绑定前提）")
    void modelTableMappingsAreRegistered() {
        assertEquals("eova_user", EovaTableMapping.me().getTable(User.class).getName(),
                "★ User 必须绑定 eova_user（登录链路的 dao 前提）");
        assertEquals("eova_menu", EovaTableMapping.me().getTable(Menu.class).getName(),
                "★ Menu 必须绑定 eova_menu（菜单链路前提）");
        assertEquals("eova_button", EovaTableMapping.me().getTable(Button.class).getName(),
                "★ Button 必须绑定 eova_button（按角色取按钮的前提）");
    }
}
