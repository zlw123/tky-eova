/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * **切片 S2b 判据：真容器下的 HTTP 契约（登录 → 菜单 → 拦截器 → 404）**。
 *
 * <p><b>为什么这样判</b>：S2a 的分发器只做到"编译 + 解析口径"，而 HTTP 层真正的验收面是
 * **对外契约**：URL、JSON 信封、会话 Cookie、拦截器行为、404 形态。故本判据起**真容器**
 * （{@code RANDOM_PORT}）走真 HTTP，并与**旧 demo 实录**逐字对照（实录见
 * {@code docs/.local/baseline/evidence/03-post-dologin-ok.http} 等）。</p>
 *
 * <p><b>断言（5 条，互为独立面）</b>：</p>
 * <ol>
 *   <li>{@code POST /user/doLogin} → 200 + 正文 {@code {"state":"ok"}} + {@code Set-Cookie: eovasid=…}
 *       （旧实录逐字：响应正文就是 {@code {"state":"ok"}}，Cookie 名就是 {@code eovasid}）；</li>
 *   <li>带该 Cookie {@code POST /api/home/menu} → 200 + {@code state=ok} + {@code menus} 33 条
 *       （r174 在旧栈实测 33；旧库 {@code eova_menu} 33 行）**且每项是模型属性**（含 code/name，
 *       不含 dao/configured —— 旧栈实测 menus[0] 键为 code/name/icon/id/parent_id/…）；</li>
 *   <li>**不带 Cookie** 同一端点 ⇒ **不得 200** —— 这条专门证明**拦截器链真的跑了**
 *       （{@code configRoute} 把 LoginInterceptor 加在 Routes 上，分发器把它接进链）；</li>
 *   <li>未知路径 ⇒ **404 且不回落到 SPA**（旧栈行为；回落会把路由错误伪装成正常页面）；</li>
 *   <li>{@code GET /user/login} ⇒ 200 + {@code text/html} + 旧登录页正文 —— 这一条钉的是
 *       **模板源映射**（{@code /eova/x ⇒ <视图根>/webapp/eova/x}）：映射写错时前 4 条全绿而本页 500。</li>
 * </ol>
 *
 * <p><b>前置</b>：真库 baseline（MySQL 13306）必须可达 —— 与既有 live 判据同源；不可达时
 * 本判据会红（属环境缺失，不是通过）。驾驶账号取自旧 demo 的实测真值（{@code eova}/{@code 000000}，
 * 摘要口径 {@code SM32} 由 {@code Sm32LoginDigestGoldenTest} 钉住）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegacyHttpContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TestRestTemplate rest;

    /** 登录（旧 demo 实测真值；返回 Set-Cookie 里的 eovasid） */
    private String login() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> resp = rest.postForEntity("/user/doLogin",
                new HttpEntity<>("login_id=eova&login_pwd=000000", h), String.class);

        assertEquals(200, resp.getStatusCode().value(), "登录必须 200，实际=" + resp.getStatusCode());
        assertEquals("{\"state\":\"ok\"}", resp.getBody(), "★ 登录成功正文必须与旧实录逐字一致");
        List<String> cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies, "必须下发会话 Cookie");
        String sid = cookies.stream().filter(c -> c.startsWith("eovasid=")).findFirst()
                .orElseThrow(() -> new AssertionError("★ Cookie 名必须是 eovasid（旧契约），实际=" + cookies));
        return sid.substring(0, sid.indexOf(';') > 0 ? sid.indexOf(';') : sid.length());
    }

    @Test
    @DisplayName("★ S2b-1：POST /user/doLogin → {\"state\":\"ok\"} + Set-Cookie: eovasid")
    void loginReturnsOldEnvelopeAndSessionCookie() {
        String sid = login();
        assertTrue(sid.startsWith("eovasid="), "会话 Cookie 形如 eovasid=…，实际=" + sid);
    }

    @Test
    @DisplayName("★ S2b-2：带会话 Cookie POST /api/home/menu → state=ok 且 menus 33 条")
    void menuReturnsAuthorizedMenuTree() throws Exception {
        String sid = login();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add(HttpHeaders.COOKIE, sid);
        ResponseEntity<String> resp = rest.exchange("/api/home/menu", HttpMethod.POST,
                new HttpEntity<>("{}", h), String.class);

        assertEquals(200, resp.getStatusCode().value(), "带会话访问菜单必须 200，实际=" + resp.getStatusCode());
        Map<?, ?> body = JSON.readValue(resp.getBody(), Map.class);
        assertEquals("ok", body.get("state"), "信封 state 必须为 ok：" + resp.getBody());
        assertTrue(body.containsKey("cats"), "旧契约含 cats（只含有子菜单的目录）：" + body.keySet());
        Object menus = body.get("menus");
        assertNotNull(menus, "旧契约含 menus");
        assertEquals(33, ((List<?>) menus).size(),
                "★ 菜单条数必须与旧栈实测一致（33，对应 eova_menu 33 行）");

        // ★ 逐项形状：menu 是【模型】，必须按属性序列化（r248 补钉）。旧栈实测（curl 9090）
        //   menus[0] 的键是 code/name/icon/id/parent_id/short_name/template/…；
        //   若 JSON 序列化把模型当普通 JavaBean，这里会变成 {dao, configured} ——
        //   前端拿不到 menu.code/name，而"只数条数"的断言照样绿（假通过）。
        Map<?, ?> first = (Map<?, ?>) ((List<?>) menus).get(0);
        assertTrue(first.containsKey("code") && first.containsKey("name"),
                "★ menus 每项必须是模型属性（含 code/name），实际键=" + first.keySet());
        assertFalse(first.containsKey("dao") || first.containsKey("configured"),
                "★ 不得退化成 JavaBean 视图（旧栈模型 JSON 里没有 dao/configured），实际键=" + first.keySet());
    }

    @Test
    @DisplayName("★ S2b-3：不带 Cookie 访问同一端点 ⇒ 不得 200（证明拦截器链真的跑了）")
    void menuWithoutSessionIsRejectedByLoginInterceptor() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.exchange("/api/home/menu", HttpMethod.POST,
                new HttpEntity<>("{}", h), String.class);
        // ★ 必须是 401/403 —— 只断言"非 2xx"是**假通过**：404 也满足，但那说明"根本没路由"，
        //   而这条判据要证明的是"**拦截器链生效**"（实测踩到：分发器注解错误时这里照样绿）。
        int code = resp.getStatusCode().value();
        assertTrue(code == 401 || code == 403,
                "★ 未登录应被 LoginInterceptor 拦下（401/403），实际=" + code + "（404 说明路由本身没命中）");
    }

    @Test
    @DisplayName("★ S2b-4：未知路径 ⇒ 404，不回落到 SPA")
    void unknownPathIsNotFound() {
        ResponseEntity<String> resp = rest.getForEntity("/definitely/not/a/route", String.class);
        assertEquals(404, resp.getStatusCode().value(), "★ 必须 404（旧栈行为，不得回落 SPA）");
    }

    @Test
    @DisplayName("★ r305 U1：actionKey 退化**只剥一层** —— 单段未知落根路由，多段未知 404（旧栈边界）")
    void actionKeyDegradationIsExactlyOneLevel() {
        // 旧栈带会话实测（curl 9090）：`/zzz_unknown` 200（根路由 index + urlPara）、
        // `/app/meta_product` 200（前缀 `/app` 是注册路由）、`/a/b` 与 `/definitely/not/a/route` 404。
        // ★ 这条判据的由来：首版把退化实现成「**逐段剥离直到命中**」⇒ 多段未知路径统统落首页（200），
        //   而既有 `unknownPathIsNotFound` 抓到了 ⇒ 修实现（不是改判据）。
        //   两个方向都要锁，否则「全部 404」与「全部 200」都能蒙过其中一半。
        String sid = login();
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, sid);

        // ① 单段未知 ⇒ 根路由 index + urlPara（旧栈 200）
        assertEquals(200, get(sid, "/zzz_unknown").getStatusCode().value(),
                "★ 单段未知路径必须落根路由（旧栈 200）");
        // ② 前缀是已注册路由 ⇒ 该控制器 index + urlPara（旧栈 200）
        ResponseEntity<String> app = get(sid, "/app/meta_product");
        assertEquals(200, app.getStatusCode().value(), "★ /app/meta_product 必须落 AppController#index");
        // ③ 多段未知、且前缀**不是**注册路由 ⇒ 404（旧栈行为，不得继续剥离到根）
        assertEquals(404, get(sid, "/a/b").getStatusCode().value(),
                "★ 多段未知路径必须 404（只剥一层，不得继续剥到根路由）");
        assertEquals(404, rest.exchange("/a/b/c", HttpMethod.GET, new HttpEntity<>(h), String.class)
                        .getStatusCode().value(),
                "★ 三段未知路径同样 404");
    }

    /** 带会话 GET */
    private ResponseEntity<String> get(String sid, String path) {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, sid);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    @Test
    @DisplayName("★ S2b-6：GET / ⇒ 200 + text/html（空 actionKey ⇒ index 的 jfinal 约定；旧栈带会话实测 200）")
    void rootMapsToIndexAction() {
        String sid = login();
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, sid);
        ResponseEntity<String> resp = rest.exchange("/", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertEquals(200, resp.getStatusCode().value(),
                "★ 旧栈带会话 GET / 是 200（IndexController.index 渲染首页），实际=" + resp.getStatusCode());
        String ct = String.valueOf(resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(ct.contains("text/html"), "首页必须是 HTML，实际=" + ct);
    }

    @Test
    @DisplayName("★ S2b-5：GET /user/login ⇒ 200 + text/html + **SPA 壳**（U1 页面入口退役）")
    void loginPageIsSpaShellAfterU1Takeover() {
        ResponseEntity<String> resp = rest.getForEntity("/user/login", String.class);
        // 旧栈实测（curl 9090）：200 / text/html / 正文含 <title>EOVA低代码开发平台</title>。
        // r305 U1【契约变更·已声明】：`/user/login` 是**页面入口**，已退役为 SPA 壳 ⇒ 正文不再来自旧模板。
        //   原判据的后半句（标题必须来自旧登录页模板）**换锚点**到仍走旧模板的 `/auth`
        //   —— 见 `stillLegacyPageRendersFromLegacyViewRoot`，那里继续钉【模板源映射】。
        assertEquals(200, resp.getStatusCode().value(), "登录页必须 200，实际=" + resp.getStatusCode());
        String ct = String.valueOf(resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(ct.contains("text/html"), "★ 必须是 HTML，实际=" + ct);
        String body = resp.getBody();
        assertNotNull(body, "必须有正文");
        assertTrue(body.startsWith("<!DOCTYPE html>"), "壳正文以 <!DOCTYPE html> 开头，实际="
                + body.substring(0, Math.min(60, body.length())));
        assertTrue(body.contains("eova-assets/"), "★ U1 后登录页正文必须是 SPA 壳（引用打包产物），实际不含 eova-assets/");
        assertFalse(body.contains("EOVA低代码开发平台"),
                "★ U1 后不得再出现旧登录页模板正文（说明退役被回退）");
    }

    @Test
    @DisplayName("★ S2b-5b：GET /auth ⇒ 200 + 旧模板正文（**模板源映射**判据的新锚点）")
    void stillLegacyPageRendersFromLegacyViewRoot() {
        String sid = login();
        ResponseEntity<String> resp = get(sid, "/auth");
        // 旧栈实测（带会话 curl 9090）：200 / text/html / <title>功能权限分配</title>。
        // ★ 这条专门钉住【模板源映射】：`/eova/role/auth/app.html` ⇒ `<视图根>/webapp/eova/...`。
        //   映射写错（少了 webapp 一层）时本页会 500，而其它判据照样全绿。
        //   为什么换锚点到 `/auth`：U1 把 `/user/login` 退役成壳后，旧锚点已不再走模板渲染
        //   ⇒ 若仍留在原判据上，这条【模板源映射】判据会**空洞化**（断言壳里永远不含旧标题）。
        assertEquals(200, resp.getStatusCode().value(), "/auth 必须 200，实际=" + resp.getStatusCode());
        String ct = String.valueOf(resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(ct.contains("text/html"), "★ 必须是 HTML（旧契约），实际=" + ct);
        String body = resp.getBody();
        assertNotNull(body, "必须有正文");
        assertTrue(body.contains("功能权限分配"), "★ 正文必须来自旧模板（标题缺失说明模板源映射错了）");
        assertFalse(body.contains("eova-assets/"), "★ /auth 仍是旧模板页，不得被壳接管（顺序/范围都会被这条抓到）");
    }
}
