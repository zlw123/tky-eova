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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @DisplayName("★ 命中：静态空间内的真实文件可解析（**内容供给**由真容器判据覆盖，本处只钉解析政策）")
    void resolvesExistingFileInsideStaticSpace(@TempDir Path dir) throws Exception {
        LegacyStaticAssets assets = new LegacyStaticAssets(fixture(dir));

        File f = assets.resolve("/eova/lib/x.css");
        assertNotNull(f, "静态空间内的真实文件必须命中");
        assertEquals("x.css", f.getName());
        assertEquals("text/css", assets.contentType("/eova/lib/x.css"), "旧栈实测 .css ⇒ text/css");
        assertArrayEquals("body{color:red}".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(f.toPath()), "命中的文件字节必须与夹具一致");

        // ★★ r332（经拿哥授权的一次性判据演进）：原此处断言的是 `assets.serve(path, resp)` 直出
        //   （200 + Content-Type + 字节）。U1 之后**该方法已不在请求路径上**，故本轮删除它，
        //   可观测面按下方"覆盖转移"改由两条判据承担 —— 不是放宽：
        //     ① `LegacyStaticAssetContractTest`（真容器 + 冻结 sha256）：三条运行时制品 + 两个样式的
        //        **200 + 字节金标 + Content-Type** 逐字节核对；
        //     ② `StaticResourceHandlerMappingTest`：命中 ⇒ 交出 Spring `ResourceHttpRequestHandler`，
        //        并**真实驱动 `handleRequest`** 断言状态码 / Content-Type / 字节；
        //     ③ 本类新增**反向结构断言**（下方 serveIsGone）：旧机制不得回归。
    }

    /**
     * **反向结构判据**（演进后替代原"serve 直出"断言）：`LegacyStaticAssets` 不得再有 `serve` 方法。
     *
     * <p>理由：该方法在 U1 后已离开请求路径，保留它等于留着"第二条供给路径"；判据断言其**不存在**，
     * 比断言"它写下 200"更能防回归 —— 覆盖由①真容器字节金标 ②Spring 供给器行为判据承担。</p>
     */
    @Test
    @DisplayName("★ 反向：`LegacyStaticAssets` 不得再声明 `serve`（旧机制不得回归）")
    void serveIsGone() {
        boolean present = java.util.Arrays.stream(LegacyStaticAssets.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("serve"));
        assertFalse(present, "★ 静态供给已由 StaticResourceHandlerMapping + Spring ResourceHttpRequestHandler 承担；"
                + "LegacyStaticAssets#serve 不得回归（判据 ①LegacyStaticAssetContractTest ②StaticResourceHandlerMappingTest 承担覆盖）");
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

    /**
     * **静态空间不得吞掉接口前缀**（第 305 轮 · U1 配套）。
     *
     * <p>为什么需要"结构性"断言：壳供给引入后，静态空间一旦把 `/api/**` 也算进来，
     * 行为上**看不出来**（`/api/home/menu` 在静态根下没有同名文件 ⇒ `resolve()` 返回 null ⇒
     * 照旧走动作路由）—— 实测该变异（M4）在 HTTP 判据下**未被捕获**，属"等价变异"。
     * 但意图必须钉住：静态空间就是那三个前缀，不得扩到接口/动作上。</p>
     */
    @Test
    @DisplayName("静态空间只认七个前缀（/eova /_eova /eova-assets /demo /_view /excel /_static）—— /api 与动作路径都不算")
    void staticSpaceDoesNotSwallowApi() {
        LegacyStaticAssets assets = new LegacyStaticAssets(null, null);
        assertTrue(assets.isStaticSpace("/eova/lib/eova/eovaui.js"), "/eova/** 是静态空间");
        assertTrue(assets.isStaticSpace("/_eova/assets/eova.ui.ext.js"), "/_eova/** 是静态空间");
        assertTrue(assets.isStaticSpace("/eova-assets/index-abc.js"), "SPA 产物是静态空间");
        assertTrue(assets.isStaticSpace("/demo/test/btn.js"),
                "/demo/** 是静态空间（按钮脚本按该 URL 取；旧栈实测 200 application/javascript）");
        // ★ r307（U3）：三个"仍由后端渲染的旧页的子资源"空间 —— 缺口实测都是"页面 200、子资源 404"
        assertTrue(assets.isStaticSpace("/_view/theme/index.js"),
                "/_view/** 是静态空间（/main 主题页的脚本；旧栈 200、新栈曾 404）");
        assertTrue(assets.isStaticSpace("/excel/import/app.js"),
                "/excel/** 是静态空间（Excel 导入页自己的脚本；旧栈 200、新栈曾 404）");
        assertTrue(assets.isStaticSpace("/_static/excel/酒店导入模版.xlsx"),
                "/_static/** 是静态空间（可下载模板；旧栈 200、新栈曾 404）");
        assertFalse(assets.isStaticSpace("/api/home/menu"), "/api/** 不是静态空间（那是接口）");
        for (String p : new String[]{"/api/home/menu", "/menu/add", "/meta/reorder_data", "/app/meta_product"}) {
            assertFalse(assets.isStaticSpace(p), p + " 不得被静态空间吞掉");
        }
        // ★ 反向：`/excel` 既是静态空间又是**动作路由前缀** ⇒ 动作 URL 必须仍然走动作层。
        //   本层只在**文件真实存在**时直出（`serve` 返回 false 时调用方继续走动作路由），
        //   `/excel/imports/<objectCode>` 在静态根下没有同名文件 ⇒ 不会被吞。
        assertFalse(assets.resolve("/excel/imports/sys_hotel") != null,
                "★ /excel/imports/<code> 是动作 URL，静态层不得命中它（否则 Excel 导入页会变成文件供给）");
    }

    @Test
    @DisplayName("★ r307：静态层必须做 URL 解码（非 ASCII 文件名），且解码不得造成目录穿越")
    void staticSpaceDecodesPercentEncodedPath() {
        // 用真实 legacy 根构造（非 ASCII 文件名只有真文件才测得出差异）
        java.io.File webRoot = new java.io.File(
                "../../../../front/remis-eova-ui/src/legacy").getAbsoluteFile();
        org.junit.jupiter.api.Assumptions.assumeTrue(webRoot.isDirectory(), "legacy 根不存在 ⇒ 跳过（fail-closed 由 HTTP 判据兜底）");
        LegacyStaticAssets assets = new LegacyStaticAssets(webRoot, null);

        // 未编码：文件真实存在 ⇒ 命中
        assertNotNull(assets.resolve("/_static/excel/酒店导入模版.xlsx"), "未编码路径应命中真实文件");
        // 编码（浏览器实际发的形态）：必须同样命中 —— 不实现解码的话这里永远是 null
        assertNotNull(assets.resolve("/_static/excel/%E9%85%92%E5%BA%97%E5%AF%BC%E5%85%A5%E6%A8%A1%E7%89%88.xlsx"),
                "★ 百分号编码路径必须命中（浏览器发的就是这种形态，缺解码则永远 404）");
        // 目录穿越：编码形态也必须被拦住（解码在越界校验**之前**）
        assertNull(assets.resolve("/_static/%2e%2e%2f%2e%2e%2fpom.xml"), "★ 编码形式的目录穿越必须拒绝");
        assertNull(assets.resolve("/_view/%2e%2e/%2e%2e/pom.xml"), "★ 同上（/_view 空间）");
    }
}
