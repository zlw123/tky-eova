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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **T04 第二段判据：页面渲染腿（RENDER）的**可达性面**——"还剩几处真的在用 Enjoy"必须可复现**。
 *
 * <p><b>为什么需要它</b>：退役 {@code com.jfinal:enjoy} 时，真正决定难度的不是"文件个数"，
 * 而是**哪些接缝是活的**（有人接线、必须换实现）与**哪些是死的**（无调用方、可随依赖一起删）。
 * 本判据把这两类**逐条冻结**：活面被拆掉、或死面被接上线，都必须显式改表。</p>
 *
 * <p><b>分类口径</b>：只统计**代码引用**（排除 {@code *} / {@code //} 注释行与类自身文件）——
 * 本轮实测就踩过"注释里提到类名被当成调用方"的坑（`ResourceRender` 首轮统计出 2 个"调用方"，
 * 实际全是 Javadoc 里的提及）。</p>
 *
 * <p><b>r308 第 3 轮取证结论</b>：</p>
 * <ul>
 *   <li><b>活面 6 类</b>：`LegacyTemplateRender`（`BaseController.render` 的执行者）·
 *       `EnjoyTemplateRenderService`（引擎实现）· `LegacyEngine`（compat 引擎包装）·
 *       `EovaRenderSourceFactory`（`EovaConfig#setSourceFactory` 接线）·
 *       `LegacyViewSourceFactory`（`LegacyWebBootstrap` 接线）· `LegacyWebBootstrap`（装配）；</li>
 *   <li><b>启动期注册 1 处</b>：`JsonDirective`（`EovaConfig#addDirective("json", …)`）——
 *       但它在模板里的**唯一使用者**是 `eova/_view/meta/reorder/app.html`，而 `/meta/reorder`
 *       已在 U1 退役为 SPA 壳 ⇒ 该指令**实际不再被任何活模板使用**（登记为"可随依赖一起删"的候选）；</li>
 *   <li><b>死链 6 个</b>：`RenderUtil` 的全部调用方（`Html2DocRender`/`Html2PdfRender`/`Html2XlsRender`/
 *       `OfficeRender`/`ResourceRender`）**自身都没有调用方** ⇒ 这条链在新栈里**完全不可达**
 *       ⇒ `RenderUtil` 对 Enjoy（`Engine` + `ClassPathSourceFactory`）的依赖是**死依赖**；</li>
 *   <li><b>另有 1 个"接线但无生产调用方"</b>：`EnjoyTemplateRenderService`（只有它自己的单测在用；
 *       `LegacyEngine` 的注释说"新栈由它承担"是**过时注释** —— 实际由 `LegacyTemplateRender` 直接用 `Engine`）。</li>
 * </ul>
 *
 * <p><b>对退役的意义</b>：真正阻塞退役的是**活面 + 2 个仍由后端渲染的活页面**
 * （`/excel/imports/<objectCode>`、`/main` 主题页，见 `LegacyTemplateFaceHttpTest`）；
 * 死链与 `#json` 只差一次口径确认就能删。本判据把这条界线钉死，避免"以为还剩很多"或"以为已经没了"。</p>
 */
class EnjoyRenderSurfaceTest {

    /** 声明：类 → 生产代码里的**调用方文件**（相对源码根，排序）；空列表 = 无调用方（死面） */
    private static final Map<String, List<String>> DECLARED_CALLERS = new LinkedHashMap<>();

    static {
        // ---- 活面：渲染链路上真正被接线的两类 ----
        DECLARED_CALLERS.put("cn.eova.compat.render.LegacyTemplateRender", List.of(
                "cn/eova/common/base/BaseController.java",
                "cn/eova/compat/render/DefaultLegacyRenderFactory.java",
                "cn/eova/web/LegacyWebBootstrap.java"));
        // ★ 实测纠正（两处都是我首版声明的错）：
        //   ① 键写成 `cn/eova/...`（斜杠路径）⇒ 内部简单名取不到 ⇒ 恒为"无调用方"（假绿）。
        //      教训：**声明的键必须与扫描算法取简单名的方式一致**（点号 FQCN）。
        //   ② 该类在生产代码里**只被 Javadoc 提及**（`LegacyEngine` 的注释说"新栈由它承担"，
        //      但新栈实际是 `LegacyTemplateRender` 直接用 `Engine`；全仓无生产调用方、无反射装配，
        //      只有它自己的 `EnjoyTemplateRenderServiceTest` 在用）⇒ 声明为**空**（= 断言"无生产调用方"）。
        DECLARED_CALLERS.put("cn.eova.compat.template.EnjoyTemplateRenderService", List.of());
        DECLARED_CALLERS.put("cn.eova.compat.jfinal.config.LegacyEngine", List.of(
                "cn/eova/compat/jfinal/config/LegacyJFinalBoot.java",
                "cn/eova/compat/jfinal/config/LegacyJFinalConfig.java",
                "cn/eova/config/EovaConfig.java",
                "cn/eova/web/LegacyWebBootstrap.java"));
        DECLARED_CALLERS.put("cn.eova.ext.jfinal.EovaRenderSourceFactory", List.of(
                "cn/eova/config/EovaConfig.java"));
        DECLARED_CALLERS.put("cn.eova.web.LegacyViewSourceFactory", List.of(
                "cn/eova/web/LegacyWebBootstrap.java"));
        // ---- 启动期注册：被接线，但活的模板使用者已随 U1 退役 ----
        DECLARED_CALLERS.put("cn.eova.ext.jfinal.directive.JsonDirective", List.of(
                "cn/eova/config/EovaConfig.java"));
        // ---- 死链：这些类彼此调用，但**入口无调用方** ----
        DECLARED_CALLERS.put("cn.eova.common.render.RenderUtil", List.of(
                "cn/eova/common/render/Html2DocRender.java",
                "cn/eova/common/render/Html2PdfRender.java",
                "cn/eova/common/render/Html2XlsRender.java",
                "cn/eova/common/render/OfficeRender.java",
                "cn/eova/common/render/ResourceRender.java"));
    }

    /** **入口类**：无调用方即"整条链不可达"（死面） */
    private static final List<String> DEAD_CHAIN_ENTRIES = List.of(
            "cn/eova/common/render/Html2DocRender",
            "cn/eova/common/render/Html2PdfRender",
            "cn/eova/common/render/Html2XlsRender",
            "cn/eova/common/render/OfficeRender",
            "cn/eova/common/render/ResourceRender");

    private static Path moduleDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        assertTrue(Files.isDirectory(dir.resolve("src/main/java")),
                "★ fail-closed：找不到模块源码根（user.dir=" + dir + "）");
        return dir;
    }

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

    /** 该类在生产代码里的**代码引用**（排除注释行与类自身文件） */
    private static List<String> callersOf(String fqcn) throws IOException {
        String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        String self = fqcn.replace('.', '/') + ".java";
        Pattern ref = Pattern.compile("\\b" + Pattern.quote(simple) + "\\b");
        List<String> out = new ArrayList<>();
        for (Path root : sourceRoots()) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path f : walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                    String rel = root.relativize(f).toString();
                    if (rel.equals(self)) {
                        continue;
                    }
                    boolean hit = false;
                    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        String s = line.strip();
                        if (s.startsWith("*") || s.startsWith("//") || s.startsWith("/*")) {
                            continue;   // ★ 注释里的提及不算调用方（本轮踩过的坑）
                        }
                        // ★ 变异 M6 逼出来的收紧：**import 行也不算"调用方"** ——
                        //   否则"删掉接线只留 import"会被误判成"仍在使用"（判据看不见接线被拆）。
                        if (s.startsWith("import ")) {
                            continue;
                        }
                        Matcher m = ref.matcher(line);
                        if (m.find()) {
                            hit = true;
                            break;
                        }
                    }
                    if (hit) {
                        out.add(rel);
                    }
                }
            }
        }
        return new TreeSet<>(out).stream().collect(Collectors.toList());
    }

    @Test
    @DisplayName("★ T04-9：渲染腿调用面**双向冻结**（活面被拆、死面被接线都必须红）")
    void renderSurfaceIsFrozenBothWays() throws IOException {
        List<String> mismatch = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : DECLARED_CALLERS.entrySet()) {
            List<String> actual = callersOf(e.getKey());
            if (!e.getValue().equals(actual)) {
                mismatch.add(e.getKey() + "\n      声明=" + e.getValue() + "\n      实际=" + actual);
            }
        }
        assertTrue(mismatch.isEmpty(),
                "★ 渲染腿调用面与声明不一致（退役影响面变了，必须显式改表）：\n    "
                        + String.join("\n    ", mismatch));
        assertEquals(7, DECLARED_CALLERS.size(), "声明条数（活面 5 + 注册 1 + 死链入口 1）");
    }

    /** 渲染腿的**接线点**：路径 → （文件, 必须出现的代码片段） */
    private static final Map<String, String[]> WIRING = new java.util.LinkedHashMap<>();

    static {
        WIRING.put("me.setSourceFactory(new EovaRenderSourceFactory())", new String[]{
                "cn/eova/config/EovaConfig.java"});
        WIRING.put("engine.setSourceFactory(new LegacyViewSourceFactory(viewRoot))", new String[]{
                "cn/eova/web/LegacyWebBootstrap.java"});
        WIRING.put("me.addDirective(\"json\", JsonDirective.class)", new String[]{
                "cn/eova/config/EovaConfig.java"});
    }

    @Test
    @DisplayName("★ T04-12：渲染腿的**接线点**必须逐条在场（拆掉接线 ⇒ 立即红）")
    void renderWiringPointsArePresent() throws IOException {
        // ★ 这条是变异 M6 逼出来的：只统计"调用方文件清单"时，**删掉接线但留着 import**
        //   会让清单不变 ⇒ 判据看不见"接线被拆"。故对每个接线点直接断言源码片段在场。
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String[]> e : WIRING.entrySet()) {
            for (String rel : e.getValue()) {
                Path f = sourceRoots().stream().map(r -> r.resolve(rel)).filter(Files::isRegularFile)
                        .findFirst().orElse(null);
                if (f == null) {
                    missing.add(rel + "（文件不存在）");
                } else if (!Files.readString(f, StandardCharsets.UTF_8).contains(e.getKey())) {
                    missing.add(rel + " 缺少接线：" + e.getKey());
                }
            }
        }
        assertTrue(missing.isEmpty(), "★ 渲染腿接线点缺失（渲染链被拆）：" + missing);
        assertEquals(3, WIRING.size(), "接线点清单条数（源工厂 ×2 + json 指令注册）");
    }

    @Test
    @DisplayName("★ T04-10：死链入口**确实无调用方**（= 这 6 个渲染器在新栈不可达，可随依赖一起删）")
    void deadChainHasNoCallers() throws IOException {
        List<String> halfDead = new ArrayList<>();
        for (String fqcn : DEAD_CHAIN_ENTRIES) {
            List<String> callers = callersOf(fqcn);
            if (!callers.isEmpty()) {
                // `RenderUtil` 在链内互相调用是允许的：只有"链外调用方"才算接上线
                List<String> outside = callers.stream()
                        .filter(c -> !c.startsWith("cn/eova/common/render/"))
                        .collect(Collectors.toList());
                if (!outside.isEmpty()) {
                    halfDead.add(fqcn + " 被链外调用：" + outside);
                }
            }
        }
        assertTrue(halfDead.isEmpty(),
                "★ 死链被接上线了（那它就不再是'可随依赖一起删'的候选，必须重判）：" + halfDead);
        // 反空断言：这 5 个类必须真的存在于源码里（防"类被删了、判据却因为找不到而通过"）
        List<String> existing = new ArrayList<>();
        for (Path root : sourceRoots()) {
            for (String fqcn : DEAD_CHAIN_ENTRIES) {
                Path f = root.resolve(fqcn.replace('.', '/') + ".java");
                if (Files.isRegularFile(f)) {
                    existing.add(fqcn);
                }
            }
        }
        assertEquals(DEAD_CHAIN_ENTRIES.size(), existing.size(),
                "★ 死链类应全部存在（若已删除，请把它们从 DEAD_CHAIN_ENTRIES 移除并记录退役进度）");
    }

    @Test
    @DisplayName("★ T04-11：`#json` 指令的唯一模板使用者仍是**已退役**的页面（登记：可随依赖一起删）")
    void jsonDirectiveHasNoLiveTemplateUser() throws IOException {
        // 取证：`#json` 在 legacy 根模板里只出现在 `eova/_view/meta/reorder/app.html`，
        // 而 `/meta/reorder` 已在 U1 退役为 SPA 壳（`SPA_OWNED_PATHS`）⇒ 该模板不再被渲染。
        Path legacy = moduleDir().resolve("../../../../front/remis-eova-ui/src/legacy").normalize();
        assertTrue(Files.isDirectory(legacy), "★ fail-closed：legacy 视图根不存在 " + legacy);
        List<String> users = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(legacy)) {
            for (Path f : walk.filter(p -> p.toString().endsWith(".html")).collect(Collectors.toList())) {
                if (Files.readString(f, StandardCharsets.UTF_8).contains("#json")) {
                    users.add(legacy.relativize(f).toString());
                }
            }
        }
        assertEquals(List.of("eova/_view/meta/reorder/app.html"), users,
                "★ `#json` 的模板使用者清单变了 ⇒ 必须重判 `JsonDirective` 是活面还是可删（期望：只剩那个已退役页）");
    }
}
