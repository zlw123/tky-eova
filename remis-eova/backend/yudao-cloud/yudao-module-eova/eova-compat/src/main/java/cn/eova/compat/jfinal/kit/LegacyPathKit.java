/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.io.File;
import java.net.URL;

/**
 * <p>ported from: com.jfinal.kit.PathKit（enjoy 5.3.0 制品随附的 kit 子集）
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br><b>为什么要有它</b>：{@code com.jfinal.kit.*} 由 {@code com.jfinal:enjoy} 制品提供，而
 * T04 第二段要退役该依赖 ⇒ 生产代码里对 {@code PathKit} 的用法必须落在**本仓自有 port** 上。
 * 这是"UTIL 腿"（与模板渲染无关、可独立完成）的那一半。</p>
 *
 * <p><b>语义按实跑取证逐条对齐</b>（探针在同一环境调用旧 {@code PathKit} 记录真值）：</p>
 * <ul>
 *   <li>{@code getWebRootPath()}：默认值 = **classpath 根的祖父目录**。surefire 下 classpath 根是
 *       {@code <module>/target/test-classes} ⇒ 默认值 = {@code <module>}（实测
 *       {@code /…/yudao-module-eova/eova-compat}）；可用 {@code setWebRootPath} 覆盖（旧实现是可变静态值）；</li>
 *   <li>{@code getRootClassPath()}：默认值 = 类加载器 {@code getResource("")} 的绝对路径
 *       （实测 {@code <module>/target/test-classes}）；可用 {@code setRootClassPath} 覆盖；</li>
 *   <li>{@code getPackagePath(Object)}：返回该类**包路径**的斜杠形式，**无首尾斜杠**
 *       （实测 {@code PathKit.getPackagePath(String.class)} = {@code java/lang}、
 *       探针类 = {@code cn/eova/compat/probe}）—— 调用方是
 *       {@code ResourceRender#buildResource}，它再拼 {@code "%s/resources/%s"}；</li>
 *   <li>旧 {@code getPath(Class)} 对 JDK 类会 **NPE**（实测：{@code Class.getResource("")} 返回 null），
 *       本 port **不提供**该易碎方法（生产代码也没用）。</li>
 * </ul>
 *
 * <p>★ <b>与 Enjoy 的耦合（必须知道）</b>：旧栈里 {@code PathKit.setWebRootPath(...)} 不只是给本仓代码看的
 * —— **Enjoy 的 {@code FileSource}/{@code Engine} 内部也会读它**（{@code EnjoyTemplateRenderService} 的类注释
 * 把这条标为"最隐蔽的一条"）。故在 Enjoy 退役之前，设置 web 根的地方必须**两边同时设**，
 * 否则会出现"本仓读到默认值、Enjoy 读到宿主值"的静默分歧。判据：
 * {@code LegacyPathKitGoldenTest#webRootStaysInSyncWithEnjoyPathKit}。</p>
 */
public class LegacyPathKit {

    /** web 根（旧实现：可变静态值，默认由 classpath 推导） */
    private static String webRootPath;

    /** classpath 根（旧实现：可变静态值，默认由类加载器推导） */
    private static String rootClassPath;

    private LegacyPathKit() {
    }

    /**
     * 取 classpath 根。
     *
     * @return classpath 根绝对路径（推导不到时退回 {@code user.dir}）
     */
    public static String getRootClassPath() {
        if (rootClassPath == null) {
            try {
                URL url = LegacyPathKit.class.getClassLoader().getResource("");
                if (url != null) {
                    rootClassPath = new File(url.toURI().getPath()).getAbsolutePath();
                }
            } catch (Exception e) {
                // 旧实现在异常时同样退回默认值（不抛）
            }
            if (rootClassPath == null) {
                rootClassPath = new File(System.getProperty("user.dir", ".")).getAbsolutePath();
            }
        }
        return rootClassPath;
    }

    /**
     * 设置 classpath 根（旧实现：可变静态值）。
     *
     * @param rootClassPath classpath 根
     */
    public static void setRootClassPath(String rootClassPath) {
        LegacyPathKit.rootClassPath = rootClassPath;
    }

    /**
     * 取 web 根。
     *
     * @return web 根绝对路径
     */
    public static String getWebRootPath() {
        if (webRootPath == null) {
            webRootPath = detectWebRootPath();
        }
        return webRootPath;
    }

    /**
     * 设置 web 根（旧实现：可变静态值）。
     *
     * <p>⚠️ 在 Enjoy 退役之前，调用本方法的地方**必须同时**调用 {@code com.jfinal.kit.PathKit#setWebRootPath}
     * —— Enjoy 内部也读后者（见类注释）。</p>
     *
     * @param webRootPath web 根
     */
    public static void setWebRootPath(String webRootPath) {
        LegacyPathKit.webRootPath = webRootPath;
    }

    /**
     * 推导默认 web 根：classpath 根的**祖父目录**（旧实现实测口径）。
     *
     * @return 默认 web 根绝对路径
     */
    private static String detectWebRootPath() {
        try {
            File parent = new File(getRootClassPath()).getCanonicalFile().getParentFile();
            return parent != null && parent.getParentFile() != null
                    ? parent.getParentFile().getCanonicalPath()
                    : new File(System.getProperty("user.dir", ".")).getCanonicalPath();
        } catch (Exception e) {
            return new File(System.getProperty("user.dir", ".")).getAbsolutePath();
        }
    }

    /**
     * 取对象所在类的**包路径**（斜杠形式，无首尾斜杠）。
     *
     * @param object 目标对象
     * @return 包路径，如 {@code cn/eova/common/render}
     */
    public static String getPackagePath(Object object) {
        Package pkg = object.getClass().getPackage();
        return pkg == null ? "" : pkg.getName().replace('.', '/');
    }

    /**
     * 是否绝对路径（旧实现口径：**以 {@code /} 开头** 或 **第 2 个字符是 {@code :}**）。
     *
     * <p>★ 该规则由 golden 判据实测纠正：{@code "C:"} 在旧实现里是 **true**（只看下标 1 是不是冒号，
     * 不要求后面还有分隔符）—— 首版实现写成"须有 {@code X:\} 三段式"⇒ 判据当场报不等价。</p>
     *
     * <p>★ **已声明的差异**：{@code null} 入参旧实现会 NPE，本 port 返回 false（生产代码无调用点，
     * 取更稳的语义；判据里显式断言这条差异）。</p>
     *
     * @param path 路径
     * @return 是否绝对路径
     */
    public static boolean isAbsolutePath(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/") || path.indexOf(':') == 1;
    }
}
