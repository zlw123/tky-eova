/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin.activerecord;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInvocation;
import cn.eova.compat.jfinal.kit.LegacyLogKit;
import cn.eova.db.EovaActiveRecordException;
import cn.eova.db.EovaDbGateway;
import cn.eova.db.EovaGateways;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.plugin.activerecord.tx.Tx} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.plugin.activerecord.tx.Tx}（jfinal 5.2.6）。</p>
 *
 * <p><b>旧栈用法（EOVA 全树实测）：</b>动作方法上标注 {@code @Before(Tx.class)}，
 * 需要指定数据源时再加 {@code @TxConfig(Ds.EOVA)}；共 6 个单元使用，
 * 例如 {@code MetaController}(8 处)、{@code AuthController}、{@code ButtonController}、
 * {@code HomeController}。</p>
 *
 * <p><b>逐条取自旧字节码（含<b>完整异常表</b>）的语义：</b></p>
 * <ol>
 *   <li><b>数据源查找顺序</b>：<b>先方法</b>（{@code inv.getMethod().getAnnotation(TxConfig.class)}）
 *       <b>后目标类</b>（{@code inv.getTarget().getClass().getAnnotation(TxConfig.class)}）；
 *       两者都没有则用默认数据源。</li>
 *   <li>标注了 {@code @TxConfig} 但该数据源<b>不存在</b>时抛
 *       {@code RuntimeException("Config not found with TxConfig:" + value)} ——
 *       注意<b>冒号后无空格</b>（javap 注释里看不出来，靠常量池读出）。</li>
 *   <li><b>最外层与嵌套层的行为不同</b>（旧：{@code config.getThreadLocalConnection() == null} 判别）：
 *       <ul>
 *         <li>最外层：取连接 → 记 autoCommit → 绑线程 → 设隔离级别 → autoCommit=false
 *             → 执行 → 提交；异常则回滚；最后恢复 autoCommit 并关闭连接；</li>
 *         <li>嵌套层：<b>只把隔离级别提到不低于外层，然后执行，不提交也不关闭</b>
 *             —— 参与外层事务。</li>
 *       </ul></li>
 *   <li>{@link LegacyNestedTransactionHelpException}：<b>由最外层捕获</b> → 回滚 →
 *       {@code LogKit.logNothing} → <b>静默返回</b>（不向调用方抛出）。
 *       嵌套层<b>不捕获</b>它，任其向上传播给最外层 —— 所以"内层吞掉"是错的
 *       （那会让外层照常提交）。本接缝据此用 {@link EovaDbGateway#inTransaction()}
 *       判别层级。</li>
 *   <li>其他异常：{@code RuntimeException} <b>原样抛出</b>；非运行时异常
 *       <b>包成</b> {@code ActiveRecordException}（本接缝包成
 *       {@link EovaActiveRecordException}，消息取 {@code cause.toString()}，
 *       与旧 {@code super(cause)} 的取值一致，并额外保留 cause）。</li>
 * </ol>
 *
 * <p><b>已声明的适配（2 处）：</b></p>
 * <ol>
 *   <li>事务本身委托给 {@link EovaDbGateway#tx}（§4：唯一允许接触 JDBC/事务的边界）——
 *       其实现已含"已在事务中则并入当前事务"的判据，与旧的线程绑定连接等价。</li>
 *   <li>旧 {@code TxFun} 钩子（{@code setTxFun}/{@code getTxFun}）<b>不实现</b>：
 *       EOVA 全树使用数为 0，且它要求把 {@code java.sql.Connection} 交给业务代码，
 *       与 §4 的边界冲突。登记为已声明待办，而非留空壳。</li>
 * </ol>
 */
public class LegacyTx implements LegacyInterceptor {

    /**
     * 解析本条调用应使用的数据源名（旧 {@code getConfigByTxConfig}）。
     *
     * <p>查找顺序：方法注解 → 目标类注解；命中但数据源未注册时抛错（消息逐字对齐旧实现）。</p>
     *
     * @param inv 调用
     * @return 数据源名；未标注 {@code @TxConfig} 时返回 {@code null}（表示用默认数据源）
     */
    public static String getConfigNameByTxConfig(LegacyInvocation inv) {
        LegacyTxConfig tc = inv.getMethod().getAnnotation(LegacyTxConfig.class);
        if (tc == null) {
            Object target = inv.getTarget();
            if (target != null) {
                tc = target.getClass().getAnnotation(LegacyTxConfig.class);
            }
        }
        if (tc == null) {
            return null;
        }
        if (EovaGateways.find(tc.value()) == null) {
            // 消息逐字对齐旧实现：冒号后【没有】空格
            throw new RuntimeException("Config not found with TxConfig:" + tc.value());
        }
        return tc.value();
    }

    /**
     * 事务拦截：把整条 action 调用包进一个事务。
     *
     * @param inv 调用
     */
    @Override
    public void intercept(LegacyInvocation inv) {
        String configName = getConfigNameByTxConfig(inv);
        EovaDbGateway gateway = configName != null ? EovaGateways.find(configName) : EovaGateways.fallback();
        if (gateway == null) {
            throw new IllegalStateException(
                    "未注册数据源网关（数据源=" + configName + "）—— 请由 eova-db-adapter 在启动时注入");
        }

        // 用私有包装类型护送【原始】可抛物穿过网关：网关对非运行时异常会换成自己的包装，
        // 而旧实现要求"非运行时异常包成 ActiveRecordException / 运行时异常原样抛出"，
        // 故必须在网关之外还能拿到原始异常。
        boolean outermost = !gateway.inTransaction();
        try {
            gateway.tx(() -> {
                try {
                    inv.invoke();
                    return Boolean.TRUE;
                } catch (Throwable t) {
                    throw new BodyThrowable(t);
                }
            });
        } catch (BodyThrowable w) {
            RuntimeException mapped = mapThrowable(w.original, outermost);
            if (mapped == null) {
                // 最外层遇到"静默回滚"信号：回滚已由网关完成，此处不抛（旧实现 LogKit.logNothing）
                LegacyLogKit.logNothing(w.original);
                return;
            }
            throw mapped;
        }
    }

    /**
     * 按旧实现规则映射事务体内抛出的可抛物（<b>抽成独立方法以便逐格判据覆盖</b>）。
     *
     * <p><b>为什么要能单测：</b>"受检异常 → {@code ActiveRecordException}"这条分支
     * <b>经由 action 不可达</b> —— {@code LegacyInvocation.invoke()} 已先把它包成
     * {@code RuntimeException(cause)}（旧语义如此）。但它对"事务体内其他来源的可抛物"
     * 仍然生效，且是旧实现的显式分支，故必须保住并可直接验证。</p>
     *
     * @param t         事务体内抛出的原始可抛物
     * @param outermost 是否为最外层事务
     * @return 应向上抛出的异常；<b>返回 {@code null} 表示静默吞掉</b>
     */
    static RuntimeException mapThrowable(Throwable t, boolean outermost) {
        if (t instanceof LegacyNestedTransactionHelpException) {
            // 最外层吞掉（静默回滚）；嵌套层向上传播，交由最外层回滚整个外层事务
            return outermost ? null : (LegacyNestedTransactionHelpException) t;
        }
        if (t instanceof RuntimeException re) {
            return re;
        }
        return new EovaActiveRecordException(t.toString(), t);
    }

    /** 护送原始可抛物穿过网关的私有包装（非旧 API，仅本类内部使用） */
    private static final class BodyThrowable extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /** 原始可抛物 */
        private final Throwable original;

        /**
         * 构造。
         *
         * @param original 原始可抛物
         */
        private BodyThrowable(Throwable original) {
            super(original);
            this.original = original;
        }
    }
}
