/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.util;

/**
 * 系统信息工具。
 *
 * <p>ported from: cn.eova.common.utils.util.SysUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port。
 */
public class SysUtil {
    /**
     * 是否为Windows系统
     *
     * @return
     */
    public static boolean isWindows() {
        String osName = System.getProperty("os.name");
        return osName.contains("Windows");
    }
}
