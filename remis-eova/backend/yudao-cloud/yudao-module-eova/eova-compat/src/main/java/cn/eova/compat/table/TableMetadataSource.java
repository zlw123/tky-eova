/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.table;

/**
 * 表元数据接缝：把"表名 → 列集合 + 主键"的解析与宿主存储解耦。
 *
 * <p><b>存在理由：</b>旧栈的这套信息由 JFinal 在
 * {@code ActiveRecordPlugin.addMapping(tableName, modelClass)} 注册时
 * <b>立即</b>经 JDBC 元数据（{@code DatabaseMetaData}）解析并写入 {@code Table}。
 * 新栈里 {@code eova-core} 不得接触数据库（§4 约束 1/2），故抽成接缝：
 * {@code eova-compat} 定义接口，{@code eova-db-adapter} 提供 JDBC 实现，启动时注入。
 *
 * <p><b>为什么需要列集合（不只是主键）：</b>实测 jfinal 5.2.6 的
 * {@code Model.set(k, v)} 会先调 {@code _getTable().hasColumnLabel(k)}，
 * <b>列不存在时抛 {@code ActiveRecordException("The attribute name does not exist: \"k\"")}</b>；
 * 而 {@code Model.put(k, v)} 不做任何校验。
 * 这一"set 校验 / put 不校验"的不对称属对外可观测行为，
 * 故 {@link TableMetadata#hasColumn} 是必须的能力，不能只给主键。
 *
 * <p><b>行为要求：</b>
 * <ul>
 *   <li>{@link TableMetadata#primaryKeys()} 顺序必须与 JDBC {@code KEY_SEQ} 一致
 *       （{@code Model.delete()} 取的是 {@code getPrimaryKey()[0]}）；</li>
 *   <li>{@link TableMetadata#columns()} 顺序必须与 JDBC {@code ORDINAL_POSITION} 一致
 *       （旧实现 {@code Model.save()} 生成的 insert 列序来源于此）；</li>
 *   <li>表不存在时返回"空元数据"（空列、空主键）而非 null —— 由调用方决定如何应对。</li>
 * </ul>
 */
public interface TableMetadataSource {

    /**
     * 取指定表的元数据
     *
     * @param tableName 表名
     * @return 表元数据；表不存在时返回空元数据（不得返回 null）
     */
    TableMetadata metadata(String tableName);
}
