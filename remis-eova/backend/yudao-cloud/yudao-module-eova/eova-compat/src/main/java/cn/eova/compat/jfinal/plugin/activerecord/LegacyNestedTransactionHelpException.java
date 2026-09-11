/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin.activerecord;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.plugin.activerecord.NestedTransactionHelpException}
 * 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.plugin.activerecord.NestedTransactionHelpException}
 * （jfinal 5.2.6）。</p>
 *
 * <p><b>语义：</b>这是事务的"<b>静默回滚</b>"信号。业务在事务体内抛出它，
 * 表示"请回滚，但不要把异常抛给调用方" —— 最外层的 {@link LegacyTx}
 * 捕获它、回滚、记 nothing，然后<b>正常返回</b>。</p>
 *
 * <p><b>两处易错点（均取自字节码）：</b></p>
 * <ol>
 *   <li>构造器是 {@code super(message)}，<b>没有无参构造</b>；</li>
 *   <li>{@link #fillInStackTrace()} <b>覆写为 {@code return this}</b> ——
 *       不采集堆栈。这是有意的性能取舍（该异常只作控制流信号），
 *       故它的 {@code getStackTrace()} 长度为 <b>0</b>。若"顺手"删掉这个覆写，
 *       行为上多出一次堆栈采集，且 {@code getStackTrace()} 不再为空 —— 已由判据钉住。</li>
 * </ol>
 */
public class LegacyNestedTransactionHelpException extends RuntimeException {

    /**
     * 序列化标识：取自旧制品的 {@code ConstantValue}（3813238946083156753L），
     * <b>不是</b>随手写的 1L —— 跨版本反序列化要能对上。
     */
    private static final long serialVersionUID = 3813238946083156753L;

    /**
     * 构造。
     *
     * @param message 消息
     */
    public LegacyNestedTransactionHelpException(String message) {
        super(message);
    }

    /**
     * 不采集堆栈（旧实现即如此）。
     *
     * @return 本对象
     */
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
