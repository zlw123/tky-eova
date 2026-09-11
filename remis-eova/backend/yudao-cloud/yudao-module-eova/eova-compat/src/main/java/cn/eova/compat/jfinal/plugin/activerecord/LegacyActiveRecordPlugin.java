/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin.activerecord;

import cn.eova.compat.jfinal.plugin.LegacyPlugin;
import cn.eova.compat.table.EovaTableMapping;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.plugin.activerecord.ActiveRecordPlugin} 的等价接缝（**绑定面**）。
 *
 * <p>ported from: com.jfinal.plugin.activerecord.ActiveRecordPlugin（jfinal 5.2.6 制品）
 *
 * <p><b>只 port"映射登记"这一半，理由（与 r79 的 AR 族裁定同源）：</b>
 * 旧 ARP 承担三件事 —— ① 建 Config/连接池；② {@code addMapping(table, modelClass)} 登记
 * 模型↔表绑定；③ {@code start()} 时建表元数据（走 {@code forTableBuilderDoBuild}）。
 * 新栈里 ① 由宿主的数据源（Spring/Druid 池）+ {@code JdbcEovaDbGateway} 承担、
 * ③ 由 {@code JdbcTableMetadataSource} 走 {@code DatabaseMetaData} 承担，
 * 只有 ② 是 EOVA 业务代码真正写下的内容（{@code EovaConfig.mappingEova} 里 15 条 {@code addMapping}）。
 * 故本接缝把 ② 直接接到新栈既有的 {@link EovaTableMapping}（它已有同形的
 * {@code addMapping(configName, tableName, modelClass)}），使 {@code mappingEova(arp)} 得以逐行 port。</p>
 */
public class LegacyActiveRecordPlugin implements LegacyPlugin {

    /** 数据源名（对应旧 ARP 持有的 Config 名） */
    private final String configName;

    /**
     * @param configName 数据源名（旧实现为 Config 名，如 {@code eova}/{@code main}）
     */
    public LegacyActiveRecordPlugin(String configName) {
        this.configName = configName;
    }

    /**
     * 登记模型↔表绑定（等价旧 {@code addMapping(String tableName, Class modelClass)}）。
     *
     * @param tableName  表名
     * @param modelClass 模型类
     * @return this
     */
    public LegacyActiveRecordPlugin addMapping(String tableName, Class<?> modelClass) {
        EovaTableMapping.me().addMapping(configName, tableName, modelClass);
        return this;
    }

    /**
     * 取数据源名。
     *
     * @return 数据源名
     */
    public String getConfigName() {
        return configName;
    }

    /**
     * 启动：新栈无需动作（表元数据在首次访问时经 {@code JdbcTableMetadataSource} 解析）。
     *
     * @return true
     */
    @Override
    public boolean start() {
        return true;
    }

    /**
     * 停止：新栈无需动作（连接池由宿主管理）。
     *
     * @return true
     */
    @Override
    public boolean stop() {
        return true;
    }

}
