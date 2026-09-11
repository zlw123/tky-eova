/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova;

import cn.eova.api.sys.DemoApi;
import cn.eova.api.sys.RoleApi;
import cn.eova.api.sys.UserApi;
import cn.eova.core.api.ApiRoutes;

/**
 * <p>ported from: cn.eova.EovaApiRoutes
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>Eova 开放接口路由（28 行）：/eova/demo、/eova/role、/eova/user 三条</li>
 *   <li>【零替换】本文件不 import 任何 jfinal 类型（父类 ApiRoutes 已 port），逐字节即语义等价</li>
 *   <li>三段 add 的顺序与路径属对外契约（URL 变更即破坏企业侧调用），不得调整</li>
 *   <li>父类 ApiRoutes.add 会统一加 ApiRouterHandler.ROUTER 前缀，故此处写的是相对路径</li>
 * </ol>
 */
public class EovaApiRoutes extends ApiRoutes {

    public void config() {
        super.config();

        // Eova 演示接口
        add("/eova/demo", DemoApi.class);

        // Eova 角色接口
        add("/eova/role", RoleApi.class);
        // Eova 用户接口
        add("/eova/user", UserApi.class);

    }

}