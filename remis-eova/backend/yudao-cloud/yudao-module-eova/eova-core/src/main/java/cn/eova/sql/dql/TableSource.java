/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.sql.dql;

import java.text.MessageFormat;

/**
 * SQL表相关信息
 *
 * @author Jieven
 *
 * <p>ported from: cn.eova.sql.dql.TableSource
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>source sha256: 见 docs/.local/ledger/java-units.jsonl（unitType=D）
 * <br>本单元为逐行等价 port，未做任何语义改写。以下两处<b>刻意保留原样</b>，不得"顺手修正"：
 * <ol>
 *   <li>字段与方法的 {@code Rigth} 拼写（非 Right）—— 属对外契约，改名即破坏 API；</li>
 *   <li>{@link #getField()} 在 {@code leftAlias == null} 时会抛 NPE —— 属既有异常语义，
 *       需与旧实现保持一致（见 TableSourceGoldenTest 的行为等价断言）。</li>
 * </ol>
 */
public class TableSource {

    private String table;
    private String alias;

    private String leftField;
    private String leftAlias;

    private String rigthField;
    private String rigthAlias;

    // private Record data;

    /**
     * 取别名；未设置别名时回退为表名
     */
    public String getAlias() {
        if (alias == null) {
            return table;
        }
        return alias;
    }

    /**
     * 获得当前表的关联字段
     */
    public String getField() {
        if (leftAlias.equals(alias)) {
            return leftField;
        }
        return rigthField;
    }

    /**
     * 表关联条件的字面描述
     */
    public String toString() {
        return MessageFormat.format("{0} as {1} , condition {2}.{3} = {4}.{5}", table, alias, leftAlias, leftField, rigthAlias, rigthField);
    }

    /**
     * 取表名
     */
    public String getTable() {
        return table;
    }

    /**
     * 设置表名
     */
    public void setTable(String table) {
        this.table = table;
    }

    /**
     * 设置别名
     */
    public void setAlias(String alias) {
        this.alias = alias;
    }

    /**
     * 取左表关联字段
     */
    public String getLeftField() {
        return leftField;
    }

    /**
     * 设置左表关联字段
     */
    public void setLeftField(String leftField) {
        this.leftField = leftField;
    }

    /**
     * 取左表别名
     */
    public String getLeftAlias() {
        return leftAlias;
    }

    /**
     * 设置左表别名
     */
    public void setLeftAlias(String leftAlias) {
        this.leftAlias = leftAlias;
    }

    /**
     * 取右表关联字段（方法名 Rigth 为原样保留的拼写）
     */
    public String getRigthField() {
        return rigthField;
    }

    /**
     * 设置右表关联字段
     */
    public void setRigthField(String rigthField) {
        this.rigthField = rigthField;
    }

    /**
     * 取右表别名（方法名 Rigth 为原样保留的拼写）
     */
    public String getRigthAlias() {
        return rigthAlias;
    }

    /**
     * 设置右表别名
     */
    public void setRigthAlias(String rigthAlias) {
        this.rigthAlias = rigthAlias;
    }


}
