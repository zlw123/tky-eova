/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.cache;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code LegacyCacheKit} 的<b>行为</b>判据（{@code CacheKit} 等价接缝）。
 *
 * <p><b>为什么必须补它：</b>变异测试实测发现 —— 把
 * {@code LegacyCacheKit.put} 里的 {@code put(cacheName, key, value)} 写成
 * {@code put(cacheName, value, key)}（<b>键值参数写反</b>）竟然<b>没有任何判据失败</b>。
 * 原因是当时只有声明面判据（比签名），而签名完全没变。
 * 声明面判据能查"方法在不在、签名对不对"，查不了"参数用没用对"。</p>
 *
 * <p>本判据用<b>往返</b>来钉住参数语义：put 之后必须能按<b>同一个 key</b> 取回
 * <b>同一个 value</b>；若键值写反，按 key 取回的就是 null（或取回的是别的对象），
 * 往返立刻失败。</p>
 *
 * <p>acceptanceProfile: golden-cachekit-seam</p>
 */
class LegacyCacheKitGoldenTest {

    /** 用 BaseCache 里真实存在的缓存名（TTL/策略由 ehcache.xml 决定，逐字节 port） */
    private static final String CACHE = BaseCacheNames.SYS;

    /**
     * 挂上 EhCache 实现；先强制清掉可能已被其它判据关停的单例
     * （见 DES-002-R4 §R49：EhCacheService 单例可能持有已死的 CacheManager）。
     */
    @BeforeAll
    static void setUp() {
        EhCacheService.shutdown();
        CacheServices.set(EhCacheService.fromClasspath());
    }

    /**
     * 收尾清空注入，避免影响其它判据。
     */
    @AfterAll
    static void tearDown() {
        CacheServices.clear();
        EhCacheService.shutdown();
    }

    /**
     * <b>核心往返判据：</b>put 之后按同一 key 必须取回同一 value。
     */
    @Test
    @DisplayName("往返：put(cache,key,value) 后 get(cache,key) 必须取回同一 value")
    void putThenGetRoundTrip() {
        String key = "k-seam-" + System.nanoTime();
        Object value = "v-seam";

        LegacyCacheKit.put(CACHE, key, value);
        assertEquals(value, LegacyCacheKit.get(CACHE, key),
                "按同一 key 必须取回同一 value —— 若键值参数写反，此处会取回 null");
    }

    /**
     * <b>键值不可交换：</b>用 value 当 key 去查必须查不到。
     *
     * <p>这条是"参数写反"的<b>正面探测器</b>：若实现写成 {@code put(cache, value, key)}，
     * 那么按 value 查就会命中，本断言失败。</p>
     */
    @Test
    @DisplayName("键值不可交换：按 value 当 key 查必须查不到")
    void keyAndValueAreNotInterchangeable() {
        String key = "k-swap-" + System.nanoTime();
        String value = "v-swap-" + System.nanoTime();

        LegacyCacheKit.put(CACHE, key, value);

        assertNull(LegacyCacheKit.get(CACHE, value),
                "value 不得被当作 key 存入 —— 该断言专门探测 put 的参数顺序写反");
        assertEquals(value, LegacyCacheKit.get(CACHE, key), "按真正的 key 仍应命中");
    }

    /**
     * remove 之后取不到（且用的是同一个 key）。
     */
    @Test
    @DisplayName("remove(cache,key) 之后 get(cache,key) 为 null")
    void removeThenGetIsNull() {
        String key = "k-del-" + System.nanoTime();
        LegacyCacheKit.put(CACHE, key, "v-del");
        assertEquals("v-del", LegacyCacheKit.get(CACHE, key));

        LegacyCacheKit.remove(CACHE, key);
        assertNull(LegacyCacheKit.get(CACHE, key), "remove 后必须取不到");
    }

    /**
     * 覆盖值的类型不被改变（泛型转换不得引入额外语义）。
     */
    @Test
    @DisplayName("取值类型不变：放入 Integer 取回仍是 Integer")
    void valueTypeIsPreserved() {
        String key = "k-type-" + System.nanoTime();
        LegacyCacheKit.put(CACHE, key, 42);
        Object got = LegacyCacheKit.get(CACHE, key);
        assertEquals(Integer.valueOf(42), got, "值语义必须原样保留");
    }

    /**
     * 本判据用到的缓存名常量（取自 BaseCache，避免在此硬编码字符串而与实现漂移）。
     *
     * <p>之所以不直接引用 {@code cn.eova.common.base.BaseCache}：该类在 eova-core，
     * 而 eova-compat 不能反向依赖 core。故此处用同一份字面量并以断言固定其一致性 ——
     * 缓存名常量本身已由 {@code CacheSeamGoldenTest} 的目录完整性判据覆盖。</p>
     */
    private static final class BaseCacheNames {
        /** 对应 BaseCache.SYS = "sys" */
        static final String SYS = "sys";
    }

}
