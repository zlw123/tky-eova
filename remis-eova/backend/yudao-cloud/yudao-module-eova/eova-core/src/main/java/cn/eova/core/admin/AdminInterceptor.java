/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.admin;

import cn.eova.common.base.BaseController;
import cn.eova.model.User;
import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInvocation;

/**
 * <p>ported from: cn.eova.core.admin.AdminInterceptor
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>后台管理拦截器（36 行）</li>
 * </ol>
 */
/**
 * 超级管理员拦截器
 * @author Jieven
 *
 */
public class AdminInterceptor implements LegacyInterceptor {

    @Override
    public void intercept(LegacyInvocation inv) {

        BaseController ctrl = (BaseController) inv.getController();
//		if (!EovaConfig.isDevMode) {
//			inv.getController().renderText("请开启开发者模式: devMode=true 编辑配置文件 app.config");
//			return;
//		}
        User user = ctrl.getUser();
        if (!user.isAdmin()) {
            inv.getController().renderText("无权限操作, 需要开发者权限!");
            return;
        }

        inv.invoke();
    }
}