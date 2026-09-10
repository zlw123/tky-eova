/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.sql.ddl;

import java.util.List;

/**
 * 表定义（DDL 用）。
 *
 * <p>ported from: cn.eova.sql.ddl.DefineTable
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br><b>刻意保留：</b>字段名为 {@code cols}，而访问器名为 {@code getFields}/{@code setFields}
 * （非 getCols/setCols）—— 属对外契约，改名即破坏调用方。
 */
public class DefineTable {

    public static final String NUMBER = "number";
    public static final String STRING = "string";
    public static final String DATE = "date";
    public static final String DATETIME = "datetime";
    public static final String TIMESTAMP = "timestamp";

    private String cn;
    private String en;
    private String pk;
    private List<DefineColumn> cols;

    /**
     * 构造表定义
     *
     * @param cn   中文名（表注释）
     * @param en   英文名（表名）
     * @param pk   主键字段
     * @param cols 字段列表
     */
    public DefineTable(String cn, String en, String pk, List<DefineColumn> cols) {
        super();
        this.cn = cn;
        this.en = en;
        this.pk = pk;
        this.cols = cols;
    }

    /**
     * 取中文名
     */
    public String getCn() {
        return cn;
    }

    /**
     * 设置中文名
     */
    public void setCn(String cn) {
        this.cn = cn;
    }

    /**
     * 取英文名（表名）
     */
    public String getEn() {
        return en;
    }

    /**
     * 设置英文名（表名）
     */
    public void setEn(String en) {
        this.en = en;
    }

    /**
     * 取主键字段
     */
    public String getPk() {
        return pk;
    }

    /**
     * 设置主键字段
     */
    public void setPk(String pk) {
        this.pk = pk;
    }

    /**
     * 取字段列表（字段内部名为 cols）
     */
    public List<DefineColumn> getFields() {
        return cols;
    }

    /**
     * 设置字段列表（字段内部名为 cols）
     */
    public void setFields(List<DefineColumn> fields) {
        this.cols = fields;
    }

}
