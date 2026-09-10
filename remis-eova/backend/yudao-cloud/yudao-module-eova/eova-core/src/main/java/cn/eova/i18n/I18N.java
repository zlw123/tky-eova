/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.i18n;

import java.util.HashMap;

import cn.eova.tools.x;

/**
 * <p>ported from: cn.eova.i18n.I18N
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>I18N 覆写了 get：无词条或译文为空时返回【键本身】而非 null —— 与 Map 约定不同，属既有行为</li>
 * </ol>
 */
public class I18N extends HashMap<String, String> {

    private static final long serialVersionUID = 1L;

    public String get(String key) {
        String s = super.get(key);
        if (x.isEmpty(s)) {
            return key;
        }
        return s;
    }

} 