/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.mod;

import cn.eova.compat.jfinal.config.LegacyRoutes;

/**
 * <p>ported from: cn.eova.mod.EovaModRoutes
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>模块路由容器（14 行，空 config）—— EovaModPlugin.moduleRoutes 构造它</li>
 *   <li>【已声明适配 1】com.jfinal.config.Routes -> cn.eova.compat.jfinal.config.LegacyRoutes（第 61 轮落地的接缝）</li>
 * </ol>
 */
public class EovaModRoutes extends LegacyRoutes {
    public void config() {

    }
}
