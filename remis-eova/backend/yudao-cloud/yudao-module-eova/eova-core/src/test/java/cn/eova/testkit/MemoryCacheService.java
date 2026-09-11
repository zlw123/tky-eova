/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.testkit;

import java.util.HashMap;
import java.util.Map;

import cn.eova.compat.cache.CacheService;

/**
 * 内存缓存实现（判据用）。
 *
 * <p><b>为什么要它（R58 的产物）：</b>判据一旦依赖全局缓存单例（{@code BaseCache} 经
 * {@code CacheServices} 取实现），就会"单独跑绿、全量跑红" —— 同 JVM 里别的用例
 * 关掉 EhCache 的 {@code CacheManager} 后，本用例会报
 * {@code IllegalStateException: The CacheManager has been shut down}。
 * 故需要缓存参与的判据统一注入本实现，用例结束后经
 * {@code CacheServices.set(原值)} 还原现场。</p>
 */
public class MemoryCacheService implements CacheService {

    /** 存储：cacheName → (key → value) */
    private final Map<String, Map<Object, Object>> store = new HashMap<>();

    @Override
    public Object get(String cacheName, Object key) {
        Map<Object, Object> m = store.get(cacheName);
        return m == null ? null : m.get(key);
    }

    @Override
    public void put(String cacheName, Object key, Object value) {
        store.computeIfAbsent(cacheName, k -> new HashMap<>()).put(key, value);
    }

    @Override
    public void remove(String cacheName, Object key) {
        Map<Object, Object> m = store.get(cacheName);
        if (m != null) {
            m.remove(key);
        }
    }
}
