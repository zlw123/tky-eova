/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import cn.eova.compat.jfinal.core.LegacyActionReporter;
import cn.eova.compat.jfinal.json.LegacyMixedJsonFactory;
import cn.eova.compat.jfinal.kit.LegacyProp;
import cn.eova.compat.jfinal.kit.LegacyPropKit;
import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W3 接缝族（第 82 轮，数据面）对 jfinal 5.2.6 真制品的**跨实现等价验证**。
 *
 * <p><b>为什么这一族要单独重判：</b>{@link LegacyConstants} 是旧 {@code EovaConfig.configConstant()}
 * 的全部写入目标，它的<b>默认值与校验消息</b>就是"未配置时的系统行为"；
 * {@link LegacyPropKit}/{@link LegacyProp} 决定配置文件加载（dev/test/pre/pro/prd 五档）。
 * 这些是**启动期行为**，一旦与旧栈不一致，整站配置语义就变了 —— 且编译绿、其它判据都不会报。</p>
 *
 * <p><b>验证方式：</b>把旧 jfinal 制品用 {@code OldImplementationLoader} 装进隔离加载器，
 * 逐项比对（默认值、校验消息、Prop 加载语义）；无法用"同一资源文件"比对的部分
 * （PropKit 依赖 classpath 资源）在其加载来源上做等价性断言。</p>
 *
 * acceptanceProfile: golden-legacy-jfinal-constants
 */
class W3ConstantsAndPropGoldenTest {

    private static Class<?> oldConstantsClass;

    private static Class<?> oldPropClass;

    private static Class<?> oldPropKitClass;

    /**
     * 装载旧 jfinal 制品中的三个类。
     *
     * @throws Exception 装载失败
     */
    @BeforeAll
    static void loadOld() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldJFinalJarAvailable(),
                "旧 jfinal 制品缺失：" + OldImplementationLoader.oldJFinalJar());
        ClassLoader loader = OldImplementationLoader.createForJFinalOnly();
        oldConstantsClass = Class.forName("com.jfinal.config.Constants", true, loader);
        oldPropClass = Class.forName("com.jfinal.kit.Prop", true, loader);
        oldPropKitClass = Class.forName("com.jfinal.kit.PropKit", true, loader);
    }

    @AfterEach
    void tearDown() {
        LegacyPropKit.clear();
    }

    /**
     * 自校验：确认参与比对的确实来自 jfinal 制品。
     */
    private static void assertOldArtifact() {
        var src = oldConstantsClass.getProtectionDomain().getCodeSource();
        String loc = src == null || src.getLocation() == null ? "null" : src.getLocation().toString();
        assertTrue(loc.endsWith("jfinal-5.2.6.jar"), "必须来自 jfinal-5.2.6.jar，实际=" + loc);
    }

    /**
     * 取旧侧 getter 值（反射调用）。
     *
     * @param obj    旧 Constants 实例
     * @param getter getter 名
     * @return 值
     * @throws Exception 反射失败
     */
    private static Object oldGet(Object obj, String getter) throws Exception {
        return oldConstantsClass.getMethod(getter).invoke(obj);
    }

    @Test
    @DisplayName("Constants：全部默认值与旧 jfinal 逐项一致")
    void constantsDefaultsMatchOld() throws Exception {
        assertOldArtifact();
        Object old = oldConstantsClass.getDeclaredConstructor().newInstance();
        LegacyConstants now = new LegacyConstants();

        assertEquals(oldGet(old, "getDevMode"), now.getDevMode(), "devMode");
        assertEquals(oldGet(old, "getBaseUploadPath"), now.getBaseUploadPath(), "baseUploadPath");
        assertEquals(oldGet(old, "getBaseDownloadPath"), now.getBaseDownloadPath(), "baseDownloadPath");
        assertEquals(oldGet(old, "getEncoding"), now.getEncoding(), "encoding");
        assertEquals(oldGet(old, "getUrlParaSeparator"), now.getUrlParaSeparator(), "urlParaSeparator");
        assertEquals(oldGet(old, "getViewExtension"), now.getViewExtension(), "viewExtension");
        assertEquals(oldGet(old, "getMaxPostSize"), now.getMaxPostSize(), "maxPostSize");
        assertEquals(oldGet(old, "getFreeMarkerTemplateUpdateDelay"), now.getFreeMarkerTemplateUpdateDelay(),
                "freeMarkerTemplateUpdateDelay");
        assertEquals(oldGet(old, "getConfigPluginOrder"), now.getConfigPluginOrder(), "configPluginOrder");
        assertEquals(oldGet(old, "getDenyAccessJsp"), now.getDenyAccessJsp(), "denyAccessJsp");
        assertEquals(String.valueOf(oldGet(old, "getViewType")), String.valueOf(now.getViewType()),
                "viewType 的名称必须一致（名字即契约，枚举实例不同侧）");
        assertFalse(now.getDevMode(), "旧默认 devMode=false");
        assertEquals(10485760L, now.getMaxPostSize(), "旧默认 10MB");
        assertEquals(3, now.getConfigPluginOrder(), "旧默认 3（EOVA 会改成 1）");
        assertEquals(3600, now.getFreeMarkerTemplateUpdateDelay());
        assertTrue(now.getDenyAccessJsp());
    }

    @Test
    @DisplayName("Constants：四处校验消息与旧 jfinal 逐字一致（含旧的拼写错误）")
    void constantsValidationMessagesMatchOld() throws Exception {
        assertOldArtifact();
        assertEquals(errMsg(oldConstantsClass, "setEncoding", new Class<?>[]{String.class}, ""),
                errMsg(LegacyConstants.class, "setEncoding", new Class<?>[]{String.class}, ""));
        assertEquals("encoding can not be blank.",
                errMsg(LegacyConstants.class, "setEncoding", new Class<?>[]{String.class}, ""));
        assertEquals("baseUploadPath can not be blank.",
                errMsg(LegacyConstants.class, "setBaseUploadPath", new Class<?>[]{String.class}, ""));
        assertEquals("baseDownloadPath can not be blank.",
                errMsg(LegacyConstants.class, "setBaseDownloadPath", new Class<?>[]{String.class}, ""));
        assertEquals("viewType can not be null",
                errMsg(LegacyConstants.class, "setViewType", new Class<?>[]{LegacyViewType.class}, (Object) null));
        // urlParaSeparator：空串与含 '/' 都必须拒绝，且消息里保留旧的拼写错误 "Separtor"
        String sepMsg = errMsg(LegacyConstants.class, "setUrlParaSeparator", new Class<?>[]{String.class}, "/");
        assertEquals("urlParaSepartor can not be blank and can not contains \"/\"", sepMsg);
        assertEquals(sepMsg, errMsg(oldConstantsClass, "setUrlParaSeparator", new Class<?>[]{String.class}, "/"));
    }

    /**
     * 反射调用 setter 并取异常消息。
     *
     * @param cls    目标类
     * @param setter setter 名
     * @param types  形参类型
     * @param arg    实参
     * @return 异常消息
     * @throws Exception 反射失败（或未抛异常时失败）
     */
    private static String errMsg(Class<?> cls, String setter, Class<?>[] types, Object arg) throws Exception {
        Method m = cls.getMethod(setter, types);
        Throwable t = null;
        try {
            Object target = cls == oldConstantsClass
                    ? oldConstantsClass.getDeclaredConstructor().newInstance() : new LegacyConstants();
            m.invoke(target, arg);
        } catch (Exception e) {
            t = e.getCause() == null ? e : e.getCause();
        }
        assertNotNull(t, setter + " 未按契约抛异常");
        return t.getMessage();
    }

    @Test
    @DisplayName("Constants：EOVA 实际写入的每条都能落值（configConstant 用面覆盖）")
    void constantsAcceptsEovaConfigWrites() {
        LegacyConstants me = new LegacyConstants();
        me.setEncoding("UTF-8");
        me.setToJavaAwtHeadless();
        me.setDevMode(true);
        me.setMaxPostSize(1024 * 1024 * 500);
        me.setViewType(LegacyViewType.JFINAL_TEMPLATE);
        me.setResolveJsonRequest(true);
        me.setError403View("/eova/error/403.html");
        me.setError404View("/eova/error/404.html");
        me.setError500View("/eova/error/500.html");
        me.setErrorView(400, "/eova/error/404.html");
        me.setErrorView(503, "/eova/error/503.html");
        me.setBaseUploadPath("/data/eova");
        me.setBaseDownloadPath("/data/eova");
        me.setJsonFactory(LegacyMixedJsonFactory.me().getClass().getName());
        me.setCaptchaCache(new cn.eova.compat.jfinal.captcha.LegacyCaptchaCache() {
            @Override
            public void put(cn.eova.compat.jfinal.captcha.LegacyCaptcha captcha) {
            }

            @Override
            public cn.eova.compat.jfinal.captcha.LegacyCaptcha get(String key) {
                return null;
            }

            @Override
            public void remove(String key) {
            }

            @Override
            public void removeAll() {
            }
        });
        me.setConfigPluginOrder(1);

        assertEquals("/data/eova", me.getBaseUploadPath());
        assertEquals("/data/eova", me.getBaseDownloadPath());
        assertEquals(1024 * 1024 * 500, me.getMaxPostSize());
        assertEquals(1, me.getConfigPluginOrder(), "EOVA 把插件顺序改为 1（在 configConstant 之后装载）");
        assertEquals("/eova/error/403.html", me.getErrorStatusView(403));
        assertEquals("/eova/error/404.html", me.getErrorStatusView(404));
        assertEquals("/eova/error/500.html", me.getErrorStatusView(500));
        assertEquals("/eova/error/404.html", me.getErrorView(400));
        assertEquals("/eova/error/503.html", me.getErrorView(503));
        assertTrue(me.getResolveJsonRequest());
        assertTrue(me.getDevMode());
        assertEquals("true", System.getProperty("java.awt.headless"), "setToJavaAwtHeadless 必须设置系统属性");
        assertNotNull(me.getCaptchaCache());
        assertNotNull(me.getJsonFactoryName());
    }

    @Test
    @DisplayName("Prop/PropKit：加载语义与旧实现一致（含缺失文件的消息与 useFirstFound 回退）")
    void propSemanticsMatchOld() throws Exception {
        assertOldArtifact();
        // 旧侧的 Prop 与 PropKit 都在隔离加载器里，但读取的是同一份 classpath 资源
        Class<?> oldProp = Class.forName("com.jfinal.kit.Prop", true, oldPropClass.getClassLoader());

        // 缺失文件：消息逐字一致（旧消息含 "Properties file not found in classpath: "）
        String oldMsg = ctorErr(oldProp, "eova/__missing__.txt");
        String newMsg = ctorErr(LegacyProp.class, "eova/__missing__.txt");
        assertEquals("Properties file not found in classpath: eova/__missing__.txt", newMsg);
        assertEquals(oldMsg, newMsg, "缺失文件的消息必须与旧实现一致");

        // useFirstFound：全部缺失 ⇒ 抛旧消息
        Method useFirstFound = LegacyPropKit.class.getMethod("useFirstFound", String[].class);
        Exception e = assertThrows(Exception.class, () -> useFirstFound.invoke(null,
                (Object) new String[]{"eova/__missing1__.txt", "eova/__missing2__.txt"}));
        Throwable cause = e.getCause() == null ? e : e.getCause();
        assertEquals("没有配置文件可被使用", cause.getMessage());

        // 绑定 classpath 上的真实资源（判据资源 eova-w3-probe.txt）
        LegacyProp prop = LegacyPropKit.use("eova-w3-probe.txt");
        assertSame(prop, LegacyPropKit.use("eova-w3-probe.txt"), "同一文件必须命中缓存（旧实现如此）");
        assertSame(prop, LegacyPropKit.getProp());
        assertEquals("1", prop.get("probe.int"));
        assertEquals(Integer.valueOf(1), prop.getInt("probe.int"));
        assertEquals(Integer.valueOf(7), prop.getInt("probe.missing", 7));
        assertNull(prop.getInt("probe.notint"), "解析失败返回 null（不抛异常）");
        assertEquals(Boolean.TRUE, prop.getBoolean("probe.bool"));
        assertEquals(Boolean.FALSE, prop.getBoolean("probe.missing", Boolean.FALSE));
        assertEquals("中文值", prop.get("probe.cn"), "UTF-8 读取");
        assertEquals("spaced", prop.get("probe.padded"),
                "命中后必须 trim（属性文件里是 'spaced   ' —— 尾随空格由本接缝去掉）");
        // 与旧制品同资源比对（同一个 classpath 资源，两侧都应 trim）
        Object oldPropInst = oldProp.getConstructor(String.class).newInstance("eova-w3-probe.txt");
        assertEquals("spaced", oldProp.getMethod("get", String.class).invoke(oldPropInst, "probe.padded"),
                "旧 Prop 同样 trim ⇒ 语义等价");
        assertEquals("fallback", prop.get("probe.missing", "fallback"));
        assertTrue(prop.containsKey("probe.int"));
        assertFalse(prop.isEmpty());
        assertNotNull(prop.getProperties());

        // 未装配时 getProp 的旧消息（用 clear 后断言）
        LegacyPropKit.clear();
        IllegalStateException ise = assertThrows(IllegalStateException.class, LegacyPropKit::getProp);
        assertEquals("Load propties file by invoking PropKit.use(String fileName) method first.", ise.getMessage(),
                "旧消息里的拼写错误 propties 必须原样保留");
    }

    /**
     * 构造异常消息（取 cause）。
     *
     * @param cls      目标类
     * @param fileName 文件名
     * @return 异常消息
     * @throws Exception 反射失败
     */
    private static String ctorErr(Class<?> cls, String fileName) throws Exception {
        try {
            cls.getConstructor(String.class).newInstance(fileName);
        } catch (Exception e) {
            Throwable t = e.getCause() == null ? e : e.getCause();
            return t.getMessage();
        }
        throw new AssertionError("未抛异常：" + cls + " " + fileName);
    }

    @Test
    @DisplayName("ActionReporter/MixedJsonFactory：旧默认值与单例语义")
    void reporterAndJsonFactory() {
        assertEquals(50, LegacyActionReporter.getMaxOutputLengthOfParaValue(), "旧默认 50");
        LegacyActionReporter.setTitle("EovaMeta-x action report - ");
        assertEquals("EovaMeta-x action report - ", LegacyActionReporter.getTitle());
        assertSame(LegacyMixedJsonFactory.me(), LegacyMixedJsonFactory.me(), "必须单例");
        List<String> names = new ArrayList<>();
        names.add(LegacyMixedJsonFactory.me().getClass().getName());
        assertEquals(1, names.size());
    }
}
