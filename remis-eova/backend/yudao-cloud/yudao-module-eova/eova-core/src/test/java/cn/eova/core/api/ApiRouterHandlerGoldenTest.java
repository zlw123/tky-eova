/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.api;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.eova.compat.jfinal.handler.LegacyHandler;
import cn.eova.compat.render.DefaultLegacyRenderFactory;
import cn.eova.compat.render.LegacyRenderManager;
import cn.eova.tools.tool.SignTool;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ApiRouterHandler}（第 64 轮 port）的判据。
 *
 * <p><b>为什么优先判据这一支：</b>它是<b>安全相关</b>路径 —— 直接访问 {@code /router/*}
 * 必须被 403 拦住、{@code /router} 缺参必须 400，且签名校验决定调用方是否有权。
 * 这三条任一被"顺手重构"掉，都是对外行为与安全的双重变化。</p>
 */
class ApiRouterHandlerGoldenTest {

    /** 捕获到的响应体与 contentType */
    static final class Captured {
        StringWriter body = new StringWriter();
        PrintWriter writer = new PrintWriter(body);
        List<String> contentTypes = new ArrayList<>();
        List<Integer> statuses = new ArrayList<>();
    }

    /** 被委派的 target 记录 */
    static final List<String> DELEGATED = new ArrayList<>();

    /**
     * 探针：{@code LegacyHandler.next} 是 {@code protected}（与 jfinal 一致），
     * 跨包只能经<b>子类</b>赋值，故用本子类包一层。
     */
    static class ProbeRouter extends ApiRouterHandler {

        /**
         * 挂上后继 handler。
         *
         * @param next 后继
         */
        void setNext(LegacyHandler next) {
            this.next = next;
        }
    }

    /** 使用真实渲染实现，故装上默认渲染工厂 */
    @BeforeAll
    static void installFactory() {
        LegacyRenderManager.setRenderFactory(new DefaultLegacyRenderFactory());
    }

    /**
     * 建一个 jakarta 请求替身。
     *
     * @param params 请求参数
     * @param body   请求体（可为 null）
     * @return 替身
     */
    private static HttpServletRequest request(Map<String, String> params, String body) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getParameter":
                    return params.get(args[0]);
                case "getCharacterEncoding":
                    return null;
                case "getInputStream":
                    return body == null ? null
                            : new jakarta.servlet.ServletInputStream() {
                                private final java.io.ByteArrayInputStream in =
                                        new java.io.ByteArrayInputStream(
                                                body.getBytes(java.nio.charset.StandardCharsets.UTF_8));

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
                                public void setReadListener(
                                        jakarta.servlet.ReadListener readListener) {
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
        return (HttpServletRequest) Proxy.newProxyInstance(
                ApiRouterHandlerGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, h);
    }

    /**
     * 建一个 jakarta 响应替身。
     *
     * @param cap 捕获器
     * @return 替身
     */
    private static HttpServletResponse response(Captured cap) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getWriter":
                    return cap.writer;
                case "setContentType":
                    cap.contentTypes.add((String) args[0]);
                    return null;
                case "setStatus":
                    cap.statuses.add((Integer) args[0]);
                    return null;
                case "setCharacterEncoding":
                    return null;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (HttpServletResponse) Proxy.newProxyInstance(
                ApiRouterHandlerGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, h);
    }

    @Test
    @DisplayName("handle：直接访问 /router/* 必须 403 拦截；普通 URL 交给 next 且不置 isHandled")
    void directAccessIsForbiddenAndNormalUrlsPassThrough() {
        ProbeRouter handler = new ProbeRouter();
        DELEGATED.clear();
        handler.setNext(new LegacyHandler() {
            @Override
            public void handle(String target, HttpServletRequest req, HttpServletResponse resp,
                               boolean[] isHandled) {
                DELEGATED.add(target);
            }
        });

        // ① 直接访问 API（/router/xxx）→ 403 禁止访问，且置 isHandled
        Captured c1 = new Captured();
        boolean[] h1 = {false};
        handler.handle("/router/user/login", request(Map.of(), null), response(c1), h1);
        assertTrue(h1[0], "拦截后必须置 isHandled=true（否则会继续走后续 handler）");
        assertTrue(c1.body.toString().contains("禁止访问"),
                "响应体必须含 403 文案，实际：" + c1.body);
        assertTrue(c1.body.toString().contains("403"),
                "响应体必须含 403 状态码，实际：" + c1.body);
        assertEquals(0, DELEGATED.size(), "拦截时不得继续委派");

        // ② 普通 URL → 原样委派给 next，且【不】置 isHandled（由后续 handler 决定）
        Captured c2 = new Captured();
        boolean[] h2 = {false};
        handler.handle("/eova/_view/index/index.html", request(Map.of(), null), response(c2), h2);
        assertFalse(h2[0], "普通 URL 不得置 isHandled");
        assertEquals(List.of("/eova/_view/index/index.html"), DELEGATED, "必须原样委派");
        assertEquals("", c2.body.toString(), "普通 URL 不得渲染任何内容");
    }

    @Test
    @DisplayName("handle：/router 缺参必须 400 参数缺失（不再往下走）")
    void missingParamsYield400() {
        ApiRouterHandler handler = new ApiRouterHandler();
        Captured cap = new Captured();
        boolean[] isHandled = {false};
        handler.handle("/router", request(Map.of("app_key", "k"), null), response(cap), isHandled);

        assertTrue(isHandled[0], "缺参时也必须置 isHandled");
        assertTrue(cap.body.toString().contains("参数缺失"),
                "必须是 400 参数缺失，实际：" + cap.body);
        assertTrue(cap.body.toString().contains("400"), "状态码必须是 400");
    }

    @Test
    @DisplayName("signCheck：用 eova-tools 的 SignTool 生成签名，正例放行、反例拒绝")
    void signCheckMatchesSignTool() {
        ApiRouterHandler handler = new ApiRouterHandler();
        String appKey = "demo";
        String appSecret = "secret";
        String method = "user.info";
        String data = "{\"id\":1}";
        long ts = 1700000000000L;

        // 旧实现：new SignTool(SIGN_HMAC, appKey, appSecret, null).generate(method, data, ts)
        SignTool.SignUrl su = new SignTool(SignTool.SIGN_HMAC, appKey, appSecret, null)
                .generate(method, data, ts);

        assertTrue(handler.signCheck(appKey, appSecret, method, String.valueOf(ts), su.getSign(), data),
                "正确签名必须通过");
        assertTrue(handler.signCheck(appKey, appSecret, method, String.valueOf(ts),
                        su.getSign().toUpperCase(), data),
                "旧实现用 equalsIgnoreCase —— 大小写不敏感");
        assertFalse(handler.signCheck(appKey, appSecret, method, String.valueOf(ts), "bogus", data),
                "错误签名必须拒绝");
        assertFalse(handler.signCheck(appKey, "otherSecret", method, String.valueOf(ts), su.getSign(), data),
                "换密钥后签名必须失效");
        assertFalse(handler.signCheck(appKey, appSecret, "other.method", String.valueOf(ts), su.getSign(), data),
                "换 method 后签名必须失效（method 参与签名）");
        assertFalse(handler.signCheck(appKey, appSecret, method, String.valueOf(ts + 1), su.getSign(), data),
                "换 timestamp 后签名必须失效（timestamp 参与签名）");
    }

    @Test
    @DisplayName("getAppConfig/addAppConfig：静态注册表可读可写（mod 用它登记 appKey→secret）")
    void appConfigRegistry() {
        Map<String, String> before = new LinkedHashMap<>(ApiRouterHandler.getAppConfig());
        try {
            ApiRouterHandler.addAppConfig("key1", "sec1");
            assertEquals("sec1", ApiRouterHandler.getAppConfig().get("key1"));
            // 返回的是内部实例（旧实现直接 return APP_CONFIG），故可被外部修改
            assertTrue(ApiRouterHandler.getAppConfig() == ApiRouterHandler.getAppConfig(),
                    "必须返回同一实例（旧实现无防御性拷贝）");
        } finally {
            ApiRouterHandler.getAppConfig().clear();
            ApiRouterHandler.getAppConfig().putAll(before);
        }
    }
}
