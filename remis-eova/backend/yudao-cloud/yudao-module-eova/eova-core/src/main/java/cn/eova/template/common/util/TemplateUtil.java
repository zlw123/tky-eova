/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.template.common.util;

import cn.eova.tools.x;
import cn.eova.common.utils.io.ClassUtil;
import cn.eova.common.utils.util.ExceptionUtil;
import cn.eova.common.utils.xx;
import cn.eova.config.EovaConfig;
import cn.eova.model.Button;
import cn.eova.model.MetaField;
import cn.eova.compat.jfinal.kit.LegacyLogKit;

/**
 * <p>ported from: cn.eova.template.common.util.TemplateUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>模板公共工具（93 行）：初始化元对象业务拦截器（含默认拦截器回落）</li>
 *   <li>【已声明适配 1】com.jfinal.kit.LogKit -> LegacyLogKit</li>
 *   <li>依赖 EovaConfig.getDefaultMetaObjectIntercept()（已按旧源码逐字补入 stub）</li>
 * </ol>
 */
public class TemplateUtil {

    /**
     * 值的类型转换
     *
     * @param item 元字段
     * @param value
     * @return
     */
    public static Object buildValue(MetaField item, Object value) {
        if (x.isEmpty(value)) {
            return value;
        }
        // 获取字符串忽略两端空格
        String val = value.toString().trim();
        // 控件类型
        String type = item.getStr("type");
        // 数据类型
        // String dataType = item.getDataTypeName();
        // 布尔框需要特转换值
        if (type.equals(MetaField.TYPE_BOOL)) {
            if (xx.isTrue(val)) {
                return 1;
            } else {
                return 0;
            }
        }
        // JSON框去掉空格
        else if (type.equals(MetaField.TYPE_JSON)) {
            // 去换行 去Tab 去空格*4
            return val = val.replaceAll("\t|\r|\n|    ", "");
        }

        return val;
    }

    /**
     * 构建异常信息为HTML
     *
     * @param e
     * @return
     */
    public static String buildException(Exception e) {
        LegacyLogKit.error("Eova common operation exception:" + e.getMessage(), e);

        String type = e.getClass().getName();
        type = type.equals("java.lang.Exception") ? e.getMessage() : type;
        return "<br/><p style=\"color:red\" title=\"" + ExceptionUtil.getStackTrace(e) + "\">" + type + " [查看异常]</p>";
    }

    /**
     * 初始化元对象拦截器
     *
     * @param bizIntercept
     * @return
     * @throws Exception
     */
    public static <T> T initMetaObjectIntercept(String bizIntercept) throws Exception {
        Object o = ClassUtil.newClass(bizIntercept);
        if (o == null) {
            // 命中默认拦截器(如果有)
            return (T) EovaConfig.getDefaultMetaObjectIntercept();
        }
        return (T) o;
    }

    /**
     * 默认查询按钮
     * @return
     */
    public static Button getQueryButton() {
        Button btn = new Button();
        btn.set("name", "查询");
        btn.set("ui", "query");
        return btn;
    }
}