/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin.druid;

import jakarta.servlet.http.HttpServletRequest;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.plugin.druid.IDruidStatViewAuth} 的等价接缝。
 *
 * <p>ported from: com.jfinal.plugin.druid.IDruidStatViewAuth（jfinal 5.2.6 制品）
 *
 * <p>单方法接口：返回 true 才允许访问 Druid 监控页。</p>
 */
public interface LegacyDruidStatViewAuth {

    /**
     * 是否允许访问监控页。
     *
     * @param request 请求
     * @return 是否允许
     */
    boolean isPermitted(HttpServletRequest request);

}
