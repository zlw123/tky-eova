/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **切片 S3 判据：旧静态空间 {@code /eova/**} 的字节级供给**。
 *
 * <p><b>为什么这样判</b>：{@code remis-eova-ui} 按契约纪律用**旧原路径**引用 vendor 级运行时
 * （{@code index.html} 的 {@code <link>/<script>} 与 {@code compat/legacy-runtime.ts} 的
 * {@code LEGACY_RUNTIME_SCRIPTS}），dev 期靠 Vite 代理、生产期靠本层供给。这条链路此前只能靠
 * 代码审阅：URL 少一层、根目录取错、Content-Type 不对，**构建/单测/闸门全绿**，症状是
 * "页面样式全丢、组件渲染错位"，只有浏览器里才看得见（与第 102/104 轮同类漂移）。</p>
 *
 * <p><b>期望 sha256 是外部事实</b>：取自冻结账本 {@code docs/.local/ledger/frontend-*.jsonl}
 * （{@code sourceSha256}，来源为旧源码 {@code view/src/main/resources/webapp/eova/**}）。
 * 这里**内联为常数**而不是读账本文件：判据要钉的是"HTTP 直出的字节就是冻结的那份"，
 * 若两侧都读同一份账本，账本本身被改就一起漂移（自证陷阱）。</p>
 *
 * <p><b>断言（5 条互为独立面）</b>：</p>
 * <ol>
 *   <li>三制品（EovaTools / LayuiVue / EovaUI）⇒ 200 且 sha256 与冻结值**逐字节相同**；</li>
 *   <li>两个样式 ⇒ 200 且 sha256 与冻结值相同；</li>
 *   <li>Content-Type 契约：{@code .css} ⇒ {@code text/css}、{@code .js} ⇒ {@code application/javascript}
 *       （旧 demo 实测值）；</li>
 *   <li>静态空间内不存在的文件 ⇒ **404**（不得回落到 SPA 200）；</li>
 *   <li>目录穿越 ⇒ **不得读出根外文件**（响应里不能出现根外文件的内容）。<b>诚实标注</b>：Tomcat
 *       会在分发前规范化/拒绝 {@code ..}，本请求可能根本没进本层 ⇒ 该条**不足以**证明越界守卫，
 *       守卫本身由 {@code LegacyStaticAssetsTest.rejectsDotDotTraversal}（无容器、直调）钉住。</li>
 * </ol>
 *
 * <p><b>前置</b>：视图根 {@code eova.webapp.root}（默认 {@code remis-eova/front/remis-eova-ui/src/legacy}）
 * 必须可达；不可达时本判据会红（属环境/落点缺失，不是通过）。</p>
 */
// ★ 与 LegacyHttpContractTest / LegacyWebBootstrapTest 用同一个上下文配置：
//   旧引导是"每 JVM 一次"语义，同 JVM 里多起一个上下文会立刻
//   `Model mapping already exists`（r246 实测）。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegacyStaticAssetContractTest {

    /** 冻结账本里的 sha256（外部事实；来源文件见类注释） */
    private static final Map<String, String> FROZEN_SHA256 = new HashMap<>();

    static {
        FROZEN_SHA256.put("/eova/lib/eova/lib/eova-tools.umd.js",
                "e363f0e2d4f051c3585632dbd82e2fb550b13cad522986f7a52936af692c6e95");
        FROZEN_SHA256.put("/eova/lib/eova/lib/layui.umd.js",
                "85cf246d11fb9ae82cdd0c5bea1c7cbbd0be308538513bcacf68589887d9d230");
        FROZEN_SHA256.put("/eova/lib/eova/eovaui.js",
                "856b5257f1a61522060c094793ec3d0415e8da9f9aa3fc9988655aa17e29ed66");
        FROZEN_SHA256.put("/eova/lib/eova/eovaui.css",
                "426b977d8166a07f0f0286b1a17f36d48914d7416f6c18148ce68789a6f7557e");
        FROZEN_SHA256.put("/eova/ui/css/common.css",
                "44bf7ac9ce0db618ab74f28f7205d03a791bc15466cdff140686181c23326fd1");
    }

    @Autowired
    private TestRestTemplate rest;

    /**
     * 取字节并校验 sha256 等于冻结值
     *
     * @param url 旧原路径
     * @return 响应（已断言 200 与哈希）
     */
    private ResponseEntity<byte[]> fetchPinned(String url) {
        ResponseEntity<byte[]> resp = rest.getForEntity(url, byte[].class);
        assertEquals(200, resp.getStatusCode().value(), url + " 必须 200（静态空间供给），实际="
                + resp.getStatusCode());
        byte[] body = resp.getBody();
        assertNotNull(body, url + " 必须有正文");
        assertEquals(FROZEN_SHA256.get(url), sha256(body),
                "★ " + url + " 的字节必须与冻结账本逐字节一致（长度=" + body.length + "）");
        return resp;
    }

    /** sha256 十六进制（小写） */
    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(bytes)) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("★ S3-1：三制品（EovaTools/LayuiVue/EovaUI）⇒ 200 且 sha256 与冻结账本逐字节一致")
    void threeRuntimeArtifactsAreServedByteIdentical() {
        fetchPinned("/eova/lib/eova/lib/eova-tools.umd.js");
        fetchPinned("/eova/lib/eova/lib/layui.umd.js");
        fetchPinned("/eova/lib/eova/eovaui.js");
    }

    @Test
    @DisplayName("★ S3-2：样式（eovaui.css / common.css）⇒ 200 且 sha256 与冻结账本逐字节一致")
    void stylesAreServedByteIdentical() {
        fetchPinned("/eova/lib/eova/eovaui.css");
        fetchPinned("/eova/ui/css/common.css");
    }

    @Test
    @DisplayName("★ S3-3：Content-Type 契约（.css=text/css、.js=application/javascript，旧 demo 实测值）")
    void contentTypeMatchesLegacy() {
        ResponseEntity<byte[]> css = rest.getForEntity("/eova/lib/eova/eovaui.css", byte[].class);
        String cssType = String.valueOf(css.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(cssType.startsWith("text/css"), "★ .css 必须是 text/css（旧实测），实际=" + cssType);

        ResponseEntity<byte[]> js = rest.getForEntity("/eova/ui/meta/eova.meta.js", byte[].class);
        assertEquals(200, js.getStatusCode().value(), "/eova/ui/meta/eova.meta.js 必须 200");
        String jsType = String.valueOf(js.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(jsType.startsWith("application/javascript"),
                "★ .js 必须是 application/javascript（旧实测），实际=" + jsType);
    }

    @Test
    @DisplayName("★ S3-4：静态空间内不存在的文件 ⇒ 404（不得回落到 SPA 200）")
    void missingStaticFileIsNotFound() {
        ResponseEntity<String> resp = rest.getForEntity("/eova/lib/eova/lib/no-such-artifact.js", String.class);
        assertEquals(404, resp.getStatusCode().value(),
                "★ 必须 404（旧栈静态 miss ⇒ 动作层也没有 ⇒ 404），实际=" + resp.getStatusCode());
    }

    @Test
    @DisplayName("★ S3-5：目录穿越 ⇒ 不得读出静态根之外的文件")
    void pathTraversalIsRejected() {
        // 根外有个必然存在的文件：模块自身的 pom.xml（含 <artifactId>）。穿越成功会把它的内容吐出来。
        ResponseEntity<String> resp = rest.getForEntity("/eova/%2e%2e/pom.xml", String.class);
        String body = resp.getBody() == null ? "" : resp.getBody();
        assertFalse(body.contains("<artifactId>eova-web</artifactId>") || body.contains("</project>"),
                "★ 不得读出静态根之外的文件（目录穿越），实际状态=" + resp.getStatusCode());
    }
}
