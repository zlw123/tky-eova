/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import javax.sql.DataSource;

import cn.eova.common.Ds;
import cn.eova.compat.cache.CacheServices;
import cn.eova.db.EovaModel;
import cn.eova.compat.jfinal.config.LegacyEngine;
import cn.eova.compat.jfinal.config.LegacyJFinalBoot;
import com.jfinal.template.Engine;
import cn.eova.compat.jfinal.config.LegacyRoutes;
import cn.eova.compat.table.EovaTableMapping;
import cn.eova.config.EovaConfig;
import cn.eova.db.EovaGateways;
import cn.eova.db.JdbcEovaDbGateway;
import cn.eova.compat.render.DefaultLegacyRenderFactory;
import cn.eova.compat.render.LegacyTemplateRender;
import cn.eova.compat.render.LegacyRenderManager;
import cn.eova.db.JdbcTableMetadataSource;
import cn.eova.tools.x;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/**
 * **Eova 自有 Web 层的启动装配（切片 S1，第 245 轮起）** —— 复用已 port 的引导序列，
 * 不自己发明生命周期。
 *
 * <p><b>它做什么</b>：把旧栈的启动姿势搬进 Spring 容器 ——
 * {@code new LegacyJFinalBoot().init(new EovaConfig())}（与既有绿判据
 * {@code EovaConfigPortGoldenTest#bootDrivesRealEovaConfig} **同一姿势**，逐字对齐其必需配置项），
 * 并把 {@link LegacyJFinalBoot} 暴露成 bean，供后续切片的分发器/渲染取用路由表与拦截器；
 * 停机时按旧序列 {@code boot.stop(config)}。</p>
 *
 * <p><b>S1 的边界（不夸口）</b>：本切片只保证"**引导可跑、路由表可枚举**"。
 * 尚无分发器、无渲染落盘、无静态资源、无会话契约 ⇒ **阶段 1 的「HTTP 容器层」仍记 not executed**，
 * 直到 S5（真浏览器 + 新旧同视口截图对照）通过为止。</p>
 *
 * <p><b>一处显式声明（不是静默 stub）</b>：{@link EovaTableMapping} 的元数据源 ——
 * 若容器里有 {@link DataSource} bean，就用真自省（{@link JdbcTableMetadataSource}）；
 * 否则退化为**只提供列名/主键占位**的替身，并**打日志声明**。
 * 理由：S1 不需要表结构（只枚举路由），而把 MySQL 驱动提进 main 作用域属 S2 的边界决定，
 * 不在这里顺手做掉。**S2 必须用真自省替换**（届时 dao 才可用）。</p>
 */
@Configuration
public class LegacyWebBootstrap {

    private static final Logger log = LoggerFactory.getLogger(LegacyWebBootstrap.class);

    /** 旧 configConstant 的必需配置（缺则该值"不得空白"的旧校验会抛） */
    @Value("${eova.file.dir.base:${java.io.tmpdir}/eova-web}")
    private String fileDirBase;

    /** 数据源名（旧 `db.datasource`，逗号分隔可多源；此处只声明 EOVA 主源） */
    @Value("${eova.db.datasource:eova}")
    private String dbDatasource;

    /** 主数据源 URL：默认指向本机 baseline `eova_meta`（与既有 live 判据同源） */
    @Value("${eova.db.url:jdbc:mysql://127.0.0.1:13306/eova_meta}")
    private String dbUrl;

    @Value("${eova.db.user:root}")
    private String dbUser;

    @Value("${eova.db.pwd:root}")
    private String dbPwd;

    @Value("${eova.db.driver:com.mysql.cj.jdbc.Driver}")
    private String dbDriver;

    /**
     * web 根目录（**顶层同时含** `eova/` 与 `_eova/` 的那一层）。
     *
     * <p>旧栈有两个来源：渲染模板 {@code /eova/**} 来自 classpath 里的 {@code webapp/eova/**}
     * （{@code eova-meta-view-*.jar} 183 个资源），被 include 的片段 {@code /_eova/**} 来自
     * undertow webroot；移植后的资产树把两者放在同一层 ⇒ 新栈只剩一个根，且不依赖只读基线。</p>
     */
    @Value("${eova.webapp.root:remis-eova/front/remis-eova-ui/src/legacy}")
    private String webappRoot;

    private LegacyJFinalBoot boot;
    private EovaConfig config;

    /**
     * 启动装配：回调 {@link EovaConfig} 的旧生命周期，并暴露引导对象。
     *
     * @param dataSourceProvider 容器里可能存在的 DataSource（用于真自省；缺则退化并声明）
     * @return 已初始化的 {@link LegacyJFinalBoot}
     */
    @Bean
    public LegacyJFinalBoot legacyBoot(ObjectProvider<DataSource> dataSourceProvider) {
        // ① 旧实现的【必需配置】：file.dir.base 必须非空白
        x.conf.addConfig("file.dir.base", fileDirBase);
        // ② 数据源坐标（旧 configPlugin 走 EovaDataSource.create 需要）
        x.conf.addConfig("db.datasource", dbDatasource);
        x.conf.addConfig("eova.url", dbUrl);
        x.conf.addConfig("eova.user", dbUser);
        x.conf.addConfig("eova.pwd", dbPwd);
        x.conf.addConfig("eova.driver", dbDriver);

        // ③ 元数据源：真自省优先，缺则退化为占位并【显式声明】
        // ★ S2b：网关 + 真自省是 dao 可用的前提（旧栈由 configPlugin 的 ARP 承担）。
        //   没有容器 DataSource 时，按配置自建 DriverManager 版（驱动由运行时 classpath 提供，
        //   故 main 不引驱动依赖）——不再退化占位，否则 /user/doLogin 之类一查库就失败。
        DataSource ds = dataSourceProvider.getIfAvailable();
        if (ds == null) {
            ds = new DriverManagerDataSource(dbUrl, dbUser, dbPwd, dbDriver);
            log.info("Eova Web 层：自建 DriverManager DataSource → {}", dbUrl);
        } else {
            log.info("Eova Web 层：使用容器 DataSource bean");
        }
        EovaGateways.register(Ds.EOVA, new JdbcEovaDbGateway(ds, Ds.EOVA));
        EovaTableMapping.setMetadataSource(new JdbcTableMetadataSource(ds));
        log.info("Eova Web 层：网关已注册（Ds.EOVA）+ 元数据源 = JdbcTableMetadataSource（真自省）");

        // ④ 旧引导序列（与 EovaConfigPortGoldenTest 同姿势）
        this.config = new EovaConfig();
        this.boot = new LegacyJFinalBoot();
        this.boot.init(this.config);

        // ⑤ 缓存接缝：旧栈由缓存插件装配全局缓存（{@code LegacyEhCachePlugin.start()} →
        //   {@code CacheServices.set(...)}），但 {@code EovaModel} 自己的静态持有者仍需宿主注入；
        //   不给时任何走缓存的 dao 调用都会抛 IllegalStateException: EovaModel 未注入 CacheService。
        EovaModel.setCacheService(CacheServices.get());
        log.info("Eova Web 层：缓存已注入 EovaModel ← {}", CacheServices.get().getClass().getName());
        // ★ 渲染工厂：`LegacyRenderManager` 明确要求"由宿主在启动时注入"（其报错文案即
        //   "未装配渲染工厂…请由宿主在启动时注入"），而主代码里没有任何地方调用它 ——
        //   这就是 HTTP 容器层留给宿主的最后一块。实测：不装配时所有 render 路径 500。
        // ★ 模板引擎也必须由宿主构造（结构性事实，第 250 轮查明）：
        //   LegacyEngine 只是**配置收集器**（addSharedMethod/addDirective/addSourceFactory…），
        //   它**不持有**原始 com.jfinal.template.Engine；而 LegacyTemplateRender.init(Engine)
        //   在**全仓主代码里没有任何调用点** ⇒ 宿主需按收集到的设置自建 enjoy Engine 再注入。
        Engine engine = Engine.create("eova-web");
        LegacyEngine collected = this.boot.getEngine();
        java.io.File viewRoot = resolveViewRoot();
        if (viewRoot != null) {
            // ★ 必须【覆盖】收集到的源工厂，而不是"null 才补"：旧栈把 `webapp` 放在 view 模块的
            //   classpath 上，收集到的是 classpath 源，而 eova-web **不依赖**该模块 ⇒ 在本进程里
            //   永远找不到模板（实测：File not found in CLASSPATH or JAR :
            //   "webapp/eova/_view/index/login.html"）。且已核：新栈 main 资源里没有任何 html。
            engine.setSourceFactory(new LegacyViewSourceFactory(viewRoot));
            engine.setBaseTemplatePath(viewRoot.getAbsolutePath());
            log.info("Eova Web 层：模板源 = 文件系统 {}（覆盖收集到的 {}）",
                    viewRoot.getAbsolutePath(), collected.getSourceFactory());
        } else if (collected.getSourceFactory() != null) {
            engine.setSourceFactory(collected.getSourceFactory());
            log.warn("Eova Web 层：未找到视图根目录（eova.webapp.root={}），模板走收集到的源 {} —— 页面渲染会失败",
                    webappRoot, collected.getSourceFactory());
        }
        for (Object m : collected.getSharedMethods()) {
            engine.addSharedMethod(m);
        }
        for (java.util.Map.Entry<String, Class<? extends com.jfinal.template.Directive>> e
                : collected.getDirectives().entrySet()) {
            engine.addDirective(e.getKey(), e.getValue());
        }
        for (String f : collected.getSharedFunctions()) {
            engine.addSharedFunction(f);
        }
        for (java.util.Map.Entry<String, Object> e : collected.getSharedObjects().entrySet()) {
            engine.addSharedObject(e.getKey(), e.getValue());
        }
        LegacyTemplateRender.init(engine);
        log.info("Eova Web 层：模板引擎已构造并注入（源工厂={}，共享方法 {}，指令 {}，共享函数 {}，共享对象 {}）",
                collected.getSourceFactory(), collected.getSharedMethods().size(),
                collected.getDirectives().size(), collected.getSharedFunctions().size(),
                collected.getSharedObjects().size());

        LegacyRenderManager.setRenderFactory(new DefaultLegacyRenderFactory());
        log.info("Eova Web 层：渲染工厂已装配（宿主职责）");

        LegacyRoutes routes = this.boot.getRoutes();
        log.info("Eova Web 层：引导完成，路由条目 {} 条", routes.getRouteItemList().size());
        return this.boot;
    }

    /**
     * 解析视图根目录（含 `webapp` 子目录的那一层）。
     *
     * <p>判据：该层**同时**存在 `eova/` 与 `_eova/` 两个目录（旧栈两根合并后的特征）。</p>
     *
     * <p>属性优先，其次**从工作目录向上**逐级拼相对路径 —— 因为 maven 模块测试、IDE、命令行
     * 三种场景的工作目录各不相同（实测踩到：CWD = 模块目录时，仓库根相对路径解析不到，
     * 补丁静默走了 classpath 分支）。找不到返回 {@code null}（调用方告警，不静默）。</p>
     *
     * @return 存在 `webapp` 子目录的那一层；都没有则 null
     */
    private java.io.File resolveViewRoot() {
        java.util.List<java.io.File> candidates = new java.util.ArrayList<>();
        candidates.add(new java.io.File(webappRoot));
        java.io.File dir = new java.io.File("").getAbsoluteFile();
        for (int i = 0; i < 6 && dir != null; i++) {
            candidates.add(new java.io.File(dir, webappRoot));
            dir = dir.getParentFile();
        }
        for (java.io.File c : candidates) {
            if (new java.io.File(c, "eova").isDirectory() && new java.io.File(c, "_eova").isDirectory()) {
                return c;
            }
        }
        return null;
    }

    /** 停机：按旧序列 beforeJFinalStop → 插件 stop → onStop */
    @PreDestroy
    public void shutdown() {
        if (boot != null && boot.isStarted()) {
            boot.stop(config);
            log.info("Eova Web 层：已按旧序列停机");
        }
    }

    /**
     * 极简 {@link DataSource}：按配置的驱动类名加载驱动并直连（无池语义）。
     *
     * <p>为什么不引驱动依赖到 main：驱动属运行时提供物（部署时进 classpath）。
     * 本项目既有 live 判据也用同款直连实现。</p>
     */
    static final class DriverManagerDataSource implements DataSource {
        private final String url;
        private final String user;
        private final String pwd;
        private final String driver;

        DriverManagerDataSource(String url, String user, String pwd, String driver) {
            this.url = url;
            this.user = user;
            this.pwd = pwd;
            this.driver = driver;
            try {
                Class.forName(driver);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("驱动类不存在（请把驱动加入运行时 classpath）：" + driver, e);
            }
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, user, pwd);
        }

        @Override
        public Connection getConnection(String u, String p) throws SQLException {
            return DriverManager.getConnection(url, u, p);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("no parent logger");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
