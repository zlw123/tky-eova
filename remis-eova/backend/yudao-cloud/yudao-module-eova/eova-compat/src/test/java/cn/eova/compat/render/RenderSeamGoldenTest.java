/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code com.jfinal.render.*} 接缝的<b>跨实现等价</b>验证。
 *
 * <p><b>为什么不只测新实现：</b>只断言"新实现自己怎么样"是非判别性的
 * （R45 三类教训之一）—— 断言写得再细，也可能在实现悄悄偏离旧语义时依然为绿。
 * 本测试的做法是：在<b>同一个 JVM</b> 里同时上场
 * <ul>
 *   <li><b>旧侧</b>：{@code OldImplementationLoader.createForJFinalOnly()} 加载的
 *       jfinal 5.2.6 制品（{@code com.jfinal.render.Render / TextRender / ContentType /
 *       RenderException}）；</li>
 *   <li><b>新侧</b>：{@code cn.eova.compat.render.Legacy*}；</li>
 * </ul>
 * 然后对同一输入比对<b>可观测结果</b>：响应调用序列、写出字节、字段与常量。</p>
 *
 * <p><b>非空洞性自检（R33）：</b>{@link #oldSideIsReallyOld()} 用
 * {@code assertFromJar} 断言旧侧 {@code Render} 确实来自 jfinal 制品，
 * 否则"旧侧"可能被父加载器解析成新代码，比对退化为自己跟自己比。</p>
 *
 * <p><b>javax / jakarta 双接口替身：</b>旧侧形参是 {@code javax.servlet.http.HttpServletResponse}，
 * 新侧是 jakarta 版。两者在本测试关心的子集上签名完全一致
 * （{@code setContentType(String)} / {@code setCharacterEncoding(String)} /
 * {@code setHeader(String,String)} / {@code setDateHeader(String,long)} /
 * {@code getWriter()} / {@code reset()}），故用一个同时实现两个接口的
 * {@link Proxy} 即可作为两侧共同的响应替身，并记录调用序列。</p>
 */
public class RenderSeamGoldenTest {

    /** 与 jfinal 5.2.6 Render 的静态初值比对用的期望编码 */
    private static final String OLD_DEFAULT_ENCODING = "UTF-8";

    /**
     * 非空洞性自检：旧侧必须真的来自 jfinal 制品，而不是被父加载器解析成新代码。
     *
     * @throws Exception 反射/IO 失败
     */
    @Test
    @DisplayName("非空洞性自检：旧侧 Render 来自 jfinal 5.2.6 制品")
    void oldSideIsReallyOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldRender = Class.forName("com.jfinal.render.Render", true, jf);

        OldImplementationLoader.assertFromJar(oldRender, OldImplementationLoader.oldJFinalJar());
        assertTrue(OldImplementationLoader.oldJFinalJarAvailable(), "旧 jfinal 制品必须可用");

        // 旧侧类加载器必须【不是】应用类加载器：若两者相同，"跨实现比对"就退化成自己跟自己比。
        // （R45 教训：不拿恒真断言充数 —— 这里断言的是一条真实可能失守的性质。）
        ClassLoader oldLoader = oldRender.getClassLoader();
        ClassLoader newLoader = LegacyRender.class.getClassLoader();
        assertNotNull(oldLoader, "旧侧必须由独立加载器加载（bootstrap 加载器不可能加载 com.jfinal）");
        assertTrue(oldLoader != newLoader,
                "旧侧加载器与应用加载器相同，跨实现比对会退化为自己跟自己比");

        // 旧侧解析到的 Render 必须是 jfinal 制品里的那个类对象，而非新接缝
        assertTrue(oldRender != LegacyRender.class,
                "旧侧 Render 不得就是新接缝 LegacyRender 本身");
        assertTrue(!LegacyRender.class.isAssignableFrom(oldRender),
                "旧侧 jfinal Render 不得是 LegacyRender 的子类（否则旧侧已被换成新实现）");
        assertEquals("com.jfinal.render.Render", oldRender.getName());
        assertEquals("cn.eova.compat.render.LegacyRender", LegacyRender.class.getName());
    }

    /**
     * {@code ContentType} 的 6 个常量名、取值与 16 条 parse 映射逐条比对旧枚举。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("LegacyContentType 与旧 ContentType 常量/取值/parse 映射逐条一致")
    void contentTypeMatchesOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCt = Class.forName("com.jfinal.render.ContentType", true, jf);
        OldImplementationLoader.assertFromJar(oldCt, OldImplementationLoader.oldJFinalJar());

        Object[] oldValues = (Object[]) oldCt.getMethod("values").invoke(null);
        LegacyContentType[] newValues = LegacyContentType.values();

        // 常量个数与顺序（values() 顺序属契约）
        assertEquals(oldValues.length, newValues.length, "枚举常量个数必须一致");
        for (int i = 0; i < oldValues.length; i++) {
            String oldName = ((Enum<?>) oldValues[i]).name();
            assertEquals(oldName, newValues[i].name(), "第 " + i + " 个常量名必须一致");

            String oldVal = (String) oldCt.getMethod("value").invoke(oldValues[i]);
            assertEquals(oldVal, newValues[i].value(), oldName + " 的取值必须一致");
            assertEquals(oldVal, newValues[i].toString(), oldName + " 的 toString 必须等于 value()");
        }

        // parse：16 条映射键（8 小写别名 + 8 枚举名）+ 未命中断言
        Method oldParse = oldCt.getMethod("parse", String.class);
        String[] keys = {
                "text", "plain", "html", "xml", "json", "javascript", "js", "eventStream",
                "TEXT", "PLAIN", "HTML", "XML", "JSON", "JAVASCRIPT", "JS", "EVENTSTREAM"
        };
        for (String k : keys) {
            Object o = oldParse.invoke(null, k);
            LegacyContentType n = LegacyContentType.parse(k);
            assertNotNull(o, "旧实现 parse(\"" + k + "\") 不应为 null");
            assertNotNull(n, "新实现 parse(\"" + k + "\") 不应为 null");
            assertEquals(((Enum<?>) o).name(), n.name(), "parse(\"" + k + "\") 必须一致");
        }

        // 未命中：旧实现返回 null（无兜底），新实现必须同样返回 null
        assertNull(oldParse.invoke(null, "bogus"), "旧实现未命中应为 null");
        assertNull(LegacyContentType.parse("bogus"), "新实现未命中应为 null");
        // 大小写敏感：旧映射无 toLowerCase，故 "Html" 必须不命中
        assertNull(oldParse.invoke(null, "Html"), "旧实现大小写敏感，应未命中");
        assertNull(LegacyContentType.parse("Html"), "新实现大小写敏感，应未命中");
    }

    /**
     * {@code serialVersionUID} 逐值比对旧异常（属序列化契约）。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("LegacyRenderException.serialVersionUID 与旧 RenderException 一致")
    void renderExceptionSerialVersionUidMatchesOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldEx = Class.forName("com.jfinal.render.RenderException", true, jf);
        OldImplementationLoader.assertFromJar(oldEx, OldImplementationLoader.oldJFinalJar());

        Field f = oldEx.getDeclaredField("serialVersionUID");
        f.setAccessible(true);
        long oldUid = f.getLong(null);

        Field nf = LegacyRenderException.class.getDeclaredField("serialVersionUID");
        nf.setAccessible(true);
        assertEquals(oldUid, nf.getLong(null), "serialVersionUID 必须逐值一致");

        // 父类与构造器形态
        assertEquals(RuntimeException.class, LegacyRenderException.class.getSuperclass());
        assertNotNull(LegacyRenderException.class.getConstructor());
        assertNotNull(LegacyRenderException.class.getConstructor(String.class));
        assertNotNull(LegacyRenderException.class.getConstructor(Throwable.class));
        assertNotNull(LegacyRenderException.class.getConstructor(String.class, Throwable.class));
    }

    /**
     * {@code getEncoding()} 静态初值与旧 {@code Render} 比对。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("LegacyRender.getEncoding 静态初值与旧 Render 一致")
    void encodingDefaultMatchesOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldRender = Class.forName("com.jfinal.render.Render", true, jf);
        String oldEnc = (String) oldRender.getMethod("getEncoding").invoke(null);

        assertEquals(OLD_DEFAULT_ENCODING, oldEnc, "旧实现静态初值应为 UTF-8");
        assertEquals(oldEnc, LegacyRender.getEncoding(), "新实现静态初值必须一致");

        Boolean oldDev = (Boolean) oldRender.getMethod("getDevMode").invoke(null);
        assertEquals(oldDev.booleanValue(), LegacyRender.getDevMode(), "devMode 初值必须一致");
    }

    /**
     * {@code setContext(req,resp,prefix)} 的 view 前缀分支逐条比对旧实现。
     *
     * <p>使用 {@code null} 请求响应：旧实现只做字段赋值 + view 判断，不触发 NPE，
     * 故无需替身即可比对 4 个分支。</p>
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("setContext 的 view 前缀分支逐条比对旧 Render")
    void setContextViewPrefixMatchesOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.render.TextRender", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());

        Method oldSetView = oldCls.getMethod("setView", String.class);
        Method oldSetCtx3 = oldCls.getMethod("setContext",
                Class.forName("javax.servlet.http.HttpServletRequest", true, jf),
                Class.forName("javax.servlet.http.HttpServletResponse", true, jf),
                String.class);
        Method oldGetView = oldCls.getMethod("getView");

        String prefix = "/view/";
        // 4 个分支：null / 空串 / 绝对路径(以'/'开头) / 相对路径
        String[] views = {null, "", "/abs.html", "rel.html"};

        for (String v : views) {
            // 旧侧
            Object oldObj = oldCls.getConstructor(String.class).newInstance("t");
            oldSetView.invoke(oldObj, v);
            oldSetCtx3.invoke(oldObj, null, null, prefix);
            Object oldView = oldGetView.invoke(oldObj);

            // 新侧
            LegacyTextRender newObj = new LegacyTextRender("t");
            newObj.setView(v);
            newObj.setContext(null, null, prefix);
            String newView = newObj.getView();

            assertEquals(oldView, newView,
                    "view=" + v + " 时前缀分支结果必须与旧实现一致");
        }
    }

    /**
     * {@code render()} 的响应调用序列 + 写出字节 + writer 是否被关闭，
     * 三个可观测面同时比对旧 {@code TextRender}。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("TextRender.render 的调用序列/写出字节/未关闭 writer 与旧实现一致")
    void textRenderRenderMatchesOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.render.TextRender", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());

        Class<?> javaxReq = Class.forName("javax.servlet.http.HttpServletRequest", true, jf);
        Class<?> javaxResp = Class.forName("javax.servlet.http.HttpServletResponse", true, jf);
        Class<?> jakartaReq = jakarta.servlet.http.HttpServletRequest.class;
        Class<?> jakartaResp = jakarta.servlet.http.HttpServletResponse.class;
        // javaxReq 仅用于定位 setContext 的形参类型
        assertNotNull(javaxReq);

        Method oldSetCtx = oldCls.getMethod("setContext", javaxReq, javaxResp);
        Method oldRender = oldCls.getMethod("render");

        // 三种输入：默认 contentType / 含 charset / 走 parse 的别名
        String[][] cases = {
                {"hello 中文", null},
                {"body", "text/html;charset=GBK"},
                {"body2", "HTML"},
                {"body3", "application/json"},
        };

        for (String[] c : cases) {
            String text = c[0];
            String ct = c[1];

            // ---- 旧侧 ----
            ResponseSpy oldSpy = new ResponseSpy();
            Object oldRespProxy = oldSpy.proxy(javaxResp);
            Object oldObj = (ct == null)
                    ? oldCls.getConstructor(String.class).newInstance(text)
                    : oldCls.getConstructor(String.class, String.class).newInstance(text, ct);
            oldSetCtx.invoke(oldObj, null, oldRespProxy);
            oldRender.invoke(oldObj);

            // ---- 新侧 ----
            ResponseSpy newSpy = new ResponseSpy();
            Object newRespProxy = newSpy.proxy(jakartaResp);
            LegacyTextRender newObj = (ct == null)
                    ? new LegacyTextRender(text)
                    : new LegacyTextRender(text, ct);
            newObj.setContext(null, (jakarta.servlet.http.HttpServletResponse) newRespProxy);
            newObj.render();

            String label = "text=" + text + ", ct=" + ct;
            assertEquals(oldSpy.calls, newSpy.calls, "响应调用序列必须一致 —— " + label);
            assertEquals(oldSpy.written(), newSpy.written(), "写出字节必须一致 —— " + label);
            assertEquals(oldSpy.writerCloseCount, newSpy.writerCloseCount,
                    "成功路径 writer 是否被关闭必须一致（旧实现不关）—— " + label);
        }
    }

    /**
     * {@code getContentType()} 的解析结果逐条比对旧实现（默认值 / 别名 / 原始串）。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("getContentType 解析结果逐条比对旧 TextRender")
    void getContentTypeMatchesOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.render.TextRender", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());
        Method oldGet = oldCls.getMethod("getContentType");

        String[] cts = {"HTML", "html", "XML", "JSON", "javascript", "js", "text", "plain",
                "eventStream", "TEXT", "EVENTSTREAM", "bogus/type", "text/html;charset=GBK"};

        for (String ct : cts) {
            Object oldObj = oldCls.getConstructor(String.class, String.class).newInstance("x", ct);
            String oldCt = (String) oldGet.invoke(oldObj);
            String newCt = new LegacyTextRender("x", ct).getContentType();
            assertEquals(oldCt, newCt, "contentType=\"" + ct + "\" 的解析结果必须一致");
        }

        // 单参构造器：旧实现直接取常量 "text/plain"（不走 parse）
        Object oldDef = oldCls.getConstructor(String.class).newInstance("x");
        assertEquals((String) oldGet.invoke(oldDef), new LegacyTextRender("x").getContentType(),
                "默认 contentType 必须一致");
    }

    /**
     * {@code close(AutoCloseable)} 的吞异常语义比对旧 {@code Render}。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("close 吞掉异常且不外抛，与旧 Render 一致")
    void closeSwallowsExceptionLikeOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.render.TextRender", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());
        // 注意：close(AutoCloseable) 声明在父类 Render 上（protected），不在 TextRender 上
        Class<?> oldBase = Class.forName("com.jfinal.render.Render", true, jf);
        assertTrue(oldBase.isAssignableFrom(oldCls), "TextRender 必须继承旧 Render");
        Method oldClose = oldBase.getDeclaredMethod("close", AutoCloseable.class);
        oldClose.setAccessible(true);

        AutoCloseable boom = () -> {
            throw new IllegalStateException("boom");
        };

        // 旧侧不得抛出
        Object oldObj = oldCls.getConstructor(String.class).newInstance("x");
        oldClose.invoke(oldObj, boom);

        // 新侧同样不得抛出
        new LegacyTextRender("x").close(boom);

        // null 分支：两侧都不得 NPE
        oldClose.invoke(oldObj, new Object[]{null});
        new LegacyTextRender("x").close(null);
    }

    /**
     * 响应替身：一个同时实现 javax 与 jakarta 版 {@code HttpServletResponse}
     * 的动态代理，记录调用序列并收集写出的字符。
     */
    private static final class ResponseSpy {

        /** 按调用顺序记录的 {@code 方法名(参数...)} 文本 */
        final List<String> calls = new ArrayList<>();

        private final StringWriter sink = new StringWriter();

        /** 每次 {@code getWriter()} 返回一个新的 PrintWriter；这里记录 close 次数 */
        int writerCloseCount = 0;

        /**
         * 创建<b>单接口</b>响应代理。
         *
         * <p><b>为什么不能用一个代理同时实现 javax 与 jakarta 两版：</b>实测
         * {@code getOutputStream()} 在两版里的返回类型不同
         * （{@code javax.servlet.ServletOutputStream} vs
         * {@code jakarta.servlet.ServletOutputStream}），{@code Proxy} 会直接抛
         * {@code IllegalArgumentException: methods with same signature ... but
         * incompatible return types}。故两侧各用一个代理，再比对两侧记录的调用序列
         * —— 比对的是"可观测转录"，结论强度不变。</p>
         *
         * @param respType 响应接口（javax 版或 jakarta 版）
         * @return 响应替身
         */
        Object proxy(Class<?> respType) {
            return Proxy.newProxyInstance(
                    RenderSeamGoldenTest.class.getClassLoader(),
                    new Class<?>[]{respType},
                    (p, m, args) -> {
                        String name = m.getName();
                        if ("equals".equals(name)) {
                            return p == args[0];
                        }
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(p);
                        }
                        calls.add(render(name, args));
                        if ("getWriter".equals(name)) {
                            return new CountingPrintWriter(sink, this);
                        }
                        // 其余方法一律返回默认值（void -> null）
                        return defaultValue(m.getReturnType());
                    });
        }

        /** 取收集到的写出内容 */
        String written() {
            return sink.toString();
        }

        /** 把调用渲染成可比较的文本；字符数组等内容做有界摘要 */
        private static String render(String name, Object[] args) {
            StringBuilder sb = new StringBuilder(name).append('(');
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    Object a = args[i];
                    if (a instanceof byte[]) {
                        sb.append("bytes[").append(((byte[]) a).length).append(']');
                    } else {
                        sb.append(a);
                    }
                }
            }
            return sb.append(')').toString();
        }

        /** 基本类型返回类型的默认值 */
        private static Object defaultValue(Class<?> t) {
            if (!t.isPrimitive()) {
                return null;
            }
            if (t == boolean.class) {
                return Boolean.FALSE;
            }
            if (t == void.class) {
                return null;
            }
            if (t == long.class) {
                return 0L;
            }
            if (t == double.class) {
                return 0d;
            }
            if (t == float.class) {
                return 0f;
            }
            if (t == char.class) {
                return (char) 0;
            }
            return 0;
        }
    }

    /** 统计 {@code close()} 次数的 PrintWriter（用于断言成功路径不关闭） */
    private static final class CountingPrintWriter extends PrintWriter {

        private final ResponseSpy spy;

        /**
         * 构造。
         *
         * @param out 目标
         * @param spy 归属的替身
         */
        CountingPrintWriter(StringWriter out, ResponseSpy spy) {
            super(out);
            this.spy = spy;
        }

        @Override
        public void close() {
            spy.writerCloseCount++;
            spy.calls.add("writer.close()");
            super.close();
        }
    }

    /**
     * 反射调用包装：把受检异常显式转出，避免测试方法签名噪音。
     *
     * @param target 目标对象
     * @param m      方法
     * @param args   参数
     * @return 返回值
     */
    @SuppressWarnings("unused")
    private static Object invoke(Object target, Method m, Object... args) {
        try {
            return m.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

}
