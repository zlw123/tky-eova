/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.mod;

import java.util.ArrayList;
import java.util.List;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.core.LegacyController;

/**
 * <p>ported from: cn.eova.mod.EovaModPlugin（**静态部分**；同文件、同 FQCN）
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 *
 * <p><b>本类是"部分 port"，边界已登记在 {@code port-units.py}：</b>
 * <ul>
 *   <li><b>已 port</b>：静态配置集 {@code modConfigs}、静态路由集 {@code modRoutes}，
 *       以及 {@code getModConfigs()}/{@code getModRoutes()}/{@code addRoute(String)}/
 *       {@code moduleRoutes(EovaModConfig)} —— 它们是<b>活代码</b>：
 *       旧 {@code EovaConfig.java:298-300} 直接读 {@code getModConfigs()} 并
 *       {@code me.add(EovaModPlugin.moduleRoutes(mc))}。</li>
 *   <li><b>未 port</b>：{@code IPlugin} 生命周期（{@code start()}/{@code stop()}，即 mod jar 加载 +
 *       {@code new TableBuilder().build(v, DbKit.getConfig(k))}）。依据：旧 {@code EovaConfig.java:394}
 *       的 {@code plugins.add(new EovaModPlugin())} <b>已被注释</b> ⇒ 该生命周期在旧栈从不执行；
 *       且新栈没有 jfinal 的 {@code IPlugin}/{@code TableBuilder}/{@code DbKit}。
 *       逐项证据与待验收见 {@code NOT_PORTED_UNITS} 的对应条目。</li>
 * </ul>
 *
 * <p><b>逐条取自旧字节码：</b>{@code addRoute(String)} 的方法体<b>整行被注释</b>
 * （只留空方法）—— 属既有语义，原样保留；{@code moduleRoutes} 的路由 Key 规则
 * {@code "/" + GROUP() + key}、视图前缀 {@code "/_mod/" + groupKey} 与"先加路由再加拦截器"
 * 的顺序都属对外契约。</p>
 */
public class EovaModPlugin {

    // 当前启动Mod配置集
    private static List<EovaModConfig> modConfigs = new ArrayList<>();

    // 当前启动Mod路由集
    private static List<String> modRoutes = new ArrayList<>();

    /**
     * 取当前 Mod 配置集。
     *
     * @return 配置集
     */
    public static List<EovaModConfig> getModConfigs() {
        return modConfigs;
    }

    /**
     * 取当前 Mod 路由集。
     *
     * @return 路由集
     */
    public static List<String> getModRoutes() {
        return modRoutes;
    }

    /**
     * 登记 Mod 路由（旧实现方法体整行被注释，原样保留为空）。
     *
     * @param route 路由
     */
    public static void addRoute(String route) {
//        EovaModPlugin.modRoutes.add(route);
    }

    /**
     * 注册路由规则:/组织名/路由名
     *
     * @param module mod 配置
     * @return mod 路由表
     */
    public static EovaModRoutes moduleRoutes(EovaModConfig module) {
        // 子应用路由
        EovaModRoutes routes = new EovaModRoutes();
        // 个人或组织名
        String groupKey = module.GROUP();
        // mod在安装时会解压到/_mod 方便读取静态资源文件(开发时和运行时保持一致)
        routes.setBaseViewPath("/_mod/" + groupKey);

        EovaModRoute appRoute = new EovaModRoute();

        module.configRoute(appRoute);
        // 加载路由(路由Key强制加上用户名)
        for (String key : appRoute.getRouteMap().keySet()) {
            Class<? extends LegacyController> cs = appRoute.getRouteMap().get(key);
            String ctrlKey = String.format("/%s%s", groupKey, key);
            routes.add(ctrlKey, cs, key);
            // 登记Mod Ctrl key
            addRoute(ctrlKey);
        }
        // 加载路由拦截器
        for (LegacyInterceptor i : appRoute.getInterceptors()) {
            routes.addInterceptor(i);
        }
        return routes;
    }

}
