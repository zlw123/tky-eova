/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

import java.util.ArrayList;
import java.util.List;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.jfinal.kit.LegacyStrKit;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.config.Routes} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.config.Routes} +
 * {@code com.jfinal.config.Routes$Route}（jfinal 5.2.6）。</p>
 *
 * <p><b>逐条取自旧字节码的语义（7 处易错点，均已由跨实现判据逐格对照）：</b></p>
 * <ol>
 *   <li>{@code add(String,Class)} 等价于 {@code add(path, cls, path)} ——
 *       <b>viewPath 默认取 controllerPath 本身</b>，不是 {@code null}/{@code "/"}。
 *       这是我最初想当然会写错的地方。</li>
 *   <li>{@code Route} 构造：{@code controllerPath} 空白 ⇒
 *       {@code IllegalArgumentException("controllerPath can not be blank")}；
 *       {@code controllerClass == null} ⇒
 *       {@code IllegalArgumentException("controllerClass can not be null")}；
 *       {@code viewPath} 空白 ⇒ <b>先归一成 {@code "/"}</b> 再走 processViewPath。</li>
 *   <li>{@code processControllerPath}：trim + 缺前导 {@code "/"} 则补。</li>
 *   <li>{@code processViewPath}：trim + 补前导 {@code "/"} + <b>补尾部 {@code "/"}</b>。
 *       与 {@link #setBaseViewPath} <b>相反</b>（后者<b>去掉</b>尾部斜杠）——
 *       两者不可"统一"。</li>
 *   <li>{@code getControllerKey()} 直接返回 {@code controllerPath}（无任何变换）。</li>
 *   <li>{@code getInterceptors()}：非空时返回新数组；<b>为空时返回共享的
 *       {@link #NULL_INTERS} 常量</b>（旧为 {@code InterceptorManager.NULL_INTERS}）。</li>
 *   <li>{@code clear()} 只有在 {@code clearAfterMapping} 为真时才生效，
 *       且会把<b>静态的</b> {@code routesList} 置为 {@code null}
 *       （于是 {@code getRoutesList()} 返回 null、后续 {@code add(Routes)} 会 NPE）——
 *       <b>既有语义，不得"顺手"改成清空列表</b>。</li>
 * </ol>
 *
 * <p><b>已声明的适配（1 处）：</b>旧 {@code addInterceptor} 在
 * {@code AopManager.me().isInjectDependency()} 为真时先做 {@code Aop.inject(interceptor)}
 * （jfinal 自带的字段注入）。新栈的依赖注入由 Spring 容器承担，故本接缝省略该步。
 * <b>依据：</b>EOVA 全树对 {@code @Inject}/{@code AopManager}/{@code Aop.inject}
 * 的使用数<b>为 0</b>（普查脚本见本轮记录），故该步骤在 EOVA 中从无可观测行为；
 * 若将来引入使用 jfinal {@code @Inject} 的第三方模块，须改为显式装配并重新评估。</p>
 *
 * <p><b>未实现的成员（显式声明，非静默留空）：</b>{@code scan(String)} /
 * {@code scan(String, Predicate)}（类路径扫描）—— EOVA 全树调用数为 <b>0</b>；
 * 实现它需要复刻 jfinal 的目录/ jar 扫描与包名匹配规则，属独立单元，
 * 故登记为已声明待办而非空壳。</p>
 */
public abstract class LegacyRoutes {

    /** 旧 {@code InterceptorManager.NULL_INTERS} 的等价常量（空拦截器数组） */
    public static final LegacyInterceptor[] NULL_INTERS = new LegacyInterceptor[0];

    /** 旧 {@code DEFAULT_MAPPING_SUPER_CLASS} 的取值（编译期常量 false） */
    static final boolean DEFAULT_MAPPING_SUPER_CLASS = false;

    /** 全局已注册的 Routes（旧实现是静态可变列表，clear() 会把整个字段置 null） */
    private static List<LegacyRoutes> routesList = new ArrayList<>();

    /** 是否映射父类 Action（三态：null=未设置，交由调用方继承） */
    Boolean mappingSuperClass;

    /** 基视图路径（无尾部斜杠） */
    private String baseViewPath;

    /** 路由项列表 */
    private List<Route> routeItemList;

    /** 拦截器列表（旧实现构造时即 new ArrayList，不是 null） */
    private List<LegacyInterceptor> interList;

    /** 是否在映射后清理（旧实现默认 false） */
    private boolean clearAfterMapping;

    /**
     * 构造：与旧实现字段初值逐条一致。
     */
    public LegacyRoutes() {
        this.mappingSuperClass = null;
        this.baseViewPath = null;
        this.routeItemList = new ArrayList<>();
        this.interList = new ArrayList<>();
        this.clearAfterMapping = false;
    }

    /**
     * 子类在此注册路由。
     */
    public abstract void config();

    /**
     * 设置是否映射父类 Action。
     *
     * @param mappingSuperClass 是否映射
     * @return 本对象（链式）
     */
    public LegacyRoutes setMappingSuperClass(boolean mappingSuperClass) {
        this.mappingSuperClass = mappingSuperClass;
        return this;
    }

    /**
     * 取是否映射父类 Action。
     *
     * @return 未设置时返回 false（旧为 DEFAULT_MAPPING_SUPER_CLASS）
     */
    public boolean getMappingSuperClass() {
        return mappingSuperClass != null ? mappingSuperClass : DEFAULT_MAPPING_SUPER_CLASS;
    }

    /**
     * 注册路由（viewPath 默认取 controllerPath 本身）。
     *
     * @param controllerPath  控制器路径
     * @param controllerClass 控制器类型
     * @return 本对象（链式）
     */
    public LegacyRoutes add(String controllerPath, Class<? extends LegacyController> controllerClass) {
        return add(controllerPath, controllerClass, controllerPath);
    }

    /**
     * 注册路由。
     *
     * @param controllerPath  控制器路径
     * @param controllerClass 控制器类型
     * @param viewPath        视图路径
     * @return 本对象（链式）
     */
    public LegacyRoutes add(String controllerPath, Class<? extends LegacyController> controllerClass,
                            String viewPath) {
        routeItemList.add(new Route(controllerPath, controllerClass, viewPath));
        return this;
    }

    /**
     * 合并另一个 Routes：先调用其 {@code config()}，
     * 若其未设置 mappingSuperClass 则继承本对象的，再登记到静态列表。
     *
     * @param routes 另一个 Routes
     * @return 本对象（链式）
     */
    public LegacyRoutes add(LegacyRoutes routes) {
        routes.config();
        if (routes.mappingSuperClass == null) {
            routes.mappingSuperClass = this.mappingSuperClass;
        }
        routesList.add(routes);
        return this;
    }

    /**
     * 追加拦截器（旧实现在此先做 jfinal 的 Aop.inject，本接缝省略，见类注释）。
     *
     * @param interceptor 拦截器
     * @return 本对象（链式）
     */
    public LegacyRoutes addInterceptor(LegacyInterceptor interceptor) {
        interList.add(interceptor);
        return this;
    }

    /**
     * 设置基视图路径：trim + 补前导 "/" + <b>去尾部 "/"</b>（与 processViewPath 相反）。
     *
     * @param baseViewPath 基视图路径
     * @return 本对象（链式）
     */
    public LegacyRoutes setBaseViewPath(String baseViewPath) {
        if (LegacyStrKit.isBlank(baseViewPath)) {
            throw new IllegalArgumentException("baseViewPath can not be blank");
        }
        baseViewPath = baseViewPath.trim();
        if (!baseViewPath.startsWith("/")) {
            baseViewPath = "/" + baseViewPath;
        }
        if (baseViewPath.endsWith("/")) {
            baseViewPath = baseViewPath.substring(0, baseViewPath.length() - 1);
        }
        this.baseViewPath = baseViewPath;
        return this;
    }

    /**
     * 取基视图路径。
     *
     * @return 基视图路径；未设置时 null
     */
    public String getBaseViewPath() {
        return baseViewPath;
    }

    /**
     * 取路由项列表（返回内部实例本身，可变 —— 旧实现如此）。
     *
     * @return 路由项列表
     */
    public List<Route> getRouteItemList() {
        return routeItemList;
    }

    /**
     * 取拦截器数组：非空时新建数组，为空时返回共享常量 {@link #NULL_INTERS}。
     *
     * @return 拦截器数组
     */
    public LegacyInterceptor[] getInterceptors() {
        return interList.size() > 0
                ? interList.toArray(new LegacyInterceptor[interList.size()])
                : NULL_INTERS;
    }

    /**
     * 取全局已注册的 Routes。
     *
     * @return 列表；被 {@link #clear()} 清理过后为 null
     */
    public static List<LegacyRoutes> getRoutesList() {
        return routesList;
    }

    /**
     * 设置是否在映射后清理。
     *
     * @param clearAfterMapping 是否清理
     */
    public void setClearAfterMapping(boolean clearAfterMapping) {
        this.clearAfterMapping = clearAfterMapping;
    }

    /**
     * 清理（仅在 clearAfterMapping 为真时生效；会把静态 routesList 置为 null）。
     */
    public void clear() {
        if (clearAfterMapping) {
            routesList = null;
            this.baseViewPath = null;
            this.routeItemList = null;
            this.interList = null;
        }
    }

    /**
     * 测试/适配用：恢复被 {@link #clear()} 置空的静态列表。
     *
     * <p><b>为什么需要它：</b>{@code clear()} 的既有语义是"把静态字段置 null"，
     * 一旦在进程内执行过，后续所有 {@code getRoutesList()}/{@code add(Routes)}
     * 都会受影响。本方法不属旧 API，仅供接缝自测在用例结束后复原现场，
     * 避免测试间污染（旧实现没有这个问题，因为它只被调用一次）。</p>
     */
    static void restoreRoutesListForTest() {
        routesList = new ArrayList<>();
    }

    /**
     * 单个路由项（旧 {@code Routes$Route} 的等价实现）。
     */
    public static class Route {

        /** 控制器路径（已归一） */
        private String controllerPath;

        /** 控制器类型 */
        private Class<? extends LegacyController> controllerClass;

        /** 视图路径（已归一，必然以 "/" 结尾） */
        private String viewPath;

        /**
         * 构造。
         *
         * @param controllerPath  控制器路径（不可空白）
         * @param controllerClass 控制器类型（不可 null）
         * @param viewPath        视图路径（空白时归一为 "/"）
         */
        public Route(String controllerPath, Class<? extends LegacyController> controllerClass,
                     String viewPath) {
            if (LegacyStrKit.isBlank(controllerPath)) {
                throw new IllegalArgumentException("controllerPath can not be blank");
            }
            if (controllerClass == null) {
                throw new IllegalArgumentException("controllerClass can not be null");
            }
            if (LegacyStrKit.isBlank(viewPath)) {
                viewPath = "/";
            }
            this.controllerPath = processControllerPath(controllerPath);
            this.controllerClass = controllerClass;
            this.viewPath = processViewPath(viewPath);
        }

        /**
         * 控制器路径归一：trim + 补前导 "/"。
         *
         * @param controllerPath 原路径
         * @return 归一后路径
         */
        private String processControllerPath(String controllerPath) {
            controllerPath = controllerPath.trim();
            if (!controllerPath.startsWith("/")) {
                controllerPath = "/" + controllerPath;
            }
            return controllerPath;
        }

        /**
         * 视图路径归一：trim + 补前导 "/" + 补尾部 "/"。
         *
         * @param viewPath 原路径
         * @return 归一后路径
         */
        private String processViewPath(String viewPath) {
            viewPath = viewPath.trim();
            if (!viewPath.startsWith("/")) {
                viewPath = "/" + viewPath;
            }
            if (!viewPath.endsWith("/")) {
                viewPath = viewPath + "/";
            }
            return viewPath;
        }

        /**
         * 取控制器路径。
         *
         * @return 控制器路径
         */
        public String getControllerPath() {
            return controllerPath;
        }

        /**
         * 取控制器 key（旧实现直接返回 controllerPath，无变换）。
         *
         * @return 控制器 key
         */
        public String getControllerKey() {
            return controllerPath;
        }

        /**
         * 取控制器类型。
         *
         * @return 控制器类型
         */
        public Class<? extends LegacyController> getControllerClass() {
            return controllerClass;
        }

        /**
         * 取最终视图路径：非 null 时为其与本项 viewPath 的拼接。
         *
         * @param viewPath 视图路径
         * @return 最终视图路径
         */
        public String getFinalViewPath(String viewPath) {
            return viewPath != null ? viewPath + this.viewPath : this.viewPath;
        }
    }
}
