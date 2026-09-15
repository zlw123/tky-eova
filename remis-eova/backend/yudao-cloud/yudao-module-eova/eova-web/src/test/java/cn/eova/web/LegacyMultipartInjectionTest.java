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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.eova.compat.jfinal.config.LegacyConstants;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.jfinal.upload.LegacyUploadConfig;
import cn.eova.compat.jfinal.upload.LegacyUploadFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **DES-012 P1-U4 单元判据：Spring multipart → 旧口径部件容器的注入**（不起容器）。
 *
 * <p>它钉三件事：</p>
 * <ol>
 *   <li><b>行为</b>：multipart 请求带文件 ⇒ 控制器拿得到容器，且按既有落盘语义（{@code getFiles(dir)}）
 *       落出 {@code parameterName / originalFileName / contentType / 字节} 全对的 {@link LegacyUploadFile}。</li>
 *   <li><b>★ 失败面等价</b>：**非 multipart** 与**multipart 但无文件部件**两种情形一律**不注入** ⇒
 *       {@code LegacyController#getFiles} 抛"未注入"⇒ 500，与旧栈（COS 解析层报错）同一失败面
 *       —— 不把"没带文件"悄悄改成 200 空列表。</li>
 *   <li><b>契约对齐</b>：Spring 的 multipart 上限必须等于旧常量 {@code LegacyConstants#getMaxPostSize()}
 *       （Boot 默认 1MB 会在解析层提前改变契约）。</li>
 * </ol>
 *
 * <p><b>为什么必须有这条判据</b>：该缺口此前**对全量扫描不可见** —— 既有上传判据全是单元级
 * （自己调 {@code setMultipartRequest}），而 HTTP 面 {@code POST /upload/file} 在旧栈 200、新栈 500。
 * HTTP 侧的对照由 {@code UploadHttpTest} 承担。</p>
 */
class LegacyMultipartInjectionTest {

    /** 落盘用临时根（把 baseUploadPath 置为 "/" ⇒ 最终目录就是被测目录本身） */
    private String savedBase;
    private long savedMax;
    private String savedEncoding;

    /**
     * 记下旧值（{@code LegacyUploadConfig} 是静态持有者，判据必须自隔离 —— R76 教训）。
     */
    private void snapshotConfig() {
        savedBase = LegacyUploadConfig.getBaseUploadPath();
        savedMax = LegacyUploadConfig.getMaxPostSize();
        savedEncoding = LegacyUploadConfig.getEncoding();
    }

    /** 还原静态配置，避免跨判据类泄漏 */
    @AfterEach
    void restoreConfig() {
        if (savedBase != null) {
            LegacyUploadConfig.init(savedBase, savedMax, savedEncoding);
        }
    }

    @Test
    @DisplayName("行为：multipart 带文件 ⇒ 注入容器，且落盘字段/字节与部件一致")
    void multipartWithFileIsInjectedAndLanded(@TempDir Path dir) throws Exception {
        snapshotConfig();
        LegacyUploadConfig.init("/", savedMax, savedEncoding);

        MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
        request.setMethod("POST");
        byte[] bytes = "PNG-PROBE".getBytes(StandardCharsets.UTF_8);
        request.addFile(new MockMultipartFile("file", "probe.png", "image/png", bytes));
        request.addParameter("name", "file");

        LegacyController controller = new LegacyController();
        LegacyMultipartInjector.inject(controller, request);

        assertNotNull(controller.getMultipartRequest(), "★ multipart 带文件时必须注入部件容器（否则 /upload/* 会 500）");
        List<LegacyUploadFile> files = controller.getFiles(dir.toString());
        assertEquals(1, files.size(), "应落盘 1 个文件");
        LegacyUploadFile f = files.get(0);
        assertEquals("file", f.getParameterName(), "字段名 = Spring 的部件名");
        assertEquals("probe.png", f.getOriginalFileName(), "★ 浏览器原名必须原样带过来（响应里的 oldFileName 靠它）");
        assertTrue(f.getFileName().endsWith(".png"), "落盘文件名保留扩展名，实际=" + f.getFileName());
        assertEquals("image/png", f.getContentType(), "Content-Type 取自部件");
        assertArrayEquals(bytes, Files.readAllBytes(f.getFile().toPath()), "落盘字节必须与上传字节一致");
    }

    @Test
    @DisplayName("★ 失败面等价：非 multipart ⇒ 不注入（getFiles 抛未注入 ⇒ 500）")
    void nonMultipartIsNotInjected() {
        snapshotConfig();
        LegacyController controller = new LegacyController();
        LegacyMultipartInjector.inject(controller, new MockHttpServletRequest("POST", "/upload/file"));
        assertNull(controller.getMultipartRequest(), "★ 非 multipart 不得注入（旧栈由 COS 报错）");
    }

    @Test
    @DisplayName("★ 失败面等价：multipart 但无文件部件 ⇒ 不注入（不得变成 200 空列表）")
    void multipartWithoutFilePartIsNotInjected() {
        snapshotConfig();
        MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
        request.setMethod("POST");
        request.addParameter("name", "file");
        LegacyController controller = new LegacyController();
        LegacyMultipartInjector.inject(controller, request);
        assertNull(controller.getMultipartRequest(), "★ 没有文件部件时不得注入空容器（那会把 500 改成 200）");
    }

    @Test
    @DisplayName("契约对齐：Spring multipart 上限 = 旧常量 maxPostSize（10MB），且两处事实源一致")
    void multipartLimitMatchesLegacyConstant() throws Exception {
        long legacy = new LegacyConstants().getMaxPostSize();
        assertEquals(10L * 1024 * 1024, legacy, "旧常量 maxPostSize 应为 10MB（LegacyUploadConfig 的上游）");

        String yaml = Files.readString(new File("src/main/resources/application.yaml").toPath(),
                StandardCharsets.UTF_8);
        long expectedMb = legacy / 1024 / 1024;
        Matcher m = Pattern.compile("max-file-size:\\s*(\\S+)").matcher(yaml);
        assertTrue(m.find(), "★ application.yaml 必须显式声明 max-file-size（Boot 默认 1MB ⇒ 解析层提前改变契约）");
        Matcher m2 = Pattern.compile("max-request-size:\\s*(\\S+)").matcher(yaml);
        assertTrue(m2.find(), "★ application.yaml 必须显式声明 max-request-size");
        assertEquals(expectedMb + "MB", m.group(1), "★ max-file-size 必须与旧常量 maxPostSize 对齐");
        assertEquals(expectedMb + "MB", m2.group(1), "★ max-request-size 同样对齐");
    }
}
