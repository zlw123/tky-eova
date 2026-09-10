/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.sql.ddl.dialect;

/**
 * EOVA数据类型
 * 约定几种通用类型, 基本可以满足日常使用, 屏蔽多DB个性化影响.
 *
 * <p>ported from: cn.eova.sql.ddl.dialect.EovaType
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port。枚举<b>声明顺序即 ordinal</b>，属对外契约，不得调整。
 */
public enum EovaType {

    DATE("DATE", "日期"),
    TIME("TIME", "时间"),
    DATETIME("DATETIME", "时间"),
    VARCHAR("VARCHAR", "字符串"),
    CHAR("CHAR", "字符"),
    NUMBER("NUMBER", "数字"),
    BOOL("BOOL", "布尔");

    private String val;
    private String txt;

    /**
     * 构造：绑定类型代码与中文说明
     */
    EovaType(String val, String txt) {
        this.val = val;
        this.txt = txt;
    }

    /**
     * 取类型代码
     */
    public String getVal() {
        return this.val;
    }

    /**
     * 取中文说明
     */
    public String getTxt() {
        return this.txt;
    }

    /**
     * 按类型代码反查枚举；未命中返回 null
     */
    public static EovaType getEnum(String val) {
        EovaType[] values = EovaType.values();
        for (EovaType v : values) {
            if (v.getVal().equals(val)) {
                return v;
            }
        }
        return null;
    }
}
