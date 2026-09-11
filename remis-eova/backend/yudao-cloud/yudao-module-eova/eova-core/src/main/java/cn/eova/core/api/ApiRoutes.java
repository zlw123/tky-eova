/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.api;

import cn.eova.compat.jfinal.config.LegacyRoutes;
import cn.eova.compat.jfinal.core.LegacyController;

/**
 * <p>ported from: cn.eova.core.api.ApiRoutes
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>API 路由容器（23 行）：只挂 ApiInterceptor，并把所有 add 的路径加上 /router 前缀</li>
 *   <li>【已声明适配 1】com.jfinal.config.Routes -> cn.eova.compat.jfinal.config.LegacyRoutes</li>
 *   <li>【已声明适配 2】com.jfinal.core.Controller -> cn.eova.compat.jfinal.core.LegacyController</li>
 *   <li>add 的重写（前缀拼接）属契约：所有注册到该容器的控制器都被强制置于 /router 之下</li>
 * </ol>
 */
public class ApiRoutes extends LegacyRoutes {

    public void config() {
        addInterceptor(new ApiInterceptor());
    }

    @Override
    public LegacyRoutes add(String controllerPath, Class<? extends LegacyController> controllerClass) {
        return super.add(ApiRouterHandler.ROUTER + controllerPath, controllerClass);
    }


}