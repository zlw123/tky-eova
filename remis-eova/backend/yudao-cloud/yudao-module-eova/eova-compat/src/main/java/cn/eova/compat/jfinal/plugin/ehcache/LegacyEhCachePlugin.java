/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin.ehcache;

import cn.eova.compat.cache.CacheServices;
import cn.eova.compat.cache.EhCacheService;
import cn.eova.compat.jfinal.plugin.LegacyPlugin;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.plugin.ehcache.EhCachePlugin} 的等价接缝。
 *
 * <p>ported from: com.jfinal.plugin.ehcache.EhCachePlugin（jfinal 5.2.6 制品）
 *
 * <p><b>行为映射：</b>旧插件 {@code start()} 用配置文件建 {@code CacheManager} 并交给
 * jfinal 的 {@code CacheKit}；{@code stop()} 关闭它。新栈已有等价物
 * {@link EhCacheService}（由 {@code CacheServices} 持有），故本接缝只做装配/关闭：
 * {@code start()} → {@code CacheServices.set(EhCacheService.fromClasspath())}；
 * {@code stop()} → {@code EhCacheService.shutdown()}。</p>
 *
 * <p>默认配置名 {@code "ehcache.xml"} 与 {@link EhCacheService#DEFAULT_CONFIG} 同值。</p>
 */
public class LegacyEhCachePlugin implements LegacyPlugin {

    /** 配置文件（classpath 资源名） */
    private final String configName;

    /** 默认构造：{@code ehcache.xml} */
    public LegacyEhCachePlugin() {
        this(EhCacheService.DEFAULT_CONFIG);
    }

    /**
     * @param configName classpath 上的 ehcache 配置
     */
    public LegacyEhCachePlugin(String configName) {
        this.configName = configName;
    }

    /**
     * 装配缓存服务。
     *
     * @return true
     */
    @Override
    public boolean start() {
        CacheServices.set(EhCacheService.fromClasspath());
        return true;
    }

    /**
     * 关闭缓存管理器。
     *
     * @return true
     */
    @Override
    public boolean stop() {
        EhCacheService.shutdown();
        return true;
    }

}
