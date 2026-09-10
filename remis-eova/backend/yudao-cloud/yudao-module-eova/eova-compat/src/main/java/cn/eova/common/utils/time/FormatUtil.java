/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.time;

import java.text.SimpleDateFormat;

/**
 * 格式化时间
 *
 * @author Jieven
 * @date 2013-10-21
 */
/**
 * <p>ported from: cn.eova.common.utils.time.FormatUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>纯 JDK 实现；{@code format(Object, style)} 对非 Date 入参的行为原样保留</li>
 *   <li>类内保留原 {@code main} 调试方法</li>
 * </ol>
 */
public class FormatUtil {

    public final static String YYYY = "yyyy";
    public final static String MM = "MM";
    public final static String DD = "dd";
    public final static String YYYY_MM_DD = "yyyy-MM-dd";
    public final static String YYYY_MM = "yyyy-MM";
    public final static String HH_MM_SS = "HH:mm:ss";
    public final static String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    /**
     * 格式化Time为String
     * @param time 时间(Data/Timestamp)
     * @param style 格式
     * @return
     */
    public static String format(Object time, String style) {
        return new SimpleDateFormat(style).format(time);
    }

    /**
     * 格式化当前时间
     * @param style 格式
     * @return
     */
    public static String formatNow(String style) {
        return new SimpleDateFormat(style).format(System.currentTimeMillis());
    }

    public static void main(String[] args) {
        {
            String s = format(TimestampUtil.getNow(), YYYY_MM_DD);
            System.out.println(s);
        }
        {
            String s = format(DateUtil.getCurrDate(), YYYY_MM_DD);
            System.out.println(s);
        }
        {
            String s = formatNow(YYYY_MM_DD);
            System.out.println(s);
        }
    }
}