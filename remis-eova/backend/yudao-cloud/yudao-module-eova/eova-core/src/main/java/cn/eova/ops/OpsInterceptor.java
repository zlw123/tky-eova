/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.ops;

import cn.eova.common.base.BaseController;
import cn.eova.tools.x;
import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInvocation;

/**
 * <p>ported from: cn.eova.ops.OpsInterceptor
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>运维拦截器（41 行）</li>
 * </ol>
 */
/**
 * 运维服务通用逻辑
 *
 * @author Jieven
 */
public class OpsInterceptor implements LegacyInterceptor {

    @Override
    public void intercept(LegacyInvocation inv) {
        BaseController ctrl = (BaseController) inv.getController();

        String token = ctrl.get("token");
        if (x.isEmpty(token)) {
            ctrl.NO("运维令牌不存在");
            return;
        }
        String opsToken = x.conf.get("eova.ops.token");
        if (x.isEmpty(opsToken)) {
            ctrl.NO("运维令牌未配置, 无法使用运维服务");
            return;
        }
        if (!token.equals(opsToken)) {
            ctrl.NO("运维令牌错误");
            return;
        }

        inv.invoke();
    }
}