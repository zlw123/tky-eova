/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.table;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 表映射等价物：替代 jfinal 5.2.6 的
 * {@code com.jfinal.plugin.activerecord.TableMapping} + {@code Table}，
 * 供 EOVA 的 {@code BaseModel}/{@code EovaModel} 使用（阶段 1 `D-MODEL` 前置 3）。
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
 *   <li>映射在 {@code addMapping} 时<b>立即</b>解析列与主键（旧实现经 {@code TableBuilder}
 *       读 JDBC 元数据），之后缓存；本实现同构，经 {@link TableMetadataSource} 接缝解析。</li>
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
    private final Map<Class<?>, TableMetadata> modelToTable = new HashMap<>();

    /** 表名 → 模型类（用于重复注册检测，与旧实现同构） */
    private final Map<String, Class<?>> tableToModel = new HashMap<>();

    /** 模型类 → 数据源名（对应旧实现 model→Config 的绑定） */
    private final Map<Class<?>, String> modelToConfig = new HashMap<>();

    /** 表元数据来源接缝；未设置时调用 {@link #addMapping} 会明确报错（避免静默得到空表） */
    private static volatile TableMetadataSource metadataSource;

    private EovaTableMapping() {
    }

    /**
     * 取单例
     */
    public static EovaTableMapping me() {
        return ME;
    }

    /**
     * 设置表元数据来源（启动时由 eova-db-adapter 注入 JDBC 实现）
     *
     * @param source 元数据来源
     */
    public static void setMetadataSource(TableMetadataSource source) {
        metadataSource = source;
    }

    /**
     * 注册映射：表名 + 模型类，主键经接缝立即解析（与旧实现同构）
     *
     * @param tableName  表名
     * @param modelClass 模型类
     */
    public void addMapping(String tableName, Class<?> modelClass) {
        addMapping(DEFAULT_CONFIG, tableName, modelClass);
    }

    /** 未显式指定数据源时使用的名字（EOVA 的 eova 数据源） */
    public static final String DEFAULT_CONFIG = "eova";

    /**
     * 注册映射：显式指定数据源名。
     *
     * <p><b>为什么必须有数据源维度：</b>实测 jfinal 的
     * {@code Model._getConfig().getName()} 返回的是<b>该模型注册时所属的 Config 名</b>，
     * 而 EOVA 是双数据源（{@code mappingEova} 把 15 个模型挂在 eova 数据源，
     * 另有 Diy 数据源）。{@code BaseModel.execute()} 正是用
     * {@code Db.use(this._getConfig().getName())} 决定操作哪个库 ——
     * 丢掉这一维度会导致跨库写错数据。
     *
     * @param configName 数据源名
     * @param tableName  表名
     * @param modelClass 模型类
     */
    public void addMapping(String configName, String tableName, Class<?> modelClass) {
        TableMetadataSource source = metadataSource;
        if (source == null) {
            throw new IllegalStateException(
                    "未设置 TableMetadataSource —— 注册 [" + tableName + "] 时无法解析列与主键。"
                            + "请由 eova-db-adapter 在启动时注入 JDBC 实现");
        }
        TableMetadata meta = source.metadata(tableName);
        if (meta == null) {
            throw new IllegalStateException(
                    "TableMetadataSource 返回 null（约定：表不存在时返回空元数据）：" + tableName);
        }
        addMapping(configName, modelClass, meta);
    }

    /**
     * 注册映射：显式给出元数据（供可离线运行的场景与验证判据使用）
     *
     * @param modelClass 模型类
     * @param meta       表元数据
     */
    public void addMapping(Class<?> modelClass, TableMetadata meta) {
        addMapping(DEFAULT_CONFIG, modelClass, meta);
    }

    /**
     * 注册映射：显式给出数据源名与元数据
     *
     * @param configName 数据源名
     * @param modelClass 模型类
     * @param meta       表元数据
     */
    public void addMapping(String configName, Class<?> modelClass, TableMetadata meta) {
        String tableName = meta.getName();
        if (tableToModel.containsKey(tableName)) {
            throw new IllegalStateException("Model mapping already exists : " + tableName);
        }
        tableToModel.put(tableName, modelClass);
        modelToTable.put(modelClass, meta);
        modelToConfig.put(modelClass, configName);
    }

    /**
     * 取模型类绑定的数据源名；未注册的类返回 null（与 {@link #getTable} 的 null 语义一致）
     *
     * @param modelClass 模型类
     * @return 数据源名或 null
     */
    public String getConfigName(Class<?> modelClass) {
        return modelToConfig.get(modelClass);
    }

    /**
     * 取模型类对应的表信息；<b>未注册的类返回 null</b>（与旧实现一致）
     *
     * @param modelClass 模型类
     * @return 表信息或 null
     */
    public TableMetadata getTable(Class<?> modelClass) {
        return modelToTable.get(modelClass);
    }

    /**
     * 清空全部映射（仅供测试隔离用）
     */
    public void clear() {
        modelToTable.clear();
        tableToModel.clear();
        modelToConfig.clear();
    }

    /** 供验证判据读取当前已注册的表名集合（不参与运行时逻辑） */
    public Map<String, Class<?>> registeredTables() {
        return Collections.unmodifiableMap(new HashMap<>(tableToModel));
    }
}
