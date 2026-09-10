/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.table;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 表映射等价物：替代 jfinal 5.2.6 的
 * {@code com.jfinal.plugin.activerecord.TableMapping} + {@code Table}，
 * 供 EOVA 的 {@code BaseModel} 使用（阶段 1 `D-MODEL` 前置 3）。
 *
 * <p><b>为什么是"等价物"而不是"port"：</b>JFinal 的 {@code TableMapping} 存的是
 * {@code Map<Class<? extends Model<?>>, Table>}，而 {@code Model} 类型在新栈不存在。
 * 故保留<b>可观测行为</b>，把键类型放宽为 {@code Class<?>}：
 * 注册（{@link #addMapping}）→ 取表（{@link #getTable}）。
 *
 * <p><b>实测的旧行为（据完整方法体，非片段推断 —— 见 R43）：</b>
 * <ol>
 *   <li>{@code TableMapping.getTable(cls)} 的方法体就是
 *       {@code modelToTableMap.get(modelClass)} 加一次泛型 {@code checkcast}：
 *       <b>未映射的类返回 {@code null}，不抛异常</b>。
 *       故 EOVA 中未注册映射的模型（实测 {@code Msg}/{@code EovaLog}/{@code MenuObject}
 *       三个未出现在 {@code EovaConfig} 的 addMapping 列表中）在调用
 *       {@code BaseModel.save()} 时会在 {@code table.getPrimaryKey()[0]} 处抛
 *       {@code NullPointerException}。<b>本实现保留该行为（返回 null）</b>，
 *       不改成"抛更友好的异常" —— 那属行为变更。</li>
 *   <li>映射在{@code addMapping} 时<b>立即</b>解析主键（旧实现经 {@code TableBuilder}
 *       读 JDBC 元数据），之后缓存；本实现同构，经 {@link PrimaryKeySource} 接缝解析。</li>
 *   <li>重复注册同一张表名会抛 {@code IllegalStateException}
 *       （旧实现消息形如 {@code Model mapping already exists : ...}）。</li>
 * </ol>
 *
 * <p>ported from: com.jfinal.plugin.activerecord.TableMapping + Table（语义等价重实现）
 * <br>source artifact: com.jfinal:jfinal:5.2.6
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>注册来源：{@code cn.eova.config.EovaConfig} 中的 15 条
 * {@code arp.addMapping(tableName, ModelClass.class)}（实测全项目仅此一处注册；
 * {@code AutoBindModel}/@TableBind 为<b>死代码</b>，无任何引用）
 */
public final class EovaTableMapping {

    /** 单例（对应旧实现的 {@code TableMapping.me()}） */
    private static final EovaTableMapping ME = new EovaTableMapping();

    /** 模型类 → 表信息 */
    private final Map<Class<?>, TableInfo> modelToTable = new HashMap<>();

    /** 表名 → 模型类（用于重复注册检测，与旧实现同构） */
    private final Map<String, Class<?>> tableToModel = new HashMap<>();

    /** 主键来源接缝；未设置时调用 {@link #addMapping} 会明确报错（避免静默得到无主键的表） */
    private static volatile PrimaryKeySource primaryKeySource;

    private EovaTableMapping() {
    }

    /**
     * 取单例
     */
    public static EovaTableMapping me() {
        return ME;
    }

    /**
     * 设置主键来源（启动时由 eova-db-adapter 注入 JDBC 实现）
     *
     * @param source 主键来源
     */
    public static void setPrimaryKeySource(PrimaryKeySource source) {
        primaryKeySource = source;
    }

    /**
     * 注册映射：表名 + 模型类，主键经接缝立即解析（与旧实现同构）
     *
     * @param tableName  表名
     * @param modelClass 模型类
     */
    public void addMapping(String tableName, Class<?> modelClass) {
        PrimaryKeySource source = primaryKeySource;
        if (source == null) {
            throw new IllegalStateException(
                    "未设置 PrimaryKeySource —— 注册 [" + tableName + "] 时无法解析主键。"
                            + "请由 eova-db-adapter 在启动时注入 JDBC 实现");
        }
        String[] pk = source.primaryKeys(tableName);
        if (pk == null) {
            throw new IllegalStateException(
                    "PrimaryKeySource 返回 null（约定：无主键时返回空数组）：" + tableName);
        }
        addMapping(tableName, modelClass, pk);
    }

    /**
     * 注册映射：显式给出主键（供可离线运行的场景与验证判据使用）
     *
     * @param tableName  表名
     * @param modelClass 模型类
     * @param primaryKey 主键列
     */
    public void addMapping(String tableName, Class<?> modelClass, String[] primaryKey) {
        if (tableToModel.containsKey(tableName)) {
            throw new IllegalStateException("Model mapping already exists : " + tableName);
        }
        tableToModel.put(tableName, modelClass);
        modelToTable.put(modelClass, new TableInfo(tableName, primaryKey));
    }

    /**
     * 取模型类对应的表信息；<b>未注册的类返回 null</b>（与旧实现一致）
     *
     * @param modelClass 模型类
     * @return 表信息或 null
     */
    public TableInfo getTable(Class<?> modelClass) {
        return modelToTable.get(modelClass);
    }

    /**
     * 清空全部映射（仅供测试隔离用）
     */
    public void clear() {
        modelToTable.clear();
        tableToModel.clear();
    }

    /**
     * 表信息等价物：对应旧实现的 {@code com.jfinal.plugin.activerecord.Table}
     * 在 EOVA 使用范围内的可观测面（表名 + 主键）。
     */
    public static final class TableInfo {

        private final String name;
        private final String[] primaryKey;

        TableInfo(String name, String[] primaryKey) {
            this.name = name;
            this.primaryKey = primaryKey == null ? new String[0] : primaryKey.clone();
        }

        /** 表名 */
        public String getName() {
            return name;
        }

        /**
         * 主键列名数组（返回副本，防止调用方改动内部状态）
         *
         * <p>注意：{@code BaseModel.save()} 取的是 {@code getPrimaryKey()[0]} ——
         * 若表无主键则此处为空数组，取下标 0 会抛 {@code ArrayIndexOutOfBoundsException}，
         * 与旧实现一致。
         */
        public String[] getPrimaryKey() {
            return primaryKey.clone();
        }

        @Override
        public String toString() {
            return name + " pk=" + Arrays.toString(primaryKey);
        }
    }

    /** 供验证判据读取当前已注册的表名集合（不参与运行时逻辑） */
    public Map<String, Class<?>> registeredTables() {
        return Collections.unmodifiableMap(new HashMap<>(tableToModel));
    }
}
