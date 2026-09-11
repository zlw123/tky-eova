/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.core;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.core.ActionReporter} 的等价接缝（**配置面**）。
 *
 * <p>ported from: com.jfinal.core.ActionReporter（jfinal 5.2.6 制品）
 *
 * <p><b>为什么只 port 配置面：</b>旧 {@code EovaConfig.configConstant} 只调
 * {@code ActionReporter.setTitle(...)} 一处（用于 action 日志标题）。真正的
 * {@code report(...)} 打印逻辑要 jfinal 的 Action/Invocation 对象，新栈无对应物，
 * 故<b>未包含</b>（新栈的访问日志由宿主的日志/拦截器承担 —— 已在 §r82 记录）。</p>
 *
 * <p><b>默认值取自旧字节码静态初始化：</b>{@code title} 默认 {@code null}；
 * {@code reportAfterInvocation=false}；{@code maxOutputLengthOfParaValue=50}。</p>
 */
public class LegacyActionReporter {

    /** 日志标题（旧实现 protected static） */
    protected static String title;

    /** 是否在 action 之后打印 */
    protected static boolean reportAfterInvocation = false;

    /** 参数值最大输出长度 */
    protected static int maxOutputLengthOfParaValue = 50;

    /**
     * 设置标题。
     *
     * @param title 标题
     */
    public static void setTitle(String title) {
        LegacyActionReporter.title = title;
    }

    /**
     * 取标题。
     *
     * @return 标题
     */
    public static String getTitle() {
        return title;
    }

    /**
     * 设置是否在 action 之后打印。
     *
     * @param reportAfterInvocation 是否打印
     */
    public static void setReportAfterInvocation(boolean reportAfterInvocation) {
        LegacyActionReporter.reportAfterInvocation = reportAfterInvocation;
    }

    /**
     * 取参数值最大输出长度。
     *
     * @return 长度
     */
    public static int getMaxOutputLengthOfParaValue() {
        return maxOutputLengthOfParaValue;
    }

    /**
     * 设置参数值最大输出长度。
     *
     * @param maxOutputLengthOfParaValue 长度
     */
    public static void setMaxOutputLengthOfParaValue(int maxOutputLengthOfParaValue) {
        LegacyActionReporter.maxOutputLengthOfParaValue = maxOutputLengthOfParaValue;
    }

}
