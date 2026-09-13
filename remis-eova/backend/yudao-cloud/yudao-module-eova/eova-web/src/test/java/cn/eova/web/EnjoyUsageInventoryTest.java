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
 * **PHASE-2 / T04 收官判据：生产代码对 {@code com.jfinal:enjoy} 的依赖面 == 0（防回潮）**。
 *
 * <p><b>这张清单的用法在本轮发生了翻转</b>：r308 时它是"还剩哪些生产代码在用 enjoy"的**进度账本**
 * （双向冻结：新增用法、或删了不改表都红）；r310 按口径授权摘除引擎之后，目标变成
 * <b>恒为空集</b> —— 判据改为断言"任何生产源码文件都不得出现 {@code com.jfinal.*}"，
 * 并把此前逐条登记过的退役对象列成**不得复活清单**。</p>
 *
 * <p><b>为什么仍然要扫源码（而不是只看 pom）</b>：pom 里删掉依赖、源码里还留 import，
 * 编译时才会红；而"生产代码偷偷用了 jfinal 的类型"这件事必须在**评审期**就可见。
 * 另外本判据还钉住 pom 的**作用域**：enjoy 只允许以 {@code test} 作用域存在
 * （它现在唯一的用途是 oracle 判据：把本仓 port 与第三方真制品逐方法/逐字节比对）。</p>
 *
 * <p><b>退役账本（r308 → r310，实测）</b>：17 个文件 → UTIL 腿 5 个（r308 第 2 轮）→
 * {@code AuthUri} → 3 处死面 + 死链（r309）→ {@code ExpUtil} → 引擎兜底与 7 处接线（r310）
 * → <b>0 个</b>。</p>
 *
 * <p><b>fail-closed</b>：源码根解析不到直接失败（不得静默通过）。</p>
 */
class EnjoyUsageInventoryTest {

    /**
     * 生产源码里对 enjoy 制品的依赖面：**恒为空集**（r310 收官）。
     *
     * <p>保留这个常量（而不是删掉）是为了让"依赖面"这件事在判据里**仍然有名字**：
     * 扫描结果与它逐条比对，任何一条都红。</p>
     */
    private static final Map<String, List<String>> DECLARED = new TreeMap<>();

    /** r310 之前逐条登记过、现已退役的生产文件：**不得复活**（复活 ⇒ 生产又依赖 enjoy） */
    private static final List<String> RETIRED_FILES = List.of(
            // r308：UTIL 腿（PathKit/StrKit）
            "cn/eova/common/render/ResourceRender.java",
            "cn/eova/config/EovaConst.java",
            "cn/eova/mod/EovaModConst.java",
            "cn/eova/mod/EovaModPackage.java",
            "cn/eova/handler/UrlBanHandler.java",
            // r308 第 6 轮：表达式求值腿
            "cn/eova/auth/AuthUri.java",
            // r309：3 处死面 + 死链（按口径授权删除）
            "cn/eova/engine/ExpUtil.java",
            "cn/eova/compat/template/EnjoyTemplateRenderService.java",
            "cn/eova/compat/template/LegacyRowFieldGetter.java",
            "cn/eova/ext/jfinal/directive/JsonDirective.java",
            "cn/eova/common/render/RenderUtil.java",
            // r310：引擎兜底与接线（按口径授权摘除）
            "cn/eova/ext/jfinal/EovaRenderSourceFactory.java",
            "cn/eova/web/LegacyViewSourceFactory.java",
            "cn/eova/config/PageConst.java",
            "cn/eova/compat/render/LegacyTemplateRender.java",
            "cn/eova/compat/jfinal/config/LegacyEngine.java",
            "cn/eova/web/LegacyWebBootstrap.java");

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
    @DisplayName("★ T04-1：生产源码里 **enjoy 依赖面 == 0**（出现任何一处 com.jfinal.* 都必须红）")
    void enjoyUsageSurfaceIsFrozenBothWays() throws IOException {
        Map<String, List<String>> actual = scan();

        // 反向断言（本轮的主角）：任何生产文件都不得出现 enjoy/jfinal 的类
        assertTrue(actual.isEmpty(),
                "★ 生产源码里出现了 enjoy 依赖（r310 起必须为 0）：" + actual
                        + " —— 若确属必需，请先改回 compile 作用域并显式改本判据（那不是顺手能做的事）");

        // 反空断言：扫描规则必须**真的在扫**（否则"扫不到 ⇒ 空 ⇒ 通过"是假绿）
        //   做法：拿一个必然存在的、且**当前不应含 com.jfinal** 的文件做正对照。
        Path probe = moduleDir().resolve("src/main/java/cn/eova/web/LegacyTemplateRender.java");
        if (!Files.isRegularFile(probe)) {
            probe = moduleDir().resolve("src/main/java/cn/eova/web/LegacyWebBootstrap.java");
        }
        assertTrue(Files.isRegularFile(probe), "★ fail-closed：正对照文件不存在 " + probe);
        assertTrue(Files.readString(probe, StandardCharsets.UTF_8).contains("class "),
                "★ 正对照文件读不出内容 ⇒ 扫描基线可疑");
        assertTrue(!sourceRoots().isEmpty(), "★ fail-closed：源码根解析失败");

        // 已退役清单：这些文件**不得再出现在依赖面上**（复活 ⇒ 生产又依赖 enjoy）
        for (String gone : RETIRED_FILES) {
            assertFalse(actual.containsKey(gone), "已退役的生产文件又依赖 enjoy 了：" + gone);
        }
        assertEquals(0, DECLARED.size(), "依赖面目标：0（r310 收官）");
    }

    @Test
    @DisplayName("★ T04-2：退役进度账本 —— 17 个文件 → 0，且清单不得被悄悄缩短")
    void categoriesAreCounted() {
        // 账本（实测轨迹）：17 →（r308 UTIL 腿 5）→（AuthUri 1）→（r309 死面/死链 4）→（r310 引擎与接线 7）
        assertEquals(17, RETIRED_FILES.size(),
                "退役账本条数（17 = r308 起的全部登记项；少一条说明有人把名单删短了）");
        // 各类都必须在场（防"只留一两个名字充数"）
        for (String must : List.of("cn/eova/auth/AuthUri.java",
                "cn/eova/engine/ExpUtil.java",
                "cn/eova/compat/template/EnjoyTemplateRenderService.java",
                "cn/eova/ext/jfinal/EovaRenderSourceFactory.java",
                "cn/eova/web/LegacyViewSourceFactory.java",
                "cn/eova/config/PageConst.java")) {
            assertTrue(RETIRED_FILES.contains(must), "退役账本缺少（各轮的代表项）：" + must);
        }
    }

    @Test
    @DisplayName("★ T04-3：enjoy 只允许以 **test** 作用域存在（唯一用途 = oracle 判据），且不得回流 compile")
    void enjoyIsOnlyATestScopeOracleDependency() throws IOException {
        Path pom = moduleDir().getParent().resolve("eova-compat/pom.xml");
        assertTrue(Files.isRegularFile(pom), "★ fail-closed：找不到 eova-compat/pom.xml");
        String xml = Files.readString(pom, StandardCharsets.UTF_8);

        int i = xml.indexOf("<artifactId>enjoy</artifactId>");
        assertTrue(i >= 0, "★ eova-compat 应保留 enjoy 声明（**test 作用域**，供 port 与真制品比对）");
        int blockStart = xml.lastIndexOf("<dependency>", i);
        int blockEnd = xml.indexOf("</dependency>", i);
        String block = xml.substring(blockStart, blockEnd);
        assertTrue(block.contains("<scope>test</scope>"),
                "★ enjoy 必须是 test 作用域（生产编译/运行期不得依赖它）：" + block);
        assertFalse(block.contains("<scope>provided</scope>"), "★ provided 会让依赖退到一半又回流");

        // eova-web 侧同理（差分 oracle 在那里）
        Path webPom = moduleDir().resolve("pom.xml");
        String webXml = Files.readString(webPom, StandardCharsets.UTF_8);
        int j = webXml.indexOf("<artifactId>enjoy</artifactId>");
        assertTrue(j >= 0, "★ eova-web 应有 enjoy（test 作用域）—— 差分 oracle 需要真引擎");
        String webBlock = webXml.substring(webXml.lastIndexOf("<dependency>", j), webXml.indexOf("</dependency>", j));
        assertTrue(webBlock.contains("<scope>test</scope>"),
                "★ eova-web 的 enjoy 必须是 test 作用域：" + webBlock);
    }
}
