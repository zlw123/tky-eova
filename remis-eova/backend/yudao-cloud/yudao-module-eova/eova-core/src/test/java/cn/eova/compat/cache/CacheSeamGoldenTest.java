/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.cache;

import cn.eova.common.base.BaseCache;
import cn.eova.testkit.OldImplementationLoader;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存接缝（{@link CacheService} / {@link EhCacheService} / {@link BaseCache}）的等价判据。
 *
 * <p><b>为什么需要它：</b>缓存在 EOVA 里不是可随意替换的装饰 —— 每个 cache 名都带明确策略，
 * 其中 {@code meta}/{@code service} 的 {@code timeToLiveSeconds="1"} 决定
 * "元数据改动约 1 秒内生效"，{@code login}/{@code player} 的 {@code timeToIdleSeconds="1800"}
 * 决定登录态空闲失效，多数 cache 开着 {@code copyOnRead}/{@code copyOnWrite}
 * （缓存里存的是<b>快照</b>）。用朴素 Map 实现会让这些语义静默改变。
 *
 * <p><b>判据口径分三层：</b>
 * <ol>
 *   <li><b>配置制品保真</b>：新模块的 {@code ehcache.xml} 与旧 demo 的配置制品
 *       <b>逐字节一致</b>。这一条最强 —— 策略等价不靠人工誊抄比对，而由字节相等蕴含。
 *       同时也避免了 R43 那类"据片段推断"的风险。</li>
 *   <li><b>目录完整性</b>：{@code BaseCache} 的每个 cache 名常量都必须在配置中<b>真实存在</b>。
 *       这是真实缺陷类 —— 名字写错会让运行时抛"未配置"而不是缓存失效，
 *       而静态检查看不出来。</li>
 *   <li><b>行为与所配策略一致</b>：断言从配置里<b>读出来</b>的 TTL/copy 标志与实际观测行为一致，
 *       而不是把策略值誊抄进测试（誊抄会让测试与配置一起漂移）。</li>
 * </ol>
 *
 * <p>acceptanceProfile: golden-cache-seam
 */
class CacheSeamGoldenTest {

    private static final String NEW_RESOURCE = "ehcache.xml";

    private static EhCacheService service;
    private static CacheManager manager;

    @BeforeAll
    static void setUp() {
        service = EhCacheService.fromClasspath();
        manager = service.cacheManager();
    }

    @AfterAll
    static void tearDown() {
        EhCacheService.shutdown();
    }

    @Test
    @DisplayName("配置制品保真：新模块 ehcache.xml 与旧 demo 制品逐字节一致")
    void configArtifactIsByteIdentical() throws Exception {
        Path oldConfig = OldImplementationLoader.locateRepoRoot()
                .resolve("meta-eova/eova/demo/src/main/resources/ehcache.xml");
        Assumptions.assumeTrue(Files.isRegularFile(oldConfig), "旧配置制品缺失：" + oldConfig);

        URL url = getClass().getClassLoader().getResource(NEW_RESOURCE);
        assertNotNull(url, "classpath 下未找到 " + NEW_RESOURCE);
        byte[] oldBytes = Files.readAllBytes(oldConfig);
        byte[] newBytes = Files.readAllBytes(Path.of(url.toURI()));

        assertArrayEquals(oldBytes, newBytes,
                "ehcache.xml 必须与旧制品逐字节一致 —— 策略等价由字节相等蕴含，"
                        + "任何改动都会静默改变缓存过期与 copy 语义");
        System.out.println("[缓存接缝] ehcache.xml 与旧制品逐字节一致，长度 " + oldBytes.length + " 字节");
    }

    @Test
    @DisplayName("目录完整性：BaseCache 的每个 cache 名常量都必须在 ehcache.xml 中真实存在")
    void everyConstantNamesARealCache() throws Exception {
        List<String> names = new ArrayList<>();
        for (Field f : BaseCache.class.getDeclaredFields()) {
            if (f.getType() == String.class && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                names.add((String) f.get(null));
            }
        }
        assertTrue(names.size() >= 8, "BaseCache 的 cache 名常量应至少 8 个，实际 " + names.size());

        List<String> missing = new ArrayList<>();
        for (String name : names) {
            if (manager.getCache(name) == null) {
                missing.add(name);
            }
        }
        System.out.println("[缓存接缝] BaseCache 常量 " + names.size() + " 个，其中未配置的 " + missing.size());
        assertTrue(missing.isEmpty(),
                "以下 cache 名在 ehcache.xml 中不存在（名字写错会导致运行时抛错而非缓存失效）：" + missing);
    }

    @Test
    @DisplayName("过期行为与所配 TTL 一致：meta/service 为 1s 会过期，sys(3600s)/login(TTL=0) 不受 1s 影响")
    void expiryFollowsConfiguredTtl() throws Exception {
        // 从配置读出 TTL，避免把策略值誊抄进测试（誊抄会与配置一起漂移）
        long metaTtl = ttl("meta");
        long serviceTtl = ttl("service");
        long sysTtl = ttl("sys");
        long loginTtl = ttl("login");

        assertEquals(1L, metaTtl, "meta 的 TTL 应为 1s —— 该值决定元数据改动的生效延迟");
        assertEquals(1L, serviceTtl, "service 的 TTL 应为 1s");
        assertEquals(3600L, sysTtl, "sys 的 TTL 应为 3600s（配置如此；TTI 才是 1800）");
        assertEquals(0L, loginTtl, "login 的 TTL 应为 0（仅按空闲过期）");

        service.put("meta", "k", "v");
        service.put("service", "k", "v");
        service.put("sys", "k", "v");
        service.put("login", "k", "v");

        // 等待超过最短 TTL
        Thread.sleep(metaTtl * 1000 + 400);

        assertNull(service.get("meta", "k"), "TTL=1s 的 meta 应已过期");
        assertNull(service.get("service", "k"), "TTL=1s 的 service 应已过期");
        assertEquals("v", service.get("sys", "k"), "TTL=0 的 sys 不应因 1s 而失效");
        assertEquals("v", service.get("login", "k"), "TTL=0 的 login 不应因 1s 而失效");

        service.remove("sys", "k");
        service.remove("login", "k");
        System.out.println("[缓存接缝] 过期行为与配置 TTL 一致（meta/service=1s 已过期；sys=3600s、login=0 仍有效）");
    }

    @Test
    @DisplayName("copy 语义与所配标志一致：copyOnRead 为真的 cache 免疫调用方改动，为假的会受影响")
    void copySemanticsFollowConfiguredFlag() {
        boolean sysCopy = copyOnRead("sys");
        boolean loginCopy = copyOnRead("login");
        assertTrue(sysCopy, "sys 应开启 copyOnRead（配置如此）");
        assertTrue(!loginCopy, "login 未开启 copyOnRead（配置如此）—— 本断言用于确认两类的差异真实存在");

        // copyOnRead=true：取回的是副本，改动不污染缓存
        List<String> v1 = new ArrayList<>(List.of("a"));
        service.put("sys", "list", v1);
        @SuppressWarnings("unchecked")
        List<String> got1 = (List<String>) service.get("sys", "list");
        got1.add("b");
        @SuppressWarnings("unchecked")
        List<String> again1 = (List<String>) service.get("sys", "list");
        assertEquals(1, again1.size(),
                "sys 开启 copyOnRead：调用方改动【不应】污染缓存（否则后续读取结果与旧栈不同）");

        // copyOnRead=false：取回的是引用，改动会反映到缓存
        List<String> v2 = new ArrayList<>(List.of("a"));
        service.put("login", "list", v2);
        @SuppressWarnings("unchecked")
        List<String> got2 = (List<String>) service.get("login", "list");
        got2.add("b");
        @SuppressWarnings("unchecked")
        List<String> again2 = (List<String>) service.get("login", "list");
        assertEquals(2, again2.size(),
                "login 未开 copyOnRead：调用方改动【会】反映到缓存 —— 该不对称属既有行为，必须保留");

        service.remove("sys", "list");
        service.remove("login", "list");
        System.out.println("[缓存接缝] copy 语义与配置一致（sys 隔离 / login 不隔离，不对称属既有行为）");
    }

    @Test
    @DisplayName("BaseCache 方法到 cache 名的映射：sys 与 service 两组互不串扰")
    void baseCacheMapsToRightCaches() {
        BaseCache.setCacheService(service);

        BaseCache.put("k", "sysVal");
        BaseCache.putSer("k", "serVal");

        assertEquals("sysVal", BaseCache.get("k"), "get 应读 sys 缓存");
        assertEquals("serVal", BaseCache.getSer("k"), "getSer 应读 service 缓存");
        assertNull(service.get(BaseCache.META, "k"), "不应写入 meta 缓存");

        BaseCache.del("k");
        assertNull(BaseCache.get("k"), "del 应删除 sys 中的键");
        assertEquals("serVal", BaseCache.getSer("k"), "del 不应影响 service 缓存");

        BaseCache.delSer("k");
        assertNull(BaseCache.getSer("k"), "delSer 应删除 service 中的键");

        // 基本读写语义
        service.put("waf_ban", "1.2.3.4", Boolean.TRUE);
        assertEquals(Boolean.TRUE, service.get("waf_ban", "1.2.3.4"));
        service.remove("waf_ban", "1.2.3.4");
        assertNull(service.get("waf_ban", "1.2.3.4"), "remove 后取值应为 null");
        assertNull(service.get("waf_ban", "不存在"), "缺键取值应为 null");

        System.out.println("[缓存接缝] BaseCache 的 sys/service 映射与互不串扰已确认");
    }

    @Test
    @DisplayName("同键不同 cache 互不影响（session 与元数据不会互相覆盖）")
    void cachesAreIsolated() {
        service.put("login", "same", "loginVal");
        service.put("sys", "same", "sysVal");
        service.put("meta", "same", "metaVal");

        assertEquals("loginVal", service.get("login", "same"));
        assertEquals("sysVal", service.get("sys", "same"));
        assertEquals("metaVal", service.get("meta", "same"));

        service.remove("sys", "same");
        assertEquals("loginVal", service.get("login", "same"), "删除 sys 不应影响 login");

        service.remove("login", "same");
        service.remove("meta", "same");
        System.out.println("[缓存接缝] 同名 cache 间隔离已确认");
    }

    // ———————————————————————— 辅助：从配置读策略 ————————————————————————

    private static CacheConfiguration cfg(String name) {
        Cache c = manager.getCache(name);
        assertNotNull(c, "ehcache.xml 中未配置 cache：" + name);
        return c.getCacheConfiguration();
    }

    private static long ttl(String name) {
        return cfg(name).getTimeToLiveSeconds();
    }

    private static boolean copyOnRead(String name) {
        return cfg(name).isCopyOnRead();
    }
}
