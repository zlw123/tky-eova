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
    public String[] getPrimaryKey() {
        return primaryKeys.clone();
    }

    /**
     * 是否存在该列（对应 jfinal {@code Table.hasColumnLabel(String)}）。
     *
     * <p><b>区分大小写</b> —— 实测 jfinal 的 {@code hasColumnLabel("NAME")} 对列 {@code name}
     * 返回 {@code false}。这一点是契约的一部分：{@code Model.set(k, v)} 用它做列校验，
     * 故 EOVA 代码传错大小写时，旧实现会抛
     * {@code The attribute name does not exist: "NAME"}，而<b>不会</b>静默接受。
     * （本类最初写成大小写不敏感，是据"EOVA 的 islowercase 容器语义"做的推断，
     * 缺证据；已由 {@code BaseModelGoldenTest} 对着真实 jfinal {@code Table} 的比对纠正。）
     *
     * <p>列名集合本身也区分大小写（同 {@link #getColumnNameSet()}）。
     */
    public boolean hasColumnLabel(String column) {
        return column != null && columnSet.contains(column);
    }

    /**
     * 列名集合（对应 jfinal {@code Table.getColumnNameSet()}）。
     *
     * <p>EOVA 有代码直接调用它做"该企业字段是否存在"的判断
     * （{@code Role.findSubRole} 里的
     * {@code this._getTable().getColumnNameSet().contains(companyField)}），
     * 故方法名必须与 jfinal 一致，否则逐字节 port 的代码无法编译。
     *
     * <p>返回不可变集合；大小写敏感（与列名原文一致）。
     */
    public Set<String> getColumnNameSet() {
        return columnSet;
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
