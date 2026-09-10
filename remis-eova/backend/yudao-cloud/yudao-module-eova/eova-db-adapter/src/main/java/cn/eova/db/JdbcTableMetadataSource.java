/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import cn.eova.compat.table.TableMetadata;
import cn.eova.compat.table.TableMetadataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link TableMetadataSource} 的 JDBC 实现：从数据库元数据解析表的列与主键。
 *
 * <p><b>为什么必须走元数据而不是硬编码：</b>旧栈的列集与主键由 JFinal 的
 * {@code ActiveRecordPlugin.addMapping(tableName, modelClass)}（两参形式）在注册时
 * 经 {@code DatabaseMetaData} 解析，<b>注册处并未写出列名或主键名</b>。
 * 若在新实现里写成常量表，就等于凭空引入一份"看起来对"的复制品 ——
 * 一旦库结构变化，两边会静默分叉。
 *
 * <p><b>顺序语义：</b>
 * <ul>
 *   <li>列按 {@code ORDINAL_POSITION} 升序（旧实现 {@code Model.save()} 生成的 insert 列序来源于此）；</li>
 *   <li>主键按 {@code KEY_SEQ} 升序（{@code Model.delete()} 取 {@code getPrimaryKey()[0]}）。</li>
 * </ul>
 *
 * <p><b>缓存：</b>旧实现在注册时解析一次并缓存；本实现同样按表名缓存。
 * 表不存在时返回<b>空元数据</b>（空列、空主键），不返回 null。
 */
public class JdbcTableMetadataSource implements TableMetadataSource {

    private final DataSource dataSource;

    /** 表名 → 元数据（已解析结果缓存） */
    private final Map<String, TableMetadata> cache = new LinkedHashMap<>();

    public JdbcTableMetadataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public TableMetadata metadata(String tableName) {
        synchronized (cache) {
            TableMetadata cached = cache.get(tableName);
            if (cached != null) {
                return cached;
            }
        }
        TableMetadata meta = resolve(tableName);
        synchronized (cache) {
            cache.put(tableName, meta);
        }
        return meta;
    }

    /** 经 JDBC 元数据解析列与主键 */
    private TableMetadata resolve(String tableName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            // 大小写：MySQL 下表名大小写敏感性取决于 OS，此处按原样与全小写各试一次
            for (String candidate : new String[]{tableName, tableName.toLowerCase()}) {
                String[] columns = columns(meta, catalog, candidate);
                if (columns.length > 0) {
                    return new TableMetadata(tableName, columns,
                            primaryKeys(meta, catalog, candidate));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("解析表元数据失败：" + tableName, e);
        }
        return new TableMetadata(tableName, new String[0], new String[0]);
    }

    /** 列名，按 ORDINAL_POSITION 升序 */
    private static String[] columns(DatabaseMetaData meta, String catalog, String table)
            throws SQLException {
        Map<Integer, String> byPos = new LinkedHashMap<>();
        try (ResultSet rs = meta.getColumns(catalog, null, table, null)) {
            while (rs.next()) {
                byPos.put(rs.getInt("ORDINAL_POSITION"), rs.getString("COLUMN_NAME"));
            }
        }
        return sortedValues(byPos);
    }

    /** 主键列名，按 KEY_SEQ 升序 */
    private static String[] primaryKeys(DatabaseMetaData meta, String catalog, String table)
            throws SQLException {
        Map<Short, String> bySeq = new LinkedHashMap<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, null, table)) {
            while (rs.next()) {
                bySeq.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
        }
        return sortedValues(bySeq);
    }

    /** 按数值键升序输出值 */
    private static <K extends Number> String[] sortedValues(Map<K, String> map) {
        List<K> keys = new ArrayList<>(map.keySet());
        keys.sort((a, b) -> Integer.compare(a.intValue(), b.intValue()));
        List<String> out = new ArrayList<>();
        for (K k : keys) {
            out.add(map.get(k));
        }
        return out.toArray(new String[0]);
    }

    /** 清空缓存（仅供测试） */
    public void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }
}
