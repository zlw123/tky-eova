/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import cn.eova.compat.cache.CacheServices;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源网关的静态注册表：{@code Db.use(<ds>)} 的等价入口。
 *
 * <p><b>存在理由：</b>旧实现里 {@code Db.use(ds)} 是<b>静态</b>入口，任何类都能用 ——
 * 不只 `Model` 子类。实测反例：{@code cn.eova.core.meta.ColumnMeta} 是普通 POJO，
 * 却直接调用 {@code Db.use(this.ds).delete(...)} / {@code .save(...)}。
 * 故网关不能只挂在 {@link EovaModel} 上（那样非模型类无从取用）。
 *
 * <p>本类同时是网关解析的<b>单一事实源</b>：{@link EovaModel} 的数据源解析也委托到这里，
 * 避免"模型一套、非模型一套"的双份注册表漂移。
 *
 * <p><b>语义：</b>
 * <ul>
 *   <li>{@link #get(String)}：按数据源名取；未注册时回落到默认网关；都没有则明确报错
 *       （不静默给一个"看似能用"的网关 —— 那会跨库写错数据）。</li>
 *   <li>与旧 {@code Db.use(ds)} 的差别：旧实现在 ds 未注册时也会抛
 *       （{@code DbKit} 取不到 Config）。此处同样报错，方向一致。</li>
 * </ul>
 */
public final class EovaGateways {

    /** 数据源名 → 网关 */
    private static final Map<String, EovaDbGateway> BY_CONFIG = new LinkedHashMap<>();

    /** 默认网关（单数据源场景，或未按名注册时的回落） */
    private static volatile EovaDbGateway fallback;

    private EovaGateways() {
    }

    /**
     * 按数据源名注册网关（启动时对每个数据源各注入一个）
     *
     * @param configName 数据源名
     * @param gw         该数据源的网关
     */
    public static void register(String configName, EovaDbGateway gw) {
        synchronized (BY_CONFIG) {
            BY_CONFIG.put(configName, gw);
        }
    }

    /**
     * 设置默认网关
     *
     * @param gw 默认网关
     */
    public static void setFallback(EovaDbGateway gw) {
        fallback = gw;
    }

    /**
     * 取默认网关
     */
    public static EovaDbGateway fallback() {
        return fallback;
    }

    /**
     * 按数据源名<b>精确查找</b>网关；未注册返回 {@code null}（<b>不回落</b>默认网关）。
     *
     * <p><b>为什么必须与 {@link #get(String)} 并存（DES-DB-OWNERSHIP-R2 §5）：</b>
     * 两者语义不同且都不可少 ——
     * <ul>
     *   <li>{@code get(ds)}：业务代码用，未注册时<b>回落</b>默认网关
     *       （单数据源场景友好，且与旧 {@code Db.use(ds)} 的可用性方向一致）；</li>
     *   <li>{@code find(ds)}：<b>需要"未注册"这一事实本身</b>的调用方用。
     *       典型是 {@code DsUtil.getConnection(ds)} —— 旧实现是
     *       {@code DbKit.getConfig(ds)}，jfinal 的实现就是 {@code configMap.get(name)}
     *       （<b>无任何回落</b>），取不到即抛
     *       {@code SQLException(ds + " datasrouce can not get config")}。
     *       若此处用 {@code get(ds)}，未注册的数据源会静默去连<b>默认库</b> ——
     *       表结构自省会读错库，且错得很安静。</li>
     * </ul>
     *
     * <p>语义对齐：{@code find(null)} 返回 {@code null}（与 jfinal
     * {@code DbKit.getConfig(null)} 的查表结果一致）。</p>
     *
     * @param configName 数据源名
     * @return 该数据源的网关；未注册返回 {@code null}
     */
    public static EovaDbGateway find(String configName) {
        if (configName == null) {
            return null;
        }
        synchronized (BY_CONFIG) {
            return BY_CONFIG.get(configName);
        }
    }

    /**
     * 按数据源名取网关；未注册该数据源时回落到默认网关
     *
     * @param configName 数据源名，可为 null（此时直接用默认网关）
     * @return 网关
     * @throws IllegalStateException 两者都不可用时
     */
    public static EovaDbGateway get(String configName) {
        if (configName != null) {
            synchronized (BY_CONFIG) {
                EovaDbGateway byDs = BY_CONFIG.get(configName);
                if (byDs != null) {
                    return byDs;
                }
            }
        }
        EovaDbGateway fb = fallback;
        if (fb == null) {
            throw new IllegalStateException(
                    "未注册数据源网关（数据源=" + configName + "）—— 请由 eova-db-adapter 在启动时注入");
        }
        return fb;
    }

    /**
     * 清空注册（仅供测试隔离）
     */
    /**
     * 带缓存的查询（对应 jfinal 静态 {@code Db.findByCache}）。
     *
     * <p>语义与旧实现一致：先按 {@code (cacheName, key)} 取缓存；未命中则查库并回填
     * （旧 {@code Db.findByCache} 同样用 service 缓存）。
     *
     * @param cacheName 缓存名
     * @param key       缓存键
     * @param sql       查询语句
     * @param paras     参数
     * @return 记录列表（未命中时是刚查出的列表，命中时是缓存中的实例）
     */
    @SuppressWarnings("unchecked")
    public static List<EovaRecord> findByCache(String cacheName, Object key, String sql,
                                               Object... paras) {
        Object cached = CacheServices.get().get(cacheName, key);
        if (cached != null) {
            return (List<EovaRecord>) cached;
        }
        List<EovaRecord> list = get(null).find(sql, paras);
        CacheServices.get().put(cacheName, key, list);
        return list;
    }

    /**
     * 执行更新/DDL（对应静态 {@code Db.update(sql)}，走默认数据源）
     *
     * @param sql 语句
     * @return 受影响行数
     */
    public static int update(String sql) {
        return get(null).update(sql);
    }

    /**
     * 查询单列首行并转字符串（对应 jfinal 静态 {@code Db.queryStr}，走默认数据源）
     *
     * @param sql   查询语句
     * @param paras 参数
     * @return 字符串；无命中返回 {@code null}
     */
    public static String queryStr(String sql, Object... paras) {
        return get(null).queryStr(sql, paras);
    }

    /**
     * 查询单列首行（对应 jfinal 静态 {@code Db.queryColumn}，走默认数据源）
     *
     * @param sql   查询语句
     * @param paras 参数
     * @param <T>   列值类型
     * @return 首行首列值；无命中返回 {@code null}
     */
    public static <T> T queryColumn(String sql, Object... paras) {
        return get(null).queryColumn(sql, paras);
    }

    public static void clear() {
        synchronized (BY_CONFIG) {
            BY_CONFIG.clear();
        }
        fallback = null;
    }

    /**
     * 已注册的数据源名快照（供诊断与判据使用）
     */
    public static Map<String, EovaDbGateway> snapshot() {
        synchronized (BY_CONFIG) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(BY_CONFIG));
        }
    }
}
