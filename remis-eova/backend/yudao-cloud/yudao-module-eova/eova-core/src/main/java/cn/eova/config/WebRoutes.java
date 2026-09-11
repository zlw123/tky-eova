/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.config;

import cn.eova.auth.AuthInterceptor;
import cn.eova.interceptor.LoginInterceptor;
import cn.eova.compat.jfinal.config.LegacyRoutes;

/**
 * <p>ported from: cn.eova.config.WebRoutes
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>Web 路由拦截器装配（21 行）：config() 里按序挂 LoginInterceptor、AuthInterceptor</li>
 *   <li>【已声明适配 1】com.jfinal.config.Routes -> cn.eova.compat.jfinal.config.LegacyRoutes</li>
 *   <li>顺序即语义：LoginInterceptor 必须先于 AuthInterceptor（后者直接读前者的 excludes，且依赖已登录用户），不得调换</li>
 * </ol>
 */
public class WebRoutes extends LegacyRoutes {

    public void config() {
        // 登录验证
        addInterceptor(new LoginInterceptor());
        // 权限验证拦截
        addInterceptor(new AuthInterceptor());
    }

}