/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.testkit;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 旧实现加载器：把【旧源码编译产物】加载进测试 JVM，用于跨实现行为等价验证。
 *
 * <p>存在理由：迁移的判据是"与旧实现等价"，而判断等价最可靠的方式是
 * <b>让旧实现在环上自己作证</b>，而不是人工阅读源码后断言自造期望值。
 * 后者会漏掉那些"看起来像 bug 但属既有契约"的行为（NPE 分支、null 回退、悬空逗号等）。
 *
 * <p>加载策略（子优先 + 精确前缀）：
 * <ul>
 *   <li>对 {@link #CHILD_FIRST_PREFIXES} 中的包与 {@link #CHILD_FIRST_EXACT} 中的类，
 *       <b>优先从旧产物加载</b> ——
 *       否则会解析到本次 port 的新实现，比对就失去意义；</li>
 *   <li>其余类（如 {@code cn.eova.tools.x}、druid）交给父加载器 ——
 *       旧类会依赖它们，必须与测试类路径共享同一份，否则 NoClassDefFound。</li>
 * </ul>
 */
public final class OldImplementationLoader {

    /**
     * 需要子优先加载的包前缀。
     *
     * <p><b>必须覆盖全部 {@code cn.eova.}</b> —— 早期版本只列了少数包，
     * 导致 {@code cn.eova.common.utils.*} 等被父加载器解析成【本次 port 的新实现】，
     * 比对退化为"新 vs 新"，恒真且无意义。
     *
     * <p>旧产物中不存在的类（如 {@code cn.eova.compat.LegacySettings}、
     * {@code cn.eova.tools.x}）在 {@code findClass} 阶段抛 ClassNotFoundException，
     * 会自动回落父加载器，故覆盖全部前缀是安全的。
     */
    private static final List<String> CHILD_FIRST_PREFIXES = List.of("cn.eova.");

    /**
     * 需要子优先加载的<b>精确类名</b>（不在 {@code cn.eova.} 命名空间内）。
     *
     * <p>存在的理由：{@code com.jfinal.kit.TypeKit} / {@code TimeKit} 同时存在于旧栈的
     * {@code com.jfinal:jfinal:5.2.6} 与新栈的 {@code com.jfinal:enjoy:5.3.0} 中，
     * 二者语义有实测差异。验证 {@code LegacyTypeKit} port 是否正确，
     * 必须让<b>5.2.6 的那一份</b>上场作证，否则比对又会退化为"新 vs 新"。
     *
     * <p>刻意只列这两个类而非整个 {@code com.jfinal.}：旧 jfinal jar 内嵌 Enjoy，
     * 若整包子优先会连带换掉模板引擎实现，影响面远超本单元所需。
     */
    private static final List<String> CHILD_FIRST_EXACT = List.of(
            "com.jfinal.kit.TypeKit",
            "com.jfinal.kit.TimeKit");

    private OldImplementationLoader() {
    }

    /**
     * 定位旧工程编译产物目录（{@code meta-eova/eova/core/target/classes}）。
     *
     * @return 目录路径；不存在时返回该路径本身，由调用方决定是否 skip
     */
    public static Path oldClassesDir() {
        return locateRepoRoot().resolve("meta-eova/eova/core/target/classes");
    }

    /**
     * 旧产物是否可用（不存在时应 skip 测试，而非失败）。
     */
    public static boolean oldClassesAvailable() {
        return Files.isDirectory(oldClassesDir());
    }

    /**
     * 自校验：断言给定类确实来自【旧产物目录】而非本次 port 的新实现。
     *
     * <p>存在的理由：加载器一旦配置错误，比对会退化为"新 vs 新"并恒真 ——
     * 这种错误不会以失败形式暴露，只会让测试失去意义。故必须显式断言来源。
     *
     * @param clazz 通过本加载器加载的旧类
     * @throws IllegalStateException 若来源不是旧产物目录
     */
    public static void assertFromOldArtifacts(Class<?> clazz) {
        var src = clazz.getProtectionDomain().getCodeSource();
        Path actual;
        try {
            actual = src == null || src.getLocation() == null
                    ? null : Path.of(src.getLocation().toURI());
        } catch (Exception e) {
            actual = null;
        }
        // 按【文件系统路径】比较：URL.toString() 是 file:/x，URI 是 file:///x，字符串前缀比对会误判
        Path expected = oldClassesDir().toAbsolutePath().normalize();
        if (actual == null || !actual.toAbsolutePath().normalize().equals(expected)) {
            throw new IllegalStateException(
                    "跨实现比对失效：" + clazz.getName() + " 实际来源=" + actual + "，期望=" + expected);
        }
    }

    /**
     * 创建子优先加载器。
     *
     * @param repoRoot 仓库根目录
     * @return 可加载旧实现的类加载器
     */
    public static ClassLoader create(Path repoRoot) throws IOException {
        URL url = oldClassesDir().toUri().toURL();
        return new ChildFirstLoader(new URL[]{url}, OldImplementationLoader.class.getClassLoader());
    }

    /**
     * 创建<b>额外挂载旧 jfinal 制品</b>的子优先加载器。
     *
     * <p><b>只允许</b>用于让 {@code com.jfinal.kit.TypeKit}/{@code TimeKit} 上场作证的测试
     * （见 {@code LegacyTypeKitGoldenTest}）。<b>不得</b>用于 EOVA 类的跨实现比对。
     *
     * <p>原因（由 {@code IoUtilsGoldenTest.txtUtilBoundary} 实测捕获）：
     * 旧 EOVA 类依赖 {@code JFinal.me().getConstants()} 这类<b>运行时全局</b>。
     * 挂载 jfinal jar 前，该调用抛 {@code NoClassDefFoundError}（Error，不被
     * {@code catch(Exception)} 捕获）—— 失败形态<b>响亮</b>，测试得以断言
     * "此路径在 harness 中不可忠实执行"；挂载之后类可解析，失败形态变成被吞掉的
     * NPE，旧侧<b>静默返回错值</b>，验证边界被掩盖。
     * 故 jfinal 制品必须与 EOVA 类比对隔离在两个加载器里。
     *
     * @param repoRoot 仓库根目录
     * @return 可加载旧 jfinal 制品的类加载器
     */
    public static ClassLoader createWithOldJFinal(Path repoRoot) throws IOException {
        URL url = oldClassesDir().toUri().toURL();
        Path jfinal = oldJFinalJar();
        if (!Files.isRegularFile(jfinal)) {
            throw new IOException("旧 jfinal 制品不存在：" + jfinal);
        }
        return new ChildFirstLoader(new URL[]{url, jfinal.toUri().toURL()},
                OldImplementationLoader.class.getClassLoader());
    }

    /**
     * 定位旧栈实际使用的 {@code com.jfinal:jfinal:5.2.6} jar。
     *
     * <p>依据：{@code docs/.local/spikes/sp6-record-semantics/pom.xml} 明文声明
     * {@code jfinal 5.2.6}，该探针产出的 golden 即本测试的比对基准。
     *
     * <p>查找顺序：系统属性 {@code eova.old.jfinal.jar} → 本机 maven 仓库固定坐标。
     *
     * @return jar 路径；不存在时返回期望路径本身，由调用方决定是否 skip
     */
    public static Path oldJFinalJar() {
        String override = System.getProperty("eova.old.jfinal.jar");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"),
                ".m2", "repository", "com", "jfinal", "jfinal", "5.2.6", "jfinal-5.2.6.jar");
    }

    /**
     * 旧栈 jfinal 制品是否可用（不存在时应 skip 测试，而非失败）。
     */
    public static boolean oldJFinalJarAvailable() {
        return Files.isRegularFile(oldJFinalJar());
    }

    /**
     * 自校验（jar 形态）：断言给定类确实来自期望的旧 jar，防止比对退化为"新 vs 新"。
     *
     * @param clazz        通过加载器加载的旧类
     * @param expectedJar  期望来源 jar
     * @throws IllegalStateException 若来源不是期望 jar
     */
    public static void assertFromJar(Class<?> clazz, Path expectedJar) {
        var src = clazz.getProtectionDomain().getCodeSource();
        Path actual;
        try {
            actual = src == null || src.getLocation() == null
                    ? null : Path.of(src.getLocation().toURI());
        } catch (Exception e) {
            actual = null;
        }
        Path expected = expectedJar.toAbsolutePath().normalize();
        if (actual == null || !actual.toAbsolutePath().normalize().equals(expected)) {
            throw new IllegalStateException(
                    "跨实现比对失效：" + clazz.getName() + " 实际来源=" + actual + "，期望=" + expected);
        }
    }

    /**
     * 从当前工作目录向上定位仓库根（以 {@code meta-eova/eova} 目录为标志）。
     */
    public static Path locateRepoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("meta-eova/eova"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("未能定位仓库根（找不到 meta-eova/eova）");
    }

    /**
     * 创建<b>只加载旧 jfinal 制品</b>的加载器：子优先前缀为 {@code com.jfinal.}，
     * 且<b>不挂载旧 EOVA 产物目录</b>。
     *
     * <p><b>存在理由：</b>验证宿主类语义（`Record.toJson`、`Kv`、`Ret`、`JsonKit`）
     * 时，只需要旧 jfinal 制品上场，不需要旧 EOVA 类。做成"纯 jfinal"加载器可
     * <b>结构性避开 R38 的陷阱</b> —— 该陷阱的成因是"旧 EOVA 类 + 新挂的 jfinal jar"
     * 组合改变了旧 EOVA 类缺失依赖的失败形态。这里根本不加载 EOVA 类，故无此风险。
     *
     * <p>实测可行性：{@code com.jfinal.json.Json} 的静态初始化即
     * {@code defaultJsonFactory = new JFinalJsonFactory()}，
     * 故 {@code JsonKit.toJson} / {@code Record.toJson} <b>无需 JFinal 启动</b>即可执行。
     *
     * @return 可加载旧 jfinal 制品的类加载器
     */
    public static ClassLoader createForJFinalOnly() throws IOException {
        Path jfinal = oldJFinalJar();
        if (!Files.isRegularFile(jfinal)) {
            throw new IOException("旧 jfinal 制品不存在：" + jfinal);
        }
        return new ChildFirstLoader(new URL[]{jfinal.toUri().toURL()},
                OldImplementationLoader.class.getClassLoader(), List.of("com.jfinal."));
    }

    /** 对指定包前缀做子优先加载，其余委派父加载器 */
    private static final class ChildFirstLoader extends URLClassLoader {

        private final List<String> childFirst;

        ChildFirstLoader(URL[] urls, ClassLoader parent) {
            this(urls, parent, CHILD_FIRST_PREFIXES);
        }

        ChildFirstLoader(URL[] urls, ClassLoader parent, List<String> childFirst) {
            super(urls, parent);
            this.childFirst = childFirst;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (childFirst.stream().anyMatch(name::startsWith)
                    || CHILD_FIRST_EXACT.contains(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        try {
                            c = findClass(name);
                        } catch (ClassNotFoundException ignored) {
                            // 旧产物里没有该类（例如新增类），回落到父加载器
                        }
                    }
                    if (c != null) {
                        if (resolve) {
                            resolveClass(c);
                        }
                        return c;
                    }
                }
            }
            return super.loadClass(name, resolve);
        }
    }
}
