/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.table;

/**
 * 主键来源接缝：把"表 → 主键列"的解析与宿主存储解耦。
 *
 * <p><b>存在理由：</b>旧栈的主键由 JFinal 的 {@code ActiveRecordPlugin.addMapping(tableName, modelClass)}
 * 在<b>注册时立即</b>通过 JDBC 元数据（{@code DatabaseMetaData}）解析并写入 {@code Table}。
 * 新栈里 {@code eova-core} 不得接触数据库（§4 约束 1/2：只有 {@code eova-db-adapter} 能碰数据源），
 * 故把这一步抽成接缝：{@code eova-compat} 定义接口，{@code eova-db-adapter} 提供 JDBC 实现，
 * 启动时注入。
 *
 * <p><b>行为要求：</b>实现返回的列顺序必须与 JDBC 元数据中主键的 {@code KEY_SEQ} 顺序一致
 * （复合主键场合），因为旧实现直接把该顺序写入 {@code Table.getPrimaryKey()}，
 * 而 {@code BaseModel.save()} 取的是 {@code getPrimaryKey()[0]}。
 */
public interface PrimaryKeySource {

    /**
     * 取指定表的主键列名（按 JDBC KEY_SEQ 顺序）
     *
     * @param tableName 表名
     * @return 主键列名数组；表不存在或无主键时返回空数组（不得返回 null）
     */
    String[] primaryKeys(String tableName);
}
