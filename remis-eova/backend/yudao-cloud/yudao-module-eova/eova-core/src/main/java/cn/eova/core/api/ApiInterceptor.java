/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.api;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInvocation;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.jfinal.kit.LegacyLogKit;

/**
 * <p>ported from: cn.eova.core.api.ApiInterceptor
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>API 拦截器（34 行）：implements Interceptor</li>
 *   <li>【已声明适配】Interceptor -> LegacyInterceptor；Invocation -> LegacyInvocation；Controller -> LegacyController；LogKit -> LegacyLogKit</li>
 * </ol>
 */
/**
 * API 统一处理
 * 1.异常处理
 *
 * @author Jieven
 */
public class ApiInterceptor implements LegacyInterceptor {

    @Override
    public void intercept(LegacyInvocation inv) {
        LegacyController ctrl = inv.getController();

        try {
            inv.invoke();
        } catch (Exception e) {
            LegacyLogKit.error(e.getMessage(), e);
            // 统一返回格式
            ctrl.renderJson(ApiResponse.NO("服务内部错误"));
        }
    }

}