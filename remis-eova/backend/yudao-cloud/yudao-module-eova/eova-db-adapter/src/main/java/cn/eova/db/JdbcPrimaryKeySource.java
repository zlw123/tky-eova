/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import cn.eova.compat.table.PrimaryKeySource;

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
 * {@link PrimaryKeySource} 的 JDBC 实现：从数据库元数据解析主键。
 *
 * <p><b>为什么必须走元数据而不是硬编码：</b>旧栈的主键由 JFinal 的
 * {@code ActiveRecordPlugin.addMapping(tableName, modelClass)}（两参形式）在注册时
 * 经 {@code DatabaseMetaData} 解析，<b>注册处并未写出主键名</b>。
 * 若在新实现里把主键写成常量表，就等于凭空引入了一份"看起来对"的复制品 ——
 * 一旦库结构变化，两边会静默分叉。故此处同样从元数据解析。
 *
 * <p><b>列顺序：</b>按 JDBC 的 {@code KEY_SEQ} 升序，与旧实现一致
 * （{@code BaseModel.save()} 取 {@code getPrimaryKey()[0]}，复合主键场合顺序即语义）。
 *
 * <p><b>缓存：</b>旧实现在注册时解析一次并缓存；本实现同样缓存（按表名），
 * 避免每次取表都查元数据。
 */
public class JdbcPrimaryKeySource implements PrimaryKeySource {

    private final DataSource dataSource;

    /** 表名 → 主键列（已解析结果缓存） */
    private final Map<String, String[]> cache = new LinkedHashMap<>();

    public JdbcPrimaryKeySource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String[] primaryKeys(String tableName) {
        synchronized (cache) {
            String[] cached = cache.get(tableName);
            if (cached != null) {
                return cached.clone();
            }
        }
        String[] pk = resolve(tableName);
        synchronized (cache) {
            cache.put(tableName, pk);
        }
        return pk.clone();
    }

    /** 经 JDBC 元数据解析主键列，按 KEY_SEQ 升序 */
    private String[] resolve(String tableName) {
        Map<Short, String> bySeq = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            // 大小写：MySQL 下表名大小写敏感性取决于 OS，此处按原样与全小写各试一次
            for (String candidate : new String[]{tableName, tableName.toLowerCase()}) {
                if (collect(meta, conn, candidate, bySeq)) {
                    break;
                }
                bySeq.clear();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("解析主键失败：" + tableName, e);
        }
        // 按 KEY_SEQ 升序输出
        List<Short> seqs = new ArrayList<>(bySeq.keySet());
        seqs.sort(null);
        List<String> out = new ArrayList<>();
        for (Short s : seqs) {
            out.add(bySeq.get(s));
        }
        return out.toArray(new String[0]);
    }

    /** 收集指定表的主键列；返回是否命中该表 */
    private boolean collect(DatabaseMetaData meta, Connection conn, String candidate,
                            Map<Short, String> out) throws SQLException {
        boolean found = false;
        try (ResultSet rs = meta.getPrimaryKeys(conn.getCatalog(), null, candidate)) {
            while (rs.next()) {
                found = true;
                out.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
        }
        return found;
    }

    /** 清空缓存（仅供测试） */
    public void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }
}
