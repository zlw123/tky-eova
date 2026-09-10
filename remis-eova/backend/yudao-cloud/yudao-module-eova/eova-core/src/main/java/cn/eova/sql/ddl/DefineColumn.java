/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.sql.ddl;

import cn.eova.sql.ddl.dialect.EovaType;

/**
 * 字段定义（DDL 用）。
 *
 * <p>ported from: cn.eova.sql.ddl.DefineColumn
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>字段默认值：{@code cn=""}、{@code decimal=0}、{@code defaultValue=null}、
 *       {@code isNull=true}、{@code isAuto=false}；</li>
 *   <li>字符串构造器对 type 调 {@code toUpperCase()} 后经 {@code EovaType.getEnum} 解析，
 *       <b>type 为 null 时抛 NPE</b>（既有异常语义，不"顺手"加保护）；</li>
 *   <li>{@code getDefault()} 方法名与字段名 {@code defaultValue} 不一致 —— 契约原样保留。</li>
 * </ol>
 */
public class DefineColumn {

    private String cn = "";
    private String en;
    private EovaType type;
    private int size;
    private int decimal = 0;
    private String defaultValue = null;
    private boolean isNull = true;
    private boolean isAuto = false;
    //	private boolean isPrimary = false;

    /**
     * 定义字段
     *
     * @param en      字段名
     * @param type    字段类型（字符串形式，内部转大写后解析为 EovaType）
     * @param size    字段长度
     * @param decimal 字段精度
     * @param cn      字段备注
     */
    public DefineColumn(String en, String type, int size, int decimal, String cn) {
        super();
        this.en = en;
        this.cn = cn;
        this.type = EovaType.getEnum(type.toUpperCase());
        this.size = size;
        this.decimal = decimal;
    }

    /**
     * 定义字段
     * @param en 字段名
     * @param type 字段类型
     * @param size 字段长度
     * @param decimal 字段精度
     * @param cn 字段备注
     */
    public DefineColumn(String en, EovaType type, int size, int decimal, String cn) {
        super();
        this.en = en;
        this.cn = cn;
        this.type = type;
        this.size = size;
        this.decimal = decimal;
    }

    //	public DbColumn isPrimary() {
    //		this.isPrimary = true;
    //		return this;
    //	}
    //
    /**
     * 标记自增（链式）
     */
    public DefineColumn auto() {
        this.isAuto = true;
        return this;
    }

    /**
     * 标记非空（链式）
     */
    public DefineColumn notNull() {
        this.isNull = false;
        return this;
    }

    /**
     * 设置默认值（链式）
     */
    public DefineColumn setDefault(String defaultvalue) {
        this.defaultValue = defaultvalue;
        return this;
    }

    /**
     * 取字段类型
     */
    public EovaType getType() {
        return type;
    }

    /**
     * 设置字段类型
     */
    public void setType(EovaType type) {
        this.type = type;
    }

    /**
     * 取字段备注
     */
    public String getCn() {
        return cn;
    }

    /**
     * 设置字段备注
     */
    public void setCn(String cn) {
        this.cn = cn;
    }

    /**
     * 取字段名
     */
    public String getEn() {
        return en;
    }

    /**
     * 设置字段名
     */
    public void setEn(String en) {
        this.en = en;
    }

    /**
     * 取字段长度
     */
    public int getSize() {
        return size;
    }

    /**
     * 设置字段长度
     */
    public void setSize(int size) {
        this.size = size;
    }

    /**
     * 取字段精度
     */
    public int getDecimal() {
        return decimal;
    }

    /**
     * 设置字段精度
     */
    public void setDecimal(int decimal) {
        this.decimal = decimal;
    }

    /**
     * 是否允许为空
     */
    public boolean isNull() {
        return isNull;
    }

    /**
     * 取默认值（方法名与字段名 defaultValue 不一致，为原样保留）
     */
    public String getDefault() {
        return defaultValue;
    }

    /**
     * 是否自增
     */
    public boolean isAuto() {
        return isAuto;
    }

    /**
     * 设置是否允许为空
     */
    public void setNull(boolean isNull) {
        this.isNull = isNull;
    }

    /**
     * 设置是否自增
     */
    public void setAuto(boolean isAuto) {
        this.isAuto = isAuto;
    }

}
