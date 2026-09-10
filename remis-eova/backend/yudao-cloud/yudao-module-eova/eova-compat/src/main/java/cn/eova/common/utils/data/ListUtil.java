/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.data;

import java.util.ArrayList;
import java.util.List;

/**
 * List 工具。
 *
 * <p>ported from: cn.eova.common.utils.data.ListUtil
 * <br>source revision: meta-bova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port。
 *
 * <p><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>入参 list 为<b>空列表时返回 {@code null}</b>（而非空列表）；</li>
 *   <li>元素为 {@code null} 时<b>跳过</b>（不占位）；</li>
 *   <li>{@code cs} 非 Integer/Long/Float/Double 时<b>不抛异常</b>，而是丢弃全部元素后返回空列表；</li>
 *   <li>返回类型为未检查转换 {@code (List<T>)} —— 原样保留。</li>
 * </ol>
 */
public class ListUtil {

    /**
     * List&lt;Object> 转常用数值类型
     * @param list
     * @param cs
     */
    public static <T> List<T> toNumber(List<Object> list, Class<? extends Number> cs) {
        if (list.isEmpty()) {
            return null;
        }
        List<Object> t = new ArrayList<>();
        for (Object o : list) {
            if (o == null)
                continue;
            String s = o.toString();
            if (cs == Integer.class) {
                t.add(Integer.valueOf(s));
            } else if (cs == Long.class) {
                t.add(Long.valueOf(s));
            } else if (cs == Float.class) {
                t.add(Float.valueOf(s));
            } else if (cs == Double.class) {
                t.add(Double.valueOf(s));
            }
        }
        return (List<T>) t;
    }

}
