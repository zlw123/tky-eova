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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * **U2 判据：旧模板渲染点清单（防漂移）+ 每个渲染点的归属证据**。
 *
 * <p><b>为什么需要"清单钉"</b>：{@link LegacyTemplateFaceHttpTest} 钉的是**行为**（哪些 URL 现在返回
 * 旧模板页 / 壳 / 500 / 404）。但行为钉不住"**源码里又多了一个 render 点**"这件事 ——
 * 新加一处 {@code render("…html")} 只要没被走到，HTTP 层永远看不见。退役 {@code com.jfinal:enjoy} 时
 * 这种"看不见的渲染点"就是漏网之鱼。故本判据在**源码层**把清单钉死：新增/删除渲染点都必须**显式改表**。</p>
 *
 * <p><b>分类（每条都有实测/源码证据，不是推断）</b>：</p>
 * <ul>
 *   <li>{@code LEGACY_LIVE} —— 可达且模板存在（旧栈实测 200）：只有 {@code AuthController#index} 与
 *       {@code ExcelController#imports}；</li>
 *   <li>{@code BROKEN_TEMPLATE} —— 控制器已注册、URL 可达，但**模板文件全仓不存在** ⇒ 两侧 500（死入口）；</li>
 *   <li>{@code DEAD_CONTROLLER} —— 所在控制器**两侧都没注册路由** ⇒ 动作不可达（死代码）。</li>
 * </ul>
 *
 * <p><b>fail-closed</b>：源码根或 legacy 视图根解析不到时**直接失败**（不是跳过）—— 否则一次路径变动
 * 就能把本判据静默变成恒真。</p>
 */
class LegacyTemplateFaceInventoryTest {

    /** 生产代码里的渲染点：沿用与 `docs/.local/spikes` 探针一致的识别口径 */
    private static final Pattern CLASS_DECL = Pattern.compile("^public\\s+(?:abstract\\s+)?class\\s+(\\w+)");
    private static final Pattern METHOD_DECL = Pattern.compile(
            "^ {4}(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:public|protected|private)\\s+(?:static\\s+)?(?:final\\s+)?"
                    + "[\\w<>\\[\\],.\\s?]+\\s+(\\w+)\\s*\\([^;]*\\)\\s*(?:throws [\\w.,\\s]+)?\\{");
    private static final Pattern RENDER_CALL = Pattern.compile(
            "\\brenderEnjoy\\s*\\(\\s*\"([^\"]+)\"|(?<![\\w.])render\\s*\\(\\s*\"([^\"]+)\"");

    /** 渲染点：`类#方法 -> 模板` 与归属分类 */
    private record Site(String key, String view, Kind kind) {
    }

    private enum Kind {
        /** 可达 + 模板存在（旧栈实测 200） */
        LEGACY_LIVE,
        /** 可达 + 模板缺失（两侧 500），且**无副作用** */
        BROKEN_TEMPLATE,
        /** 控制器两侧都未注册路由（动作不可达） */
        DEAD_CONTROLLER,
        /**
         * ★ 可达 + 模板缺失（两侧 500），但**有写副作用** ⇒ **不得用 HTTP 判据去调**。
         *
         * <p>本轮真实事故：`MetaController#diy()` 先 `save("eova_diy", r)` 补写缺失字段、**再**渲染
         * 缺失的模板 ⇒ 调一次写 14 行，`check-baseline-integrity.py` 第 7 步因此变红
         * （`eova_diy 实际=14 期望=0`）。故这类渲染点只做**静态登记**，不进行为调用。</p>
         */
        SIDE_EFFECT_NO_CALL
    }

    /**
     * ★ 冻结清单（19 条，2026-09-13 r306 实跑/源码双向核对）。
     * 旧栈真值：带会话直连 9090 逐 URL 实测；模板存在性：全仓 `find` 核对。
     */
    private static final List<Site> DECLARED = List.of(
            // ---- 仍活着的旧模板页（退役前置清单的唯一存活项）----
            // ★ r306（U2 取证后迁移）：`AuthController#index` 曾在这里（`/eova/role/auth/app.html`，
            //   旧栈实测 200「功能权限分配」）。取证结论：SPA 侧该页早已迁移（`views/role/RoleAuth.vue`，
            //   契约逐条对齐 `AuthController#index/data/doAuth`），只是 SPA 路由被写成 `/eova/role/auth/:rid`
            //   （把**模板路径**当成 URL）而一直不可达。
            //   处置：SPA 路由改回旧原路径 `/auth/:rid` + 页面入口退役为壳 ⇒ 该渲染点**消失**，
            //   冻结清单由 19 条降为 18 条。（模板 `eova/_view/role/auth/app.html` 随之成为**孤儿模板**：
            //   已无任何渲染点引用它，退役 enjoy 时可删 —— 登记在台账。）
            new Site("ExcelController#imports", "/excel/import/app.html", Kind.LEGACY_LIVE),
            // ---- 模板全仓不存在 ⇒ 两侧 500 的死入口 ----
            new Site("IndexController#code", "/eova/code.html", Kind.BROKEN_TEMPLATE),
            new Site("AdminController#upgrade", "/eova/admin/upgrade.html", Kind.BROKEN_TEMPLATE),
            new Site("MenuController#icon", "/eova/icon.html", Kind.BROKEN_TEMPLATE),
            new Site("MenuController#flow", "/eova/menu/flow.html", Kind.BROKEN_TEMPLATE),
            new Site("MenuController#toMenuFun", "/eova/menu/menuFun.html", Kind.BROKEN_TEMPLATE),
            new Site("MenuController#toUpdate", "/eova/menu/add.html", Kind.BROKEN_TEMPLATE),
            new Site("ButtonController#quick", "/eova/button/quick.html", Kind.BROKEN_TEMPLATE),
            // ★ 有**写副作用**：`diy()` 先补写 `eova_diy` 再渲染（见 Kind.SIDE_EFFECT_NO_CALL）
            new Site("MetaController#diy", "/eova/meta/diy.html", Kind.SIDE_EFFECT_NO_CALL),
            new Site("MetaController#find", "/eova/widget/find/find.html", Kind.BROKEN_TEMPLATE),
            // ---- 控制器两侧都没注册路由 ⇒ 死代码 ----
            new Site("SingleController#importXls", "/eova/template/common/import.html", Kind.DEAD_CONTROLLER),
            new Site("WidgetCtrl#find", "/eova/widget/find/select.html", Kind.DEAD_CONTROLLER),
            new Site("WidgetCtrl#find", "/eova/widget/find/select_callbak.html", Kind.DEAD_CONTROLLER),
            new Site("WidgetCtrl#find", "/eova/widget/find/find.html", Kind.DEAD_CONTROLLER),
            new Site("FormController#add", "/eova/widget/form/add.html", Kind.DEAD_CONTROLLER),
            new Site("FormController#update", "/eova/widget/form/update.html", Kind.DEAD_CONTROLLER),
            new Site("FormController#detail", "/eova/widget/form/detail.html", Kind.DEAD_CONTROLLER),
            new Site("FormController#diy", "/eova/widget/form/diy.html", Kind.DEAD_CONTROLLER)
    );

    /** 解析模块根（surefire 的工作目录就是模块目录）；解析不到 ⇒ fail-closed */
    private static Path moduleDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        assertTrue(Files.isDirectory(dir.resolve("src/main/java")),
                "★ fail-closed：找不到模块源码根（user.dir=" + dir + "）—— 本判据不得因路径变化而静默通过");
        return dir;
    }

    /** 生产源码根（三个模块） */
    private static List<Path> sourceRoots() {
        Path module = moduleDir();
        List<Path> roots = List.of(
                module.resolve("src/main/java"),
                module.getParent().resolve("eova-core/src/main/java"),
                module.getParent().resolve("eova-compat/src/main/java"));
        for (Path r : roots) {
            assertTrue(Files.isDirectory(r), "★ fail-closed：源码根不存在 " + r + " —— 本判据不得静默通过");
        }
        return roots;
    }

    /** legacy 视图根（前端 legacy 目录，后端渲染旧模板时以此为根） */
    private static Path legacyViewRoot() {
        Path root = moduleDir().resolve("../../../../front/remis-eova-ui/src/legacy").normalize();
        assertTrue(Files.isDirectory(root),
                "★ fail-closed：legacy 视图根不存在 " + root + " —— 本判据不得静默通过");
        return root;
    }

    /**
     * 模板落点：`/eova/x` 经 `BaseController.render` 的 `_view` 重写 ⇒ `<根>/eova/_view/x`；
     * 其它路径（如 `/excel/import/app.html`）直接用原路径。
     */
    private static Path resolveTemplate(Path root, String view) {
        String rel = view.startsWith("/eova/")
                ? "/eova/_view/" + view.substring("/eova/".length())
                : view;
        return root.resolve(rel.substring(1));
    }

    /** 扫描源码里的全部渲染点（跳过注释行；`renderSpaShell()`/`renderJson(` 等自然不匹配） */
    private static Set<String> scanSites() throws IOException {
        Set<String> found = new LinkedHashSet<>();
        for (Path root : sourceRoots()) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path f : walk.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList())) {
                    String cls = "";
                    String method = "?";
                    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        String s = line.strip();
                        if (s.startsWith("//") || s.startsWith("*") || s.startsWith("/*")) {
                            continue;
                        }
                        Matcher mc = CLASS_DECL.matcher(line);
                        if (mc.find()) {
                            cls = mc.group(1);
                        }
                        Matcher mm = METHOD_DECL.matcher(line);
                        if (mm.find()) {
                            method = mm.group(1);
                        }
                        Matcher cr = RENDER_CALL.matcher(line);
                        while (cr.find()) {
                            String view = cr.group(1) != null ? cr.group(1) : cr.group(2);
                            found.add(cls + "#" + method + " -> " + view);
                        }
                    }
                }
            }
        }
        return found;
    }

    private static Set<String> declaredKeys() {
        return DECLARED.stream().map(s -> s.key() + " -> " + s.view()).collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    @DisplayName("★ U2-6：渲染点清单**双向**冻结（新增或删除都必须显式改表）")
    void renderSiteInventoryIsFrozenBothWays() throws IOException {
        Set<String> actual = new TreeSet<>(scanSites());
        Set<String> declared = declaredKeys();

        Set<String> undeclared = new TreeSet<>(actual);
        undeclared.removeAll(declared);
        Set<String> missing = new TreeSet<>(declared);
        missing.removeAll(actual);

        assertTrue(undeclared.isEmpty(),
                "★ 出现**未声明**的旧模板渲染点（退役 enjoy 时的漏网之鱼）：" + undeclared
                        + " —— 必须先在 DECLARED 里登记分类与证据");
        assertTrue(missing.isEmpty(),
                "★ 已声明的渲染点消失了：" + missing + " —— 若是有意删除，请同步更新 DECLARED（保留删除理由）");
        assertEquals(18, declared.size(), "冻结清单条数（U2 实跑基线：19 条渲染点 − 1 条已迁移的 /auth）");
    }

    @Test
    @DisplayName("★ U2-7：分类与**文件系统证据**一致（活页面模板必须在、死入口模板必须不在）")
    void kindsMatchTemplateExistence() {
        Path root = legacyViewRoot();
        Map<String, String> checks = new LinkedHashMap<>();
        for (Site s : DECLARED) {
            Path tpl = resolveTemplate(root, s.view());
            boolean exists = Files.isRegularFile(tpl);
            if (s.kind() == Kind.LEGACY_LIVE) {
                assertTrue(exists, "★ 标为 LEGACY_LIVE 的模板必须存在：" + s.view() + " → " + tpl);
                checks.put(s.key() + " -> " + s.view(), "活页面（模板在）");
            } else if (s.kind() == Kind.BROKEN_TEMPLATE || s.kind() == Kind.SIDE_EFFECT_NO_CALL) {
                assertFalse(exists,
                        "★ 标为 BROKEN_TEMPLATE（两侧 500）的模板**不该存在**：" + s.view() + " → " + tpl
                                + " ⇒ 若模板被补上，请把该条改判为 LEGACY_LIVE 并补 HTTP 判据");
                checks.put(s.key() + " -> " + s.view(), "死入口（模板缺）");
            }
        }
        // 非空断言：防止上面两个分支被改成"什么都不查"而恒真
        assertEquals(10, checks.size(), "必须有 10 条被真实核对（1 活 + 9 死）—— /auth 退役后活页面只剩 Excel 导入");
        // ★ 反空断言：有副作用的那类**确实存在**且只有它被排除在 HTTP 调用之外
        //   （否则 SIDE_EFFECT_NO_CALL 可能被悄悄清空 ⇒ 上一条的核对数照样对，但语义已变）
        assertEquals(1, DECLARED.stream().filter(s -> s.kind() == Kind.SIDE_EFFECT_NO_CALL).count(),
                "SIDE_EFFECT_NO_CALL 必须恰好 1 条（MetaController#diy）");
    }

    @Test
    @DisplayName("★ U2-8：DEAD_CONTROLLER 的控制器**确实没注册路由**（结构性证据）")
    void deadControllersHaveNoRoute() throws IOException {
        Path routes = moduleDir().getParent().resolve("eova-core/src/main/java/cn/eova/EovaWebRoutes.java");
        assertTrue(Files.isRegularFile(routes), "★ fail-closed：路由表源码不存在 " + routes);
        String src = Files.readString(routes, StandardCharsets.UTF_8);

        List<String> deadClasses = new ArrayList<>(List.of("WidgetCtrl", "FormController", "SingleController"));
        for (String cls : deadClasses) {
            assertFalse(src.contains(cls),
                    "★ " + cls + " 出现在了路由表里 ⇒ 它不再是「未注册控制器」，"
                            + "其渲染点必须改判并补 HTTP 判据");
        }
        // 反面对照：已注册控制器必须在路由表里（证明上一条不是恒真）
        assertTrue(src.contains("ExcelController") && src.contains("MenuController"),
                "★ 已注册控制器必须出现在路由表（反面对照，防本判据恒真）");
    }
}
