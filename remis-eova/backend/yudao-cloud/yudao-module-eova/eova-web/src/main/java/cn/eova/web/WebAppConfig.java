/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import cn.eova.compat.jfinal.config.LegacyRoutes;
import cn.eova.config.EovaConfig;
import cn.eova.core.DemoPageController;

/**
 * <p>ported from: cn.eova.meta.AppConfig（demo 工程）
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br><b>为什么需要这个子类</b>：旧栈里 demo 的 {@code AppConfig extends EovaConfig} 覆写了扩展点
 * {@code route(Routes me)}，用 {@code me.add(EovaConfig.EOVA_INDEX, AppController.class)}
 * **覆盖根路由**（demo 的 {@code AppController extends IndexController}，多出
 * {@code main()}/{@code widget()}/{@code ip()}/{@code sso()}）；core 的 {@code EovaConfig} 随后有守卫
 * {@code if (routeList.noneMatch(path == EOVA_INDEX))} ⇒ 因此**跳过** core {@code IndexController}。
 * 新栈没有 demo 模块，但同样的两个 demo 页面 URL（{@code /main}、{@code /ip}）必须由后端供给 ⇒
 * 用本类复现"宿主覆盖根路由"这一结构。</p>
 *
 * <p><b>★ 为什么不把这条 {@code add("/", …)} 写进 {@code EovaWebRoutes}</b>（r307 真实踩坑）：
 * {@code EovaConfig} 的守卫调的是 {@code me.getRouteItemList()}，它**只包含直接 add 的路由**，
 * 看不到子 {@code Routes}（如 {@code EovaWebRoutes}）里的条目 ⇒ 写在那里守卫会照旧再注册一条
 * {@code IndexController}，于是根路由**重复两条**，而分发器按"路径长度降序 + 稳定排序"取**先出现**的那条
 * ⇒ 实际生效的是 {@code IndexController}，{@code /main} 退化到 {@code index()}`（返回 SPA 壳），
 * 表现为"代码看着对、URL 却是壳"。判据：`CaptchaAndUserFamilyGoldenTest` 断言
 * {@code EovaWebRoutes} **不得**含 `/`，根路由由本类注册。</p>
 *
 * <p>旧栈 demo 还注册了 {@code /test}（{@code AppRoutes}）等，那些 URL 现由 SPA 供给或由壳接管
 * （见 `EovaWebRoutes` 的壳路由段），故本类不重复登记。</p>
 */
public class WebAppConfig extends EovaConfig {

    /**
     * 宿主路由扩展：覆盖根路由，承载 demo 页面族（{@code /main}、{@code /ip}）。
     *
     * <p>必须在守卫之前、且用**直接 add**（子 {@code Routes} 里的条目守卫看不见 —— 见类注释）。</p>
     *
     * @param me 路由表（与旧 {@code Routes} 同语义）
     */
    @Override
    protected void route(LegacyRoutes me) {
        me.add(EOVA_INDEX, DemoPageController.class);
    }
}
