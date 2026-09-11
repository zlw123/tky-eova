/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.mod;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;

import cn.eova.compat.jfinal.config.LegacyRoutes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code EovaModConfig} / {@code EovaModRoutes}（第 63 轮 port）的判据。
 *
 * <p><b>为什么把抽象方法集钉死：</b>{@code EovaModConfig} 是<b>给外部 mod 制品实现</b>的契约
 * （{@code EovaModPlugin} 用 {@code Class.forName("com.eova.mod.<group>.<code>.ModConfig")}
 * 反射实例化）。少一个抽象方法，外部 mod 的编译期契约就变了；多一个抽象方法，
 * 既有 mod 会编译不过。故这一集必须逐条钉住。</p>
 *
 * <p><b>不跨实现的原因：</b>旧类的 {@code configModel(HashMap<String, List<Table>>)}
 * 形参含 jfinal 的 {@code Table}，加载旧类会牵出 jfinal 运行时（R38/R44 明令隔离）。</p>
 */
class EovaModFamilyGoldenTest {

    /** 具体实现的探针 */
    static class ProbeMod extends EovaModConfig {

        @Override
        public String GROUP() {
            return "remis";
        }

        @Override
        public String CODE() {
            return "demo";
        }

        @Override
        public void afterEovaStart() {
        }

        @Override
        public void beforeEovaStop() {
        }

        @Override
        public void configRoute(EovaModRoute me) {
        }

        @Override
        public void configModel(java.util.HashMap<String, List<cn.eova.compat.table.TableMetadata>> mapping) {
        }

        @Override
        public void onInstall() {
        }

        @Override
        public void onUninstall() {
        }

        @Override
        public void onUpgrade() {
        }

        /**
         * 暴露 protected 的 getViewPath 供断言。
         *
         * @return 视图路径
         */
        String view() {
            return getViewPath();
        }
    }

    @Test
    @DisplayName("EovaModConfig：8 个抽象方法与旧契约逐条一致（外部 mod 的编译期契约）")
    void abstractSurfaceIsPinned() {
        TreeSet<String> abstracts = new TreeSet<>();
        for (Method m : EovaModConfig.class.getDeclaredMethods()) {
            if (Modifier.isAbstract(m.getModifiers())) {
                abstracts.add(m.getName() + "/" + m.getParameterCount());
            }
        }
        assertEquals(new TreeSet<>(List.of(
                        "GROUP/0", "CODE/0", "afterEovaStart/0", "beforeEovaStop/0",
                        "configRoute/1", "configModel/1", "onInstall/0", "onUninstall/0", "onUpgrade/0")),
                abstracts, "抽象方法集（名 + 形参数）必须与旧契约一致 —— 外部 mod 按它实现");

        // toString 必须是【具体】方法（旧实现覆写了它），不得变成抽象或缺失
        boolean toStringConcrete = java.util.Arrays.stream(EovaModConfig.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("toString") && m.getParameterCount() == 0
                        && !Modifier.isAbstract(m.getModifiers()));
        assertTrue(toStringConcrete, "本类必须自身覆写 toString()（旧实现如此）");
    }

    @Test
    @DisplayName("EovaModConfig：toString 为 GROUP-CODE；getViewPath 用 File.separator 拼接")
    void toStringAndViewPath() {
        ProbeMod mod = new ProbeMod();
        assertEquals("remis-demo", mod.toString(), "旧实现：String.format(\"%s-%s\", GROUP(), CODE())");

        // 旧实现：DIR_MOD_VIEW + GROUP + File.separator + CODE
        String expected = EovaModConst.DIR_MOD_VIEW + "remis" + File.separator + "demo";
        assertEquals(expected, mod.view(), "路径分隔符必须是 File.separator（不是硬编码 '/'）");
        assertTrue(mod.view().endsWith("remis" + File.separator + "demo"));
    }

    @Test
    @DisplayName("EovaModRoutes：继承 LegacyRoutes，config() 不注册任何路由与拦截器")
    void modRoutesIsEmptyLegacyRoutes() {
        EovaModRoutes routes = new EovaModRoutes();
        assertTrue(routes instanceof LegacyRoutes, "必须继承第 61 轮的 Routes 接缝");
        routes.config();
        assertEquals(0, routes.getRouteItemList().size(), "空 config：不注册路由");
        assertEquals(0, routes.getInterceptors().length, "空 config：不注册拦截器");
        assertSame(LegacyRoutes.NULL_INTERS, routes.getInterceptors(),
                "空拦截器必须复用接缝常量（与 jfinal 语义一致）");
        // 模块路由由 EovaModPlugin 用 setBaseViewPath/add 填充，故此处只保证"容器可用"
        routes.setBaseViewPath("/_mod/remis");
        assertEquals("/_mod/remis", routes.getBaseViewPath());
        // add 传 null 控制器：Route 构造器必须抛 IllegalArgumentException（与 jfinal 一致）
        assertThrows(IllegalArgumentException.class, () -> routes.add("/remisindex", null),
                "null 控制器必须抛 IllegalArgumentException，而不是静默加入一条无效路由");
        assertEquals(0, routes.getRouteItemList().size(), "抛错后不得留下半成品路由项");
    }
}
