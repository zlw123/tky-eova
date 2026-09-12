/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import javax.sql.DataSource;

import cn.eova.compat.jfinal.config.LegacyJFinalBoot;
import cn.eova.compat.jfinal.config.LegacyRoutes;
import cn.eova.compat.table.EovaTableMapping;
import cn.eova.config.EovaConfig;
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
        DataSource ds = dataSourceProvider.getIfAvailable();
        if (ds != null) {
            EovaTableMapping.setMetadataSource(new JdbcTableMetadataSource(ds));
            log.info("Eova Web 层：元数据源 = JdbcTableMetadataSource（真自省）");
        } else {
            EovaTableMapping.setMetadataSource(tableName ->
                    new cn.eova.compat.table.TableMetadata(tableName,
                            new String[]{"id", "name"}, new String[]{"id"}));
            log.warn("Eova Web 层：未发现 DataSource bean ⇒ 元数据源退化为占位（S1 只枚举路由，够用）；"
                    + "S2 必须换成真自省，否则 dao 不可用");
        }

        // ④ 旧引导序列（与 EovaConfigPortGoldenTest 同姿势）
        this.config = new EovaConfig();
        this.boot = new LegacyJFinalBoot();
        this.boot.init(this.config);
        LegacyRoutes routes = this.boot.getRoutes();
        log.info("Eova Web 层：引导完成，路由条目 {} 条", routes.getRouteItemList().size());
        return this.boot;
    }

    /** 停机：按旧序列 beforeJFinalStop → 插件 stop → onStop */
    @PreDestroy
    public void shutdown() {
        if (boot != null && boot.isStarted()) {
            boot.stop(config);
            log.info("Eova Web 层：已按旧序列停机");
        }
    }
}
