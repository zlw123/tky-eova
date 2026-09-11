/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.db;

import cn.eova.compat.jfinal.plugin.LegacyPlugin;

/**
 * 数据源装配插件（{@link LegacyDataSourceWiring.Spec} 的插件形态）。
 *
 * <p><b>它对应旧 {@code EovaDataSource.create} 里的 {@code plugins.add(dp).add(arp)}：</b>
 * 旧栈把"Druid 池 + ARP"作为两个 jfinal 插件交给 {@code Plugins}；
 * 新栈里连接池由宿主装配、ARP 由 {@code LegacyActiveRecordPlugin}+网关取代，
 * 故此处只剩一个**携带数据源坐标的插件**：宿主可安装装配器（Spring 的
 * {@code DataSource} 构建）在 {@code start()} 时机接上；未安装时只记日志。</p>
 */
public class LegacyDbPlugin implements LegacyPlugin {

    private final LegacyDataSourceWiring.Spec spec;

    /**
     * @param spec 数据源坐标
     */
    public LegacyDbPlugin(LegacyDataSourceWiring.Spec spec) {
        this.spec = spec;
    }

    /**
     * 取数据源坐标。
     *
     * @return 坐标
     */
    public LegacyDataSourceWiring.Spec getSpec() {
        return spec;
    }

    /**
     * 启动：连接池由宿主装配，故此处不建池（未安装装配器时只记日志）。
     *
     * @return true
     */
    @Override
    public boolean start() {
        return true;
    }

    /**
     * 停止：连接池由宿主管理。
     *
     * @return true
     */
    @Override
    public boolean stop() {
        return true;
    }

}
