/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.port;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **U2 日志命名收口判据（r333 / DES-012 P4-(a)）**：`LegacyLogKit` 接缝**退役**，
 * 27 个 port 单元的调用点收口为**行内 SLF4J**。
 *
 * <p><b>为什么需要反向判据</b>：这次改动是"删掉一个接缝 + 把 57 处调用换成行内形态"，
 * 既有判据（声明面/字节复核）**只会因为"多出来"而红，不会因为"少掉/复活"而红** ——
 * 若有人把 `LegacyLogKit` 类加回来（或把调用改回接缝），
 * `port-units.py --verify` 只比对**文件体与旧源 + 声明替换对**，
 * 声明对本身也被改回去时它会**照样全绿**。故这里独立钉住三件事：</p>
 *
 * <ol>
 *   <li>{@link #retiredClassIsGone()}：类**不在 classpath 上**、源码里也没有该文件（复活即红）；</li>
 *   <li>{@link #noCodeReferenceRemains()}：生产源码里**非注释**的 `LegacyLogKit` 引用 = 0；</li>
 *   <li>{@link #inlineFormIsPresentAndNoFieldAdded()}：行内形态**确实在**（反空断言，防"两边都没有"），
 *       且**没有**新增 `Logger LOG` 字段 —— 这是当初选择行内形态的硬约束：
 *       `BFacePortSurfaceGoldenTest` 逐字段严格相等且无"已声明新增"机制，新增字段会红。</li>
 * </ol>
 *
 * <p><b>已声明差异（不属对外契约，但必须留痕）</b>：日志器名由接缝里的
 * {@code com.jfinal.kit.LogKit}（R37 为"沿用既有日志配置"而固化）变为**各行自身 FQCN**。
 * 新栈 `eova-web/src/main/resources/logback-spring.xml` **只有 root 级配置**（无 per-logger 规则，
 * 见 `Phase3StackAlignmentTest`）⇒ 当前无可观测的运行差异，差异仅在 logger 名文本。</p>
 */
class U2LogNamingGoldenTest {

    /** 已退役的接缝（全名，判据里按**独立字面量**写，不 import 被测方） */
    private static final String RETIRED_FQCN = "cn.eova.compat.jfinal.kit.LegacyLogKit";

    /** 反空断言用的**活面**代表类：必须仍在 classpath，否则"类找不到"可能只是 classpath 坏了（假绿） */
    private static final String LIVE_CONTROL_FQCN = "cn.eova.compat.jfinal.kit.LegacyStrKit";

    /**
     * 行内形态的**下界**（独立字面量，不 import 被测方）。
     *
     * <p>r333 实测：**非注释行**的 `LoggerFactory.getLogger(` 共 **59** 处
     * （eova-core 44 · eova-compat 7 · eova-web 8 · eova-db-adapter 0）。⇒ 下界取 50 留余量：
     * 本断言只防「接缝删了、行内形态也没了」这种**两边都没有**的假绿，**不作精确值断言**
     * （精确值会随合法改动变脆；阈值也不得为了迁就实测而下调到贴边 —— r176/r187 的口径）。</p>
     */
    private static final int MIN_INLINE_CALLS = 50;

    /** 生产源码文件数下界（防"源码根解析错 ⇒ 一个文件都没扫"的假绿；r333 实测 362 个） */
    private static final int MIN_SCANNED_FILES = 300;

    private static Path moduleParent() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("eova-core/src/main/java"))
                    && Files.isDirectory(p.resolve("eova-compat/src/main/java"))) {
                return p;
            }
        }
        throw new AssertionError("★ fail-closed：从 user.dir 向上找不到各模块父目录（user.dir=" + dir + "）");
    }

    private static List<Path> sourceRoots() {
        Path parent = moduleParent();
        List<Path> roots = List.of(
                parent.resolve("eova-core/src/main/java"),
                parent.resolve("eova-compat/src/main/java"),
                parent.resolve("eova-db-adapter/src/main/java"),
                parent.resolve("eova-web/src/main/java"));
        for (Path r : roots) {
            assertTrue(Files.isDirectory(r), "★ fail-closed：源码根不存在 " + r);
        }
        return roots;
    }

    /** 是否注释行（与 EnjoyRenderSurfaceTest 同口径：`*` / `//` / 块注释起始） */
    private static boolean isComment(String line) {
        String s = line.strip();
        return s.startsWith("*") || s.startsWith("//") || s.startsWith("/*");
    }

    private static List<Path> productionFiles() throws IOException {
        List<Path> files = new java.util.ArrayList<>();
        for (Path root : sourceRoots()) {
            try (Stream<Path> walk = Files.walk(root)) {
                files.addAll(walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList()));
            }
        }
        return files;
    }

    @Test
    @DisplayName("★ U2-1：LegacyLogKit 类已退役（classpath 与源码文件都必须不存在）")
    void retiredClassIsGone() throws Exception {
        // 反空断言：活面代表类必须在 —— 否则"找不到"可能只是 classpath/源码根解析错了
        Class.forName(LIVE_CONTROL_FQCN);
        assertThrows(ClassNotFoundException.class, () -> Class.forName(RETIRED_FQCN),
                "★ 已退役的接缝又回到 classpath 上了：" + RETIRED_FQCN);

        String rel = RETIRED_FQCN.replace('.', '/') + ".java";
        List<String> found = sourceRoots().stream()
                .filter(r -> Files.isRegularFile(r.resolve(rel)))
                .map(r -> r.resolve(rel).toString())
                .collect(Collectors.toList());
        assertTrue(found.isEmpty(), "★ 已退役接缝的源文件又出现了（必须重判它是不是活面）：" + found);
    }

    @Test
    @DisplayName("★ U2-2：生产源码里非注释的 LegacyLogKit 引用为 0（追溯头里的退役说明不算）")
    void noCodeReferenceRemains() throws IOException {
        List<Path> files = productionFiles();
        assertTrue(files.size() >= MIN_SCANNED_FILES,
                "★ fail-closed：只扫到 " + files.size() + " 个生产源码文件（< " + MIN_SCANNED_FILES + "）⇒ 源码根解析有问题");

        Pattern ref = Pattern.compile("\\b" + Pattern.quote("LegacyLogKit") + "\\b");
        List<String> hits = new java.util.ArrayList<>();
        int commentMentions = 0;
        for (Path f : files) {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                if (!ref.matcher(line).find()) {
                    continue;
                }
                if (isComment(line)) {
                    commentMentions++;   // 追溯头里的"R37 的 LegacyLogKit 接缝已退役"属**声明留痕**
                } else {
                    hits.add(f.getFileName() + ": " + line.strip());
                }
            }
        }
        assertTrue(hits.isEmpty(),
                "★ 生产源码仍在**代码层面**引用已退役接缝（应改为行内 SLF4J）：\n    " + String.join("\n    ", hits));
        // 反空断言：追溯头必须仍**声明**这次退役 —— 否则"零引用"可能只是把历史说明也删光了
        assertTrue(commentMentions > 0,
                "★ 追溯头里的退役声明被删光了：U2 的「已声明适配」必须留在 ported from 头里");
    }

    @Test
    @DisplayName("★ U2-3：行内 SLF4J 形态确实在（反空断言）；且未新增 Logger 字段")
    void inlineFormIsPresentAndNoFieldAdded() throws IOException {
        Pattern inline = Pattern.compile("LoggerFactory\\.getLogger\\(");
        Pattern loggerField = Pattern.compile("\\bLogger\\s+LOG\\b");
        int inlineCalls = 0;
        List<String> addedFields = new java.util.ArrayList<>();
        for (Path f : productionFiles()) {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                if (isComment(line)) {
                    continue;
                }
                Matcher m = inline.matcher(line);
                while (m.find()) {
                    inlineCalls++;
                }
                if (loggerField.matcher(line).find()) {
                    addedFields.add(f.getFileName() + ": " + line.strip());
                }
            }
        }
        assertTrue(inlineCalls >= MIN_INLINE_CALLS,
                "★ 行内 SLF4J 形态不足（实测 " + inlineCalls + " < " + MIN_INLINE_CALLS
                        + "）⇒ 收口可能被回退成接缝调用（反空断言，防「两边都没有」）");
        // 行内形态的**硬约束**：不得为了日志适配新增 `Logger LOG` 字段
        // （BFacePortSurfaceGoldenTest 逐字段严格相等、无"已声明新增"机制）
        assertTrue(addedFields.isEmpty(),
                "★ 新增了 Logger LOG 字段（会破 B 面声明面判据，行内形态是唯一零判据改动方案）：\n    "
                        + String.join("\n    ", addedFields));
    }
}