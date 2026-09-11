/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code LegacyHttpKit.readData} 的跨实现判据（第 64 轮为 port {@code ApiRouterHandler} 而补）。
 *
 * <p><b>本判据为什么住在 eova-compat：</b>它需要 <b>javax</b>（旧制品形参）与
 * <b>jakarta</b>（新接缝形参）两套 servlet API，只有本模块同时声明了它们
 * （javax 为 test 作用域、jakarta 为 provided）。而<b>旧侧不需要编译期 jfinal</b> ——
 * 旧 {@code HttpKit} 经 {@code createForJFinalOnly()} 加载后用反射调用即可，
 * 故不存在 eova-compat 不得引入 jfinal 制品（R38）的问题。</p>
 *
 * <p><b>一次判断失误的记录：</b>本判据最初写在 eova-db-adapter，编译即报
 * "程序包 jakarta.servlet.http 不存在" —— 我此前误以为该模块有 javax（理由是
 * {@code LegacyRoutesGoldenTest} 里 {@code extends com.jfinal.core.Controller} 编译通过）。
 * 实际是：只继承不覆写时 javac 不需要 servlet 类型可解析。教训：<b>"能编译"不等于"依赖在"</b>，
 * 判断某类型可用必须直接看 pom / 直接引用它。</p>
 */
class LegacyHttpKitSeamGoldenTest {

    /**
     * 建一个 javax 请求替身（旧侧形参是 javax 版）。
     *
     * @param encoding {@code getCharacterEncoding()} 的返回（可为 null）
     * @param body     请求体字节
     * @return 替身
     */
    private static Object javaxRequest(String encoding, byte[] body) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getCharacterEncoding":
                    return encoding;
                case "getInputStream":
                    return new javax.servlet.ServletInputStream() {
                        private final ByteArrayInputStream in = new ByteArrayInputStream(body);

                        @Override
                        public int read() {
                            return in.read();
                        }

                        @Override
                        public boolean isFinished() {
                            return in.available() == 0;
                        }

                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setReadListener(javax.servlet.ReadListener rl) {
                        }
                    };
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return Proxy.newProxyInstance(LegacyHttpKitSeamGoldenTest.class.getClassLoader(),
                new Class<?>[]{javax.servlet.http.HttpServletRequest.class}, h);
    }

    /**
     * 建一个 jakarta 请求替身（新侧）。
     *
     * @param encoding {@code getCharacterEncoding()} 的返回（可为 null）
     * @param body     请求体字节
     * @return 替身
     */
    private static jakarta.servlet.http.HttpServletRequest jakartaRequest(String encoding, byte[] body) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getCharacterEncoding":
                    return encoding;
                case "getInputStream":
                    return new jakarta.servlet.ServletInputStream() {
                        private final ByteArrayInputStream in = new ByteArrayInputStream(body);

                        @Override
                        public int read() {
                            return in.read();
                        }

                        @Override
                        public boolean isFinished() {
                            return in.available() == 0;
                        }

                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setReadListener(jakarta.servlet.ReadListener rl) {
                        }
                    };
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (jakarta.servlet.http.HttpServletRequest) Proxy.newProxyInstance(
                LegacyHttpKitSeamGoldenTest.class.getClassLoader(),
                new Class<?>[]{jakarta.servlet.http.HttpServletRequest.class}, h);
    }

    @Test
    @DisplayName("readData：与旧 jfinal HttpKit 同结果（编码回落 / 显式编码 / 跨缓冲区 / setCharSet）")
    void readDataMatchesOldJFinal() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldKit = Class.forName("com.jfinal.kit.HttpKit", true, jf);
        OldImplementationLoader.assertFromJar(oldKit, OldImplementationLoader.oldJFinalJar());
        Method oldRead = oldKit.getMethod("readData", javax.servlet.http.HttpServletRequest.class);
        Method oldSet = oldKit.getMethod("setCharSet", String.class);

        // ① 编码为 null → 回落到静态 CHARSET（默认 UTF-8）
        byte[] utf8 = "中文 body {\"a\":1}".getBytes(StandardCharsets.UTF_8);
        String old1 = (String) oldRead.invoke(null, javaxRequest(null, utf8));
        assertEquals(old1, LegacyHttpKit.readData(jakartaRequest(null, utf8)),
                "未指定编码时必须回落到默认编码，且结果与旧制品一致");
        assertEquals("中文 body {\"a\":1}", old1, "默认编码应为 UTF-8");

        // ② 显式编码 → 按请求声明的编码解码
        byte[] latin = "caf\u00e9".getBytes(StandardCharsets.ISO_8859_1);
        String old2 = (String) oldRead.invoke(null, javaxRequest("ISO-8859-1", latin));
        assertEquals(old2, LegacyHttpKit.readData(jakartaRequest("ISO-8859-1", latin)),
                "显式编码路径必须与旧制品一致");
        assertEquals("caf\u00e9", old2, "按声明编码解码");

        // ③ 大于 1024 字符（跨缓冲区拼接）
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            big.append('x');
        }
        byte[] bigBytes = big.toString().getBytes(StandardCharsets.UTF_8);
        String old3 = (String) oldRead.invoke(null, javaxRequest(null, bigBytes));
        assertEquals(old3, LegacyHttpKit.readData(jakartaRequest(null, bigBytes)),
                "跨缓冲区拼接结果必须一致");
        assertEquals(3000, old3.length(), "长报文不得被截断");

        // ④ setCharSet 改默认编码（旧实现的静态可变状态）
        try {
            oldSet.invoke(null, "ISO-8859-1");
            LegacyHttpKit.setCharSet("ISO-8859-1");
            byte[] latin2 = "caf\u00e9".getBytes(StandardCharsets.ISO_8859_1);
            assertEquals((String) oldRead.invoke(null, javaxRequest(null, latin2)),
                    LegacyHttpKit.readData(jakartaRequest(null, latin2)),
                    "改默认编码后仍须与旧制品一致");
        } finally {
            oldSet.invoke(null, "UTF-8");
            LegacyHttpKit.setCharSet("UTF-8");
        }
    }
}
