/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.meta;

import cn.eova.tools.x;
import com.alibaba.druid.DbType;
import cn.eova.db.EovaGateways;
import com.alibaba.fastjson.JSONObject;
import cn.eova.common.Ds;
import cn.eova.config.EovaDataSource;
import cn.eova.compat.jfinal.kit.LegacyLogKit;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.core.meta.ColumnMeta
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>列元数据：字段名/类型/字典/备注等；EovaDataSource.getDbType 决定 dbType 分支</li>
 *   <li>自动生成字典时用 Db.use(ds).save(table, record) 写入字典表（Db 级 save，非 Model.save）</li>
 * </ol>
 */
public class ColumnMeta {

    public String ds;                // 数据源名
    public String table;            // 数据表名

    public int position;            // 序号
    public String name;                // 字段名

    public int dataType;            // 数据类型
    public String dataTypeName;        // 数据类型名称
    public int dataSize;            // 整数位长度
    public int dataDecimal;            // 小数位长度

    public boolean isNull;            // 是否允许空值
    public boolean isAuto;            // 是否自增

    public String defaultValue;        // 默认值
    public String remarks;            // 注释

    public ColumnMeta(String ds, String table, JSONObject o) {
        this.ds = ds;
        this.table = table;

        this.position = o.getIntValue("ORDINAL_POSITION");
        this.name = o.getString("COLUMN_NAME").toLowerCase();// G001 表名+主键 小写

        this.dataType = o.getIntValue("DATA_TYPE");
        this.dataTypeName = o.getString("TYPE_NAME");
        this.dataSize = o.getIntValue("COLUMN_SIZE");
        this.dataDecimal = o.getIntValue("DECIMAL_DIGITS");

        this.isNull = "YES".equalsIgnoreCase(o.getString("IS_NULLABLE")) ? true : false;

        this.defaultValue = buildDefault(o.getString("COLUMN_DEF"));
        this.isAuto = "YES".equalsIgnoreCase(o.getString("IS_AUTOINCREMENT")) ? true : false;
        // Oracle 和 PGSQL 根据默认值判定是否为自增字段
        if (defaultValue != null && defaultValue.contains("nextval")) {
            this.isAuto = true;
        }

        this.remarks = o.getString("REMARKS");
        // 月读：自动处理注释
        buildRemarks();
    }

    private String buildDefault(String s) {
        if (x.isEmpty(s)) {
            return "";
        }
        if (EovaDataSource.getDbType(ds) == DbType.postgresql) {
            // 默认值存在类型转换符的处理
            if (s.contains("::")) {
                try {
                    // nextval('seq_product'::regclass)
                    if (s.startsWith("nextval")) {
                        // 自增 新增应禁用
                        return "";
                    }

                    String[] ss = s.split("::");
                    String val = ss[0];

                    // NULL::character varying,
                    if (val.equalsIgnoreCase("NULL")) {
                        return "";
                    }
                    // 'http://www..com'::character varying
                    if (val.startsWith("'") && val.endsWith("'")) {
                        val = x.str.delStart(val, "'");
                        val = x.str.delEnd(val, "'");
                        return val;
                    }
                } catch (Exception e) {
                    System.err.print("Postgresql 导入元字段, 处理默认数据异常:" + e.getMessage());
                    return "";
                }
            }
        }

        return s;
    }

    /**
     * 自动根据注释解析列名和字典
     *
     * @param remarks 注释
     * @param table 表
     * @param en 字段
     * @return
     */
    public void buildRemarks() {
        if (x.isOneEmpty(this.ds, this.table, this.remarks)) {
            return;
        }
        // eg. 状态:1=上架，2=售罄,3=下架 ,4=过期
        String[] ss = null;
        try {
            // 获取第1项注释作为列名
            if (remarks.contains(":") || remarks.contains("：")) {
                ss = remarks.split(":|：");
            }
            if (x.isEmpty(ss)) {
                return;
            }
            this.remarks = ss[0];

            // 获取第2项注释作为字典
            if (ss.length > 1) {
                String ss1 = ss[1];
                // 如果没有=号 说明是其它描述eg. 原价:大于等于0
                if (ss1.contains("=")) {

                    LegacyLogKit.info(String.format("自动生成字典[%s - %s]", this.table, this.name));

                    String[] dicts = ss1.split(",|，");

                    String dictTable = x.conf.get("main_dict_table");
                    // Eova数据源,固定字典表
                    if (this.ds.equals(Ds.EOVA)) {
                        dictTable = "eova_dict";
                    }

                    // 清除字典
                    EovaGateways.get(this.ds).delete(String.format("delete from %s where object = ? and field = ?", dictTable), this.table, this.name);
                    // 生成字典
                    for (String dict : dicts) {

                        String[] sss = dict.split("=");
                        String value = sss[0];
                        String key = sss[1];

                        // 保存字典
                        EovaRecord r = new EovaRecord();
                        r.set("value", value);
                        r.set("name", key);
                        r.set("object", this.table);
                        r.set("field", this.name);
                        EovaGateways.get(this.ds).save(dictTable, r);
                    }
                }
            }
        } catch (Exception e) {
            LegacyLogKit.error("元字段备注预处理异常:" + remarks, e);
        }
    }
}