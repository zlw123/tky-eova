/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.type;

import java.math.BigDecimal;

/**
 * <p>ported from: cn.eova.core.type.Test
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>旧实现自带的调试类，原样保留</li>
 * </ol>
 */
public class Test {

    public static void main(String[] args) {
        String num = "9.8888888888888888888";
        System.out.println(Float.valueOf(num)); // 输出:99.888885 				8,8-2
        System.out.println(Double.valueOf(num)); // 输出:99.88888888888889		16,16-2
        System.out.println(new BigDecimal(num)); // 输出:99.8888888888888888888	支持最大
    }

}