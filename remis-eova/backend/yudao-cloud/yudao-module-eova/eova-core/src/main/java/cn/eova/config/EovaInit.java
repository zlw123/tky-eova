/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.config;

import cn.eova.tools.x;
import cn.eova.core.api.ApiRouterHandler;
import cn.eova.compat.jfinal.kit.LegacyLogKit;

/**
 * <p>ported from: cn.eova.config.EovaInit
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>启动初始化（34 行）：注册 ApiRouterHandler 等</li>
 *   <li>【已声明适配 1】com.jfinal.kit.LogKit -> LegacyLogKit</li>
 * </ol>
 */
public class EovaInit {
    /**
     * 初始化Eova Api 应用配置
     */
    static void initEovaApiAppCofing() {
        if (!ApiRouterHandler.getAppConfig().isEmpty()) {
            return;
        }

        String appsConfig = x.conf.get("eova.api.apps");
        if (x.isEmpty(appsConfig)) {
            LegacyLogKit.debug("eova.api.apps 为空, 可能无法使用API");
            return;
        }
        String[] apps = appsConfig.split(";");
        for (String app : apps) {
            String[] ss = app.split(":");
            ApiRouterHandler.addAppConfig(ss[0], ss[1]);
        }

    }

}