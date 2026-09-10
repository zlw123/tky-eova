/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.cache;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.plugin.ehcache.CacheKit} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.plugin.ehcache.CacheKit}（jfinal 5.2.6）。</p>
 *
 * <p><b>为什么是薄委托而不是重新实现：</b>旧 {@code CacheKit} 的语义就是
 * "按 cache 名取 {@code net.sf.ehcache.Cache}，再对其 put/get/remove"，
 * 而新栈的 {@link CacheService} 已把同一能力抽象为
 * {@code get(cacheName, key)} / {@code put(cacheName, key, value)} / {@code remove(cacheName, key)}
 * —— <b>签名形状与 CacheKit 完全一致</b>。故本类只做静态转发，
 * 缓存策略（TTL / copyOnRead / copyOnWrite）仍由 {@code ehcache.xml}（逐字节 port）决定。</p>
 *
 * <p><b>方法集口径：全树普查后只做被实际调用的 3 个</b> ——
 * 旧树对 {@code CacheKit} 的全部用法为 {@code put}(11)、{@code remove}(6)、{@code get}(6)；
 * 未使用的 {@code getKeys} / {@code removeAll} 及 {@code IDataLoader} 重载<b>不引入</b>
 * （它们若将来被需要，应连同其语义一并补，而不是先放个壳）。</p>
 *
 * <p><b>与 {@link CacheServices} 的关系：</b>取实现走 {@code CacheServices.get()}，
 * 即"未显式注入则按 classpath 的 {@code ehcache.xml} 惰性初始化"——
 * 与旧 {@code CacheKit} 由 {@code EhCachePlugin} 初始化后全局可用的形态一致。
 * 注意 {@code EovaModel} 另有一个<b>独立</b>的缓存注入点（见 DES-002-R4 §R48），
 * 生产装配须两处都注入。</p>
 */
public final class LegacyCacheKit {

    private LegacyCacheKit() {
    }

    /**
     * 取缓存值（对应旧 {@code CacheKit.get}）。
     *
     * @param cacheName 缓存名
     * @param key       键
     * @param <T>       值类型
     * @return 值；未命中为 null
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String cacheName, Object key) {
        return (T) CacheServices.get().get(cacheName, key);
    }

    /**
     * 写缓存（对应旧 {@code CacheKit.put}）。
     *
     * @param cacheName 缓存名
     * @param key       键
     * @param value     值
     */
    public static void put(String cacheName, Object key, Object value) {
        CacheServices.get().put(cacheName, key, value);
    }

    /**
     * 移除缓存项（对应旧 {@code CacheKit.remove}）。
     *
     * @param cacheName 缓存名
     * @param key       键
     */
    public static void remove(String cacheName, Object key) {
        CacheServices.get().remove(cacheName, key);
    }

}
