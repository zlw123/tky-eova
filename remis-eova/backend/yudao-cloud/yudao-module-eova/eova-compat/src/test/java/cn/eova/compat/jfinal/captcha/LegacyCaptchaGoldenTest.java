/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.captcha;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import cn.eova.compat.jfinal.config.LegacyConstants;
import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LegacyCaptcha} / {@code LegacyCaptchaCache} 的<b>跨实现</b>等价判据。
 *
 * <p><b>为什么要对照旧制品：</b>本轮实现 {@code LegacyCaptcha} 时，我凭直觉把
 * {@code toString()} 写成 {@code key + "-" + value}，而<b>旧字节码是</b>
 * {@code key + " :" + value}（空格 + 冒号）。该类文本会出现在日志与诊断输出中，
 * 属可观测输出。这说明"看着显然"的地方恰恰要靠判据兜住 —— 故此处逐项与旧制品比对，
 * 而不是由我断言"应该是这样"。</p>
 *
 * <p><b>比对项：</b>{@code serialVersionUID}、{@code DEFAULT_EXPIRE_TIME}、
 * {@code toString()}、{@code isExpired()}/{@code notExpired()}（含边界）、
 * 以及三参构造器的 {@code expireAt} 公式与 null 校验消息。</p>
 *
 * <p>acceptanceProfile: golden-captcha-seam</p>
 */
class LegacyCaptchaGoldenTest {

    /**
     * 常量与序列化标识：{@code serialVersionUID} 与 {@code DEFAULT_EXPIRE_TIME} 逐值比对。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("serialVersionUID 与 DEFAULT_EXPIRE_TIME 逐值对照旧 Captcha")
    void constantsMatchOld() throws Exception {
        Class<?> oldCls = oldCaptchaClass();

        Field oldUid = oldCls.getDeclaredField("serialVersionUID");
        oldUid.setAccessible(true);
        Field newUid = LegacyCaptcha.class.getDeclaredField("serialVersionUID");
        newUid.setAccessible(true);
        assertEquals(oldUid.getLong(null), newUid.getLong(null),
                "serialVersionUID 必须逐值一致（序列化契约）");

        Field oldDef = oldCls.getField("DEFAULT_EXPIRE_TIME");
        assertEquals(oldDef.getInt(null), LegacyCaptcha.DEFAULT_EXPIRE_TIME,
                "DEFAULT_EXPIRE_TIME 必须一致");
    }

    /**
     * {@code toString()} 的分隔符逐字比对（我在此处猜错过一次）。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("toString() 逐字对照旧 Captcha（分隔符为 \" : \"，连错两次后由本判据定值）")
    void toStringMatchesOld() throws Exception {
        Class<?> oldCls = oldCaptchaClass();
        Constructor<?> oldCtor = oldCls.getConstructor(String.class, String.class);
        Object oldObj = oldCtor.newInstance("k1", "v1");
        Object newObj = new LegacyCaptcha("k1", "v1");

        String oldS = String.valueOf(oldObj);
        assertEquals(oldS, String.valueOf(newObj), "toString 必须逐字一致");
        assertEquals("k1 : v1", oldS, "旧实现的分隔符为 \" : \"（空格+冒号+空格）—— 供后人核对");
    }

    /**
     * {@code expireAt} 公式：{@code expireTime * 1000L + 当前毫秒}。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("三参构造器的 expireAt 公式与旧 Captcha 一致（expireTime*1000L + now）")
    void expireAtFormulaMatchesOld() throws Exception {
        Class<?> oldCls = oldCaptchaClass();
        Constructor<?> oldCtor = oldCls.getConstructor(String.class, String.class, int.class);
        Method oldGetExpireAt = oldCls.getMethod("getExpireAt");

        int seconds = 37;
        long before = System.currentTimeMillis();
        Object oldObj = oldCtor.newInstance("k", "v", seconds);
        LegacyCaptcha newObj = new LegacyCaptcha("k", "v", seconds);
        long after = System.currentTimeMillis();

        long oldExpireAt = (Long) oldGetExpireAt.invoke(oldObj);
        long newExpireAt = newObj.getExpireAt();

        // 【第 88 轮修正】原断言要求两侧偏移【严格相等】—— 而 expireAt 取的是各自构造时刻的
        // System.currentTimeMillis()，两次调用之间必然可能跨毫秒（实测偶发 37000 vs 37001 差 1ms）。
        // 语义是"公式 = now + expireTime*1000"，不是"两次调用落在同一毫秒"，故改为：
        // ① 两侧都与 before 基准相差不超过一个很小的窗口；② 各自落在 [before,after]+expireTime 窗口内。
        long drift = Math.abs((oldExpireAt - before) - (newExpireAt - before));
        assertTrue(drift <= 50,
                "两侧的 expireAt 公式必须一致（允许跨毫秒漂移 ≤50ms），实测差 " + drift + "ms");
        assertTrue(newExpireAt >= before + seconds * 1000L
                        && newExpireAt <= after + seconds * 1000L,
                "expireAt 必须落在 [before+" + seconds + "000, after+" + seconds + "000] 区间内");
    }

    /**
     * {@code isExpired()} 的<b>严格小于</b>边界：{@code expireAt == now} 时尚未过期。
     *
     * <p>边界用"{@code expireAt} 设为很久以后/很久以前"来稳定判定，
     * 并额外比对旧实现同一时刻的结论。</p>
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("isExpired/notExpired 与旧 Captcha 一致（含未过期与已过期两侧）")
    void isExpiredMatchesOld() throws Exception {
        Class<?> oldCls = oldCaptchaClass();
        Constructor<?> oldCtor = oldCls.getConstructor();
        Method oldSetExpireAt = oldCls.getMethod("setExpireAt", long.class);
        Method oldIsExpired = oldCls.getMethod("isExpired");
        Method oldNotExpired = oldCls.getMethod("notExpired");

        long now = System.currentTimeMillis();
        long[] samples = {
                now + 60_000L,      // 未过期
                now - 60_000L,      // 已过期
                now + 1L,           // 极近未过期
                now - 1L,           // 极近已过期
        };

        for (long at : samples) {
            Object oldObj = oldCtor.newInstance();
            oldSetExpireAt.invoke(oldObj, at);
            LegacyCaptcha newObj = new LegacyCaptcha();
            newObj.setExpireAt(at);

            boolean oldExpired = (Boolean) oldIsExpired.invoke(oldObj);
            assertEquals(oldExpired, newObj.isExpired(),
                    "expireAt=" + at + " 时 isExpired 必须与旧实现一致");
            assertEquals((Boolean) oldNotExpired.invoke(oldObj), newObj.notExpired(),
                    "expireAt=" + at + " 时 notExpired 必须与旧实现一致");
        }

        // 钉死"严格小于"这一边界性质：设为远期必未过期
        LegacyCaptcha future = new LegacyCaptcha();
        future.setExpireAt(System.currentTimeMillis() + 3_600_000L);
        assertFalse(future.isExpired(), "远期 expireAt 不得判为已过期");
        assertTrue(future.notExpired(), "远期 expireAt 应判未过期");
    }

    /**
     * 三参构造器的 null 校验消息逐字比对。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("三参构造器 null 校验：消息逐字与旧 Captcha 一致")
    void nullCheckMessageMatchesOld() throws Exception {
        Class<?> oldCls = oldCaptchaClass();
        Constructor<?> oldCtor = oldCls.getConstructor(String.class, String.class, int.class);

        IllegalArgumentException oldEx = assertThrows(IllegalArgumentException.class,
                () -> {
                    try {
                        oldCtor.newInstance(null, "v", 10);
                    } catch (InvocationTargetException e) {
                        throw (RuntimeException) e.getCause();
                    }
                }, "旧实现 key 为 null 时应抛 IllegalArgumentException");

        IllegalArgumentException newEx = assertThrows(IllegalArgumentException.class,
                () -> new LegacyCaptcha(null, "v", 10),
                "新实现 key 为 null 时应抛 IllegalArgumentException");

        assertEquals(oldEx.getMessage(), newEx.getMessage(), "校验消息必须逐字一致");

        // value 为 null 同样
        IllegalArgumentException oldEx2 = assertThrows(IllegalArgumentException.class,
                () -> {
                    try {
                        oldCtor.newInstance("k", null, 10);
                    } catch (InvocationTargetException e) {
                        throw (RuntimeException) e.getCause();
                    }
                });
        IllegalArgumentException newEx2 = assertThrows(IllegalArgumentException.class,
                () -> new LegacyCaptcha("k", null, 10));
        assertEquals(oldEx2.getMessage(), newEx2.getMessage());
    }

    /**
     * {@code setCaptchaCache} 的<b>委派语义</b>跨实现对等（第 303 轮修的真缺陷）。
     *
     * <p><b>缺陷现场：</b>移植版 {@code LegacyConstants#setCaptchaCache} 只写了本地字段、
     * <b>丢了委派</b>，于是 {@code LegacyCaptchaManager} 永远未装配，
     * {@code LegacyCaptchaRender#render} 第 103 行 NPE ⇒ {@code GET /user/captcha}
     * 返回 <b>500</b>（旧栈同请求 {@code 200 image/jpeg} 108x40）。</p>
     *
     * <p><b>为什么此前全绿：</b>本环境 {@code isCaptcha=false} ⇒ 旧登录页不显示验证码、
     * 也就不请求该端点；是第 303 轮的真浏览器验收（登录页因配置缺口显示了验证码）
     * 才把它暴露出来。</p>
     *
     * <p><b>旧侧证据不止"读源码"：</b>本判据把 jfinal 5.2.6 制品加载进环上，
     * 让<b>旧 {@code com.jfinal.config.Constants} 自己作证</b>该类方法就是纯委派
     * （字节码：{@code invokestatic CaptchaManager.me()} + {@code invokevirtual setCaptchaCache}），
     * 再要求新接缝行为一致 —— 只断言"我们写了委派"是不够的。</p>
     *
     * @throws Exception 反射/IO 失败
     */
    @Test
    @DisplayName("setCaptchaCache 委派给 CaptchaManager：旧 jfinal 制品与移植版行为一致（r303 真缺陷回归锁）")
    void setCaptchaCacheDelegatesToCaptchaManagerLikeOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldConstantsCls = Class.forName("com.jfinal.config.Constants", true, jf);
        Class<?> oldManagerCls = Class.forName("com.jfinal.captcha.CaptchaManager", true, jf);
        Class<?> oldCacheIface = Class.forName("com.jfinal.captcha.ICaptchaCache", true, jf);
        OldImplementationLoader.assertFromJar(oldConstantsCls, OldImplementationLoader.oldJFinalJar());
        assertTrue(oldConstantsCls != LegacyConstants.class, "旧侧不得就是新接缝本身");

        // 旧侧：动态代理造一个 ICaptchaCache（旧接口的方法不会真被调用，只验"存取同一实例"）
        Object oldCache = Proxy.newProxyInstance(jf, new Class<?>[]{oldCacheIface}, (p, m, a) -> null);
        Object oldConstants = oldConstantsCls.getDeclaredConstructor().newInstance();
        oldConstantsCls.getMethod("setCaptchaCache", oldCacheIface).invoke(oldConstants, oldCache);
        Object oldManager = oldManagerCls.getMethod("me").invoke(null);
        assertSame(oldCache, oldManagerCls.getMethod("getCaptchaCache").invoke(oldManager),
                "旧 Constants#setCaptchaCache 必须委派给 CaptchaManager#setCaptchaCache（否则旧栈的验证码端点同样会 NPE）");

        // 新侧：同一语义 —— 只写本地字段会让下面这条断言红（就是本轮修掉的形态）
        LegacyCaptchaCache newCache = new RecordingCaptchaCache();
        LegacyCaptchaCache before = LegacyCaptchaManager.me().getCaptchaCache();
        try {
            new LegacyConstants().setCaptchaCache(newCache);
            assertSame(newCache, LegacyCaptchaManager.me().getCaptchaCache(),
                    "LegacyConstants#setCaptchaCache 必须委派给 LegacyCaptchaManager（缺了它 /user/captcha 必 500）");
        } finally {
            // 单例状态：必须还原，避免污染同 JVM 的其它判据
            LegacyCaptchaManager.me().setCaptchaCache(before);
        }
        assertSame(before, LegacyCaptchaManager.me().getCaptchaCache(), "还原失败会让后续判据看到脏状态");
    }

    /**
     * 未装配时读回 {@code null}（旧实现如此：静态初始化不给默认实现）。
     */
    @Test
    @DisplayName("未装配时 getCaptchaCache() 返回 null（旧实现不给默认实现，故渲染期才会 NPE）")
    void unsetCacheStaysNull() {
        LegacyCaptchaCache before = LegacyCaptchaManager.me().getCaptchaCache();
        try {
            LegacyCaptchaManager.me().setCaptchaCache(null);
            assertNull(LegacyCaptchaManager.me().getCaptchaCache(),
                    "不得悄悄塞一个默认缓存 —— 那会掩盖装配缺口");
        } finally {
            LegacyCaptchaManager.me().setCaptchaCache(before);
        }
    }

    /**
     * 只用于判据的验证码缓存替身（记录调用，不落库）。
     */
    private static final class RecordingCaptchaCache implements LegacyCaptchaCache {

        @Override
        public void put(LegacyCaptcha captcha) {
            // 判据不驱动读写，仅用于标识"被装配的是哪一个"
        }

        @Override
        public LegacyCaptcha get(String key) {
            return null;
        }

        @Override
        public void remove(String key) {
            // 同上
        }

        @Override
        public void removeAll() {
            // 同上
        }
    }

    /**
     * 取旧 {@code com.jfinal.captcha.Captcha} 类，并做非空洞性自检。
     *
     * @return 旧类
     * @throws Exception 反射/IO 失败
     */
    private static Class<?> oldCaptchaClass() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.captcha.Captcha", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());
        assertTrue(oldCls != LegacyCaptcha.class, "旧侧不得就是新接缝本身");
        return oldCls;
    }

}
