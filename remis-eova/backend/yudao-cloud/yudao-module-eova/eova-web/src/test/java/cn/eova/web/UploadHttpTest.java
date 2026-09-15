/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.eova.compat.jfinal.upload.LegacyUploadConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **DES-012 P1-U4 HTTP 判据：上传端点在真容器里真的能跑（旧栈 200，新栈修前 500）**。
 *
 * <p><b>旧栈实测真值（9090，带会话）</b>，逐字记在断言里：</p>
 * <ul>
 *   <li>{@code POST /upload/file}（字段 {@code file}，参数 {@code name=file}）⇒
 *       <b>200</b> {@code {"fileName":"<时间戳+随机>.png","uploadDir":"/","state":"ok","oldFileName":"probe.png"}}；</li>
 *   <li>{@code POST /upload/img}（内容不是真图片）⇒ <b>200</b>
 *       {@code {"msg":"该文件不是标准的图片文件格式，请勿手工修改文件格式","state":"fail"}}；</li>
 *   <li>multipart 但不带文件 ⇒ <b>500</b>（既有失败面，原样保留）。</li>
 * </ul>
 *
 * <p><b>本判据为什么必须存在</b>：该缺口在修前对全量扫描完全不可见 —— 既有上传判据都是单元级
 * （自己造 {@code LegacyMultipartRequest}），而真实 HTTP 面上传直接 500。属"接线层必须有判据"。</p>
 *
 * <p><b>清理</b>：上传会真落盘（{@code baseUploadPath} 下），故断言后**删除落盘文件**，
 * 避免在仓库工作区留下未跟踪文件（扫描的"工作区干净"纪律）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UploadHttpTest {

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

    /**
     * 发一次 multipart 上传。
     *
     * @param session 会话头
     * @param path    端点（如 {@code /upload/file}）
     * @param field   部件名（同时也是请求参数 name 的取值 —— 旧 jfinal {@code getFile(name, dir)} 口径）
     * @param filename 浏览器原名
     * @param content 文件内容
     * @return 响应
     */
    private ResponseEntity<String> upload(HttpHeaders session, String path, String field,
            String filename, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("name", field);
        body.add(field, new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.addAll(session);
        return rest.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    @DisplayName("★ 合法文件上传 ⇒ 200 且 state:ok（旧栈同：fileName/uploadDir/oldFileName 齐备）")
    void legalFileUploadSucceeds() {
        HttpHeaders h = session();
        ResponseEntity<String> resp = upload(h, "/upload/file", "file", "probe.png",
                "PNG-PROBE".getBytes(StandardCharsets.UTF_8));

        assertEquals(200, resp.getStatusCode().value(),
                "★ 旧栈实测 200；修前新栈 500（multipart 部件容器未注入）。实际=" + resp.getBody());
        String body = String.valueOf(resp.getBody());
        assertTrue(body.contains("\"state\":\"ok\""), "★ 必须是 LegacyRet 的 state:ok 信封，实际=" + body);
        assertTrue(body.contains("\"oldFileName\":\"probe.png\""), "★ 必须回带浏览器原名，实际=" + body);
        assertTrue(body.contains("\"uploadDir\":\"/\""), "★ 默认上传目录为 /（旧栈同），实际=" + body);

        Matcher m = Pattern.compile("\"fileName\":\"([^\"]+)\"").matcher(body);
        assertTrue(m.find(), "必须回带落盘文件名，实际=" + body);
        String landed = m.group(1);
        File file = new File(baseUploadDir(), landed);
        assertNotNull(file, "落盘路径可解析");
        assertTrue(file.isFile(), "★ 上传必须真的落盘：期望存在 " + file.getAbsolutePath());
        // 清理：不在仓库工作区留下未跟踪文件
        assertTrue(file.delete() || !file.exists(), "清理落盘文件：" + file.getAbsolutePath());
    }

    @Test
    @DisplayName("★ 非图片走 /upload/img ⇒ 200 且 state:fail + 旧栈逐字错误消息")
    void nonImageUploadFailsWithLegacyMessage() {
        HttpHeaders h = session();
        ResponseEntity<String> resp = upload(h, "/upload/img", "img", "probe.png",
                "NOT-A-REAL-IMAGE".getBytes(StandardCharsets.UTF_8));

        assertEquals(200, resp.getStatusCode().value(), "旧栈实测 200（业务失败用 state:fail 表达）");
        String body = String.valueOf(resp.getBody());
        assertTrue(body.contains("\"state\":\"fail\""), "★ 必须是 state:fail，实际=" + body);
        assertTrue(body.contains("该文件不是标准的图片文件格式，请勿手工修改文件格式"),
                "★ 错误消息必须与旧栈逐字一致，实际=" + body);
    }

    @Test
    @DisplayName("★ 失败面等价：multipart 但不带文件 ⇒ 500（旧栈同；不得变成 200）")
    void multipartWithoutFileStays500() {
        HttpHeaders h = session();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("name", "file");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.addAll(h);
        ResponseEntity<String> resp = rest.postForEntity("/upload/file", new HttpEntity<>(body, headers), String.class);
        assertEquals(500, resp.getStatusCode().value(),
                "★ 旧栈实测 500（COS 解析层报错）⇒ 新栈必须同失败面，不得静默 200 空列表");
    }

    /** 上传根目录（由引导序列推给 LegacyUploadConfig 的运行时值） */
    private static File baseUploadDir() {
        return new File(LegacyUploadConfig.getBaseUploadPath());
    }
}
