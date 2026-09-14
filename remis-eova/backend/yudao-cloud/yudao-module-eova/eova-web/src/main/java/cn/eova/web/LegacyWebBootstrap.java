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
import cn.eova.compat.jfinal.config.LegacyConfigProfile;
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
     * **配置档**（环境 × 数据库类型；r326 / DES-010）：空 ⇒ 走旧五档（`dev.txt` 优先）。
     *
     * <p>三种写法都可用：`-Deova.prop=eova/prd.txt`（JVM 属性）、`--eova.prop=eova/prd.txt`
     * （Spring 命令行）、`EOVA_PROP=eova/prd.txt`（环境变量）。见 {@link #legacyBoot} 开头的桥接。</p>
     */
    @Value("${eova.prop:}")
    private String profileProp;

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

    // ★ r326：这里原有 `addConfigIfAbsent(key, value)`（r323 引入的"仅当配置里没有该键才写宿主兜底"），
    //   本轮**删除**。它成立的前提是"`x.conf.addConfig` 不覆盖已有值"，而该前提**与字节码不符**：
    //   `cn.eova.tools.tool.ConfigTool` 的 `addConfig`/`addProp` 都是 `Map.put`（**后写者胜**，见 probe 实测）；
    //   且它在 r324 之后**已无任何调用点**。留着就是留一个"基于错误前提的接缝"。
    @Bean
    public LegacyJFinalBoot legacyBoot(ObjectProvider<DataSource> dataSourceProvider) {
        // ★★ r326（DES-010）：宿主配置档 → JVM 系统属性的桥接。
        //   兼容层 `LegacyConfigProfile` 只认 `-Deova.prop` / `EOVA_PROP` 这两个**纯 JVM/OS 事实源**
        //   （档位在 ④ `configConstant` 里装载，此刻只有它们必然可读）；桥接后
        //   `--eova.prop=eova/prd.txt`（Spring 命令行风格）与 `-Deova.prop=…` 等价。
        //   必须早于 ④ 且只在非空时写（空值不得污染 JVM 属性）。
        if (!x.isEmpty(profileProp)) {
            System.setProperty(LegacyConfigProfile.PROP_KEY, profileProp);
            log.info("Eova Web 层：宿主指定配置档 {}={}（已桥接到系统属性）",
                    LegacyConfigProfile.PROP_KEY, profileProp);
        }
        // ① 旧实现的【必需配置】：file.dir.base 必须非空白
        // ⚠️ r326 **更正**（原注释把规则写反了）：这里写的宿主值**会被配置档覆盖**。
        //   实测事实（`ConfigTool` 字节码 = `Map.put`，后写者胜）：宿主在 ② 先写、档在 ④ 后装载
        //   ⇒ **档里出现的键一律胜出**。复刻宿主顺序的探针输出：
        //     addConfig("file.dir.base","/tmp/host-fallback") 后装载 dev.txt
        //       ⇒ x.conf.get("file.dir.base") = `G:/nas/eovameta`（dev.txt:10，**不是**宿主值）
        //       ⇒ x.conf.get("db.datasource")  = `eova,main`（**不是**宿主默认 `eova`）
        //   ⇒ 这一行的真实作用只是"**档里没有该键时**的兜底"（本机单测 / 无档场景）。
        //   由此，"档里的 `G:/nas/eovameta` 在容器/生产该换成什么"仍是**部署口径、单独登记待裁**
        //   （不是"宿主兜底会赢"——原注释就是这么写错的）。
        x.conf.addConfig("file.dir.base", fileDirBase);
        // ② 数据源坐标（旧 configPlugin 走 EovaDataSource.create 需要）：
        //   同样只是**兜底**——档里有 `db.datasource=eova,main`（实测如上），故多数据源接线不受影响。
        x.conf.addConfig("db.datasource", dbDatasource);
        // ★★ r323/r324 历史（金仓收口 · 第一条真缺陷）：这里曾**无条件** `addConfig("eova.url", dbUrl)`，
        //   把 Spring 默认坐标写进 `x.conf`。r322 实测后果：档切到金仓后**元数据库仍连 MySQL**
        //   （启动日志：`自建 DriverManager DataSource → jdbc:mysql://127.0.0.1:13306/eova_meta`），
        //   而同进程的 `main` 库走档是金仓 ⇒ **同一进程连两个库**（金仓环境里根本没有那个 MySQL），
        //   金仓下 4 个列表页数据面对不上。
        //   r324 的真正修法 = ③ 的**延迟解析**（首次取连接时才读 `x.conf`）。
        //   ★ r326 更正 r323/r324 当时写下的机制（原文："`addConfig` **不覆盖**（r323 变异实验已证）"）：
        //     该结论与字节码不符——`addConfig`/`addProp` 都是 `Map.put`、**后写者胜**，
        //     宿主先写的兜底值一定会被档覆盖。所以 r324 的修法之所以**真必要**，
        //     唯一原因是**③ 早于 ④**（那一刻档还没装载 ⇒ 读不到），而不是"写不进去"。
        // ③ 元数据源：真自省优先，缺则自建（见下）；不再退化为占位。
        // ★ S2b：网关 + 真自省是 dao 可用的前提（旧栈由 configPlugin 的 ARP 承担）。
        //   没有容器 DataSource 时，按配置自建 DriverManager 版（驱动由运行时 classpath 提供，
        //   故 main 不引驱动依赖）——不再退化占位，否则 /user/doLogin 之类一查库就失败。
        DataSource ds = dataSourceProvider.getIfAvailable();
        if (ds == null) {
            // ★★ r324（金仓收口 · 第一条真缺陷的真正修法）：**延迟解析坐标**。
            //   为什么必须延迟：本处（③）在 `boot.init`（④）**之前**执行，此时档尚未装载
            //   ⇒ 无论怎么写都读不到档里的坐标（实测 ③ 处 `x.conf.get("eova.url")` 为空、
            //   ④b 处非空）。而 ③ 又必须早于 ④（④ 的 `onStart` 要读库）⇒ 不能把 ③ 挪后。
            //   ⇒ 用**首次取连接时解析**的包装：那时档已装载，坐标与 `main` 库同源（`x.conf`），
            //     宿主属性只作兜底；日志在解析时打印，且**如实标注真实出处**
            //     （★ r326：出处取自 `LegacyConfigProfile.active()`——真实装载的档名，
            //       而不是"哪个文件大概在 classpath 里"。早先只按"值非空"就写 `eova/dev.txt`，
            //       把 r322 的真根因掩盖了一轮）。
            ds = new LazyEovaDataSource(() -> {
                String cfgUrl = x.conf.get("eova.url");
                String url = pick(cfgUrl, dbUrl);
                log.info("Eova Web 层：自建 DriverManager DataSource → {}（坐标来源={}）", url,
                        x.isEmpty(cfgUrl) ? "宿主属性兜底（档里没有 eova.url）"
                                : "配置档 " + LegacyConfigProfile.active() + "（首次连接时解析）");
                return new DriverManagerDataSource(url, pick(x.conf.get("eova.user"), dbUser),
                        pick(x.conf.get("eova.pwd"), dbPwd), pick(x.conf.get("eova.driver"), dbDriver));
            });
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
    /**
     * **延迟解析坐标的数据源**（r324）：首次取连接时才解析并建真实数据源。
     *
     * <p>存在理由见 ③ 的注释：元数据源必须在 `boot.init`（配置装载）之前注册，
     * 而它的坐标又只能来自配置 ⇒ 二者用"延迟解析"调和。解析一次后缓存（与连接池语义一致：
     * 数据源在应用生命周期内只解析一次）。</p>
     */
    static final class LazyEovaDataSource implements DataSource {

        /** 真实数据源的工厂（首次取连接时调用一次） */
        private final java.util.function.Supplier<DataSource> factory;

        /** 已解析的真实数据源（volatile + 双检：并发下只解析一次） */
        private volatile DataSource delegate;

        /**
         * 构造。
         *
         * @param factory 真实数据源工厂
         */
        LazyEovaDataSource(java.util.function.Supplier<DataSource> factory) {
            this.factory = factory;
        }

        /**
         * 取真实数据源（首次调用时解析并缓存）。
         *
         * @return 真实数据源
         */
        private DataSource real() {
            DataSource d = delegate;
            if (d == null) {
                synchronized (this) {
                    if (delegate == null) {
                        delegate = factory.get();
                    }
                    d = delegate;
                }
            }
            return d;
        }

        /** 取已解析的数据源（判据用：未解析时为 null） */
        DataSource resolved() {
            return delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return real().getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return real().getConnection(username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return real().getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            real().setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            real().setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return real().getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return real().getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return real().unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return real().isWrapperFor(iface);
        }
    }

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
