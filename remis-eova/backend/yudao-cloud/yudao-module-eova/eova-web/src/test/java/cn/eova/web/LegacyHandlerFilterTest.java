/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.net.HttpURLConnection;
import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **DES-012 P3-U19 判据：旧 Handler 链必须真的在请求路径上**。
 *
 * <p><b>修前实测缺口</b>：{@code EovaConfig#configHandler} 注册的四个 handler
 * （WAF → Druid 监控页 → UrlBan → ApiRouter）在新栈里**只被收集、从未执行**
 * ⇒ 旧栈 {@code GET /druid/index.html} = **200**（Druid 监控页），新栈 = **404**。</p>
 *
 * <p><b>旧栈真值（9090 实测）</b>：{@code /druid} = **302** → {@code /druid/index.html}；
 * {@code /druid/index.html}（管理员会话）= **200 text/html** 且正文含 {@code Druid}；
 * {@code /druid/sql.html} = 200 且 {@code <title>Druid SQL Stat}；
 * 无会话 = **200 + Druid 自己的"not permitted"页**（★ 与旧栈的<b>已声明差异</b>：本栈返回 403 ——
 * 旧栈把 auth 注入监控 Servlet 覆写 {@code isPermitted}，我们保留"拒绝"语义但用 403 表达，
 * 详见 {@code LegacyDruidStatViewHandler} 注释）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegacyHandlerFilterTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private FilterRegistrationBean<LegacyHandlerFilter> legacyHandlerFilter;

    /** 随机端口（用于"不跟随重定向"的原生请求 —— TestRestTemplate 底层 HttpURLConnection 会自动跟随） */
    @LocalServerPort
    private int port;

    /** 登录（真库 baseline；返回会话请求头） */
    private HttpHeaders session() {
        HttpHeaders form = new HttpHeaders();
        form.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> resp = rest.postForEntity("/user/doLogin",
                new HttpEntity<>("login_id=eova&login_pwd=000000", form), String.class);
        assertEquals(200, resp.getStatusCode().value(), "登录必须 200（本判据依赖真库 baseline）");
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, String.valueOf(resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE)));
        return h;
    }

    /**
     * 发一次 GET。
     *
     * @param h    会话头（可为 null）
     * @param path 路径
     * @return 响应
     */
    private ResponseEntity<String> get(HttpHeaders h, String path) {
        HttpHeaders headers = h == null ? new HttpHeaders() : h;
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("★ 补缺口：`/druid/index.html`（管理员会话）⇒ 200 且是 Druid 监控页（旧栈同）")
    void druidStatPageIsServedForAdmin() {
        ResponseEntity<String> resp = get(session(), "/druid/index.html");
        assertEquals(200, resp.getStatusCode().value(),
                "★ 旧栈实测 200；修前新栈 404（handler 链从未执行）。实际=" + resp.getStatusCode());
        String body = String.valueOf(resp.getBody());
        assertTrue(body.contains("druid") || body.contains("Druid"),
                "★ 正文必须是 Druid 监控页（含 Druid 特征串），实际前 120 字=" + body.substring(0, Math.min(120, body.length())));
    }

    @Test
    @DisplayName("★ 行为对齐：`/druid` ⇒ 302，Location 指向 `/druid/index.html`（旧栈实测同）")
    void druidRootRedirectsToIndex() throws Exception {
        // ★ 必须用"不跟随重定向"的原生连接：TestRestTemplate 底层 HttpURLConnection **自动跟随**，
        //   会把 302 折叠成最终 200，从而看不出这条契约（本轮实测踩到）。
        HttpURLConnection conn = (HttpURLConnection) URI
                .create("http://127.0.0.1:" + port + "/druid").toURL().openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("GET");
        conn.connect();
        int code = conn.getResponseCode();
        String location = String.valueOf(conn.getHeaderField("Location"));
        conn.disconnect();
        assertEquals(302, code, "★ 旧栈实测 302（jfinal 自己发重定向），实际=" + code);
        assertTrue(location.endsWith("/druid/index.html"),
                "★ Location 必须指向 /druid/index.html，实际=" + location);
    }

    @Test
    @DisplayName("★ 访问控制：无会话 ⇒ 拒绝（403；旧栈为 200+Druid 拒绝页，属已声明差异）")
    void druidStatPageIsDeniedWithoutSession() {
        ResponseEntity<String> resp = get(null, "/druid/index.html");
        assertEquals(403, resp.getStatusCode().value(),
                "★ 未授权必须被拒（旧栈被 Druid 自己的 isPermitted 拒；本栈用 403 表达，差异已登记）");
    }

    @Test
    @DisplayName("等价：UrlBan / 未知路径 / `/api/*` 仍与旧栈一致（404）")
    void urlBanAndUnknownApiStayEquivalent() {
        HttpHeaders h = session();
        assertEquals(404, get(h, "/x.sql").getStatusCode().value(), "★ 旧栈实测 404（UrlBan 模式命中后也是 404）");
        assertEquals(404, get(h, "/api/anything").getStatusCode().value(), "★ 旧栈实测 404");
    }

    @Test
    @DisplayName("结构：过滤器 registered（/*，order 早于 DispatcherServlet）")
    void filterIsRegisteredForAllRequests() {
        assertNotNull(legacyHandlerFilter, "★ 旧 Handler 链的过滤器必须在容器里（否则链又回到「从未执行」）");
        assertTrue(legacyHandlerFilter.getUrlPatterns().contains("/*"),
                "必须覆盖 /*（旧 jfinal 的 handler 对所有请求生效），实际=" + legacyHandlerFilter.getUrlPatterns());
        assertTrue(legacyHandlerFilter.getOrder() < 0,
                "★ 必须早于 DispatcherServlet 之前的一切（order<0），实际=" + legacyHandlerFilter.getOrder());
    }

    @Test
    @DisplayName("★ 反向（源码）：链不能截断 —— 路径不匹配的 handler 必须把请求交给下一环")
    void handlersMustDelegateToNext() throws Exception {
        String druid = Files.readString(new File(
                "../eova-compat/src/main/java/cn/eova/compat/jfinal/plugin/druid/LegacyDruidStatViewHandler.java")
                .toPath(), StandardCharsets.UTF_8);
        assertTrue(druid.contains("next.handle(target, request, response, isHandled);"),
                "★ 监控 handler 的「路径不匹配」分支必须 next.handle —— 否则链在第二个环就断了（全站 404）");
        String bootstrap = Files.readString(
                new File("src/main/java/cn/eova/web/LegacyWebBootstrap.java").toPath(), StandardCharsets.UTF_8);
        assertTrue(bootstrap.contains("legacyHandlerFilter"),
                "★ 过滤器注册必须留在装配里（否则链再次失去执行方）");
        assertFalse(bootstrap.contains("// legacyHandlerFilter"),
                "过滤器注册不得被注释掉");
    }
}
