/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **切片 S3 单元判据：{@link LegacyStaticAssets} 的命中口径与越界守卫**（不起容器）。
 *
 * <p><b>为什么必须另有单元判据</b>：HTTP 层那条"目录穿越不得泄露"的断言是**假通过风险**——
 * Tomcat 会在分发前规范化/拒绝 {@code ..} 与 {@code %2e%2e}，请求可能**根本没进本类**，
 * 于是删掉越界守卫也照样绿（r246 学到的"只断言非 2xx = 假通过"的同类形态）。
 * 故本判据直接调用 {@code resolve/serve}，把守卫本身钉住。</p>
 */
class LegacyStaticAssetsTest {

    /**
     * 造一个临时 web 根：{@code <root>/eova/lib/x.css} 与 {@code <root>/secret.txt}（根外目标）
     *
     * @param dir JUnit 提供的临时目录
     * @return 临时 web 根
     * @throws Exception 写文件失败
     */
    private static File fixture(Path dir) throws Exception {
        Path webRoot = Files.createDirectories(dir.resolve("web"));
        Path eovaLib = Files.createDirectories(webRoot.resolve("eova/lib"));
        Files.write(eovaLib.resolve("x.css"), "body{color:red}".getBytes(StandardCharsets.UTF_8));
        Files.write(webRoot.resolve("secret.txt"), "ROOT-OUTSIDE-SECRET".getBytes(StandardCharsets.UTF_8));
        return webRoot.toFile();
    }

    @Test
    @DisplayName("命中：静态空间内的真实文件可解析，且 serve 出的字节与 Content-Type 正确")
    void servesExistingFileInsideStaticSpace(@TempDir Path dir) throws Exception {
        LegacyStaticAssets assets = new LegacyStaticAssets(fixture(dir));

        File f = assets.resolve("/eova/lib/x.css");
        assertNotNull(f, "静态空间内的真实文件必须命中");
        assertEquals("x.css", f.getName());

        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertTrue(assets.serve("/eova/lib/x.css", resp), "serve 应直出并返回 true");
        assertEquals(200, resp.getStatus());
        assertEquals("text/css", resp.getContentType(), "旧栈实测 .css ⇒ text/css");
        assertArrayEquals("body{color:red}".getBytes(StandardCharsets.UTF_8), resp.getContentAsByteArray(),
                "直出字节必须与文件逐字节一致");
    }

    @Test
    @DisplayName("★ 越界守卫：`/eova/../secret.txt` 必须解析为 null（不得读出静态根之外）")
    void rejectsDotDotTraversal(@TempDir Path dir) throws Exception {
        LegacyStaticAssets assets = new LegacyStaticAssets(fixture(dir));
        assertNull(assets.resolve("/eova/../secret.txt"),
                "★ 目录穿越必须被拒 —— 这条断言专门钉守卫；删掉守卫它就会红");
        assertNull(assets.resolve("/eova/lib/../../secret.txt"),
                "★ 多级回退同样必须被拒");
    }

    @Test
    @DisplayName("非静态空间与目录：`/_eova/**`（旧栈 404）、目录本身都不得命中")
    void ignoresNonStaticSpaceAndDirectories(@TempDir Path dir) throws Exception {
        LegacyStaticAssets assets = new LegacyStaticAssets(fixture(dir));
        assertNull(assets.resolve("/_eova/include.html"), "旧栈实测 /_eova/** 静态 404 ⇒ 不属静态空间");
        assertNull(assets.resolve("/ui/css/common.css"), "旧栈实测 /ui/** 静态 404 ⇒ 不属静态空间");
        assertNull(assets.resolve("/eova/lib"), "目录不是文件，不得命中");
        assertNull(assets.resolve("/eova/no-such.js"), "不存在的文件不得命中（交给动作路由）");
        // ★ 别名防护：非 `/eova/` 前缀但"剥离 5 字符后恰好是有效相对路径"的请求**不得命中**
        //   （`resolve` 按固定长度剥离前缀，若前缀判断被放宽就会别名命中同一个文件）
        assertNull(assets.resolve("/xxxxx/lib/x.css"), "★ 非 /eova/ 前缀不得因固定剥离而别名命中");
        assertNull(assets.resolve("xxxxx/lib/x.css"), "无前导斜杠同样不得命中");
    }

    @Test
    @DisplayName("web 根不可用时：不得命中任何路径（响亮降级，不抛异常）")
    void missingRootYieldsNothing(@TempDir Path dir) {
        LegacyStaticAssets assets = new LegacyStaticAssets(null);
        assertNull(assets.resolve("/eova/lib/eova/eovaui.css"), "根缺失时不得命中");
    }
}
