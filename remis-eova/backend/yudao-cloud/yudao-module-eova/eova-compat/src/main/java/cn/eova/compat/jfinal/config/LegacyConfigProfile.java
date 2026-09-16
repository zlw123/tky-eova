/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.LoggerFactory;
import cn.eova.compat.jfinal.kit.LegacyProp;
import cn.eova.compat.jfinal.kit.LegacyPropKit;

/**
 * **EOVA 配置档位（环境 × 数据库类型）的装载接缝** —— 新增（**非 port**），见
 * {@code docs/DES-010-R1-eova-deploy-profile-selection.md}。
 *
 * <p><b>它替掉的是哪一行</b>：旧实现 {@code EovaConfig.java:202}（新栈 {@code EovaConfig.java:220}）
 * 的
 * {@code PropKit.useFirstFound("eova/dev.txt", "eova/test.txt", "eova/pre.txt", "eova/pro.txt", "eova/prd.txt")}。
 * 那一行的语义是"**部署时只发布当前环境的那一个文件**"，五档是"防呆回退"而**不是选择器**；
 * 新栈把多个档打在同一个 classpath 里后，"首存在者胜"会退化成"`dev.txt` 永远胜"
 * ⇒ 档位机制事实上失效（实测：`prd.txt` 存在时仍选中 `dev.txt`）。</p>
 *
 * <p><b>两条路径，互不干扰</b>：
 * <ul>
 *   <li><b>未指定档</b>（默认）⇒ {@link #LEGACY_PROFILES} 原序交给
 *       {@code LegacyPropKit.useFirstFound} —— 与旧实现**逐字等价**；</li>
 *   <li><b>已指定档</b>（{@code -Deova.prop=} / {@code EOVA_PROP=}）⇒ **只用该档、不回落**。
 *       指定了却不存在 ⇒ **抛**（`IllegalStateException`）。</li>
 * </ul>
 * 为什么"指定档"不走 {@code useFirstFound(指定档, …五档)}：那样"档名写错一个字"会**静默跑 dev 档**
 * —— 那正是 r323 花了一轮才挖出来的缺陷类型（配置没生效但一切看起来正常）。</p>
 */
public final class LegacyConfigProfile {

    /** 宿主指定档的系统属性名（部署旋钮；命令行 `-D` / `--eova.prop=` 桥接后都落到这里） */
    public static final String PROP_KEY = "eova.prop";

    /** 宿主指定档的环境变量名（`PROP_KEY` 缺失时才看它） */
    public static final String ENV_KEY = "EOVA_PROP";

    /**
     * 旧实现的五档（**顺序即优先级，逐字取自** {@code EovaConfig.java:202} 的
     * {@code PropKit.useFirstFound} 调用）。
     */
    public static final List<String> LEGACY_PROFILES = Collections.unmodifiableList(Arrays.asList(
            "eova/dev.txt", "eova/test.txt", "eova/pre.txt", "eova/pro.txt", "eova/prd.txt"));

    /**
     * 合法档名的形态：**限定在 `eova/` 下的一层 `.txt`**。
     *
     * <p>为什么必须校验：宿主旋钮若被写成 `../../etc/passwd`、`/etc/x.txt`、`eova/../application.yml`，
     * 就等于把"任意 classpath 资源"变成配置源（甚至把别的格式当 properties 解析）。
     * 宁可在启动时响亮拒绝，也不接受"能跑但来源不明"的配置。</p>
     */
    private static final Pattern LEGAL_NAME = Pattern.compile("^eova/[A-Za-z0-9._-]+\\.txt$");

    /**
     * 最近一次**真实装载**的档名（只用于日志/追溯；未装载为 {@code null}）。
     *
     * <p>为什么需要它：宿主侧的日志（如"元数据源坐标来源"）必须写**真实装载的那个文件**，
     * 而不是"哪个文件大概在 classpath 里"。r323 就是因为按值推断出处，把真根因掩盖了一轮。</p>
     */
    private static volatile String active;

    private LegacyConfigProfile() {
    }

    /**
     * 最近一次真实装载的档名。
     *
     * @return 档名；尚未装载为 null
     */
    public static String active() {
        return active;
    }

    /**
     * 宿主指定的档名（未指定 ⇒ {@code null}）。
     *
     * <p>先看 JVM 系统属性 {@link #PROP_KEY}，再看环境变量 {@link #ENV_KEY}；两侧都空白 ⇒ 未指定。
     * 只看这两个来源：本方法在容器 bean 装配期被调用，此时它们的必然可读性最高
     * （Spring 属性需宿主桥接，见 {@code LegacyWebBootstrap}）。</p>
     *
     * @return 档名；未指定为 null
     */
    public static String specified() {
        String v = System.getProperty(PROP_KEY);
        if (v == null || v.trim().isEmpty()) {
            v = System.getenv(ENV_KEY);
        }
        if (v == null || v.trim().isEmpty()) {
            return null;
        }
        return v.trim();
    }

    /**
     * 装载生效档（{@code EovaConfig.configConstant} 的唯一调用点）。
     *
     * @return 已装载的属性档（同档名重复调用返回同一实例，由 {@code LegacyPropKit} 缓存）
     * @throws IllegalArgumentException 宿主指定的档名非法（非 `eova/*.txt`）
     * @throws IllegalStateException    宿主指定的档在 classpath 里不存在（**不回落**）
     */
    public static LegacyProp load() {
        String spec = specified();
        if (spec == null) {
            LegacyProp prop = LegacyPropKit.useFirstFound(LEGACY_PROFILES.toArray(new String[0]));
            active = prop.getFileName();
            LoggerFactory.getLogger(LegacyConfigProfile.class).info("配置档已装载：" + prop.getFileName() + "（来源=未指定 ⇒ 旧五档首存在者）");
            return prop;
        }
        if (!LEGAL_NAME.matcher(spec).matches()) {
            throw new IllegalArgumentException("非法的配置档名：" + spec
                    + "（要求形如 eova/<名字>.txt，见 -D" + PROP_KEY + "）");
        }
        if (!exists(spec)) {
            throw new IllegalStateException("指定的配置档不存在于 classpath：" + spec
                    + "（现有候选：" + present() + "）—— 已指定档时不做任何回落，请检查 -D" + PROP_KEY);
        }
        LegacyProp prop = LegacyPropKit.use(spec, "UTF-8");
        active = prop.getFileName();
        LoggerFactory.getLogger(LegacyConfigProfile.class).info("配置档已装载：" + prop.getFileName() + "（来源=宿主指定 " + PROP_KEY + "）");
        return prop;
    }

    /**
     * classpath 里是否存在该资源。
     *
     * @param name 资源名
     * @return 是否存在
     */
    private static boolean exists(String name) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = LegacyConfigProfile.class.getClassLoader();
        }
        return cl.getResource(name) != null;
    }

    /**
     * 现有候选档（用于"指定档不存在"时的报错信息）。
     *
     * @return 逗号分隔的候选档名
     */
    private static String present() {
        List<String> found = new ArrayList<>();
        for (String p : LEGACY_PROFILES) {
            if (exists(p)) {
                found.add(p);
            }
        }
        return String.join(", ", found);
    }

}
