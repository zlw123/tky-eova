/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.aop;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 注解驱动拦截器装配的判据（第 305 轮 · 真缺陷 P1 回归锁）。
 *
 * <p><b>缺陷现场</b>：本接缝此前整块缺失 —— 全仓没有任何地方读 {@link LegacyBefore}
 * （grep LegacyBefore.class 零命中），{@code LegacyDispatcher} 只拼“全局 + 路由级”。
 * 后果：方法级 {@code @LegacyBefore(LegacyTx.class)}（全仓 24 处）与类级
 * {@code @LegacyBefore(AdminInterceptor/OpsInterceptor.class)} <b>全部惰性</b> ⇒
 * 事务不开启 ⇒ 回滚标记 {@code LegacyNestedTransactionHelpException} 直穿成 500
 * （实测 {@code GET /menu/add}：旧栈 fail JSON、新栈 500）。</p>
 *
 * <p><b>顺序与 {@code @Clear} 语义逐条对齐旧字节码</b>
 * （{@code javap -c com.jfinal.aop.InterceptorManager} 的 {@code doBuild}）：
 * global → routes →（类级 Clear 过滤）→ controller →（方法级 Clear 过滤）→ methodInters；
 * 方法级 {@code @Clear} 空值 ⇒ 只返回方法级；类级 {@code @Clear} 空值 ⇒ 路由级与控制器级都置空。</p>
 */
class LegacyInterceptorManagerGoldenTest {

    /** 无状态夹具拦截器 A */
    public static class A implements LegacyInterceptor {
        @Override
        public void intercept(LegacyInvocation inv) {
            inv.invoke();
        }
    }

    /** 无状态夹具拦截器 B */
    public static class B implements LegacyInterceptor {
        @Override
        public void intercept(LegacyInvocation inv) {
            inv.invoke();
        }
    }

    /** 无状态夹具拦截器 C */
    public static class C implements LegacyInterceptor {
        @Override
        public void intercept(LegacyInvocation inv) {
            inv.invoke();
        }
    }

    /** 全局夹具（G） */
    public static class G extends A {
    }

    /** 路由级夹具（R） */
    public static class R extends A {
    }

    /** 类级 @Before({A, B}) 的夹具控制器 */
    @LegacyBefore({A.class, B.class})
    public static class Fixture {
        /** 方法级 @Before(C) */
        @LegacyBefore(C.class)
        public void withMethodBefore() {
        }

        /** 方法级 @Clear（空值 ⇒ 只剩方法级） */
        @LegacyBefore(C.class)
        @LegacyClear
        public void withEmptyClear() {
        }

        /** 方法级 @Clear({B.class})：移除类级带进来的 B */
        @LegacyClear({B.class})
        public void withClearOfB() {
        }
    }

    /** 类级 @Clear（空值 ⇒ 路由级与控制器级都置空）的夹具 */
    @LegacyBefore({A.class})
    @LegacyClear
    public static class ClearedFixture {
        /** 方法级 @Before(C) */
        @LegacyBefore(C.class)
        public void m() {
        }
    }

    /**
     * 取夹具方法
     *
     * @param cls  类
     * @param name 方法名
     * @return 方法
     * @throws Exception 找不到时
     */
    private static Method m(Class<?> cls, String name) throws Exception {
        return cls.getMethod(name);
    }

    /**
     * 取链上的类名序列（便于逐项断言顺序）
     *
     * @param chain 链
     * @return 逗号分隔的简单类名
     */
    private static String names(LegacyInterceptor[] chain) {
        return Arrays.stream(chain).map(i -> i.getClass().getSimpleName())
                .reduce((a, b) -> a + "," + b).orElse("");
    }

    @Test
    @DisplayName("顺序：global → routes → 类级 @Before → 方法级 @Before（旧 doBuild 的组装顺序）")
    void orderMatchesOldDoBuild() throws Exception {
        LegacyInterceptor[] chain = LegacyInterceptorManager.buildControllerActionInterceptor(
                new LegacyInterceptor[]{new G()}, new LegacyInterceptor[]{new R()},
                LegacyInterceptorManager.createControllerInterceptor(Fixture.class),
                Fixture.class, m(Fixture.class, "withMethodBefore"));
        assertEquals("G,R,A,B,C", names(chain),
                "全局与路由级在前、类级居中、方法级最后 —— 顺序即执行顺序，不得重排");
    }

    @Test
    @DisplayName("方法级 @Clear（空值）⇒ 只返回方法级拦截器（旧实现直接 return methodInters）")
    void emptyMethodClearKeepsOnlyMethodLevel() throws Exception {
        LegacyInterceptor[] chain = LegacyInterceptorManager.buildControllerActionInterceptor(
                new LegacyInterceptor[]{new G()}, new LegacyInterceptor[]{new R()},
                LegacyInterceptorManager.createControllerInterceptor(Fixture.class),
                Fixture.class, m(Fixture.class, "withEmptyClear"));
        assertEquals("C", names(chain), "空 @Clear 的方法级语义是：清掉其它、只留本方法的");
    }

    @Test
    @DisplayName("类级 @Clear（空值）⇒ 路由级与控制器级都置空（全局与方法级仍在）")
    void emptyClassClearWipesRoutesAndController() throws Exception {
        LegacyInterceptor[] chain = LegacyInterceptorManager.buildControllerActionInterceptor(
                new LegacyInterceptor[]{new G()}, new LegacyInterceptor[]{new R()},
                LegacyInterceptorManager.createControllerInterceptor(ClearedFixture.class),
                ClearedFixture.class, m(ClearedFixture.class, "m"));
        assertEquals("G,C", names(chain),
                "类级空 @Clear 只清路由级与类级（全局保留），方法级 @Before 仍生效");
    }

    @Test
    @DisplayName("@Clear({某类}) ⇒ 该类实例被从链上移除（旧 removeInterceptor 语义）")
    void clearListRemovesThatInterceptor() throws Exception {
        LegacyInterceptor[] chain = LegacyInterceptorManager.buildControllerActionInterceptor(
                new LegacyInterceptor[]{new G()}, new LegacyInterceptor[]{new R()},
                LegacyInterceptorManager.createControllerInterceptor(Fixture.class),
                Fixture.class, m(Fixture.class, "withClearOfB"));
        assertEquals("G,R,A", names(chain), "B 必须被移除，其余保持原顺序");
    }

    @Test
    @DisplayName("实例按类缓存：同一拦截器类在多条 action 上必须是同一实例（旧 singletonMap）")
    void interceptorInstancesAreCachedPerClass() {
        LegacyInterceptor[] one = LegacyInterceptorManager.createControllerInterceptor(Fixture.class);
        LegacyInterceptor[] two = LegacyInterceptorManager.createControllerInterceptor(Fixture.class);
        assertEquals(one[0], two[0], "同类拦截器必须复用实例（部分拦截器有状态）");
    }
}
