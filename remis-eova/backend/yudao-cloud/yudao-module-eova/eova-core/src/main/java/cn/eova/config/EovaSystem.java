/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.config;

import cn.eova.compat.jfinal.server.LegacyServerHandle;

/**
 * <p>ported from: cn.eova.config.EovaSystem
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>全局系统配置（23 行）：只有一个 public static 的宿主服务句柄字段</li>
 *   <li>【已声明适配 1】com.jfinal.server.undertow.UndertowServer -> cn.eova.compat.jfinal.server.LegacyServerHandle（第 66 轮新增接缝）</li>
 *   <li>字段默认 null 属契约：新宿主未注入时 isServer()==false，restart() 走'未配置'分支</li>
 * </ol>
 */
/**
 * 全局系统配置
 * 启动时由主ClassLoader负责加载
 * @author Jieven
 *
 */
public class EovaSystem {

    /**
     * 当前启动服务全局共享
     */
    public static LegacyServerHandle server = null;

}