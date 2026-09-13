/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **PHASE-2 / T04 第二段判据：{@code com.jfinal:enjoy} 的依赖面清单（防漂移）**。
 *
 * <p><b>为什么先要有这张清单</b>：退役 {@code com.jfinal:enjoy} 是阶段 2 的最后一块，而"还剩哪些
 * 生产代码真的在用 Enjoy"必须**逐条可复现**地钉住 —— 否则边退边漏，最后一编译才知道还有依赖。
 * 本判据把当前依赖面（文件 → 用到的 {@code com.jfinal.*} 类）**双向冻结**：
 * 新增一处用法、或删掉一处却没改表，都会红。</p>
 *
 * <p><b>分类（r308 取证；分类靠调用点语义，不靠包名）</b>：</p>
 * <ul>
 *   <li>{@code EXPR}〔表达式求值 / 业务字符串模板〕—— **真正的阻塞项**：
 *       {@code ExpUtil#parse} 用 {@code Engine.use().getTemplateByString(...).renderToString(pms)}；
 *       {@code AuthUri} 的 URI 规则同样按字符串模板求值；
 *       {@code LegacyRowFieldGetter extends FieldGetter} 提供表达式里 Model/Record 的字段取值语义；</li>
 *   <li>{@code RENDER}〔页面渲染接缝〕—— {@code LegacyTemplateRender}/{@code LegacyEngine}/
 *       {@code *SourceFactory}/引导类（{@code EnjoyTemplateRenderService}/{@code JsonDirective}/
 *       {@code RenderUtil} 链已于 r309 按授权删除 ⇒ 不再出现在依赖面上）；</li>
 *   <li>{@code UTIL}〔路径与字符串工具〕—— {@code PathKit}（5 处）、{@code StrKit}（1 处）：
 *       与模板无关，可直接换实现（{@code StrKit} 在 compat 里已有 port：{@code LegacyStrKit}）。</li>
 * </ul>
 *
 * <p><b>fail-closed</b>：源码根解析不到直接失败（不得静默通过）。</p>
 */
class EnjoyUsageInventoryTest {

    /** 生产源码里对 enjoy 制品的依赖面：文件（相对源码根） → 用到的类（排序后） */
    private static final Map<String, List<String>> DECLARED = new TreeMap<>(Map.ofEntries(
            // ---- EXPR：表达式求值 / 业务字符串模板（阻塞项）----
            Map.entry("cn/eova/config/PageConst.java", List.of("template.Engine")),
            Map.entry("cn/eova/compat/template/LegacyRowFieldGetter.java",
                    List.of("template.Engine", "template.expr.ast.FieldGetter")),
            // ---- RENDER：页面渲染接缝 ----
            Map.entry("cn/eova/compat/render/LegacyTemplateRender.java", List.of("template.Engine")),
            Map.entry("cn/eova/compat/jfinal/config/LegacyEngine.java",
                    List.of("template.Directive", "template.source.ISourceFactory")),
            Map.entry("cn/eova/ext/jfinal/EovaRenderSourceFactory.java",
                    List.of("template.source.ClassPathSource", "template.source.FileSource",
                            "template.source.ISource", "template.source.ISourceFactory")),
            Map.entry("cn/eova/web/LegacyViewSourceFactory.java",
                    List.of("template.source.FileSource", "template.source.ISource",
                            "template.source.ISourceFactory")),
            Map.entry("cn/eova/web/LegacyWebBootstrap.java", List.of("template.Engine"))
            // ---- UTIL：与模板无关的路径/字符串工具（可直接换实现）----
    ));

    /** 模块根（surefire 工作目录 = 模块目录）；解析不到 ⇒ fail-closed */
    private static Path moduleDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        assertTrue(Files.isDirectory(dir.resolve("src/main/java")),
                "★ fail-closed：找不到模块源码根（user.dir=" + dir + "）");
        return dir;
    }

    /** 三个模块的生产源码根 */
    private static List<Path> sourceRoots() {
        Path module = moduleDir();
        List<Path> roots = List.of(
                module.resolve("src/main/java"),
                module.getParent().resolve("eova-core/src/main/java"),
                module.getParent().resolve("eova-compat/src/main/java"),
                module.getParent().resolve("eova-db-adapter/src/main/java"));
        for (Path r : roots) {
            assertTrue(Files.isDirectory(r), "★ fail-closed：源码根不存在 " + r);
        }
        return roots;
    }

    private static final Pattern IMPORT = Pattern.compile("^import\\s+(com\\.jfinal\\.[\\w.]+)\\s*;");

    /** 扫描生产源码，返回 文件（相对源码根） → 用到的类（排序） */
    private static Map<String, List<String>> scan() throws IOException {
        Map<String, List<String>> found = new TreeMap<>();
        for (Path root : sourceRoots()) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path f : walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                    List<String> classes = new ArrayList<>();
                    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        Matcher m = IMPORT.matcher(line);
                        if (m.find()) {
                            classes.add(m.group(1).substring("com.jfinal.".length()));
                        }
                    }
                    if (!classes.isEmpty()) {
                        String rel = root.relativize(f).toString();
                        found.put(rel, new TreeSet<>(classes).stream().collect(Collectors.toList()));
                    }
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("★ T04-1：enjoy 依赖面**双向冻结**（新增用法或删了不改表都必须红）")
    void enjoyUsageSurfaceIsFrozenBothWays() throws IOException {
        Map<String, List<String>> actual = scan();

        // 反空断言：扫描规则失效时不得"因为扫不到而通过"
        // 反空断言：门限**随退役进度**下调（r309 死面删除后基线 7；UTIL 腿退役前是 17）。
        //   ⚠️ 真正防"扫描失效"的是下面"必须扫到已知文件"的断言 —— 门限只防"扫了个空"。
        assertTrue(actual.size() >= 5, "★ 扫到的依赖文件数异常少（" + actual.size() + "）⇒ 扫描规则可能已失效");
                assertTrue(actual.containsKey("cn/eova/compat/render/LegacyTemplateRender.java"), "★ 未扫到渲染接缝");
        // 反空断言 + 退役进度：已退役的 4 个文件不得再出现
        for (String gone : List.of(
                "cn/eova/engine/ExpUtil.java",
                // ---- r309 授权删除的 3 处死面（+ 死链 5 类）----
                "cn/eova/compat/template/EnjoyTemplateRenderService.java",
                "cn/eova/compat/template/TemplateRenderService.java",
                "cn/eova/ext/jfinal/directive/JsonDirective.java",
                "cn/eova/common/render/RenderUtil.java",
                "cn/eova/common/render/ResourceRender.java",
                "cn/eova/common/render/Html2DocRender.java",
                "cn/eova/common/render/Html2PdfRender.java",
                "cn/eova/common/render/Html2XlsRender.java",
                "cn/eova/common/render/OfficeRender.java")) {
            assertFalse(DECLARED.containsKey(gone), "已退役，不得再出现在依赖面清单里：" + gone);
        }

        List<String> undeclared = new ArrayList<>();
        for (String f : actual.keySet()) {
            if (!DECLARED.containsKey(f)) {
                undeclared.add(f + " → " + actual.get(f));
            } else if (!DECLARED.get(f).equals(actual.get(f))) {
                undeclared.add(f + " 类集合变化：声明=" + DECLARED.get(f) + " 实际=" + actual.get(f));
            }
        }
        List<String> gone = DECLARED.keySet().stream().filter(f -> !actual.containsKey(f)).collect(Collectors.toList());

        assertTrue(undeclared.isEmpty(),
                "★ 出现**未声明**的 enjoy 依赖（退役时会漏）：" + undeclared + " —— 请先在 DECLARED 里登记并分类");
        assertTrue(gone.isEmpty(),
                "★ 已声明的依赖消失：" + gone + " —— 若是退役成功，请同步更新 DECLARED（这就是进度账本）");
        assertEquals(7, DECLARED.size(), "依赖面文件数（17 − UTIL 5 − AuthUri 1 − 死面/死链 4：EnjoyTemplateRenderService/RenderUtil 链/JsonDirective）");
    }

    @Test
    @DisplayName("★ T04-2：分类计数冻结（EXPR 是阻塞项 —— 退役前必须先把它们解决或登记阻塞）")
    void categoriesAreCounted() {
        // ★ r309：`ExpUtil` 已整条离开依赖面（`parseTemplate` 随死链 `RenderUtil` 一起删除）；
        //   EXPR 腿上余下 2 个文件都属页面渲染侧（`PageConst` 配置 / `LegacyRowFieldGetter` 语义）。
        List<String> expr = List.of(
                "cn/eova/config/PageConst.java",
                "cn/eova/compat/template/LegacyRowFieldGetter.java");
        for (String f : expr) {
            assertTrue(DECLARED.containsKey(f), "EXPR 类文件必须在清单里：" + f);
        }
        assertEquals(2, expr.size(), "EXPR 文件数（业务表达式已全部切换；余下 PageConst 属页面渲染配置）");
        assertEquals(5, DECLARED.size() - expr.size(), "RENDER〔页面渲染接缝〕文件数（r309 删掉 3 处死面后 8 → 5）");
        // 反空断言：`AuthUri` 必须真的不在清单里（防"退役了却忘了改表"）
        assertFalse(DECLARED.containsKey("cn/eova/auth/AuthUri.java"), "AuthUri 已退役，不得再出现在依赖面清单里");
        // ★ UTIL 腿**已清零**（r308 第 2 轮）：`PathKit`(5 处) 与 `StrKit`(1 处) 全部换到 compat port
        //   （`LegacyPathKit`/`LegacyStrKit`）⇒ 那 5 个文件已不在 enjoy 依赖面上。
        //   反空断言：确认它们真的不在清单里（防"退役了却忘了改表"的另一半）。
        for (String f : List.of(
                "cn/eova/common/render/ResourceRender.java",
                "cn/eova/config/EovaConst.java",
                "cn/eova/mod/EovaModConst.java",
                "cn/eova/mod/EovaModPackage.java",
                "cn/eova/handler/UrlBanHandler.java")) {
            assertFalse(DECLARED.containsKey(f), "UTIL 腿已退役，不得再出现在依赖面清单里：" + f);
        }
    }

    @Test
    @DisplayName("★ T04-3：enjoy 制品仍是**生产依赖**（本判据是进度账本：退役后此断言须显式改掉）")
    void enjoyIsStillACompileScopeProductionDependency() throws IOException {
        Path pom = moduleDir().getParent().resolve("eova-compat/pom.xml");
        assertTrue(Files.isRegularFile(pom), "★ fail-closed：找不到 eova-compat/pom.xml");
        String xml = Files.readString(pom, StandardCharsets.UTF_8);

        int i = xml.indexOf("<artifactId>enjoy</artifactId>");
        assertTrue(i >= 0, "★ eova-compat 里应有 enjoy 依赖声明（若已退役，请把本判据改成「不得存在」）");
        // 取该 dependency 块，确认不是 test/provided 作用域
        int blockStart = xml.lastIndexOf("<dependency>", i);
        int blockEnd = xml.indexOf("</dependency>", i);
        String block = xml.substring(blockStart, blockEnd);
        assertFalse(block.contains("<scope>test</scope>") || block.contains("<scope>provided</scope>"),
                "★ enjoy 目前是**生产（compile）作用域**依赖 —— 这正是 T04 第二段要退役的对象");
        assertTrue(block.contains("<groupId>com.jfinal</groupId>"), "★ 依赖坐标应为 com.jfinal:enjoy");
    }
}
