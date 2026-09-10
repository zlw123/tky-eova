/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.cache;

/**
 * 缓存宿主能力的接缝（阶段 1 `D-MODEL` 支撑单元）。
 *
 * <p><b>存在理由：</b>旧栈的缓存由 JFinal 的 {@code EhCachePlugin} +
 * {@code com.jfinal.plugin.ehcache.CacheKit} 提供，{@code CacheKit} 在新栈中缺失（R39）。
 * EOVA 只用到它的 3 个方法 —— {@code get} / {@code put} / {@code remove}
 * （实测共 23 处调用，分布见 {@code WAFHandler}、{@code UserController}、
 * {@code LoginService}、{@code BaseCache}）。
 *
 * <p><b>为什么阶段 1 不改用内存 Map 或 Redis：</b>缓存在 EOVA 中不是可随意替换的装饰 ——
 * 每个 cache 名都带明确策略（见 {@code ehcache.xml}），其中
 * {@code meta}/{@code service} 的 {@code timeToLiveSeconds="1"} 决定了
 * "元数据改动约 1 秒内生效"，{@code login}/{@code player} 的
 * {@code timeToIdleSeconds="1800"} 决定登录态空闲失效，
 * 多数 cache 还开了 {@code copyOnRead}/{@code copyOnWrite}（缓存里存的是<b>快照</b>，
 * 调用方改动不会污染缓存）。用朴素 {@code Map} 实现会让这些语义静默改变。
 *
 * <p><b>阶段 1 的策略：把宿主能力关进接缝，实现仍用同一份 EhCache</b>
 * （SP7 实测 {@code ehcache-core 2.6.11} 在 Java 17 上可正常运行），
 * 于是缓存语义<b>按构造等价</b>，无需重新实现 LRU/LFU 与 copy 语义。
 * 替换（Redis / 分布式）属 LC-005，只需替换本接缝的实现，业务代码不受影响 ——
 * 这与 §2.1"阶段 1 把 Enjoy 关进接缝、阶段 2 再拆"是同一策略。
 */
public interface CacheService {

    /**
     * 按 cache 名与键取值；不存在或已过期返回 null
     *
     * @param cacheName cache 名（见 {@code ehcache.xml}）
     * @param key       键
     * @return 值或 null
     */
    Object get(String cacheName, Object key);

    /**
     * 按 cache 名与键存值
     *
     * @param cacheName cache 名
     * @param key       键
     * @param value     值
     */
    void put(String cacheName, Object key, Object value);

    /**
     * 按 cache 名与键删除
     *
     * @param cacheName cache 名
     * @param key       键
     */
    void remove(String cacheName, Object key);
}
