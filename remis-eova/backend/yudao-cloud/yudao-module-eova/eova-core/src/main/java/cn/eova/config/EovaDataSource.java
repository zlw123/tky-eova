/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.config;

import cn.eova.tools.x;
import com.alibaba.druid.DbType;
import com.alibaba.druid.util.JdbcUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源注册表与方言族选择：{@code cn.eova.config.EovaDataSource} 的<b>可移植核心</b>。
 *
 * <p>ported from: cn.eova.config.EovaDataSource
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 *
 * <p><b>为什么只 port 一部分（已声明的宿主适配）：</b>旧类共 302 行，其中
 * {@code create(Plugins)} / {@code initDruidPlugin(...)} / {@code initActiveRecordPlugin(...)} /
 * {@code buildDialect(...)} 的职责是<b>装配 JFinal 插件</b>（DruidPlugin + ActiveRecordPlugin、
 * DbPro 工厂、容器工厂）。新栈里数据源由 Spring Boot 装配（§4 约束 2：
 * 只有 {@code eova-db-adapter} 接触数据源与事务），故这些**host wiring 不再 port**，
 * 由 Spring 配置等价地完成。
 *
 * <p>本类保留旧类中<b>与环境无关、且被业务代码直接依赖</b>的两块：
 * <ol>
 *   <li><b>数据源注册表</b>—— {@code dataSources}(数据源名 → {@link DbType})、
 *       {@link #map()}、{@link #getDbType(String)}。方法体与旧实现一致，
 *       <b>未注册的数据源返回 {@code null}</b>（旧实现即 {@code dataSources.get(ds)}），
 *       故 {@code getDbType(ds) == DbType.oracle} 这类比较在 null 时安全地为 false。</li>
 *   <li><b>DbType 推导</b>—— 旧 {@code create()} 中那一步
 *       {@code JdbcUtils.getDbTypeRaw(url, JdbcUtils.getDriverClassName(url))}。
 *       此处抽成 {@link #register(String, String, String)}，<b>推导方式与旧实现完全相同</b>
 *       （同一 druid 工具、同一版本），不是自创映射表。</li>
 * </ol>
 *
 * <p><b>方言族选择（据 {@code buildDialect} 完整方法体）：</b>
 * 旧实现的骨架是"<b>MySQL 为默认分支</b>"——
 * 先默认 {@code EovaMysqlDialect}，仅当 dbType 为 oracle/postgresql/sqlserver 才在
 * 第一段链式判断里切换；随后一段 {@code try} 里按 dbType 动态加载<b>商业 Mod 方言</b>
 * （{@code com.eova.mod.eova.<db>.*}），加载失败仅记日志。
 * <b>注意 {@code dm} 不在第一段链里</b>：它的基础方言只有在 Mod 加载成功时
 * 才被替换为 {@code DmDialect}，否则仍是 {@code EovaMysqlDialect}。
 *
 * <p><b>与 Kingbase 的关系（本实现在此显式固化）：</b>
 * 实测 {@code jdbc:kingbase8://...} 经 druid 推导为 {@code DbType.kingbase}，
 * 而 {@link #baseDialectFamily} 的分支里<b>没有 kingbase</b>，
 * 故 EOVA 在 KingbaseES 上走的是<b>默认 MySQL 方言</b> ——
 * 与 DES-001 选定的 Kingbase mysql 兼容模式一致，无需新增方言。
 * 反之若 URL 写成 {@code jdbc:postgresql://}，druid 会推导为 {@code postgresql}，
 * EOVA 随之切到 {@code PostgreSqlDialect} 并尝试加载 PG 的 Mod 方言 ——
 * <b>这会与兼容模式冲突</b>。故 URL 方案属硬约束，见判据
 * {@code EovaDataSourceGoldenTest} 中的回归断言。
 */
public class EovaDataSource {

    /** 数据源列表<数据源名, 数据源DB类型> **/
    private static final Map<String, DbType> dataSources = new HashMap<>();

    /**
     * 注册数据源（支持多数据源）—— 旧 {@code create(Plugins)} 的**可移植半部分**逐行等价。
     *
     * <p><b>切分线（第 86 轮裁定，逐行读旧 68-110 行得出）：</b>
     * 本方法逐行保留旧实现的<b>配置语义</b>：{@code db.datasource} 逗号分隔解析、
     * 每 ds 的 {@code url/user/pwd/driver/filters}（filters 默认 {@code "log4j,stat,wall"}）、
     * {@code db.pwd.encrypt} 时的 AES 解密、DbType 写入注册表
     * （{@code JdbcUtils.getDbTypeRaw(url, getDriverClassName(url))}）、
     * <b>两条中文错误消息</b>与 info 日志。
     * 旧的<b>宿主装配半部分</b>（{@code initDruidPlugin} 建池、{@code initActiveRecordPlugin} 建 ARP、
     * {@code plugins.add(dp).add(arp)}、{@code EovaConfig.arps.put}）由 Spring 承担
     * （§4 约束 2），此处显式落成 {@link LegacyDataSourceWiring.Spec} + {@link LegacyDbPlugin}
     * —— 装配所需信息一个字段都不丢，只是不在这一层建池。</p>
     *
     * @param plugins 插件容器（旧签名；新栈只是承载装配信息）
     */
    public static void create(cn.eova.compat.jfinal.config.LegacyPlugins plugins) {
        // 多数据源支持
        String datasource = x.conf.get("db.datasource");
        if (x.isEmpty(datasource)) {
            throw new RuntimeException("数据源配置项不存在,请检查配置jdbc.config 配置项[db.datasource]");
        }
        for (String ds : datasource.split(",")) {
            ds = ds.trim();

            String url = x.conf.get(ds + ".url");
            String user = x.conf.get(ds + ".user");
            String pwd = x.conf.get(ds + ".pwd");
            String driver = x.conf.get(ds + ".driver");
            String filters = x.conf.get(ds + ".filters", "log4j,stat,wall");
            if (x.isEmpty(url)) {
                throw new RuntimeException(String.format("数据源[%s]配置异常,请检查请检查配置jdbc.config", ds));
            }

            // JDBC密码加密
            if (x.conf.getBool("db.pwd.encrypt", false)) {
                pwd = cn.eova.common.utils.string.AESUtil.decrypt(pwd);
            }

            // 【已声明适配】旧栈在此建 DruidPlugin + ActiveRecordPlugin 并 plugins.add(dp).add(arp)；
            // 新栈把坐标登记给宿主装配层（连接池归 Spring，ARP 归 LegacyActiveRecordPlugin + 网关）
            cn.eova.compat.db.LegacyDataSourceWiring.Spec spec =
                    new cn.eova.compat.db.LegacyDataSourceWiring.Spec(ds, url, user, pwd, driver, filters);
            cn.eova.compat.db.LegacyDataSourceWiring.add(spec);

            cn.eova.common.utils.xx.info("create ds[%s] %s > %s", ds, user, url);

            try {
                dataSources.put(ds, com.alibaba.druid.util.JdbcUtils.getDbTypeRaw(
                        url, com.alibaba.druid.util.JdbcUtils.getDriverClassName(url)));
            } catch (java.sql.SQLException e) {
                e.printStackTrace();
            }
            // 【已声明适配】旧栈在此 EovaConfig.arps.put(ds, arp)：ARP 同时是"该 ds 的映射登记句柄"，
            // 被 EovaConfig.mappingEova(arps.get(Ds.EOVA)) / mapping(arps) 读取。
            // 新栈用 LegacyActiveRecordPlugin 承担这一角色（其 addMapping 落到 EovaTableMapping），
            // 故此处登记同形句柄 —— 否则 mappingEova 会收到 null（第 86 轮实测到）。
            cn.eova.config.EovaConfig.arps.put(ds, new cn.eova.compat.jfinal.plugin.activerecord.LegacyActiveRecordPlugin(ds));
            plugins.add(new cn.eova.compat.db.LegacyDbPlugin(spec));
        }
    }

    /**
     * 取数据源注册表（返回内部视图，与旧实现一致）
     */
    public static Map<String, DbType> map() {
        return dataSources;
    }

    /**
     * 获取数据库类型
     *
     * @param ds 数据源名
     * @return 数据库类型；<b>未注册时返回 null</b>（旧实现即 HashMap.get）
     */
    public static DbType getDbType(String ds) {
        return dataSources.get(ds);
    }

    /**
     * 注册数据源并推导其 {@link DbType}。
     *
     * <p>等价于旧 {@code create()} 中的
     * {@code dataSources.put(ds, JdbcUtils.getDbTypeRaw(url, JdbcUtils.getDriverClassName(url)))}。
     *
     * @param ds          数据源名（如 {@code eova} / {@code main}）
     * @param url         JDBC URL
     * @param driverClass 驱动类名；可为 null（{@code getDbTypeRaw} 可从 URL 推导）
     * @return 推导出的 DbType（未识别时为 null）
     */
    public static DbType register(String ds, String url, String driverClass) {
        DbType type = JdbcUtils.getDbTypeRaw(url, driverClass);
        dataSources.put(ds, type);
        return type;
    }

    /**
     * 清空注册表（仅供测试隔离）
     */
    public static void clear() {
        dataSources.clear();
    }

    /** 方言族：对应旧 {@code buildDialect} 第一段链式判断的结果 */
    public enum DialectFamily {
        /** 默认分支，{@code EovaMysqlDialect} */
        MYSQL,
        /** {@code EovaOracleDialect} */
        ORACLE,
        /** {@code PostgreSqlDialect} */
        POSTGRESQL,
        /** {@code SqlServerDialect} */
        SQLSERVER
    }

    /**
     * 选基础方言族（对应旧 {@code buildDialect} 的第一段链）。
     *
     * <p><b>默认 MySQL</b>；{@code null} 与未列出的 DbType（含 <b>kingbase</b>、dm）
     * 都落默认分支 —— 这与旧实现一致，不是遗漏。
     *
     * @param dbType 数据库类型，可为 null
     * @return 方言族
     */
    public static DialectFamily baseDialectFamily(DbType dbType) {
        if (dbType == DbType.oracle) {
            return DialectFamily.ORACLE;
        }
        if (dbType == DbType.postgresql) {
            return DialectFamily.POSTGRESQL;
        }
        if (dbType == DbType.sqlserver) {
            return DialectFamily.SQLSERVER;
        }
        return DialectFamily.MYSQL;
    }

    /**
     * 商业 Mod 方言的包名段（对应旧 {@code buildDialect} 的 {@code try} 段）。
     *
     * <p>旧实现按 dbType 动态加载
     * {@code com.eova.mod.eova.<name>.<Name>{Convertor,QueryDialect,DefineDialect}}
     * （dm 还会加载 {@code <Name>Dialect}）；加载失败仅记日志，不影响启动。
     * 开源版不含这些 Mod，故实际恒为加载失败 —— 但<b>分支存在与否</b>是行为的一部分，
     * 故此处如实保留映射关系。
     *
     * @param dbType 数据库类型，可为 null
     * @return Mod 包名段；无对应 Mod 时返回 null
     */
    public static String businessDialectModName(DbType dbType) {
        if (dbType == DbType.oracle) {
            return "oracle";
        }
        if (dbType == DbType.postgresql) {
            return "postgresql";
        }
        if (dbType == DbType.sqlserver) {
            return "sqlserver";
        }
        if (dbType == DbType.dm) {
            return "dm";
        }
        return null;
    }

    /**
     * 取已注册数据源的只读快照（供验证判据与诊断使用，不参与运行时逻辑）
     */
    public static Map<String, DbType> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(dataSources));
    }
}
