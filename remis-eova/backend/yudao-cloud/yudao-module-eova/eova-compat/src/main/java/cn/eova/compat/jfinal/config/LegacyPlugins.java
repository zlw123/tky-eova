/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.eova.compat.jfinal.plugin.LegacyPlugin;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.config.Plugins} 的等价接缝。
 *
 * <p>ported from: com.jfinal.config.Plugins（jfinal 5.2.6 制品）
 *
 * <p><b>逐条取自旧字节码：</b>{@code final class}；内部 {@code List<IPlugin> pluginList}；
 * {@code add(IPlugin)} 返回 {@code this}；{@code getPluginList()} 返回内部列表。
 * EOVA 在 {@code configPlugin} 里 {@code plugins.add(...)} 三次
 * （{@code EovaConfigPlugin}、{@code EhCachePlugin}、条件性的 {@code EovaCronPlugin}）。</p>
 */
public final class LegacyPlugins {

    private final List<LegacyPlugin> pluginList = new ArrayList<>();

    /**
     * 追加插件。
     *
     * @param plugin 插件
     * @return this
     */
    public LegacyPlugins add(LegacyPlugin plugin) {
        pluginList.add(plugin);
        return this;
    }

    /**
     * 取插件列表（内部视图，与旧实现一致）。
     *
     * @return 插件列表
     */
    public List<LegacyPlugin> getPluginList() {
        return Collections.unmodifiableList(pluginList);
    }

}
