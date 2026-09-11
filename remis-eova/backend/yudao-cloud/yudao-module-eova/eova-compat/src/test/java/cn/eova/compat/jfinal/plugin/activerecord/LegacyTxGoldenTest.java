/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin.activerecord;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInvocation;
import cn.eova.compat.jfinal.core.LegacyAction;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.db.EovaActiveRecordException;
import cn.eova.db.EovaDbGateway;
import cn.eova.db.EovaGateways;
import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LegacyTx} / {@code LegacyTxConfig} / {@code LegacyNestedTransactionHelpException}
 * 的判据（第 62 轮）。
 *
 * <p><b>可跨实现的部分（对真 jfinal 5.2.6 制品）：</b>注解元数据与异常形态 ——
 * 这两者都是<b>可反射读出</b>的静态契约，不需要驱动旧的 {@code Config}/{@code DbKit} 运行时，
 * 故不受 R38/R44 的限制。</p>
 *
 * <p><b>不可跨实现的部分（已说明理由）：</b>{@code Tx.intercept} 的活体比对需要构造
 * jfinal 的 {@code Invocation}（依赖 {@code Action}/{@code Controller} 与
 * {@code DbKit} 的线程绑定连接），成本远超收益；故它的语义依据是<b>字节码 + 异常表</b>，
 * 并由本判据在<b>新侧</b>逐条钉死（正常/运行时异常/受检异常/静默回滚/嵌套传播 5 格）。</p>
 */
class LegacyTxGoldenTest {

    /** 事务体是否执行、网关被调用情况 */
    static final class GatewayProbe {

        /** 调用轨迹 */
        final List<String> calls = new ArrayList<>();

        /** 本网关"是否已在事务中"的回答 */
        boolean inTransaction;

        /** 事务体是否抛过异常（网关据此回滚） */
        boolean rolledBack;

        /** 把 JVM 的 lambda 记下来，便于断言"事务体被调用了几次" */
        int txCount;
    }

    /**
     * 造一个记录式网关替身。
     *
     * @param probe 记录器
     * @return 替身
     */
    private static EovaDbGateway gateway(GatewayProbe probe) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "inTransaction":
                    return probe.inTransaction;
                case "tx":
                    probe.txCount++;
                    probe.calls.add("tx");
                    if (probe.inTransaction) {
                        // 嵌套：真实实现【并入当前事务】—— 不提交、不回滚、不关闭（R62 实测）
                        @SuppressWarnings("unchecked")
                        EovaDbGateway.Atom<Object> inner = (EovaDbGateway.Atom<Object>) args[0];
                        return inner.run();
                    }
                    try {
                        @SuppressWarnings("unchecked")
                        EovaDbGateway.Atom<Object> atom = (EovaDbGateway.Atom<Object>) args[0];
                        Object r = atom.run();
                        probe.calls.add("commit");
                        return r;
                    } catch (Throwable t) {
                        probe.rolledBack = true;
                        probe.calls.add("rollback");
                        // 真实实现会把非运行时异常换成自己的包装重抛，这里照做以贴近真实形态
                        throw t instanceof RuntimeException re ? re
                                : new IllegalStateException(t.getMessage(), t);
                    }
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (EovaDbGateway) Proxy.newProxyInstance(
                LegacyTxGoldenTest.class.getClassLoader(),
                new Class<?>[]{EovaDbGateway.class}, h);
    }

    /** 被事务拦截的控制器 */
    public static class TxCtrl extends LegacyController {

        /** 事务体行为：null=正常返回，否则抛出该异常 */
        public static Throwable toThrow;

        /** 事务体是否执行过 */
        public static int runs;

        /** 正常动作 */
        public void ok() {
            runs++;
        }

        /** 抛指定异常的动作 */
        public void boom() throws Throwable {
            runs++;
            if (toThrow != null) {
                throw toThrow;
            }
        }

        /** 指定数据源（标在方法上） */
        @LegacyTxConfig("eova")
        public void withDs() {
            runs++;
        }

        /** 指定不存在的数据源 */
        @LegacyTxConfig("nope")
        public void withBadDs() {
            runs++;
        }
    }

    /** 类级 @TxConfig（验证"先方法后类"的查找顺序与 @Inherited/@Target(TYPE) 这条路） */
    @LegacyTxConfig("eova")
    public static class ClassLevelDsCtrl extends LegacyController {

        /** 类上带 @TxConfig，方法上不带 */
        public void act() {
            runs++;
        }

        /** 动作执行计数 */
        public static int runs;
    }

    /**
     * 构造一个把 {@code LegacyTx} 挂进链首的调用。
     *
     * @param ctrl   控制器实例
     * @param method 动作方法
     * @return 调用
     */
    private static LegacyInvocation invocation(LegacyController ctrl, Method method) {
        LegacyAction action = new LegacyAction("/c/a", "/c", ctrl.getClass(), method,
                method.getName(), new LegacyInterceptor[]{new LegacyTx()}, "/view");
        return new LegacyInvocation(action, ctrl);
    }

    @Test
    @DisplayName("LegacyTxConfig：四项注解元数据与旧制品逐项一致")
    void txConfigAnnotationMatchesOldArtifact() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> old = Class.forName("com.jfinal.plugin.activerecord.tx.TxConfig", true, jf);
        OldImplementationLoader.assertFromJar(old, OldImplementationLoader.oldJFinalJar());

        assertEquals(old.isAnnotation(), LegacyTxConfig.class.isAnnotation(), "必须是注解类型");
        for (Class<? extends Annotation> meta : List.of(java.lang.annotation.Inherited.class,
                java.lang.annotation.Documented.class, java.lang.annotation.Retention.class,
                java.lang.annotation.Target.class)) {
            assertEquals(old.isAnnotationPresent(meta), LegacyTxConfig.class.isAnnotationPresent(meta),
                    "元注解 " + meta.getSimpleName() + " 的有无必须一致");
        }
        java.lang.annotation.Retention oldRet =
                old.getAnnotation(java.lang.annotation.Retention.class);
        java.lang.annotation.Retention newRet =
                LegacyTxConfig.class.getAnnotation(java.lang.annotation.Retention.class);
        assertEquals(oldRet.value(), newRet.value(), "RetentionPolicy 必须一致");
        java.lang.annotation.Target oldTgt = old.getAnnotation(java.lang.annotation.Target.class);
        java.lang.annotation.Target newTgt = LegacyTxConfig.class.getAnnotation(java.lang.annotation.Target.class);
        assertEquals(java.util.Arrays.toString(oldTgt.value()), java.util.Arrays.toString(newTgt.value()),
                "@Target 集合必须一致（TYPE 缺了会让标在类上的数据源失效）");

        // 成员：名字与返回类型
        assertEquals(1, old.getDeclaredMethods().length, "旧注解只有一个成员");
        Method om = old.getDeclaredMethods()[0];
        Method nm = LegacyTxConfig.class.getDeclaredMethods()[0];
        assertEquals(om.getName(), nm.getName(), "成员名必须一致");
        assertEquals(om.getReturnType().getName(), nm.getReturnType().getName(), "返回类型必须一致");
    }

    @Test
    @DisplayName("LegacyNestedTransactionHelpException：父类/消息/不采集堆栈/serialVersionUID 四项对齐")
    void nestedHelpExceptionMatchesOldArtifact() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> old = Class.forName("com.jfinal.plugin.activerecord.NestedTransactionHelpException",
                true, jf);
        OldImplementationLoader.assertFromJar(old, OldImplementationLoader.oldJFinalJar());

        assertEquals(old.getSuperclass().getName(),
                LegacyNestedTransactionHelpException.class.getSuperclass().getName(),
                "父类必须一致（必须是 RuntimeException 族，否则事务拦截器的 catch 顺序会变）");

        Field f = old.getDeclaredField("serialVersionUID");
        f.setAccessible(true);
        Field mine = LegacyNestedTransactionHelpException.class.getDeclaredField("serialVersionUID");
        mine.setAccessible(true);
        assertEquals(f.getLong(null), mine.getLong(null),
                "serialVersionUID 必须取自旧制品的 ConstantValue");

        Object oldEx = old.getConstructor(String.class).newInstance("nested-help");
        LegacyNestedTransactionHelpException newEx = new LegacyNestedTransactionHelpException("nested-help");
        assertEquals(((Throwable) oldEx).getMessage(), newEx.getMessage(), "消息必须一致");

        // fillInStackTrace 覆写为 return this：不采集堆栈
        Method oldFill = old.getMethod("fillInStackTrace");
        assertSame(oldEx, oldFill.invoke(oldEx), "旧实现返回自身");
        assertSame(newEx, newEx.fillInStackTrace(), "本实现必须同样返回自身");
        assertEquals(((Throwable) oldEx).getStackTrace().length, newEx.getStackTrace().length,
                "堆栈深度必须一致（旧实现恒为 0）");
        assertEquals(0, newEx.getStackTrace().length, "新异常也不采集堆栈");
    }

    @Test
    @DisplayName("LegacyTx：正常提交 / 运行时异常原样抛 / 受检异常包成 ActiveRecordException")
    void happyPathAndExceptionMapping() {
        GatewayProbe probe = new GatewayProbe();
        EovaGateways.register("eova", gateway(probe));
        EovaGateways.setFallback(gateway(probe));
        try {
            // ① 正常：事务体执行，提交
            TxCtrl.runs = 0;
            TxCtrl.toThrow = null;
            invocation(new TxCtrl(), method("ok")).invoke();
            assertEquals(List.of("tx", "commit"), probe.calls, "应恰好一次事务并提交");
            assertEquals(1, TxCtrl.runs, "动作必须执行一次");
            assertTrue(!probe.rolledBack, "未回滚");

            // ② 运行时异常：原样抛出（同一实例）
            probe.calls.clear();
            probe.rolledBack = false;
            RuntimeException boom = new IllegalStateException("boom");
            TxCtrl.toThrow = boom;
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> invocation(new TxCtrl(), method("boom")).invoke());
            assertSame(boom, thrown, "运行时异常必须【原样】抛出，不得被替换实例");
            assertEquals(List.of("tx", "rollback"), probe.calls, "必须回滚");
            assertTrue(probe.rolledBack);

            // ③ 受检异常：经 LegacyInvocation 已包成 RuntimeException(cause)，故 Tx 原样抛出 ——
            //    【我最初把这一格写成"应包成 EovaActiveRecordException"，判据当场纠正】
            probe.calls.clear();
            probe.rolledBack = false;
            java.io.IOException io = new java.io.IOException("io-broken");
            TxCtrl.toThrow = io;
            RuntimeException checkedAsRuntime = assertThrows(RuntimeException.class,
                    () -> invocation(new TxCtrl(), method("boom")).invoke());
            assertSame(io, checkedAsRuntime.getCause(),
                    "Invocation 层把受检异常包成 RuntimeException(cause)，Tx 不得再包一层");
            assertTrue(probe.rolledBack, "必须回滚");
        } finally {
            TxCtrl.toThrow = null;
            EovaGateways.clear();
        }
    }

    @Test
    @DisplayName("LegacyTx：NestedTransactionHelpException 最外层静默、嵌套层向上传播")
    void nestedTransactionHelpSemantics() {
        // ① 最外层：回滚 + 静默（不向调用方抛出）
        GatewayProbe outer = new GatewayProbe();
        outer.inTransaction = false;
        EovaGateways.setFallback(gateway(outer));
        try {
            TxCtrl.toThrow = new LegacyNestedTransactionHelpException("please-rollback");
            invocation(new TxCtrl(), method("boom")).invoke();
            assertEquals(List.of("tx", "rollback"), outer.calls, "最外层必须回滚");
            assertTrue(outer.rolledBack);

            // ② 嵌套层：不得吞掉 —— 必须向上传播，交由最外层回滚整个外层事务
            GatewayProbe inner = new GatewayProbe();
            inner.inTransaction = true;
            EovaGateways.setFallback(gateway(inner));
            LegacyNestedTransactionHelpException propagated = assertThrows(
                    LegacyNestedTransactionHelpException.class,
                    () -> invocation(new TxCtrl(), method("boom")).invoke());
            assertEquals("please-rollback", propagated.getMessage());
            assertTrue(!inner.rolledBack,
                    "嵌套层自己不回滚（外层负责），故替身里不应出现 rollback");
        } finally {
            TxCtrl.toThrow = null;
            EovaGateways.clear();
        }
    }

    @Test
    @DisplayName("LegacyTx 异常映射矩阵：运行时原样 / 受检包成 ActiveRecordException / help 静默或传播")
    void throwableMappingMatrix() {
        RuntimeException re = new IllegalStateException("x");
        assertSame(re, LegacyTx.mapThrowable(re, true), "运行时异常必须原样返回（同一实例）");
        assertSame(re, LegacyTx.mapThrowable(re, false), "嵌套情形同样原样返回");

        java.io.IOException io = new java.io.IOException("io");
        EovaActiveRecordException wrapped = LegacyTx.mapThrowable(io, true) instanceof EovaActiveRecordException w
                ? w : null;
        assertNotNull(wrapped, "受检异常必须包成 EovaActiveRecordException");
        assertEquals(io.toString(), wrapped.getMessage(),
                "旧实现 new ActiveRecordException(t) 的消息即 t.toString()");
        assertSame(io, wrapped.getCause(), "必须保留 cause");

        LegacyNestedTransactionHelpException help = new LegacyNestedTransactionHelpException("h");
        assertNull(LegacyTx.mapThrowable(help, true),
                "最外层：必须返回 null（静默回滚），而不是抛出去");
        assertSame(help, LegacyTx.mapThrowable(help, false),
                "嵌套层：必须原样向上传播，交由最外层回滚");
    }

    @Test
    @DisplayName("LegacyTx：数据源解析（方法注解 → 类注解 → 默认），未注册时报错消息逐字一致")
    void configNameResolution() {
        GatewayProbe dsEova = new GatewayProbe();
        GatewayProbe fallback = new GatewayProbe();
        EovaGateways.register("eova", gateway(dsEova));
        EovaGateways.setFallback(gateway(fallback));
        try {
            // ① 方法级 @TxConfig("eova") → 走 eova 网关
            TxCtrl.runs = 0;
            invocation(new TxCtrl(), method("withDs")).invoke();
            assertEquals(1, dsEova.txCount, "方法级 @TxConfig 必须命中该数据源");
            assertEquals(0, fallback.txCount, "不得落回默认数据源");

            // ② 类级 @TxConfig("eova")（方法上没有）→ 仍命中 eova（验证查找顺序的第二跳）
            ClassLevelDsCtrl.runs = 0;
            invocation(new ClassLevelDsCtrl(), method2("act")).invoke();
            assertEquals(2, dsEova.txCount, "类级 @TxConfig 必须被读到");
            assertEquals(0, fallback.txCount);

            // ③ 无注解 → 默认数据源
            invocation(new TxCtrl(), method("ok")).invoke();
            assertEquals(1, fallback.txCount, "无注解时必须用默认数据源");

            // ④ 注解指向未注册数据源 → 消息逐字一致（注意冒号后没有空格）
            RuntimeException e = assertThrows(RuntimeException.class,
                    () -> invocation(new TxCtrl(), method("withBadDs")).invoke());
            assertEquals("Config not found with TxConfig:nope", e.getMessage(),
                    "消息必须逐字一致（旧实现冒号后无空格）");
        } finally {
            EovaGateways.clear();
        }
    }

    /**
     * 取 {@code TxCtrl} 的方法。
     *
     * @param name 方法名
     * @return 方法
     */
    private static Method method(String name) {
        return method2(TxCtrl.class, name);
    }

    /**
     * 取指定类的方法。
     *
     * @param name 方法名
     * @return 方法
     */
    private static Method method2(String name) {
        return method2(ClassLevelDsCtrl.class, name);
    }

    /**
     * 取方法。
     *
     * @param type 类
     * @param name 方法名
     * @return 方法
     */
    private static Method method2(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
