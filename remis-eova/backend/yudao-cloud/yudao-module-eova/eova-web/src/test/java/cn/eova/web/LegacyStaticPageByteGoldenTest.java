/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **T04 第二段判据：`/main`（无指令页）走「直出」且与模板文件**逐字节一致**。
 *
 * <p><b>背景</b>：`/main`（EovaUI 主题页）的模板**一条指令都没有**（判据
 * {@code LegacyPageRenderSpecTest#mainThemePageIsStatic}）⇒ Enjoy 渲染它等价于**逐字节复制原文**。
 * 故 r308 第 8 轮在渲染接缝里加了"**无指令直出**"快路径：这类页**不再经过模板引擎**
 * ⇒ 活页面对 Enjoy 的运行时依赖从 2 个减到 1 个（只剩 Excel 导入页）。</p>
 *
 * <p><b>判据口径</b>：直接断言 HTTP 响应体 **等于模板文件的字节**。
 * 这比"和内联 sha256 比"更强也更稳：① 它证明的就是"直出"这一命题本身；
 * ② 不依赖数据库/配置（主题页只由模板文件决定）；③ 模板一旦被改，本判据与 legacy 资产账本会**同时**提醒。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegacyStaticPageByteGoldenTest {

    @Autowired
    private TestRestTemplate rest;

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

    private static Path templateFile() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path f = dir.resolve("../../../../front/remis-eova-ui/src/legacy/_view/theme/index.html").normalize();
        assertTrue(Files.isRegularFile(f), "★ fail-closed：主题页模板不存在 " + f);
        return f;
    }

    @Test
    @DisplayName("★ T04-25：`/main` 响应体 == 模板文件字节（即「无指令直出」，不经模板引擎）")
    void mainPageIsServedByteIdenticalToTemplate() throws Exception {
        ResponseEntity<byte[]> resp = rest.exchange("/main", HttpMethod.GET,
                new HttpEntity<>(session()), byte[].class);
        assertEquals(200, resp.getStatusCode().value(), "/main 必须 200");
        String ct = String.valueOf(resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(ct.contains("text/html"), "必须是 HTML，实际=" + ct);

        byte[] served = resp.getBody();
        byte[] file = Files.readAllBytes(templateFile());
        assertEquals(file.length, served == null ? -1 : served.length,
                "★ 响应体长度必须等于模板文件长度（不等的唯一可能是「没走直出」或被引擎改写）");
        assertEquals(new String(file, StandardCharsets.UTF_8), new String(served, StandardCharsets.UTF_8),
                "★ 响应体必须与模板文件**逐字节一致** —— 这就是「无指令直出」的等价性证明");

        // 反空断言：模板必须有内容（防"文件变空 ⇒ 两边都空 ⇒ 假绿"）
        assertTrue(file.length > 10000, "主题页模板应约 19KB，实际=" + file.length);
    }

    @Test
    @DisplayName("★ T04-31：活页渲染**不再需要 Enjoy 引擎**（引擎兜底计数必须为 0）")
    void livePagesDoNotNeedTheEngine() {
        long before = cn.eova.compat.render.LegacyTemplateRender.getEngineFallbackCount();
        long miniBefore = cn.eova.compat.render.LegacyTemplateRender.getMiniRenderCount();
        HttpHeaders h = session();
        // 两个活页各请求一次（有指令的那个才是关键：它本会走引擎）
        assertEquals(200, get(h, "/main").getStatusCode().value());
        assertEquals(200, get(h, "/excel/imports/sys_hotel").getStatusCode().value());
        long miniAfter = cn.eova.compat.render.LegacyTemplateRender.getMiniRenderCount();
        long after = cn.eova.compat.render.LegacyTemplateRender.getEngineFallbackCount();
        // ★ 同时证明"接缝真的切过去了"：极简渲染计数必须增加（只证明"没兜底"是不够的 ——
        //   把整段极简渲染关掉也满足"兜底为 0"）
        assertTrue(miniAfter > miniBefore,
                "★ 极简渲染计数必须增加（before=" + miniBefore + " after=" + miniAfter + "）");
        assertEquals(before, after,
                "★ 引擎兜底计数必须为 0（before=" + before + " after=" + after + "）—— "
                        + "计数大于 0 说明有活页用到了极简渲染器不支持的指令，必须扩规格或修实现");
    }

    private org.springframework.http.ResponseEntity<String> get(HttpHeaders h, String path) {
        return rest.exchange(path, org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(h), String.class);
    }

    @Test
    @DisplayName("★ T04-26：请求 `/main` 必须**真的走直出快路径**（字节相等证明不了机制生效 —— 只能数它）")
    void mainPageActuallyTakesFastPath() {
        long before = cn.eova.compat.render.LegacyTemplateRender.getDirectRenderCount();
        rest.exchange("/main", HttpMethod.GET, new HttpEntity<>(session()), byte[].class);
        long after = cn.eova.compat.render.LegacyTemplateRender.getDirectRenderCount();
        assertTrue(after > before,
                "★ 直出计数必须增加（before=" + before + " after=" + after + "）—— "
                        + "否则说明「/main」又走回了模板引擎（等价但慢，且违背本轮设计）");
    }
}
