/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * <p>
 *
 * Licensed under the LGPL-3.0 license
 * Software copyright registration number:2018SR1012969
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.sql.ddl.dialect;

import cn.eova.tools.x;
import cn.eova.sql.ddl.DefineColumn;
import cn.eova.sql.ddl.DefineTable;

import static cn.eova.sql.ddl.dialect.EovaType.BOOL;
import static cn.eova.sql.ddl.dialect.EovaType.CHAR;
import static cn.eova.sql.ddl.dialect.EovaType.DATE;
import static cn.eova.sql.ddl.dialect.EovaType.DATETIME;
import static cn.eova.sql.ddl.dialect.EovaType.NUMBER;
import static cn.eova.sql.ddl.dialect.EovaType.TIME;
import static cn.eova.sql.ddl.dialect.EovaType.VARCHAR;

/**
 * MySQL 数据定义方言。
 *
 * <p>ported from: cn.eova.sql.ddl.dialect.MysqlDefineDialect
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br><b>刻意保留的既有语义（均已由 MysqlDefineDialectGoldenTest 在环比对）：</b>
 * <ol>
 *   <li>{@code toEovaType} 中 {@code TIMESTAMP} 映射为 <b>{@code TIME}</b>，不是 {@code DATETIME}；</li>
 *   <li>{@code toEovaType} 首判 {@code dbType.contains("INT")}，故 {@code BIGINT} 等也归 {@code NUMBER}；</li>
 *   <li>{@code toEovaType} 未识别类型一律回退 {@code VARCHAR}；</li>
 *   <li>{@code toDbType} 的 switch <b>无 default</b>：{@code DATETIME} 与未知枚举值返回 {@code null}；</li>
 *   <li>{@code create} 的拼接在 pk 为空时会留下<b>悬空逗号</b> —— 属既有输出契约，不修正；</li>
 *   <li>{@code updateColumn(table, old, new)} <b>返回 null</b>。</li>
 * </ol>
 */
public class MysqlDefineDialect extends DefineDialect {

    /**
     * 用反引号包裹标识符
     */
    @Override
    public String escape(String name) {
        return '`' + name + '`';
    }

    /**
     * 数据库类型转 EOVA 通用类型
     */
    @Override
    public EovaType toEovaType(String dbType, int size) {
        if (dbType.contains("INT")) {
            return NUMBER;
        } else if (dbType.equals("FLOAT")) {
            return NUMBER;
        } else if (dbType.equals("DOUBLE")) {
            return NUMBER;
        } else if (dbType.equals("DECIMAL")) {
            return NUMBER;
        } else if (dbType.equals("BIT")) {
            return BOOL;
        } else if (dbType.equals("DATE")) {
            return DATE;
        } else if (dbType.equals("DATETIME")) {
            return DATETIME;
        } else if (dbType.equals("TIMESTAMP")) {
            return TIME;
        } else if (dbType.equals("CHAR")) {
            return CHAR;
        }
        // 默认都是VARCHAR
        return VARCHAR;
    }

    /**
     * EOVA 通用类型转数据库类型
     */
    @Override
    public String toDbType(EovaType eovaType, int size, int decimal) {
        switch (eovaType) {
            case BOOL:
                return "TINYINT(1)";
            case NUMBER:
                return numberType(size, decimal);
            case CHAR:
                return String.format("CHAR(%s)", size);
            case VARCHAR:
                // 长字段用TEXT避免错误
                if (size > 2000) {
                    return "TEXT";
                }
                return String.format("VARCHAR(%s)", size);
            case DATE:
                return "DATE";
            case TIME:// 可享受默认值的便利
                return "TIMESTAMP";
        }
        return null;
    }

    /**
     * 按长度与精度推导数值类型
     */
    private String numberType(int size, int decimal) {
        // 整数
        if (decimal == 0) {
            if (size <= 4)
                return String.format("TINYINT(%s)", size);
            else if (size <= 11)// 此处按照使用习惯优先，大多数人的认知是int <= 10位
                return String.format("INT(%s)", size);
            else
                return String.format("BIGINT(%s)", size);
        }
        // 小数
        else {
            if (size + decimal <= 8)
                return String.format("FLOAT(%s,%s)", size, decimal);
            if (size + decimal <= 16)
                return String.format("DOUBLE(%s,%s)", size, decimal);
            return String.format("DECIMAL(%s,%s)", size, decimal);
        }
    }

    /**
     * 生成建表语句
     */
    @Override
    public String create(DefineTable tables) {
        StringBuilder sb = new StringBuilder();

        // + System.currentTimeMillis()
        sb.append(String.format("CREATE TABLE %s (\n", escape(tables.getEn())));
        for (DefineColumn o : tables.getFields()) {
            sb.append(buildColumn(o)).append(",\n");
        }
        if (!x.isEmpty(tables.getPk())) {
            sb.append(String.format(" PRIMARY KEY (%s)\n", escape(tables.getPk())));
        }
        sb.append(String.format(") COMMENT='%s';", tables.getCn()));
        sb.append("\n");

        return sb.toString();
    }

    /**
     * 生成重命名表语句
     */
    @Override
    public String rename(String tableName, String newTableName) {
        return String.format("ALTER TABLE %s RENAME %s;", escape(tableName), escape(newTableName));
    }

    /**
     * 生成添加字段语句
     */
    @Override
    public String addColumn(String tableName, DefineColumn o) {
        return String.format("ALTER TABLE %s ADD COLUMN %s;", escape(tableName), buildColumn(o));
    }

    /**
     * 生成修改字段语句
     */
    @Override
    public String updateColumn(String tableName, DefineColumn o) {
        return String.format("ALTER TABLE %s CHANGE COLUMN %s;", escape(tableName), buildColumn(o));
    }

    /**
     * 字段改名（MySQL 实现未支持，原样返回 null）
     */
    @Override
    public String updateColumn(String tableName, String oldColumnName, String newColumnName) {
        return null;
    }

    /**
     * 构建字段语法
     * @param o
     * @return
     */
    private String buildColumn(DefineColumn o) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(" %s", escape(o.getEn())));
        sb.append(" ").append(toDbType(o.getType(), o.getSize(), o.getDecimal()));
        sb.append(o.isNull() ? "" : " NOT NULL");
        sb.append(o.getDefault() != null ? String.format(" DEFAULT %s", formatDefault(o)) : "");
        sb.append(o.isAuto() ? " AUTO_INCREMENT" : "");
        sb.append(String.format(" COMMENT '%s'", o.getCn()));
        return sb.toString();
    }


}
