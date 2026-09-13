/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **U3 判据：旧页的「子资源」也必须在生产态可取**（页面 200 ≠ 页面可用）。
 *
 * <p><b>为什么必须单独成判据</b>：本轮的三个缺口**全部**是
 * 「页面返回 200、它引用的 JS/文件返回 404」——所有只比页面状态码的判据都看不见：</p>
 * <ul>
 *   <li>{@code /_view/theme/index.js}：{@code /main} 主题页的脚本（旧栈 200、新栈 404）；</li>
 *   <li>{@code /excel/import/app.js}：Excel 导入页自己的脚本（旧栈 200、新栈 404）——
 *       而该页是 U2 清点后**唯一仍由后端渲染的活页面**；</li>
 *   <li>{@code /_static/excel/酒店导入模版.xlsx}：该页「下载导入模板」链接（旧栈 200、新栈 404）。</li>
 * </ul>
 *
 * <p>判据口径：以**旧栈**的 HTML 为契约来源（旧页引用什么，新栈就必须供得起什么），
 * 对每个子资源在**两侧**比状态码（旧栈真值写在各断言里），并额外断言 Content-Type 形态
 * ——因为"用 HTML 冒充 JS"这类错法状态码是 200（U2 的 {@code /demo/test/btn.js} 就是这一族）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegacySubResourceHttpTest {

    @Autowired
    private TestRestTemplate rest;

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

    private ResponseEntity<String> get(HttpHeaders h, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    @Test
    @DisplayName("★ U3-1：`/main` 主题页 —— 后端渲染真页面（不是壳），且其脚本可取")
    void mainPageIsRenderedByBackendWithItsScript() {
        HttpHeaders h = session();
        ResponseEntity<String> page = get(h, "/main");
        // 旧栈带会话实测：200 + <title>EovaUI主题风格</title>
        assertEquals(200, page.getStatusCode().value(), "/main 必须 200（旧栈同）");
        String body = String.valueOf(page.getBody());
        assertTrue(body.contains("EovaUI主题风格"), "★ 必须是后端渲染的**主题页**（旧栈正文特征），实际前 80 字="
                + body.substring(0, Math.min(80, body.length())));
        // ★ 这条是 U1 回归的防复发断言：壳接管会让 iframe 里装 SPA 自己
        assertFalse(body.contains("eova-assets/"),
                "★ /main 必须是后端渲染的主题页，不得是 SPA 壳（SPA 首页把它当 iframe 内容）");
        // 其脚本：旧栈 200 application/javascript
        ResponseEntity<String> js = get(h, "/_view/theme/index.js");
        assertEquals(200, js.getStatusCode().value(), "★ /_view/theme/index.js 必须 200（旧栈同）");
        assertTrue(String.valueOf(js.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("javascript"),
                "★ 必须是 JS 形态（拿 HTML 冒充会在浏览器里 SyntaxError）");
    }

    @Test
    @DisplayName("★ U3-2：`/ip` 是**纯文本端点**（旧栈 renderText），不是页面")
    void ipIsPlainTextEndpoint() {
        HttpHeaders h = session();
        ResponseEntity<String> resp = get(h, "/ip");
        // 旧栈带会话实测：200 + text/plain，正文就是 IP（如 127.0.0.1）
        assertEquals(200, resp.getStatusCode().value(), "/ip 必须 200（旧栈同）");
        String ct = String.valueOf(resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(ct.contains("text/plain"), "★ 必须是 text/plain（旧栈 renderText），实际=" + ct);
        String body = String.valueOf(resp.getBody());
        assertFalse(body.contains("<html") || body.contains("eova-assets/"),
                "★ 不得是 HTML（U1 曾把它当页面接管成壳，语义被改）");
        assertTrue(body.trim().matches("[0-9a-fA-F:.]+"), "正文必须是 IP 形态，实际=" + body);
    }

    @Test
    @DisplayName("★ U3-3：Excel 导入页（唯一活旧页）的脚本与下载模板都可取")
    void excelImportPageSubResourcesAreServed() {
        HttpHeaders h = session();
        // 页面本身：旧栈 200 + <title>导入酒店数据</title>（U2 已钉）
        assertEquals(200, get(h, "/excel/imports/sys_hotel").getStatusCode().value(),
                "Excel 导入页必须 200（旧栈同）");

        // 页面自己的脚本：旧栈 `200 application/javascript`
        for (String js : new String[]{"/excel/import/app.js", "/excel/import/btn.js"}) {
            ResponseEntity<String> r = get(h, js);
            assertEquals(200, r.getStatusCode().value(), "★ " + js + " 必须 200（旧栈同）");
            assertTrue(String.valueOf(r.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("javascript"),
                    "★ " + js + " 必须是 JS 形态");
        }

        // 「下载导入模板」：旧栈 200 + xlsx 字节流。
        // ★ 文件名是**非 ASCII** ⇒ 这里传**原始文件名**（由客户端在线上编码成浏览器那种百分号形态），
        //   服务端 `getRequestURI()` 收到的就是编码形态 ⇒ 走的就是"必须实现 URL 解码"那条路径。
        //   ⚠️ 不要在这里手写 `%E9%85%92...`：RestTemplate 会对 `%` 再编码一次（`%25E9`），
        //   于是测的是"双重编码"而不是真实链路 —— 这个坑本轮真踩了（首版即红）。
        //   "编码形态本身能解码"由 `LegacyStaticAssetsTest#staticSpaceDecodesPercentEncodedPath` 直接钉。
        ResponseEntity<byte[]> xlsx = rest.exchange(
                "/_static/excel/酒店导入模版.xlsx",
                HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertEquals(200, xlsx.getStatusCode().value(),
                "★ 下载模板必须 200（旧栈同；缺 URL 解码时这里必红）");
        byte[] bytes = xlsx.getBody();
        assertEquals(0x50, bytes[0] & 0xFF, "xlsx 是 zip 容器（PK 头），实际首字节=" + bytes[0]);
        assertEquals(0x4B, bytes[1] & 0xFF, "同上");
    }

    @Test
    @DisplayName("★ U3-4：静态层未命中时仍是 404（不得把动作 URL 吞成静态供给）")
    void staticMissStillFallsThroughTo404() {
        HttpHeaders h = session();
        // 旧栈实测：`/excel/nope.js`、`/_view/theme/nope.js`、`/_static/excel/nope.xlsx` 全 404
        for (String p : new String[]{"/excel/nope.js", "/_view/theme/nope.js", "/_static/excel/nope.xlsx"}) {
            assertEquals(404, get(h, p).getStatusCode().value(), p + " 必须 404（旧栈同）");
        }
    }
}
