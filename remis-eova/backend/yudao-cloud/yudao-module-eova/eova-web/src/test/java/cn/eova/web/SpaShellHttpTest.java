/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
 * **生产态供给与页面入口退役**的判据（第 305 轮 · U1）。
 *
 * <p>为什么必须单独有这一层：此前所有真浏览器证据都来自 **Vite dev 代理**，
 * 而"生产态"（后端直接供给）根本没接线 —— 实测带会话直连 8080：
 * `/meta/imports` 返回**服务端渲染的旧页**、`/su`、`/main`、`/widget`、`/test/sse` 等
 * **直接 404**，而 `dist/index.html` 虽已构建却没人供给。</p>
 *
 * <p>本判据钉四件事：① SPA 拥有的页面 URL 返回**壳**（引用 {@code eova-assets/}）且
 * **不含服务端渲染标记**；② {@code /eova-assets/**} 产物可被取到；③ 动作 URL 语义**不变**
 * （`/menu/add` 仍返回旧栈同款 fail JSON）；④ {@code /api/**} 与 {@code /eova/**} 静态空间不受影响。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpaShellHttpTest {

    @Autowired
    private TestRestTemplate rest;

    /**
     * 登录并取会话 Cookie（旧 `POST /user/doLogin`，明文口令、服务端做 SM32）。
     *
     * @return Cookie 头
     */
    private String login() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> resp = rest.postForEntity("/user/doLogin",
                new HttpEntity<>("login_id=eova&login_pwd=000000", h), String.class);
        assertEquals(200, resp.getStatusCode().value(), "登录必须 200，实际=" + resp.getStatusCode());
        String sid = resp.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("eovasid=")).findFirst().orElseThrow();
        return sid.substring(0, sid.indexOf(';') > 0 ? sid.indexOf(';') : sid.length());
    }

    /**
     * 带会话取页面响应体。
     *
     * ★ 必须**显式**带 Cookie：`TestRestTemplate` 不维护会话 —— 首版判据忘了带，
     *   于是所有断言都在"未登录 ⇒ 302 到登录页（也是壳）"的路径上恒真（假绿）。
     *
     * @param path 路径
     * @return 响应
     */
    private ResponseEntity<String> get(String path) {
        return get(login(), path);
    }

    /**
     * 带指定会话取响应
     *
     * @param sid  会话 Cookie
     * @param path 路径
     * @return 响应
     */
    private ResponseEntity<String> get(String sid, String path) {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, sid);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    @Test
    @DisplayName("★ U1-1：SPA 页面 URL 返回壳（含 eova-assets/ 产物引用）且不含服务端渲染标记")
    void spaOwnedPagesServeShell() {
        String sid = login();
        // 这些 URL 在 U1 前分别是：服务端旧页（/meta/*、/menu/*、/app/*）或 404（/su、/main、/widget…）
        List<String> pages = List.of(
                "/",
                "/user/login",
                "/user/password",
                "/eova/admin/su",
                "/menu/toAdd?parent_id=0",
                "/menu/auth/1248",
                "/button/add/meta_product",
                "/meta/edit?object=meta_product",
                "/meta/field?object=meta_product",
                "/meta/reorder?object=meta_product",
                "/meta/imports",
                "/app/meta_product",
                "/app/add/meta_product",
                // ★ r306（U2）：`/auth` 页面入口退役为壳（旧栈实测 200「功能权限分配」；
                //   SPA 侧该页已迁移到**旧原路径** `/auth/:rid`）；`/auth/<rid>` 由「方法名匹配失败
                //   ⇒ 退化到 index()」落壳 —— 两条都要有响应，否则 SPA 在生产态拿不到该页。
                "/auth",
                "/auth/1248",
                "/su",
                "/placeholder",
                "/main",
                "/theme",
                "/test",
                "/test/sse",
                "/ip",
                "/sso",
                "/widget");
        for (String p : pages) {
            ResponseEntity<String> resp = get(sid, p);
            assertEquals(200, resp.getStatusCode().value(), p + " 必须 200（返回 SPA 壳），实际=" + resp.getStatusCode());
            String body = resp.getBody() == null ? "" : resp.getBody();
            assertTrue(body.contains("eova-assets/"),
                    p + " 必须引用打包产物 eova-assets/**（说明拿到的是 SPA 壳）：" + body.substring(0, Math.min(120, body.length())));
            assertFalse(body.contains("uzoo.vue.mountBefore") || body.contains("v-cloak"),
                    p + " 不得再返回服务端渲染的旧页（含 v-cloak / uzoo.vue.mountBefore）");
        }
    }

    @Test
    @DisplayName("★ U1-2：动作 URL 语义不变 —— /menu/add 仍返回旧栈同款 fail JSON（不得被壳吞掉）")
    void actionUrlStillBackend() {
        String sid = login();
        ResponseEntity<String> resp = get(sid, "/menu/add");
        assertEquals(200, resp.getStatusCode().value(), "动作仍走后端（200 + fail JSON）");
        String body = resp.getBody() == null ? "" : resp.getBody();
        assertTrue(body.contains("新增菜单失败"),
                "必须仍是旧栈那句失败文案（页面入口退役不得改变动作语义）：" + body);
        assertFalse(body.contains("eova-assets/"), "动作响应不得是 SPA 壳 —— 否则保存类动作在 dev/生产都会被打断");
    }

    @Test
    @DisplayName("★ U1-3：/api 与旧静态空间不受影响（壳供给不得吞掉它们）")
    void apiAndStaticSpacesUntouched() {
        String sid = login();
        assertEquals(200, get(sid, "/api/home/menu").getStatusCode().value(), "/api/** 必须仍是接口");
        assertEquals(200, get(sid, "/eova/lib/eova/eovaui.js").getStatusCode().value(), "/eova/** 旧静态空间必须仍可供给");
        assertEquals(200, get(sid, "/_eova/assets/eova.ui.ext.js").getStatusCode().value(),
                "/_eova/** 扩展静态空间必须仍可供给（单元格渲染器所在）");
    }

    @Test
    @DisplayName("★ U1-4：打包产物 /eova-assets/** 可被取到（壳引用的资源真的能下载）")
    void spaAssetsServed() {
        ResponseEntity<String> index = get(login(), "/");
        assertNotNull(index.getBody(), "壳必须有正文");
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(eova-assets/[A-Za-z0-9_.-]+\\.js)").matcher(index.getBody());
        assertTrue(m.find(), "壳里应引用一个 eova-assets/*.js：" + index.getBody());
        String js = "/" + m.group(1);
        ResponseEntity<String> resp = get(js);
        assertEquals(200, resp.getStatusCode().value(), js + " 必须可被后端供给（否则生产态白屏）");
    }
}
