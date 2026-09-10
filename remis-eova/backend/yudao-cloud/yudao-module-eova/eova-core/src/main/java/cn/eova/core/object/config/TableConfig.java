/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.object.config;

import com.alibaba.fastjson.annotation.JSONField;

//@JSONType(orders={"whereField","paramField"})
/**
 * <p>ported from: cn.eova.core.object.config.TableConfig
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>表格配置 bean；@JSONField 注解与字段名属契约（fastjson 反序列化依赖它）</li>
 * </ol>
 */
public class TableConfig {

    // where 条件字段
    @JSONField(ordinal = 1)
    private String whereField;
    // where 条件字段的值(必须是视图显示列)
    @JSONField(ordinal = 2)
    private String paramField;

    public String getWhereField() {
        return whereField;
    }

    public void setWhereField(String whereField) {
        this.whereField = whereField;
    }

    public String getParamField() {
        return paramField;
    }

    public void setParamField(String paramField) {
        this.paramField = paramField;
    }

}