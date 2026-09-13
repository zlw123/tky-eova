/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.template;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.eova.common.base.BaseSharedMethod;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.compat.template.EnjoyTemplateRenderService;
import cn.eova.compat.template.LegacyPageRenderer;
import cn.eova.compat.template.LegacyRowFieldGetter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **T04 收官判据：极简渲染器 vs Enjoy 的差分等价**（真实活页模板 + 同一份数据，**逐字节**比对）。
 *
 * <p>左侧 oracle = `EnjoyTemplateRenderService`（现成的 Enjoy 包装，生产里已属死面 ⇒ 当测试基准正合适）；
 * 右侧 = 本仓 `LegacyPageRenderer`。两者都喂**同一份数据与同一份共享方法**
 * （`BaseSharedMethod`：`conf('…')`/`getUIConf()` 的来源）。</p>
 *
 * <p>覆盖：`/main`（零指令 ⇒ 纯静态）+ Excel 导入页（`#(…)`/`#if/#else/#end`/`#include` 全用到，
 * 且 `include` 闭包含 `_view/_page/form.html`（内里有"JS 注释形式的指令"参与配对）与
 * `_view/_block/base.html`（`conf('ui.include')` + `#render` 的组合）。</p>
 */
class LegacyPageRendererGoldenTest {

    private static File webRoot;

    @BeforeAll
    static void setUp() {
        // 与生产一致：Model/Record 列属性读取器必须在场（Enjoy 侧靠它读 `template.name` 之类）
        LegacyRowFieldGetter.install();
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        webRoot = dir.resolve("../../../../front/remis-eova-ui/src/legacy").toFile();
        assertTrue(webRoot.isDirectory(), "★ fail-closed：legacy 视图根不存在 " + webRoot);
    }

    /** Enjoy 侧 oracle */
    private static EnjoyTemplateRenderService enjoy() {
        return new EnjoyTemplateRenderService(webRoot.getAbsolutePath(), new BaseSharedMethod());
    }

    /** 本仓侧实现 */
    private static LegacyPageRenderer mini() {
        return LegacyPageRenderer.of(webRoot, List.of(new BaseSharedMethod()));
    }

    /** Excel 导入页的数据（与 `ExcelController#imports` 同形：`template` = 一行 `eova_import_template`） */
    private static Map<String, Object> importPageScope() {
        LegacyKv template = LegacyKv.create();
        template.set("id", 1);
        template.set("code", "sys_hotel");
        template.set("name", "导入酒店数据");
        template.set("url", "/excel/import/酒店导入模版.xlsx");
        template.set("option", "[{\"val\":1,\"txt\":\"覆盖\"}]");
        template.set("info", "导入前请先下载模版");
        template.set("type", 1);
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("template", template);
        return scope;
    }

    @Test
    @DisplayName("★ T04-27：`/main`（零指令）两边逐字节一致")
    void mainPageMatches() throws Exception {
        String path = "/_view/theme/index.html";
        String oldOut = enjoy().render(path, new LinkedHashMap<>());
        String newOut = mini().render(path, new LinkedHashMap<>());
        assertEquals(oldOut, newOut, "★ /main 渲染结果必须逐字节一致");
        // 与模板文件本身一致（零指令 ⇒ 纯静态）
        String raw = Files.readString(webRoot.toPath().resolve("_view/theme/index.html"), StandardCharsets.UTF_8);
        assertEquals(raw, newOut, "零指令页应等于模板原文");
        assertTrue(newOut.length() > 10000, "主题页应有约 19KB 内容");
    }

    @Test
    @DisplayName("★ T04-28：Excel 导入页（含 include 闭包）两边逐字节一致")
    void excelImportPageMatches() {
        String path = "/excel/import/app.html";
        Map<String, Object> scope = importPageScope();
        String oldOut = enjoy().render(path, scope);
        String newOut = mini().render(path, scope);
        assertEquals(oldOut, newOut, "★ Excel 导入页渲染结果必须逐字节一致（含 include 闭包与共享方法）");
        // 反空断言：真的渲染出了内容（防"两边都空 ⇒ 假绿"）
        assertTrue(newOut.length() > 1000, "该页应渲染出上千字节，实际=" + newOut.length());
        assertTrue(newOut.contains("导入酒店数据"), "应包含 template.name 的值");
    }

    @Test
    @DisplayName("★ T04-29：`#if` 取假分支时两边一致（未取分支**不参与求值**，但仍参与配对）")
    void falseBranchMatches() {
        String path = "/excel/import/app.html";
        Map<String, Object> scope = importPageScope();
        ((LegacyKv) scope.get("template")).set("info", null);   // 让 `#if(template.info)` 取假分支
        String oldOut = enjoy().render(path, scope);
        String newOut = mini().render(path, scope);
        assertEquals(oldOut, newOut, "★ 取假分支时也必须逐字节一致（含 `#else` 前不裁空格的细节）");
    }

    @Test
    @DisplayName("★ T04-30：不支持的指令**响亮抛错**（未取分支里的未知指令同样抛错 —— Enjoy 是整篇编译）")
    void unsupportedDirectivesFailLoudly() {
        Map<String, Object> scope = importPageScope();
        for (String text : new String[]{"#for(x : list)body#end", "#set(x = 1)", "#define foo()bar#end"}) {
            assertThrows(UnsupportedOperationException.class, () -> mini().renderText(text, scope),
                    "★ 未知指令必须响亮抛错：" + text);
        }
        // 未取分支里的未知指令：Enjoy 整篇编译会失败 ⇒ 本实现也必须失败（不能"因为没走到就放过"）
        String skipped = "#if(false)x#for(a : b)c#end#end";
        assertThrows(UnsupportedOperationException.class, () -> mini().renderText(skipped, scope),
                "★ 未取分支里的未知指令也必须抛错（与 Enjoy 的整篇编译一致）");
    }
}
