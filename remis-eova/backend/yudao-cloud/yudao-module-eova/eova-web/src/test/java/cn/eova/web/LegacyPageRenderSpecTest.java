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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
 * **T04 第二段判据：页面渲染腿（RENDER）的「模板指令规格」冻结**。
 *
 * <p><b>为什么要它</b>：把 `com.jfinal:enjoy` 彻底退役，卡在"还有页面要渲染"。
 * 但**到底需要模板语言的哪些能力**不能靠感觉 —— 必须从**仍在渲染的活页**的模板文件里统计出来
 * （与 EXPR 腿用真库语料统计同构）。规格一旦冻结：① 它是自研渲染器的最小实现范围；
 * ② 也是决策依据（若构件很少 ⇒ 可自研替代；若很多 ⇒ 老实登记阻塞）。</p>
 *
 * <p><b>r308 第 7 轮实测（本判据就是它的钉子）</b>：仍由后端渲染的活页只有两个 ——
 * <ul>
 *   <li>{@code /main}（EovaUI 主题页）：模板{@code 一条指令都没有} ⇒ **纯静态**，
 *       理论上可完全不经模板引擎（直出文件字节）；</li>
 *   <li>{@code /excel/imports/<objectCode>}（Excel 导入页）：其 {@code include} 闭包（4 个文件）
 *       总共只用到 <b>6 类构件</b>：{@code #(expr)} · {@code #if(cond)} · {@code #else} · {@code #end} ·
 *       {@code #include("path")} · {@code #render(expr)}；表达式里只调用 <b>2 个共享方法</b>
 *       （{@code conf('…')}、{@code getUIConf()}）。</li>
 * </ul>
 * ⇒ 结论：**自研渲染器是可行且范围极小**（比"重写模板引擎"的直觉小得多）。
 *
 * <p><b>fail-closed</b>：legacy 视图根不存在 ⇒ 红。</p>
 */
class LegacyPageRenderSpecTest {

    /** 仍由后端渲染的活页模板（相对 legacy 视图根） */
    private static final List<String> LIVE_PAGES = List.of(
            "_view/theme/index.html",
            "excel/import/app.html");

    /** 声明的构件集合（r308 第 7 轮实测） */
    private static final Set<String> DECLARED_DIRECTIVES = new TreeSet<>(List.of(
            "#(", "#if(", "#else", "#end", "#include(", "#render("));

    /** 声明的共享方法（表达式里被调用的） */
    private static final Set<String> DECLARED_SHARED_METHODS = new TreeSet<>(List.of("conf", "getUIConf"));

    private static Path legacyRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path root = dir.resolve("../../../../front/remis-eova-ui/src/legacy").normalize();
        assertTrue(Files.isDirectory(root), "★ fail-closed：legacy 视图根不存在 " + root);
        return root;
    }

    /** 统计一份模板里的构件 token */
    private static void collect(String text, Set<String> out) {
        for (String t : new String[]{"#(", "#if(", "#else", "#end", "#include(", "#render("}) {
            if (text.contains(t)) {
                out.add(t);
            }
        }
        // 其它 `#name(` 形态（未声明指令）——单独记出来，便于报错时说明
        Matcher m = Pattern.compile("#([A-Za-z_][A-Za-z0-9_]*)\\s*\\(").matcher(text);
        while (m.find()) {
            String tok = "#" + m.group(1) + "(";
            if (!DECLARED_DIRECTIVES.contains(tok)) {
                out.add(tok);
            }
        }
    }

    /**
     * 表达式里调用的方法名（`name(` 形态）。
     *
     * <p>★ 必须同时扫 **`#(...)` 与 `#if(...)`**：首版只扫前者 ⇒ `#if(conf('ui.include'))` 里的
     * `conf` 没被统计到，判据当场报"共享方法集合变了"（判据抓的是我自己的疏漏）。</p>
     */
    private static void collectMethods(String text, Set<String> out) {
        for (Matcher m = Pattern.compile("#(?:\\(|if\\()([^)]*)\\)").matcher(text); m.find(); ) {
            for (Matcher f = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(").matcher(m.group(1)); f.find(); ) {
                out.add(f.group(1));
            }
        }
    }

    /** include 闭包（相对 legacy 根的路径集合） */
    private static Set<String> closure(Path root, String start) throws IOException {
        Set<String> seen = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>(List.of(start));
        while (!queue.isEmpty()) {
            String rel = queue.remove(0);
            if (!seen.add(rel)) {
                continue;
            }
            Path f = root.resolve(rel);
            if (!Files.isRegularFile(f)) {
                continue;
            }
            String text = Files.readString(f, StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("#(?:include|render)\\(\\s*\"([^\"]+)\"").matcher(text);
            while (m.find()) {
                String inc = m.group(1).replaceFirst("^/", "");
                for (String cand : new String[]{inc, inc.replaceFirst("^eova/", ""), "eova/" + inc}) {
                    if (Files.isRegularFile(root.resolve(cand))) {
                        queue.add(cand);
                        break;
                    }
                }
            }
        }
        return seen;
    }

    @Test
    @DisplayName("★ T04-22：`/main` 主题页模板**一条指令都没有** ⇒ 纯静态（可完全不经模板引擎）")
    void mainThemePageIsStatic() throws IOException {
        Path f = legacyRoot().resolve("_view/theme/index.html");
        assertTrue(Files.isRegularFile(f), "★ fail-closed：主题页模板不存在 " + f);
        String text = Files.readString(f, StandardCharsets.UTF_8);
        Set<String> found = new TreeSet<>();
        collect(text, found);
        // `#` 在 HTML/CSS/JS 里到处都是（`#app`、`#fff`）⇒ 只认"指令 token"，不认裸 `#`
        assertEquals(new TreeSet<String>(), found,
                "★ `/main` 主题页模板本应零指令（纯静态）；出现指令意味着「可直出」的前提被破坏");
        assertTrue(text.length() > 10000, "主题页模板应是有内容的整页（实测约 19KB）");
    }

    @Test
    @DisplayName("★ T04-23：仍渲染的活页（含 include 闭包）只用**声明的 6 类构件 + 2 个共享方法**")
    void livePageDirectiveSpecIsFrozen() throws IOException {
        Path root = legacyRoot();
        Set<String> allFiles = new TreeSet<>();
        Set<String> directives = new TreeSet<>();
        Set<String> methods = new TreeSet<>();
        for (String page : LIVE_PAGES) {
            Set<String> files = closure(root, page);
            allFiles.addAll(files);
            for (String rel : files) {
                String text = Files.readString(root.resolve(rel), StandardCharsets.UTF_8);
                collect(text, directives);
                collectMethods(text, methods);
            }
        }
        // 反空断言：闭包必须真的扫到多个文件（否则"构件集合相等"是空话）
        assertTrue(allFiles.size() >= 5, "★ include 闭包文件数异常少（" + allFiles.size() + "）⇒ 闭包解析失效");

        assertEquals(DECLARED_DIRECTIVES, directives,
                "★ 活页模板用到的指令集合变了 ⇒ 自研渲染器的规格必须同步（这是「退役 enjoy」的最小实现范围）");
        assertEquals(DECLARED_SHARED_METHODS, methods,
                "★ 表达式里调用的共享方法集合变了 ⇒ 自研渲染器必须提供同名共享方法");
        assertEquals(2, LIVE_PAGES.size(), "仍由后端渲染的活页数（实测：/main 与 Excel 导入页）");
    }

    @Test
    @DisplayName("★ T04-24：闭包必须**只有**这 4 个文件（新的 include 目标 ⇒ 规格面变化，必须显式改表）")
    void includeClosureIsFrozen() throws IOException {
        Path root = legacyRoot();
        Set<String> all = new TreeSet<>();
        for (String page : LIVE_PAGES) {
            all.addAll(closure(root, page));
        }
        // 实测闭包（含两个活页自身）：`/main` → `_view/theme/index.html`（无 include）；
        //   Excel 导入页 → `eova/_view/_page/form.html` → `eova/_view/_block/{meta,base}.html`。
        //   ⚠️ 首版把起始页漏在期望之外 ⇒ 判据报错（声明必须与"闭包含起点"的口径一致）。
        assertEquals(new TreeSet<>(List.of(
                        "_view/theme/index.html",
                        "excel/import/app.html",
                        "eova/_view/_page/form.html",
                        "eova/_view/_block/meta.html",
                        "eova/_view/_block/base.html")),
                all, "★ 活页模板闭包变了 ⇒ 渲染规格面变化，必须显式更新本判据");
    }
}
