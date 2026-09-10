/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.cache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import java.net.URL;

/**
 * {@link CacheService} 的 EhCache 实现：阶段 1 的默认实现，
 * 与旧栈使用<b>同一份</b> ehcache 制品与<b>同一份</b> {@code ehcache.xml}。
 *
 * <p>ported from: com.jfinal.plugin.ehcache.CacheKit（第三方制品）
 * <br>source artifact: com.jfinal:jfinal:5.2.6（{@code CacheKit}）+ net.sf.ehcache:ehcache-core:2.6.11
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>配置制品：{@code meta-eova/eova/demo/src/main/resources/ehcache.xml}
 * （逐字节 port 到 {@code eova-compat/src/main/resources/ehcache.xml}）
 *
 * <p><b>语义对齐要点：</b>
 * <ol>
 *   <li>旧 {@code CacheKit} 的 {@code get}/{@code put}/{@code remove} 即
 *       {@code CacheManager.getCache(name)} 后转调 {@code Cache} 的同名方法；
 *       本类保持同一映射，故 TTI/TTL/copy 语义由 {@code ehcache.xml} 决定，逐项一致。</li>
 *   <li>{@code copyOnRead}/{@code copyOnWrite} 保持配置文件里的设置
 *       （SP7 实测：开启时从缓存取回的是副本，调用方改动<b>不会</b>污染缓存）。</li>
 *   <li>cache 名不存在时的行为保持"直接暴露底层异常"，不做兜底吞错 ——
 *       旧实现亦然（{@code CacheManager.getCache} 返回 null 后即 NPE）。</li>
 * </ol>
 *
 * <p><b>生命周期：</b>{@link CacheManager} 较重且应全局唯一，故本类按配置文件路径缓存单例。
 * 关闭由容器负责；测试中可调用 {@link #shutdown()}。
 */
public class EhCacheService implements CacheService {

    /** 默认配置位置：classpath 根下的 ehcache.xml（与旧 demo 的资源同名同内容） */
    public static final String DEFAULT_CONFIG = "ehcache.xml";

    /** 单例的 CacheManager（按配置 URL 区分） */
    private static volatile CacheManager sharedManager;
    private static volatile String sharedConfig;

    private final CacheManager manager;

    /**
     * 以给定配置文件构造（内部持有 CacheManager 单例）
     *
     * @param configUrl 配置文件 URL
     */
    public EhCacheService(URL configUrl) {
        this.manager = managerOf(configUrl);
    }

    /**
     * 以 classpath 下的 {@value #DEFAULT_CONFIG} 构造
     */
    public static EhCacheService fromClasspath() {
        URL url = EhCacheService.class.getClassLoader().getResource(DEFAULT_CONFIG);
        if (url == null) {
            throw new IllegalStateException("classpath 下未找到 " + DEFAULT_CONFIG);
        }
        return new EhCacheService(url);
    }

    /**
     * 以指定配置文件路径构造
     *
     * @param configUrl 配置文件 URL
     */
    public static EhCacheService from(URL configUrl) {
        return new EhCacheService(configUrl);
    }

    private static synchronized CacheManager managerOf(URL configUrl) {
        String key = configUrl.toExternalForm();
        if (sharedManager == null || !key.equals(sharedConfig)) {
            if (sharedManager != null) {
                sharedManager.shutdown();
            }
            sharedManager = CacheManager.create(configUrl);
            sharedConfig = key;
        }
        return sharedManager;
    }

    @Override
    public Object get(String cacheName, Object key) {
        Element element = cache(cacheName).get(key);
        return element == null ? null : element.getObjectValue();
    }

    @Override
    public void put(String cacheName, Object key, Object value) {
        cache(cacheName).put(new Element(key, value));
    }

    @Override
    public void remove(String cacheName, Object key) {
        cache(cacheName).remove(key);
    }

    /**
     * 取底层 Cache；名字不存在时抛 IllegalStateException 并给出可诊断信息
     * （旧实现此处是 NPE，属"未配置即用错"场景，阶段 1 明确报错更利于定位）
     */
    private Cache cache(String cacheName) {
        Cache c = manager.getCache(cacheName);
        if (c == null) {
            throw new IllegalStateException(
                    "ehcache 中未配置名为 [" + cacheName + "] 的 cache，请检查 ehcache.xml");
        }
        return c;
    }

    /**
     * 暴露底层 CacheManager，供验证判据比对每项 cache 的策略参数
     */
    public CacheManager cacheManager() {
        return manager;
    }

    /**
     * 关闭全局 CacheManager（测试与容器关停用）
     */
    public static synchronized void shutdown() {
        if (sharedManager != null) {
            sharedManager.shutdown();
            sharedManager = null;
            sharedConfig = null;
        }
    }
}
