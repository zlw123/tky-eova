/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core;

import cn.eova.common.utils.web.WebUtil;

/**
 * <p>ported from: cn.eova.meta.AppController（demo 工程）
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br><b>旧栈结构（本类存在的理由）</b>：demo 的 {@code AppConfig#route()} 用
 * {@code me.add(EovaConfig.EOVA_INDEX, AppController.class)} **覆盖根路由**，而它是
 * {@code extends IndexController} 并多出 {@code main()}/{@code widget()}/{@code ip()}/{@code sso()} 四个动作；
 * core 的 {@code EovaConfig} 有守卫 {@code if (routeList.noneMatch(path == EOVA_INDEX))} ⇒ 跳过 core 的
 * {@code IndexController}。新栈没有 demo 模块 ⇒ 等价做法是在 {@code EovaWebRoutes} 里登记同名根路由，
 * 由本类承载那几个 demo 页面动作（守卫同样跳过 core {@code IndexController}）。</p>
 *
 * <p><b>为什么必须是子类、不能把方法加到 {@code IndexController}</b>：core 的 {@code AppController}
 * 也 {@code extends IndexController}（`/app` 路由）⇒ 加到父类会让 {@code /app/main}、{@code /app/ip}
 * 变成**可达动作**，而旧栈实测那两个 URL 都是 **500**（动作名找不到 ⇒ 退化到 {@code AppController#index()}）。
 * 本类只挂在根路由上 ⇒ {@code /app/**} 的动作面与旧栈一致。</p>
 *
 * <p><b>只承载仍在用的两个 demo 动作</b>：{@code main()}（EovaUI 主题风格演示页 —— SPA 首页把它当 iframe 内容）
 * 与 {@code ip()}（纯文本 IP 端点）。{@code widget()} 与 {@code sso()} **不 port**：前者由 SPA 接管
 * （`Widget.vue` 已迁移、`/widget` 是直接导航页），后者在旧栈本就是 **500 死页**
 * （模板 {@code _view/login/login.html} 全仓不存在）。</p>
 */
public class DemoPageController extends IndexController {

    /**
     * EovaUI 主题风格演示页（**iframe 内容**，不是顶层导航页）。
     *
     * <p>port 自 {@code cn.eova.meta.AppController#main()}，渲染 {@code /_view/theme/index.html}
     * （非 {@code /eova/} 前缀 ⇒ **不经 {@code BaseController.render} 的 {@code _view} 重写**）。</p>
     *
     * <p><b>为什么必须由后端渲染、不能交给 SPA</b>（r307 U3 取证）：SPA 首页用
     * {@code <iframe :src="m.link">} 渲染页签，而**初始页签的 link 就是 {@code /main}**
     * （{@code utils/tab.ts:38}；旧首页 {@code eova/_view/index/index.js:21} 同款）
     * ⇒ 若 {@code /main} 返回 SPA 壳，iframe 里装的就是 SPA 自己（U1 曾按口径④如此处理，
     * 结果首页 iframe 显示占位页 —— U3 按实测改回）。</p>
     *
     * <p>旧栈带会话实测：{@code /main} → 200 + {@code <title>EovaUI主题风格</title>}；
     * 其脚本 {@code /_view/theme/index.js} 旧栈 200（新栈曾 404，由静态空间 {@code /_view/**} 补齐）。</p>
     */
    public void main() {
        render("/_view/theme/index.html");
    }

    /**
     * 取客户端真实 IP（**纯文本端点**，不是页面）。
     *
     * <p>port 自 {@code cn.eova.meta.AppController#ip()}：旧实现先把 IP 打到 stdout 再 {@code renderText}
     * —— 两条都保留（调试语义属既有行为）。</p>
     *
     * <p>旧栈带会话实测：{@code /ip} → 200 + {@code text/plain}（正文就是 IP，如 {@code 127.0.0.1}）。
     * U1 曾按口径④把它当页面接管成 SPA 壳（HTML 占位），U3 按实测改回文本端点。</p>
     */
    public void ip() {
        String ip = WebUtil.getRealIp(getRequest());
        System.out.println(ip);
        renderText(ip);
    }
}
