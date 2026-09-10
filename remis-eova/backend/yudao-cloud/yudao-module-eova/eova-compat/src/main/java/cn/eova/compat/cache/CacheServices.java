/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.cache;

/**
 * 缓存实现的静态持有者：让 compat 层（含 {@code EovaGateways}）也能取到缓存，
 * 而不必依赖 core 的 {@code BaseCache}（依赖方向 compat ← core）。
 *
 * <p>{@code cn.eova.common.base.BaseCache}（core）的缓存解析已委托到本类，
 * 形成单一事实源，避免"core 一套、compat 一套"的双份持有者漂移
 * —— 与 {@code EovaGateways} 对网关的角色相同。
 */
public final class CacheServices {

    private static volatile CacheService instance;

    private CacheServices() {
    }

    /**
     * 设置缓存实现（启动时注入）
     */
    public static void set(CacheService service) {
        instance = service;
    }

    /**
     * 取缓存实现；未设置时按默认实现惰性初始化（读 classpath 的 ehcache.xml）
     */
    public static CacheService get() {
        CacheService s = instance;
        if (s == null) {
            synchronized (CacheServices.class) {
                s = instance;
                if (s == null) {
                    s = EhCacheService.fromClasspath();
                    instance = s;
                }
            }
        }
        return s;
    }

    /**
     * 清空（仅供测试隔离）
     */
    public static void clear() {
        instance = null;
    }
}
