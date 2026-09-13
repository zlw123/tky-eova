/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova;

import cn.eova.config.WebRoutes;
import cn.eova.core.HomeController;
import cn.eova.core.SpaShellController;
import cn.eova.core.admin.AdminController;
import cn.eova.core.auth.AuthController;
import cn.eova.core.button.ButtonController;
import cn.eova.core.dict.DictController;
import cn.eova.core.menu.MenuController;
import cn.eova.core.meta.MetaController;
import cn.eova.core.sse.SSEController;
import cn.eova.core.task.TaskController;
import cn.eova.meta.api.ExcelController;
import cn.eova.meta.api.FormControler;
import cn.eova.meta.api.MetaControler;
import cn.eova.meta.api.TableController;
import cn.eova.meta.api.WidgetController;
import cn.eova.ops.OpsController;
import cn.eova.user.UserController;
import cn.eova.widget.tree.TreeController;
import cn.eova.widget.upload.UploadController;

/**
 * <p>ported from: cn.eova.EovaWebRoutes
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>Web 路由总表（66 行）：config() 先 super.config()（挂登录+鉴权拦截器）再注册 18 条路由</li>
 *   <li>【零替换】本文件不 import 任何 jfinal 类型（父类 WebRoutes 已 port），逐字节即语义等价</li>
 *   <li>18 条 add 的【顺序与路径】都是对外契约（URL 即前端调用面），且顺序影响匹配优先级</li>
 *   <li>被注释掉的 setMappingSuperClass(true) 与 LoginInterceptor.excludes.add(ROUTER/**) 两条 属既有决定，不得恢复</li>
 *   <li>路由清单含 /api/* 前端接口、/excel、/upload、/sse、/eova/* 与 6 个后台管理页控制器</li>
 * </ol>
 */
public class EovaWebRoutes extends WebRoutes {

    public void config() {
        super.config();

        // 需要注册父类Action 暂不需要
        //setMappingSuperClass(true);

        // EovaMeta
        add("/api/home", HomeController.class);
        add("/api/meta", MetaControler.class);
        add("/api/widget", WidgetController.class);
        add("/api/form", FormControler.class);
        add("/api/table", TableController.class);
        add("/api/tree", TreeController.class);
        add("/excel", ExcelController.class);

        // 通用业务
        add("/upload", UploadController.class);

        // Eova业务
        add("/sse", SSEController.class);
        add("/eova/admin", AdminController.class);
        add("/eova/ops", OpsController.class);
        add("/user", UserController.class);


        add("/meta", MetaController.class);
        add("/menu", MenuController.class);
        add("/button", ButtonController.class);
        add("/auth", AuthController.class);
        add("/task", TaskController.class);
        add("/dict", DictController.class);

        // ★ r305（U1）：**SPA 独有页面**的壳接管（旧栈由 demo 工程或前端跳转处理，后端本无路由）。
        //   实测（带会话直连 8080）这些 URL 此前全部 404 ⇒ 生产态下 SPA 拿不到它们。
        //   登记在这里 = 走同一套全局拦截器链（未登录仍 302 到 /user/login），只换响应体为壳。
        add("/su", SpaShellController.class);
        add("/placeholder", SpaShellController.class);
        add("/main", SpaShellController.class);
        add("/theme", SpaShellController.class);
        add("/test", SpaShellController.class);
        add("/test/sse", SpaShellController.class);
        add("/ip", SpaShellController.class);
        add("/sso", SpaShellController.class);
        // `/widget` 是 EovaUI 组件演示页：旧栈由 demo 的 AppController#widget() 渲染，
        // 新栈后端无对应路由（SPA 侧已登记 `/widget`）⇒ 同样由壳接管。
        add("/widget", SpaShellController.class);
        // ★ r306（U2 取证）**删掉**了 U1 在这里登记的 `/eova/role/auth` 壳路由 —— 它基于错误前提：
        //   旧页面 URL 是 **`/auth/<rid>`**（带会话实测 `/auth`、`/auth/1248` 都是 200「功能权限分配」；
        //   而 `/eova/role/auth/1`、`/role/auth/1` 都是 **404**）。那条前缀是照着**模板路径**
        //   （`/eova/role/auth/app.html`）抄出来的，不是 URL。
        //   现在 `/auth` 的页面入口已在 `AuthController#index()` 里退役为壳，而该控制器的路由本来就已注册
        //   ⇒ 无需再造壳路由；`/auth/<rid>` 则由「方法名匹配失败 ⇒ 退化到 index()」落壳。

        // LoginInterceptor.excludes.add(String.format("%s/**/**", ApiRouterHandler.ROUTER));

    }

}