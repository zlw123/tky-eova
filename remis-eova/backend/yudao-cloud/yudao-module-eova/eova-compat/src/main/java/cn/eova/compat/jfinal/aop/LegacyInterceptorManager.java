/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.aop;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注解驱动的拦截器装配（旧 {@code com.jfinal.aop.InterceptorManager} 的等价接缝）。
 *
 * <p><b>ported from:</b> {@code com.jfinal.aop.InterceptorManager}（jfinal 5.2.6）。
 * 旧 {@code ActionMapping} 在装配 action 时调用
 * {@code InterceptorManager.buildControllerActionInterceptor(routes.getInterceptors(),
 * createControllerInterceptor(controllerClass), controllerClass, method)}
 * —— 也就是说 **`@Before`（类级与方法级）是拦截器链的一部分**。</p>
 *
 * <p>★★ <b>第 305 轮修的真缺陷（P1）</b>：本接缝此前**整块缺失** —— 全仓没有任何地方读
 * {@link LegacyBefore}（`grep LegacyBefore.class` 零命中），{@code LegacyDispatcher} 只拼
 * "全局拦截器 + 路由级拦截器"。后果：
 * <ul>
 *   <li>方法级 {@code @LegacyBefore(LegacyTx.class)}（全仓 24 处）**全部惰性** ⇒ 事务不开启、
 *       回滚标记 {@link cn.eova.compat.jfinal.plugin.activerecord.LegacyNestedTransactionHelpException}
 *       无处被吞 ⇒ 直穿到宿主变成 <b>500</b>。实测 {@code GET /menu/add}：
 *       旧栈返回 {@code {"msg":"新增菜单失败,请仔细查看控制台日志！","state":"fail"}}，
 *       新栈 500（{@code LegacyNestedTransactionHelpException: 新增菜单异常}）。</li>
 *   <li>类级 {@code @LegacyBefore(AdminInterceptor.class)} / {@code (OpsInterceptor.class)}
 *       **全部惰性** ⇒ 运维/超管页面的守卫根本没执行（安全相关）。</li>
 *   <li>{@code @LegacyClear}（{@code AppController} 3 处）同样惰性。</li>
 * </ul>
 *
 * <p><b>顺序与 {@code @Clear} 语义逐条对齐旧字节码</b>
 * （{@code javap -c com.jfinal.aop.InterceptorManager} 的 {@code doBuild}）：
 * <pre>
 *   方法级 @Before  → methodInters
 *   方法级 @Clear：value 为空 ⇒ **只返回 methodInters**；否则记下待清除的类
 *   类级   @Clear：value 为空 ⇒ 路由级与控制器级都置空；否则记下待清除的类
 *   组装：global → routes →（类级 Clear 过滤）→ controller →（方法级 Clear 过滤）→ methodInters
 * </pre></p>
 *
 * <p><b>实例缓存</b>：旧实现用 {@code singletonMap} 按类缓存拦截器实例（同一类在前后的调用里是同一对象），
 * 本接缝照做 —— 部分拦截器有状态（如 {@code AdminInterceptor} 的会话计数），换实例会改变行为。</p>
 */
public final class LegacyInterceptorManager {

    /** 空拦截器数组（旧实现同名常量） */
    public static final LegacyInterceptor[] NULL_INTERS = new LegacyInterceptor[0];

    /** 按类缓存的单例（旧 {@code singletonMap}） */
    private static final Map<Class<?>, LegacyInterceptor> SINGLETON = new ConcurrentHashMap<>();

    private LegacyInterceptorManager() {
    }

    /**
     * 取类级 {@code @Before} 声明的拦截器实例（旧 {@code createControllerInterceptor}）。
     *
     * @param controllerClass 控制器类
     * @return 拦截器数组；无注解时返回 {@link #NULL_INTERS}
     */
    public static LegacyInterceptor[] createControllerInterceptor(Class<?> controllerClass) {
        return createInterceptors(controllerClass == null ? null : controllerClass.getAnnotation(LegacyBefore.class));
    }

    /**
     * 组装一个 action 的完整拦截器链（旧 {@code buildControllerActionInterceptor} → {@code doBuild}）。
     *
     * @param globalInters     全局拦截器（旧 {@code globalActionInters}）
     * @param routesInters     路由级拦截器（旧 {@code routes.getInterceptors()}）
     * @param controllerInters 类级 {@code @Before}（用 {@link #createControllerInterceptor(Class)} 得到）
     * @param controllerClass  控制器类（读类级 {@code @Clear}）
     * @param method           action 方法（读方法级 {@code @Before} 与 {@code @Clear}）
     * @return 合并后的链；**顺序即执行顺序**
     */
    public static LegacyInterceptor[] buildControllerActionInterceptor(LegacyInterceptor[] globalInters,
                                                                      LegacyInterceptor[] routesInters,
                                                                      LegacyInterceptor[] controllerInters,
                                                                      Class<?> controllerClass,
                                                                      Method method) {
        LegacyInterceptor[] globals = globalInters == null ? NULL_INTERS : globalInters;
        LegacyInterceptor[] routes = routesInters == null ? NULL_INTERS : routesInters;
        LegacyInterceptor[] controllers = controllerInters == null ? NULL_INTERS : controllerInters;

        LegacyInterceptor[] methodInters =
                createInterceptors(method == null ? null : method.getAnnotation(LegacyBefore.class));

        // 方法级 @Clear：value 为空 ⇒ 只剩方法级拦截器（旧实现直接 return methodInters）
        Class<?>[] methodClear = null;
        LegacyClear mc = method == null ? null : method.getAnnotation(LegacyClear.class);
        if (mc != null) {
            if (mc.value().length == 0) {
                return methodInters;
            }
            methodClear = mc.value();
        }

        // 类级 @Clear：value 为空 ⇒ 路由级与控制器级都置空
        Class<?>[] classClear = null;
        LegacyClear cc = controllerClass == null ? null : controllerClass.getAnnotation(LegacyClear.class);
        if (cc != null) {
            if (cc.value().length == 0) {
                routes = NULL_INTERS;
                controllers = NULL_INTERS;
            } else {
                classClear = cc.value();
            }
        }

        List<LegacyInterceptor> chain = new ArrayList<>(
                globals.length + routes.length + controllers.length + methodInters.length);
        for (LegacyInterceptor i : globals) {
            chain.add(i);
        }
        for (LegacyInterceptor i : routes) {
            chain.add(i);
        }
        if (classClear != null && classClear.length > 0) {
            removeInterceptors(chain, classClear);
        }
        for (LegacyInterceptor i : controllers) {
            chain.add(i);
        }
        if (methodClear != null && methodClear.length > 0) {
            removeInterceptors(chain, methodClear);
        }
        for (LegacyInterceptor i : methodInters) {
            chain.add(i);
        }
        return chain.toArray(NULL_INTERS);
    }

    /**
     * 把 {@code @Before} 里声明的拦截器类实例化（单例缓存；旧 {@code createInterceptor}）。
     *
     * @param before 注解；为 null 时返回空数组
     * @return 拦截器数组
     */
    private static LegacyInterceptor[] createInterceptors(LegacyBefore before) {
        if (before == null || before.value().length == 0) {
            return NULL_INTERS;
        }
        Class<? extends LegacyInterceptor>[] classes = before.value();
        LegacyInterceptor[] out = new LegacyInterceptor[classes.length];
        for (int i = 0; i < classes.length; i++) {
            Class<? extends LegacyInterceptor> cls = classes[i];
            LegacyInterceptor cached = SINGLETON.get(cls);
            if (cached == null) {
                try {
                    cached = cls.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("拦截器实例化失败：" + cls.getName(), e);
                }
                SINGLETON.put(cls, cached);
            }
            out[i] = cached;
        }
        return out;
    }

    /**
     * 从链中移除指定类的实例（旧 {@code removeInterceptor}）。
     *
     * @param chain   链
     * @param classes 要移除的拦截器类
     */
    private static void removeInterceptors(List<LegacyInterceptor> chain, Class<?>[] classes) {
        chain.removeIf(inter -> {
            for (Class<?> c : classes) {
                if (inter.getClass() == c) {
                    return true;
                }
            }
            return false;
        });
    }
}
