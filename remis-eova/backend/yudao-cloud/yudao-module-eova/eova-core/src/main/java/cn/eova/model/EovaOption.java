// compile-stub for LC-011 EovaExp; not a ported unit.
// real source: meta-eova/eova/core/src/main/java/cn/eova/model/EovaOption.java
package cn.eova.model;

import java.util.Map;

/**
 * EovaOption 最小 stub。getConfObj 返回 Map 以替换 JFinal Kv（R1 基础设施替换）。
 */
public class EovaOption {

    private String ds;
    private String fieldVal;
    private String fieldTxt;
    private String sql;
    private Map<String, Object> fieldWidth;

    public String getSql() {
        return sql;
    }

    public String getDs() {
        return ds;
    }

    public String getFieldVal() {
        return fieldVal;
    }

    public String getFieldTxt() {
        return fieldTxt;
    }

    /**
     * 对齐旧 {@code Kv getConfObj(String)}，迁期返回 Map。
     */
    public Map<String, Object> getConfObj(String key) {
        if ("field_width".equals(key)) {
            return fieldWidth;
        }
        return null;
    }

    public String toJson() {
        return "{}";
    }

    public void setDs(String ds) {
        this.ds = ds;
    }

    public void setFieldVal(String fieldVal) {
        this.fieldVal = fieldVal;
    }

    public void setFieldTxt(String fieldTxt) {
        this.fieldTxt = fieldTxt;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public void setFieldWidth(Map<String, Object> fieldWidth) {
        this.fieldWidth = fieldWidth;
    }
}
