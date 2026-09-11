/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

import java.util.ArrayList;
import java.util.List;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.config.Interceptors} 的等价接缝。
 *
 * <p>ported from: com.jfinal.config.Interceptors（jfinal 5.2.6 制品）
 *
 * <p><b>逐条取自旧字节码：</b>三个独立列表与三个 {@code add*} 方法（各自返回 {@code this}）：
 * {@code interceptors}（{@code add}）、{@code globalActionInterceptors}（{@code addGlobalActionInterceptor}）、
 * {@code globalServiceInterceptors}（{@code addGlobalServiceInterceptor}）。
 * EOVA 只用 {@code addGlobalActionInterceptor}（{@code EovaConfig:407}
 * 装入 {@code ExceptionInterceptor}；登录/鉴权两条被注释掉，因为它们挂在 Routes 上）。</p>
 *
 * <p>旧实现<b>不</b>暴露这三个列表的 getter；本接缝补 {@code get*} 供引导驱动与判据读取
 * （属"新增读取口"，已在 {@code MvcFoundationGoldenTest} 的豁免表口径内 —— 见 §r83）。</p>
 */
public final class LegacyInterceptors {

    private final List<LegacyInterceptor> interceptors = new ArrayList<>();

    private final List<LegacyInterceptor> globalActionInterceptors = new ArrayList<>();

    private final List<LegacyInterceptor> globalServiceInterceptors = new ArrayList<>();

    /**
     * 追加普通拦截器。
     *
     * @param interceptor 拦截器
     * @return this
     */
    public LegacyInterceptors add(LegacyInterceptor interceptor) {
        interceptors.add(interceptor);
        return this;
    }

    /**
     * 追加全局 action 拦截器（EOVA 用这个）。
     *
     * @param interceptor 拦截器
     * @return this
     */
    public LegacyInterceptors addGlobalActionInterceptor(LegacyInterceptor interceptor) {
        globalActionInterceptors.add(interceptor);
        return this;
    }

    /**
     * 追加全局 service 拦截器。
     *
     * @param interceptor 拦截器
     * @return this
     */
    public LegacyInterceptors addGlobalServiceInterceptor(LegacyInterceptor interceptor) {
        globalServiceInterceptors.add(interceptor);
        return this;
    }

    /**
     * 取普通拦截器列表。
     *
     * @return 列表
     */
    public List<LegacyInterceptor> getInterceptors() {
        return interceptors;
    }

    /**
     * 取全局 action 拦截器列表。
     *
     * @return 列表
     */
    public List<LegacyInterceptor> getGlobalActionInterceptors() {
        return globalActionInterceptors;
    }

    /**
     * 取全局 service 拦截器列表。
     *
     * @return 列表
     */
    public List<LegacyInterceptor> getGlobalServiceInterceptors() {
        return globalServiceInterceptors;
    }

}
