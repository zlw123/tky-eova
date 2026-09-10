/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.base;

import cn.eova.compat.cache.CacheService;
import cn.eova.compat.cache.CacheServices;

/**
 * <p>ported from: cn.eova.common.base.BaseCache
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头，并把
 * {@code com.jfinal.plugin.ehcache.CacheKit} 替换为 {@link CacheService} 接缝。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>8 个 cache 名常量及其<b>取值</b>属对外契约（{@code login} / {@code loginid} /
 *       {@code sys} / {@code meta} / {@code service} / {@code player} /
 *       {@code waf_404} / {@code waf_ban}）—— 这些名字直接对应 {@code ehcache.xml} 中的
 *       cache 定义，改名会导致缓存策略丢失</li>
 *   <li>全部方法为 {@code static}（旧实现如此，调用方如 {@code MetaField}、{@code Menu}
 *       均以静态方式调用）</li>
 *   <li>{@code private getCache(cacheName, key)} 为内部转发点，保持私有</li>
 * </ol>
 *
 * <p><b>唯一的适配：</b>{@code CacheKit} → {@link CacheService}。
 * 旧 {@code CacheKit.get/put/remove} 与接缝方法一一对应，故语义不变；
 * 具体策略（TTI/TTL/copy）仍由逐字节 port 的 {@code ehcache.xml} 决定。
 */
public class BaseCache {

    /** 登录专用Cache, 空闲 30 分钟清除 **/
    public static final String LOGIN = "login";
    /** 登录专用Cache, 空闲 30 分钟清除 **/
    public static final String LOGIN_ERROR = "loginid";
    /** 本系统默认CacheName-空闲30Min超时,60Min有效,最少使用策略 **/
    public static final String SYS = "sys";
    /** 元数据 **/
    public static final String META = "meta";
    /** Service CacheName-10s有效,最近最少使用策略 **/
    public static final String SER = "service";
    /** Player CacheName-空闲30Min超时,永久有效,最少使用策略 **/
    public static final String PLAYER = "player";
    /** 404 WAF 计数（按 IP） **/
    public static final String WAF_404 = "waf_404";
    /** 404 WAF 软封禁（按 IP，TTL 到期自动解封） **/
    public static final String WAF_BAN = "waf_ban";

    // 缓存实现的持有者已上移到 cn.eova.compat.cache.CacheServices（单一事实源），
    // 因为 compat 层的 EovaGateways.findByCache 也需要取缓存，而 compat 不能依赖 core。

    /**
     * 设置缓存实现（容器启动时注入；阶段 3 换成 Redis 实现即可，业务代码不变）
     *
     * @param service 缓存实现
     */
    public static void setCacheService(CacheService service) {
        CacheServices.set(service);
    }

    /** 取缓存实现（委托 CacheServices，单一事实源） */
    private static CacheService service() {
        return CacheServices.get();
    }

    private static Object getCache(String cacheName, String key) {
        return service().get(cacheName, key);
    }

    // System Cache方法
    public static Object get(String key) {
        return getCache(SYS, key);
    }

    public static void put(String key, Object value) {
        service().put(SYS, key, value);
    }

    public static void del(String key) {
        service().remove(SYS, key);
    }

    // Service Cache方法
    public static Object getSer(String key) {
        return getCache(SER, key);
    }

    public static void putSer(String key, Object value) {
        service().put(SER, key, value);
    }

    public static void delSer(String key) {
        service().remove(SER, key);
    }

}
