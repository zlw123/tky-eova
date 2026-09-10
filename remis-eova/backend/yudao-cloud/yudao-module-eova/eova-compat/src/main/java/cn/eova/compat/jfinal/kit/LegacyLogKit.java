/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.kit.LogKit} 的等价物（阶段 1 宿主适配）。
 *
 * <p><b>为什么必须补：</b>enjoy 5.3.0 <b>不提供</b> {@code com.jfinal.kit.LogKit}，
 * 而 EOVA 实测有 <b>约 60 处</b>调用（{@code error} 35 · {@code info} 15 ·
 * {@code debug} 6 · {@code warn} 4），分布在 31 个单元里 ——
 * 它是"模型层/meta 层"若干单元的共同上游阻塞点。
 *
 * <p>ported from: com.jfinal.kit.LogKit（第三方制品，非 EOVA 源码）
 * <br>source artifact: com.jfinal:jfinal:5.2.6
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 *
 * <p><b>语义对齐要点（据 jfinal 完整实现）：</b>
 * <ol>
 *   <li>jfinal 的 {@code LogKit} 把调用转发给它<b>可插拔的 {@code Log} 实现</b>；
 *       其 slf4j 实现（{@code Slf4jLog}）在 {@code Object...} 重载里使用
 *       <b>{@code org.slf4j.helpers.MessageFormatter.arrayFormat}</b> —— 即
 *       <b>{@code {}} 占位符</b>风格，并从参数尾部提取 {@code Throwable}
 *       （{@code FormattingTuple.getThrowable()}）。
 *       这与 slf4j 原生 {@code Logger.error(String, Object...)} 的行为<b>完全一致</b>，
 *       故本实现直接委托 slf4j，占位符与异常提取语义不变。</li>
 *   <li>{@code fatal(...)} 在 jfinal 的 slf4j 实现里<b>转发到 {@code error}</b>
 *       （slf4j 无 fatal 级别）—— 原样保留该降级。</li>
 *   <li>日志器名沿用 <b>{@code com.jfinal.kit.LogKit}</b>：jfinal 的 LogKit 以自身类取
 *       日志器，沿用同名可让既有日志配置（级别/输出/过滤）继续生效。属"保留可运维性"的对齐。</li>
 *   <li>{@code synchronizeLog()} / {@code logNothing(Throwable)} 不声明 ——
 *       EOVA 无调用方；不声明会让将来若真需要时编译期报错，而不是静默留空。</li>
 * </ol>
 *
 * <p><b>已声明的适配：</b>底层实现由"jfinal 可插拔 Log（旧工程用 log4j 1.2.17）"
 * 改为 <b>SLF4J</b>（新栈由 Spring Boot 的 Logback 承载）。
 * 日志文本与级别语义一致；<b>日志文本不属对外契约</b>，故该替换不构成行为变更，
 * 但会改变日志落盘格式与实现 —— 属需要显式声明的宿主编排变化。
 */
public final class LegacyLogKit {

    /** 日志器名沿用 jfinal 的类名，使既有日志配置继续生效 */
    private static final Logger LOG = LoggerFactory.getLogger("com.jfinal.kit.LogKit");

    private LegacyLogKit() {
    }

    /** trace 级 */
    public static void trace(String message) {
        LOG.trace(message);
    }

    /** trace 级（带异常） */
    public static void trace(String message, Throwable t) {
        LOG.trace(message, t);
    }

    /** trace 级（{} 占位符） */
    public static void trace(String message, Object... args) {
        LOG.trace(message, args);
    }

    /** debug 级 */
    public static void debug(String message) {
        LOG.debug(message);
    }

    /** debug 级（带异常） */
    public static void debug(String message, Throwable t) {
        LOG.debug(message, t);
    }

    /** debug 级（{} 占位符） */
    public static void debug(String message, Object... args) {
        LOG.debug(message, args);
    }

    /** info 级 */
    public static void info(String message) {
        LOG.info(message);
    }

    /** info 级（带异常） */
    public static void info(String message, Throwable t) {
        LOG.info(message, t);
    }

    /** info 级（{} 占位符） */
    public static void info(String message, Object... args) {
        LOG.info(message, args);
    }

    /** warn 级 */
    public static void warn(String message) {
        LOG.warn(message);
    }

    /** warn 级（带异常） */
    public static void warn(String message, Throwable t) {
        LOG.warn(message, t);
    }

    /** warn 级（{} 占位符） */
    public static void warn(String message, Object... args) {
        LOG.warn(message, args);
    }

    /** error 级 */
    public static void error(String message) {
        LOG.error(message);
    }

    /** error 级（带异常） */
    public static void error(String message, Throwable t) {
        LOG.error(message, t);
    }

    /** error 级（{} 占位符） */
    public static void error(String message, Object... args) {
        LOG.error(message, args);
    }

    /** fatal 级：jfinal 的 slf4j 实现把它降级到 error —— 原样保留 */
    public static void fatal(String message) {
        LOG.error(message);
    }

    /** fatal 级（带异常）：同样降级到 error */
    public static void fatal(String message, Throwable t) {
        LOG.error(message, t);
    }

    /** fatal 级（{} 占位符）：同样降级到 error */
    public static void fatal(String message, Object... args) {
        LOG.error(message, args);
    }

    /** trace 是否启用 */
    public static boolean isTraceEnabled() {
        return LOG.isTraceEnabled();
    }

    /** debug 是否启用 */
    public static boolean isDebugEnabled() {
        return LOG.isDebugEnabled();
    }

    /** info 是否启用 */
    public static boolean isInfoEnabled() {
        return LOG.isInfoEnabled();
    }

    /** warn 是否启用 */
    public static boolean isWarnEnabled() {
        return LOG.isWarnEnabled();
    }

    /** error 是否启用 */
    public static boolean isErrorEnabled() {
        return LOG.isErrorEnabled();
    }

    /** fatal 是否启用：与 jfinal 一致，映射到 error */
    public static boolean isFatalEnabled() {
        return LOG.isErrorEnabled();
    }
}
