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
 * <p><b>r310 终局（引擎按口径授权摘除）</b>：</p>
 * <ul>
 *   <li><b>渲染链仍在、但不再接 enjoy</b>：`LegacyTemplateRender`（接缝）·
 *       `LegacyPageRenderer`（自研极简渲染器）· `LegacyWebBootstrap`（装配）·
 *       `BaseController`/`DefaultLegacyRenderFactory`（调用方）；</li>
 *   <li><b>已删除</b>：`EnjoyTemplateRenderService`/`TemplateRenderService`（r309）·
 *       `JsonDirective`/`RenderUtil` 死链（r309）· `EovaRenderSourceFactory`/`LegacyViewSourceFactory`
 *       （r310：源工厂只服务引擎）· `LegacyRowFieldGetter`（r310：它挂的是 enjoy 的字段读取器链）；</li>
 *   <li><b>本判据现在的两件事</b>：① 渲染链的**调用面**继续双向冻结（活面被拆 ⇒ 红）；
 *       ② 引擎的**接线点与装配 API 必须不存在**（`me.setSourceFactory(…)`、`engine.setSourceFactory(…)`、
 *       `LegacyTemplateRender#init(Engine)`、`Engine.create(`）—— 有人把引擎接回来 ⇒ 立刻红。</li>
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
        // ---- 活面：渲染链路上真正被接线的 5 类（实测调用方，逐条冻结）----
        DECLARED_CALLERS.put("cn.eova.compat.render.LegacyTemplateRender", List.of(
                "cn/eova/common/base/BaseController.java",
                "cn/eova/compat/render/DefaultLegacyRenderFactory.java",
                "cn/eova/web/LegacyWebBootstrap.java"));
        DECLARED_CALLERS.put("cn.eova.compat.jfinal.config.LegacyEngine", List.of(
                "cn/eova/compat/jfinal/config/LegacyJFinalBoot.java",
                "cn/eova/compat/jfinal/config/LegacyJFinalConfig.java",
                "cn/eova/config/EovaConfig.java",
                "cn/eova/web/LegacyWebBootstrap.java"));
        DECLARED_CALLERS.put("cn.eova.web.LegacyWebBootstrap", List.of());
        DECLARED_CALLERS.put("cn.eova.config.PageConst", List.of(
                "cn/eova/meta/api/TableController.java",
                "cn/eova/meta/api/WidgetController.java",
                "cn/eova/widget/WidgetCtrl.java",
                "cn/eova/widget/WidgetManager.java",
                "cn/eova/widget/grid/GridController.java"));
    }

    /**
     * **r309 按授权删除的死面**（3 处死面 + 死链 5 类）：这些 {@code .java} 必须**不存在**。
     *
     * <p>保留这份清单的意义：删掉的东西**不得复活**（有人 {@code git checkout} 回来，
     * 或新写一个同名类重新接上 enjoy）—— 那是必须显式改表的重判点。</p>
     */
    private static final List<String> DELETED_DEAD_FACES = List.of(
            // ★ 必须是**点号 FQCN**：写成斜杠路径会让下面的引用检查（按简单名匹配）空转 ⇒ 假绿。
            "cn.eova.compat.template.EnjoyTemplateRenderService",
            "cn.eova.compat.template.TemplateRenderService",
            "cn.eova.ext.jfinal.directive.JsonDirective",
            "cn.eova.common.render.RenderUtil",
            "cn.eova.common.render.Html2DocRender",
            "cn.eova.common.render.Html2PdfRender",
            "cn.eova.common.render.Html2XlsRender",
            "cn.eova.common.render.OfficeRender",
            "cn.eova.common.render.ResourceRender",
            // ---- r310：引擎兜底与接线（按口径授权摘除）----
            "cn.eova.ext.jfinal.EovaRenderSourceFactory",
            "cn.eova.web.LegacyViewSourceFactory",
            "cn.eova.compat.template.LegacyRowFieldGetter");

    /** 反空断言用的**活面**代表类：必须存在（证明"找不到文件"不是因为源码根解析错了） */
    private static final String LIVE_CONTROL_CLASS = "cn.eova.compat.render.LegacyTemplateRender";

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
        assertEquals(4, DECLARED_CALLERS.size(),
                "声明条数（渲染链活面 3：LegacyTemplateRender / LegacyEngine / LegacyWebBootstrap"
                        + " + 配置面 1：PageConst —— r310 摘除引擎后由 7 降到 4）");
    }

    /** 渲染腿的**接线点**：路径 → （文件, 必须出现的代码片段） */
    private static final Map<String, String[]> WIRING = new java.util.LinkedHashMap<>();

    static {
        // ★ r310：原来的两条接线点（`me.setSourceFactory(new EovaRenderSourceFactory())`、
        //   `engine.setSourceFactory(new LegacyViewSourceFactory(viewRoot))`）已随引擎摘除 ⇒ 现在要断言
        //   **它们不存在**（见下测试）。本表保留为空（结构不变，便于将来再有接线时直接加回来）。
    }

    @Test
    @DisplayName("★ T04-12：引擎接线点**必须不存在**（有人把 enjoy 接回来 ⇒ 立即红）")
    void engineWiringPointsAreGone() throws IOException {
        // ★ 语义翻转（r310）：原来的这条判据断言"接线点在场"（那时引擎是活的兜底）；
        //   引擎按口径授权摘除之后，同一件事实变成**反向**的：任何"把引擎接回来"的痕迹都必须红。
        //   这一条同时也接过变异 M6 的职责（"拆掉接线但留着 import ⇒ 清单不变"看不见）。
        List<String> resurrected = new ArrayList<>();
        // 片段 → 它不得出现的理由
        java.util.Map<String, String> forbidden = new java.util.LinkedHashMap<>();
        forbidden.put("setSourceFactory(new EovaRenderSourceFactory()", "EovaConfig 的引擎源工厂接线");
        forbidden.put("setSourceFactory(new LegacyViewSourceFactory(", "LegacyWebBootstrap 的引擎源工厂接线");
        forbidden.put("Engine.create(", "自建 enjoy 引擎");
        forbidden.put("LegacyTemplateRender.init(engine)", "把引擎注入渲染接缝");
        for (Map.Entry<String, String> e : forbidden.entrySet()) {
            for (Path root : sourceRoots()) {
                try (Stream<Path> walk = Files.walk(root)) {
                    for (Path f : walk.filter(x -> x.toString().endsWith(".java")).collect(Collectors.toList())) {
                        String rel = root.relativize(f).toString();
                        for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                            String t = line.strip();
                            if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*") || t.startsWith("import ")) {
                                continue;   // 注释里的提及不算接线（本轮踩过的坑）
                            }
                            if (line.contains(e.getKey())) {
                                resurrected.add(rel + " 里出现了「" + e.getKey() + "」（= " + e.getValue() + "）");
                            }
                        }
                    }
                }
            }
        }
        assertTrue(resurrected.isEmpty(),
                "★ 引擎被接回来了（r310 已按口径授权摘除）：" + resurrected);
        assertEquals(0, WIRING.size(), "接线点清单必须为空（引擎已摘除）");
    }

    @Test
    @DisplayName("★ T04-10：r309 已删除的死面**不得复活**（文件不存在 + 无残留引用）")
    void deletedDeadFacesAreGone() throws IOException {
        // 反空断言：活面代表类必须存在 —— 否则"找不到文件"可能只是源码根解析错了（假绿）
        boolean controlExists = sourceRoots().stream()
                .anyMatch(r -> Files.isRegularFile(r.resolve(LIVE_CONTROL_CLASS.replace('.', '/') + ".java")));
        assertTrue(controlExists, "★ fail-closed：活面代表类不存在 " + LIVE_CONTROL_CLASS + " ⇒ 源码根解析有问题");

        List<String> resurrected = new ArrayList<>();
        for (String fqcn : DELETED_DEAD_FACES) {
            // 反空断言：条目必须是点号 FQCN（含包名）—— 曾因写成斜杠路径而让"引用清零"检查空转
            assertTrue(fqcn.contains(".") && !fqcn.contains("/"),
                    "★ 删除清单条目必须是点号 FQCN：" + fqcn);
            String rel = fqcn.replace('.', '/') + ".java";
            for (Path root : sourceRoots()) {
                if (Files.isRegularFile(root.resolve(rel))) {
                    resurrected.add(rel);
                }
            }
            // 代码引用也必须清零（删除只删文件、留下引用会立刻编译失败；这里防"引用被挪到别处"）
            List<String> callers = callersOf(fqcn);
            if (!callers.isEmpty()) {
                resurrected.add(fqcn + " 仍被引用：" + callers);
            }
        }
        assertTrue(resurrected.isEmpty(),
                "★ 已删除的死面又出现了（必须重判它是不是活面）：" + resurrected);
        assertEquals(12, DELETED_DEAD_FACES.size(), "删除清单条数（r309 死面 3 + 死链 5 + 接口 1；r310 引擎侧 3）");
    }

    @Test
    @DisplayName("★ T04-11：`#json` 的模板使用者仍**冻结**在已退役页，且 `JsonDirective` 已删除")
    void jsonDirectiveIsGone() throws IOException {
        // 口径依据（删除前取证）：`#json` 在 legacy 根模板里只出现在 `eova/_view/meta/reorder/app.html`，
        // 而 `/meta/reorder` 已在 U1 退役为 SPA 壳 ⇒ 该指令不再被任何活模板使用 ⇒ 授权删除。
        // 这一半仍然冻结：**模板侧的 `#json` 使用者清单不得变化**（变了说明有活页开始用 `#json`，
        // 那就必须重做一个渲染器侧的 `#json` 支持，而不是继续删）。
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
                "★ `#json` 的模板使用者清单变了 ⇒ 必须重判：新渲染器是否需要支持 `#json`");

        // 另一半：指令类与启动期注册都必须消失
        assertTrue(DELETED_DEAD_FACES.contains("cn.eova.ext.jfinal.directive.JsonDirective"),
                "★ `JsonDirective` 应在 r309 删除清单里");
        Path config = sourceRoots().stream().map(r -> r.resolve("cn/eova/config/EovaConfig.java"))
                .filter(Files::isRegularFile).findFirst().orElseThrow();
        String src = Files.readString(config, StandardCharsets.UTF_8);
        assertTrue(!src.contains("addDirective"), "★ `EovaConfig` 里不得再有 `addDirective` 注册（json 指令已删）");
        assertTrue(!src.contains("JsonDirective"), "★ `EovaConfig` 里不得再提到 `JsonDirective`");
    }
}
