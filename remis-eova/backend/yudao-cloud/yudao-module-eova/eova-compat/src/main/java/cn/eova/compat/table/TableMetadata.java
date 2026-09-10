/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.table;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 表元数据：对应旧实现 {@code com.jfinal.plugin.activerecord.Table} 在
 * EOVA 使用范围内的可观测面（表名 + 列集合 + 主键）。
 *
 * <p>不可变；列与主键的<b>顺序</b>都是语义的一部分（见 {@link TableMetadataSource}）。
 */
public final class TableMetadata {

    private final String name;
    private final String[] columns;
    private final String[] primaryKeys;
    private final Set<String> columnSet;

    public TableMetadata(String name, String[] columns, String[] primaryKeys) {
        this.name = name;
        this.columns = columns == null ? new String[0] : columns.clone();
        this.primaryKeys = primaryKeys == null ? new String[0] : primaryKeys.clone();
        Set<String> set = new LinkedHashSet<>();
        for (String c : this.columns) {
            set.add(c);
        }
        this.columnSet = Collections.unmodifiableSet(set);
    }

    /** 表名 */
    public String getName() {
        return name;
    }

    /** 列名（按 JDBC ORDINAL_POSITION 顺序；返回副本） */
    public String[] columns() {
        return columns.clone();
    }

    /** 主键列名（按 JDBC KEY_SEQ 顺序；返回副本） */
    public String[] primaryKeys() {
        return primaryKeys.clone();
    }

    /**
     * 是否存在该列（对应旧实现 {@code Table.hasColumnLabel(String)}）。
     *
     * <p>大小写不敏感 —— 与 JDBC 在 MySQL 上的列名大小写行为一致，
     * 且与 EOVA 的 {@code islowercase} 容器语义相容。
     */
    public boolean hasColumn(String column) {
        if (column == null) {
            return false;
        }
        if (columnSet.contains(column)) {
            return true;
        }
        for (String c : columns) {
            if (c.equalsIgnoreCase(column)) {
                return true;
            }
        }
        return false;
    }

    /** 是否为空元数据（表不存在时由来源返回） */
    public boolean isEmpty() {
        return columns.length == 0 && primaryKeys.length == 0;
    }

    @Override
    public String toString() {
        return name + " columns=" + Arrays.toString(columns)
                + " pk=" + Arrays.toString(primaryKeys);
    }
}
