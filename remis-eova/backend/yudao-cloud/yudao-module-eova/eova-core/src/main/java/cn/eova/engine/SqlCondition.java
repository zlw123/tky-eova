// ported from: meta-eova/eova/core/src/main/java/cn/eova/engine/SqlCondition.java
// old FQCN: cn.eova.engine.SqlCondition
/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.engine;

import java.util.Arrays;
import java.util.List;


/**
 * SQL条件
 *
 * @author Jieven
 */
public class SqlCondition {

    private String sql;
    private List<Object> params;

    public SqlCondition(String sql, List<Object> params) {
        this.sql = sql;
        this.params = params;
    }

    public SqlCondition(String sql, Object... params) {
        this.sql = sql;
        this.params = Arrays.asList(params);
    }

    public SqlCondition(String sql) {
        this.sql = sql;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public List<Object> getParams() {
        return params;
    }

    public void setParams(List<Object> params) {
        this.params = params;
    }

}
