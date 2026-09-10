/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.util;

import cn.eova.common.utils.jfinal.RecordUtil;
import cn.eova.tools.x;

import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import cn.eova.compat.jfinal.kit.LegacyKv;

/**
 * <p>ported from: cn.eova.common.utils.util.JsonUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>JSON<->Kv 转换；实测 parse 走 fastjson（MixedJson.parse），故适配为 LegacyJsonKit.parse</li>
 * </ol>
 */
/**
 * 原JsonUtil 相关方法移动到RecordUtil parseArray parseObject
 * @see RecordUtil
 * @author Jieven
 *
 */
public class JsonUtil {
    /**
     * JSON转KV
     * @param json
     * @return
     */
    public static LegacyKv toKv(String json) {
        // 空KV减少异常率
        if (x.isEmpty(json)) {
            return LegacyKv.create();
        }
        return LegacyJsonKit.parse(json, LegacyKv.class);
    }
}