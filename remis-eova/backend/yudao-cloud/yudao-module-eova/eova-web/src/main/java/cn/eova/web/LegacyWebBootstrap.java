/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.PrintWriter;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import javax.sql.DataSource;

import cn.eova.common.Ds;
import cn.eova.compat.cache.CacheServices;
import cn.eova.compat.db.LegacyDataSourceWiring;
import cn.eova.db.EovaModel;
import cn.eova.compat.jfinal.config.LegacyEngine;
import cn.eova.compat.jfinal.config.LegacyJFinalBoot;
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
    /**
     * 旧静态空间 {@code /eova/**} 的宿主供给组件（切片 S3）。
     *
     * <p>与模板源共用**同一个**已解析的 web 根（属性 {@code eova.webapp.root}），
     * 避免"模板读一处、静态读另一处"的漂移。</p>
     *
     * @return 静态资源组件（根不可解析时其 serve 恒返回 false，并已告警）
     */
    @Bean
    public LegacyStaticAssets legacyStaticAssets() {
        return new LegacyStaticAssets(resolveViewRoot(), resolveSpaDistRoot());
    }

    /**
     * 解析**前端打包产物根**（含 {@code index.html}），供"页面入口退役"后的 SPA 壳供给使用。
     *
     * <p>解析顺序：配置 {@code eova.ui.dist} → 从工作目录向上最多 6 层找
     * {@code remis-eova/front/remis-eova-ui/dist}。找不到时**只告警不抛**（dev 期前端由 Vite 供给，
     * 后端不需要产物），但壳渲染一旦真被调用会**响亮报错**（见 {@code LegacySpaShellRender}）。</p>
     *
     * @return 产物根目录；不存在时 null
     */
    @Bean
    public java.io.File spaDistRoot() {
        java.io.File root = resolveSpaDistRoot();
        if (root == null) {
            log.warn("Eova Web 层：未找到前端打包产物（remis-eova-ui/dist）⇒ 页面入口退役后 SPA 壳无法供给。"
                    + "dev 期由 Vite 供给，生产期请先 pnpm build 并设置 eova.ui.dist");
        } else {
            log.info("Eova Web 层：SPA 壳产物根 = {}", root.getAbsolutePath());
        }
        return root;
    }

    /**
     * 解析前端打包产物根（不产生副作用；供 Bean 与判据复用）
     *
     * @return 目录；不存在返回 null
     */
    private java.io.File resolveSpaDistRoot() {
        String configured = System.getProperty("eova.ui.dist", "");
        if (!configured.isEmpty()) {
            java.io.File f = new java.io.File(configured);
            return f.isDirectory() ? f : null;
        }
        java.util.List<java.io.File> candidates = new java.util.ArrayList<>();
        java.io.File dir = new java.io.File("").getAbsoluteFile();
        for (int i = 0; i < 8 && dir != null; i++) {
            candidates.add(new java.io.File(dir, "remis-eova/front/remis-eova-ui/dist"));
            candidates.add(new java.io.File(dir, "front/remis-eova-ui/dist"));
            dir = dir.getParentFile();
        }
        for (java.io.File c : candidates) {
            if (new java.io.File(c, "index.html").isFile()) {
                cn.eova.compat.render.LegacySpaShellRender.setDistRoot(c);
                return c;
            }
        }
        return null;
    }

    /**
     * 为 `db.datasource` 里**除主库之外**的每个数据源注册网关（第 298 轮，DES-008）。
     *
     * <p>旧栈由 `configPlugin` 的 {@code EovaDataSource.create(plugins)} 为**每个** ds 建
     * DruidPlugin + ARP，连接与映射一并就绪；ported 侧这些坐标落在
     * {@link cn.eova.compat.db.LegacyDataSourceWiring.Spec}（**连接池归宿主**）
     * ⇒ 网关也必须由宿主逐个补上，否则第二库一律
     * {@code 未注册数据源网关（数据源=…）} 500，而旧栈可用。</p>
     *
     * <p><b>为什么按 spec 遍历、不硬编码 {@code main}</b>：旧栈的语义就是"`db.datasource` 里写几个 ds
     * 就连几个"；硬编码会让"配置里加了第三个库"再次静默 500。</p>
     *
     * <p><b>主库不动</b>：{@code Ds.EOVA} 已在 ③ 注册（容器 {@code DataSource} 优先，缺则自建），
     * 且它是元数据源（{@code EovaTableMapping.setMetadataSource}）与真自省的基准。</p>
     */
    private void registerSecondaryGateways() {
        List<LegacyDataSourceWiring.Spec> specs = LegacyDataSourceWiring.specs();
        int added = 0;
        for (LegacyDataSourceWiring.Spec spec : specs) {
            String dsName = spec.getDs();
            if (Ds.EOVA.equals(dsName)) {
                continue;
            }
            // 与主库自建路径**同形**（DriverManager 版；连接池是独立单元，见 DES-008 §5）
            DataSource other = new DriverManagerDataSource(
                    spec.getUrl(), spec.getUser(), spec.getPwd(), spec.getDriver());
            EovaGateways.register(dsName, new JdbcEovaDbGateway(other, dsName));
            added++;
            log.info("Eova Web 层：网关已注册（数据源={}）→ {}", dsName, spec.getUrl());
        }
        log.info("Eova Web 层：多数据源接线完成：spec {} 个（含主库），本次补注册 {} 个", specs.size(), added);
        if (specs.size() <= 1) {
            // 响亮告警而不是静默：配置里本该有 eova+main（eova/dev.txt:21），只解析出 1 个就是配置没进来
            log.warn("Eova Web 层：`db.datasource` 只解析出 {} 个数据源（期望至少 eova+main）"
                    + "⇒ 第二库的访问会 500（未注册数据源网关）", specs.size());
        }
    }

    /**
     * **取值优先级**：配置（`eova/dev.txt`）优先，宿主属性只作兜底（r323）。
     *
     * <p>抽成纯函数是为了**可判据、可变异** —— r322 那个部署级缺陷（元数据库连错库）
     * 就发生在"哪一边优先"这一行上，而它此前没有任何判据。</p>
     *
     * @param fromConfig 配置里的值（可能为 null/空）
     * @param fromHost   宿主（Spring）属性值
     * @return 生效值
     */
    static String pick(String fromConfig, String fromHost) {
        return x.isEmpty(fromConfig) ? fromHost : fromConfig;
    }

    /**
     * **仅当配置里没有该键时**才写入宿主兜底值（r323）。
     *
     * <p>为什么需要它：EOVA 的配置事实源是 `eova/dev.txt`（旧栈 `x.conf` 的唯一来源）；
     * 宿主（Spring）属性只能作**兜底**。无条件 `addConfig` 会把配置文件里的值**静默覆盖** ——
     * r322 实测：切金仓后元数据库仍连 MySQL（`eova.url` 被 Spring 默认值覆盖），
     * 而 `main.url` 走 dev.txt 是金仓 ⇒ 同一进程连两个库。</p>
     *
     * @param key   配置键
     * @param value 兜底值（配置里已有则忽略）
     */
    private static void addConfigIfAbsent(String key, String value) {
        if (x.isEmpty(x.conf.get(key))) {
            x.conf.addConfig(key, value);
        }
    }

    @Bean
    public LegacyJFinalBoot legacyBoot(ObjectProvider<DataSource> dataSourceProvider) {
        // ① 旧实现的【必需配置】：file.dir.base 必须非空白
        // ⚠️ 此处**保持**"宿主值优先"（未套用 addConfigIfAbsent）：`eova/dev.txt:10` 写的是旧 demo 的
        //   Windows 路径 `G:/nas/eovameta`，套用"配置优先"会让本机/容器里的文件功能真的指向 `G:/`
        //   （上传、导出、file.dir.base 相关面立刻坏）。⇒ 该键的取值口径属**部署口径**，单独登记待定；
        //   本轮的修复只针对已验证过的缺陷（`eova.*` 被覆盖导致元数据库连错库，r322）。
        x.conf.addConfig("file.dir.base", fileDirBase);
        // ② 数据源坐标（旧 configPlugin 走 EovaDataSource.create 需要）
        x.conf.addConfig("db.datasource", dbDatasource);
        // ★★ r323 修（金仓收口 · 第一条真缺陷）：`eova.*` 的**事实源必须是 `eova/dev.txt`**，
        //   与 `main.*` 同源。原先这里**无条件** `addConfig("eova.url", dbUrl)` ⇒ 把 dev.txt 里的坐标
        //   覆盖成 Spring 默认值（= 本机 MySQL baseline），实测后果：
        //   把 `dev.txt` 切到金仓后，**元数据库仍然连 MySQL**
        //   （启动日志：`自建 DriverManager DataSource → jdbc:mysql://127.0.0.1:13306/eova_meta`），
        //   金仓下 4 个列表页数据面对不上（r322 实测），而 `main` 库却是金仓
        //   ⇒ **同一进程连两个库**，属部署级缺陷（金仓环境里根本没有那个 MySQL）。
        //   规则：**配置里有就不覆盖**；Spring 属性只作"配置缺失时的宿主兜底"（本机单测/无配置文件场景）。
        addConfigIfAbsent("eova.url", dbUrl);
        addConfigIfAbsent("eova.user", dbUser);
        addConfigIfAbsent("eova.pwd", dbPwd);
        addConfigIfAbsent("eova.driver", dbDriver);

        // ③ 元数据源：真自省优先，缺则退化为占位并【显式声明】
        // ★ S2b：网关 + 真自省是 dao 可用的前提（旧栈由 configPlugin 的 ARP 承担）。
        //   没有容器 DataSource 时，按配置自建 DriverManager 版（驱动由运行时 classpath 提供，
        //   故 main 不引驱动依赖）——不再退化占位，否则 /user/doLogin 之类一查库就失败。
        DataSource ds = dataSourceProvider.getIfAvailable();
        if (ds == null) {
            // ★★ r323 修（金仓收口 · 第一条真缺陷的真正修法）：
            //   此前这里直接用宿主字段 `new DriverManagerDataSource(dbUrl, dbUser, dbPwd, dbDriver)`，
            //   **从不读 `eova/dev.txt`** ⇒ 把 dev.txt 切到金仓后，元数据库仍连 Spring 默认值
            //   （MySQL baseline）：启动日志实测 `自建 DriverManager DataSource → jdbc:mysql://127.0.0.1:13306/eova_meta`，
            //   而 `main` 库走配置是金仓 ⇒ **同一进程连两个库**（r322 端到端实测，金仓下 4 页数据面对不上）。
            //   规则与 `main` 一致：**配置优先，宿主属性只兜底**（无配置文件的本机单测场景仍可用）。
            String cfgUrl = x.conf.get("eova.url");
            String url = pick(cfgUrl, dbUrl);
            ds = new DriverManagerDataSource(url, pick(x.conf.get("eova.user"), dbUser),
                    pick(x.conf.get("eova.pwd"), dbPwd), pick(x.conf.get("eova.driver"), dbDriver));
            log.info("Eova Web 层：自建 DriverManager DataSource → {}（来源={}）", url,
                    x.isEmpty(cfgUrl) ? "宿主属性兜底（配置里没有 eova.url）" : "eova/dev.txt");
        } else {
            log.info("Eova Web 层：使用容器 DataSource bean");
        }
        EovaGateways.register(Ds.EOVA, new JdbcEovaDbGateway(ds, Ds.EOVA));
        EovaTableMapping.setMetadataSource(new JdbcTableMetadataSource(ds));
        log.info("Eova Web 层：网关已注册（Ds.EOVA）+ 元数据源 = JdbcTableMetadataSource（真自省）");

        // ④ 旧引导序列（与 EovaConfigPortGoldenTest 同姿势）
        //   ★ r307（U3）：宿主配置改为 `WebAppConfig`（`extends EovaConfig`，只覆写 `route(me)`
        //   把根路由指向 `DemoPageController`）—— 对应旧栈 demo 的 `AppConfig extends EovaConfig`。
        //   为什么必须走这个子类：`EovaConfig` 的"根路由是否已注册"守卫只看**直接 add** 的条目，
        //   把 `/` 放进子 Routes（如 `EovaWebRoutes`）会让根路由**重复两条**（见 `WebAppConfig` 类注释）。
        this.config = new WebAppConfig();
        this.boot = new LegacyJFinalBoot();
        this.boot.init(this.config);

        // ④b ★ 多数据源接线（第 298 轮，DES-008）：旧栈由 `configPlugin` 为 `db.datasource` 里的
        //   **每一个** ds 建 DruidPlugin + ARP；ported 侧这些坐标落在 `LegacyDataSourceWiring.Spec`
        //   （由 `EovaDataSource.create()` 在 ④ 里填好，**连接池归宿主**）
        //   ⇒ 网关也必须由宿主**逐个**注册。
        //   ★ 本步之前只注册了 `Ds.EOVA` ⇒ 落在第二库（`main` → `demo`）的任何访问一律
        //   `未注册数据源网关（数据源=main）` 500，而**旧栈同请求是 200**（2026-09-12 实测）。
        //   最严重的可观测后果：含查找框（`ev-find`）的表单页在浏览器里**永久卡死** ——
        //   制品 `EvFind` 的 `widget_text` 失败后走 `.catch(() => alert('请求异常'))`，而 `alert` 阻塞主线程。
        //   （详见 DES-005 §16.8.3 缺口 3 与 `docs/DES-008-R1-multi-datasource-host-wiring.md`。）
        registerSecondaryGateways();

        // ⑤ 缓存接缝：旧栈由缓存插件装配全局缓存（{@code LegacyEhCachePlugin.start()} →
        //   {@code CacheServices.set(...)}），但 {@code EovaModel} 自己的静态持有者仍需宿主注入；
        //   不给时任何走缓存的 dao 调用都会抛 IllegalStateException: EovaModel 未注入 CacheService。
        EovaModel.setCacheService(CacheServices.get());
        log.info("Eova Web 层：缓存已注入 EovaModel ← {}", CacheServices.get().getClass().getName());
        // ★ 渲染工厂：`LegacyRenderManager` 明确要求"由宿主在启动时注入"（其报错文案即
        //   "未装配渲染工厂…请由宿主在启动时注入"），而主代码里没有任何地方调用它 ——
        //   这就是 HTTP 容器层留给宿主的最后一块。实测：不装配时所有 render 路径 500。
        // ★ r310：**enjoy 引擎已按口径授权摘除**。历史上这里要"按收集到的设置自建 enjoy Engine
        //   再注入"（LegacyEngine 只是配置收集器，不持有 Engine；`LegacyTemplateRender.init(Engine)`
        //   在主代码里没有任何调用点 ⇒ 不构造就所有 render 路径 500）。
        //   现在的渲染底座是**自研**：无指令模板直出 + `LegacyPageRenderer`（与 enjoy 逐字节等价，
        //   差分判据 4/4 + 活页字节金标不变），遇到不支持的指令**响亮抛错**而不是回退引擎。
        //   依据（全部实跑）：引擎兜底实测 0 次（判据 + 扫描 4d，日志确认为"当前后端"）·
        //   可达模板面逐面枚举（LegacyTemplateFaceInventoryTest）。
        java.io.File viewRoot = resolveViewRoot();
        LegacyEngine collected = this.boot.getEngine();
        if (viewRoot == null) {
            log.error("Eova Web 层：未找到视图根目录（eova.webapp.root={}）—— 页面渲染将失败",
                    webappRoot);
        }
        // ★ r309 第 1 轮：注入**极简页面渲染器**（有指令模板先走它，与 enjoy 逐字节等价）。
        //   共享方法取引擎已注册的那批（`BaseSharedMethod` 的 `conf('…')`/`getUIConf()` 就在其中）。
        if (viewRoot != null) {
            LegacyTemplateRender.initPageRenderer(
                    cn.eova.compat.template.LegacyPageRenderer.of(viewRoot, collected.getSharedMethods()));
        }
        // ★ r308 第 8 轮：注入**模板源读取器**（供"无指令模板直出"快路径用）。
        //   视图名口径与引擎一致（相对视图根，形如 `/eova/_view/...` 或 `/_view/theme/index.html`）。
        if (viewRoot != null) {
            LegacyTemplateRender.initSourceReader(v -> {
                String rel = v.startsWith("/") ? v.substring(1) : v;
                java.io.File f = new java.io.File(viewRoot, rel);
                if (!f.isFile()) {
                    return null;
                }
                try {
                    return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                            java.nio.charset.StandardCharsets.UTF_8);
                } catch (java.io.IOException e) {
                    log.warn("Eova Web 层：模板源读取失败（{}）⇒ 回退模板引擎", f.getAbsolutePath());
                    return null;
                }
            });
        }
        log.info("Eova Web 层：页面渲染器已注入（视图根={}，共享方法 {}）—— 不再使用 enjoy 引擎",
                viewRoot, collected.getSharedMethods().size());

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
