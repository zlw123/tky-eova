/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.mod.emi;

import cn.eova.common.utils.io.ClassUtil;

/**
 * <p>ported from: cn.eova.mod.emi.EMILoader
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>EMI 加载器（34 行）：借 ClassUtil 扫描 Mod 包</li>
 *   <li>宿主依赖仅 cn.eova.common.utils.io.ClassUtil —— 【无需任何底座替换】，天然逐字节 port</li>
 * </ol>
 */
/**
 * Eova Mod Invoke Loader
 * @author Jieven
 */
public class EMILoader {

    public static EMI load(String group, String code, String className) {
        try {
            return (EMI) ClassUtil.newClass(String.format("com.eova.mod.%s.%s.%s", group, code, className));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <T> T instance(String group, String code, String className) {
        try {
            return (T) ClassUtil.newClass(String.format("com.eova.mod.%s.%s.%s", group, code, className));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}