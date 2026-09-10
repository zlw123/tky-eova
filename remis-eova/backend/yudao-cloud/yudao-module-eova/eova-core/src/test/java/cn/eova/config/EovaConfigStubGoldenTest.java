/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>compile-stub 的"逐字一致"声明判据。</b>
 *
 * <p><b>为什么需要它：</b>{@code EovaConfig} 目前是<b>已声明的 compile-stub</b> ——
 * 它只声明几个被真实 port 单元读取的静态成员，而对这些单元而言，
 * <b>stub 里的取值就是契约</b>（例如 {@code xx} 的 4 个方言分支读
 * {@code EOVA_DBTYPE}，{@code AuthUri} 读 {@code EOVA_INDEX}）。
 * 本 stub 的注释声称"每个成员的声明与旧源码逐字一致"，
 * 但在此之前<b>没有任何机械验证</b> —— 一次手滑（把 {@code EOVA_INDEX} 改成空串、
 * 或给 {@code modLoader} 换个初值）就能让依赖它的单元<b>静默改变语义</b>，
 * 而所有测试仍是绿的（因为那些单元不会去比对 stub）。
 *
 * <p>这正是 R45「非判别性断言」的另一个面：<b>声明得再详细，没有判据就只是注释。</b>
 *
 * <p><b>判据口径（直接对照只读基线文本，不做语义推断）：</b>
 * <ol>
 *   <li><b>无杜撰</b>：stub 里每一条 {@code public static <type> <name> = <init>;} 都必须在
 *       旧 {@code EovaConfig.java} 中存在<b>同名字段</b>；</li>
 *   <li><b>逐字一致</b>：该字段的"类型 + 初值"必须与旧源码<b>逐字符相同</b>
 *       （只归一空白），即迁移口径所要求的"逐字一致"由字节比较蕴含，而非人工誊抄；</li>
 *   <li><b>仍是 stub</b>：文件头必须仍带 {@code compile-stub} 标记 ——
 *       防止有人把它"顺手"当成已 port 单元；完整 port 落地时本判据应随 stub 一起删除；</li>
 *   <li><b>非空洞</b>：至少校验到 3 个成员（当前为 3 个），否则判据已退化为空跑。</li>
 * </ol>
 *
 * <p>acceptanceProfile: golden-stub-declaration
 */
class EovaConfigStubGoldenTest {

    private static final Path OLD_SOURCE_REL =
            Path.of("meta-eova/eova/core/src/main/java/cn/eova/config/EovaConfig.java");

    /** 新 stub 相对仓库根的路径 */
    private static final Path NEW_SOURCE_REL = Path.of(
            "remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java"
                    + "/cn/eova/config/EovaConfig.java");

    /** 匹配 {@code public static <type> <name> = <init>;}，捕获 类型 / 名称 / 初值 */
    private static final Pattern DECL = Pattern.compile(
            "^\\s*public\\s+static\\s+([\\w.<>\\[\\]]+)\\s+(\\w+)\\s*=\\s*(.+?);\\s*$");

    /**
     * stub 的每个静态成员都必须与旧源码逐字一致，且不得杜撰。
     *
     * @throws Exception IO 失败
     */
    @Test
    @DisplayName("EovaConfig stub 的静态成员：旧源码中同名字段的类型与初值逐字一致")
    void stubDeclarationsMatchOldSource() throws Exception {
        Path repoRoot = OldImplementationLoader.locateRepoRoot();
        Path oldFile = repoRoot.resolve(OLD_SOURCE_REL);
        Path newFile = repoRoot.resolve(NEW_SOURCE_REL);

        Assumptions.assumeTrue(Files.isRegularFile(oldFile), "旧源码缺失：" + oldFile);
        Assumptions.assumeTrue(Files.isRegularFile(newFile), "stub 缺失：" + newFile);

        Map<String, String[]> oldDecls = declaredStatics(Files.readAllLines(oldFile));
        Map<String, String[]> newDecls = declaredStatics(Files.readAllLines(newFile));

        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, String[]> e : newDecls.entrySet()) {
            String name = e.getKey();
            String[] nd = e.getValue();
            String[] od = oldDecls.get(name);
            if (od == null) {
                problems.add("stub 杜撰了旧源码中不存在的静态成员：" + name);
                continue;
            }
            if (!nd[0].equals(od[0])) {
                problems.add(name + " 的类型不一致：stub=" + nd[0] + " 旧=" + od[0]);
            }
            if (!nd[1].equals(od[1])) {
                problems.add(name + " 的初值不一致：stub=" + nd[1] + " 旧=" + od[1]);
            }
        }

        assertTrue(problems.isEmpty(),
                "stub 声明与旧源码不符（口径要求逐字一致）：\n  " + String.join("\n  ", problems));

        // 非空洞护栏
        assertTrue(newDecls.size() >= 3,
                "stub 只校验到 " + newDecls.size() + " 个静态成员，判据可能已空洞");
    }

    /**
     * stub 必须仍被标记为 compile-stub（防止静默"升格"为已 port 单元）。
     *
     * @throws Exception IO 失败
     */
    @Test
    @DisplayName("EovaConfig 仍标记为 compile-stub（未被静默升格为 port 单元）")
    void stillMarkedAsStub() throws Exception {
        Path newFile = OldImplementationLoader.locateRepoRoot().resolve(NEW_SOURCE_REL);
        Assumptions.assumeTrue(Files.isRegularFile(newFile), "stub 缺失：" + newFile);

        List<String> head = Files.readAllLines(newFile).subList(0, 4);
        assertTrue(String.join("\n", head).contains("compile-stub"),
                "文件头必须保留 compile-stub 标记；若完整 port 已落地，"
                        + "应改用真实实现并同时删除本判据（见 §进度度量口径）");
    }

    /**
     * 反射自检：stub 的静态字段确实可按声明读取（证明依赖方拿到的就是这些取值）。
     */
    @Test
    @DisplayName("反射自检：stub 的静态字段可读且取值与旧源码声明一致")
    void reflectionMatchesDeclaration() {
        assertEquals("com.alibaba.druid.DbType",
                EovaConfig.EOVA_DBTYPE.getClass().getName().equals("com.alibaba.druid.DbType")
                        ? "com.alibaba.druid.DbType" : "?",
                "EOVA_DBTYPE 的运行时类型必须是 druid 的 DbType");
        assertEquals("/", EovaConfig.EOVA_INDEX, "EOVA_INDEX 的运行时取值必须是 \"/\"");
        assertEquals(null, EovaConfig.modLoader, "modLoader 的运行时初值必须是 null");
    }

    /**
     * 抽取 {@code public static <type> <name> = <init>;} 声明。
     *
     * @param lines 源码行
     * @return 名称 -&gt; [类型, 初值]
     */
    private static Map<String, String[]> declaredStatics(List<String> lines) {
        Map<String, String[]> out = new LinkedHashMap<>();
        for (String raw : lines) {
            // 先剥掉行尾注释再匹配：旧源码 :97 是
            //   public static DbType EOVA_DBTYPE = DbType.mysql;// EOVA_DBTYPE.toString();
            // 行尾注释不是声明的一部分，若不剥离会被并进"初值"而误报
            // （该误报由本判据第一次运行就抓到了）。
            String line = raw.replaceAll("//.*$", "").replaceAll("/\\*.*?\\*/", "");
            Matcher m = DECL.matcher(line);
            if (m.matches()) {
                out.put(m.group(2), new String[]{m.group(1), m.group(3).trim()});
            }
        }
        return out;
    }

}
