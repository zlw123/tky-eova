/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova;

import cn.eova.config.WebRoutes;
import cn.eova.core.HomeController;
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

        // LoginInterceptor.excludes.add(String.format("%s/**/**", ApiRouterHandler.ROUTER));

    }

}