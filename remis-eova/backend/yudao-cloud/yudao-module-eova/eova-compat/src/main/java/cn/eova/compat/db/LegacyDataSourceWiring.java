/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.eova.compat.jfinal.plugin.LegacyPlugin;

/**
 * 数据源**宿主装配**接缝（第 86 轮，为 port {@code EovaDataSource.create} 的可移植半部分而设）。
 *
 * <p><b>切分依据（逐行读旧 {@code EovaDataSource.create}，旧 68-110 行）：</b>旧方法做两件事 ——
 * <ol>
 *   <li><b>可移植半部分（业务语义）</b>：读 {@code db.datasource}（逗号分隔、逐个 trim）、
 *       读每个 ds 的 {@code url/user/pwd/driver/filters}（filters 默认 {@code "log4j,stat,wall"}）、
 *       按 {@code db.pwd.encrypt} 决定是否 AES 解密、把 DbType 写进注册表
 *       （{@code JdbcUtils.getDbTypeRaw(url, getDriverClassName(url))}）、两条**中文错误消息**与 info 日志。
 *       这些与"谁建连接池"无关，属 EOVA 配置契约。</li>
 *   <li><b>宿主装配半部分（host wiring）</b>：{@code initDruidPlugin(...)} 建 Druid 池、
 *       {@code initActiveRecordPlugin(...)} 建 ARP、{@code plugins.add(dp).add(arp)}、
 *       {@code EovaConfig.arps.put(ds, arp)}。新树里连接池由 Spring Boot 装配
 *       （§4 约束 2：只有 {@code eova-db-adapter} 接触数据源与事务），ARP 由
 *       {@code LegacyActiveRecordPlugin} + {@code JdbcEovaDbGateway} 取代。</li>
 * </ol>
 *
 * <p><b>为什么不做成"注释掉 host 那半"：</b>那会让"数据源装配"在 port 里静默消失，
 * 而它是 {@code AuthInterceptor → EovaDataSource.getDbType → 网关取 ds} 那条链的起点。
 * 故本接缝把 host 半部分**显式化**：每个数据源生成一个 {@link Spec} 并登记到一个
 * {@link LegacyPlugin}，由宿主（Spring 配置）在装配期读取。</p>
 *
 * <p><b>未安装宿主装配器时的行为（已声明）</b>：{@code start()} 只记 debug 日志并返回 true ——
 * 即"配置已被解析、DbType 已注册，但连接池尚未由宿主装配"。这条状态可被
 * {@link #specs()} 观测，不会静默丢失。</p>
 */
public final class LegacyDataSourceWiring {

    /** 数据源坐标（旧 create 里解析出的内容） */
    public static class Spec {

        private final String ds;

        private final String url;

        private final String user;

        private final String pwd;

        private final String driver;

        private final String filters;

        /**
         * @param ds      数据源名
         * @param url     JDBC URL
         * @param user    用户名
         * @param pwd     密码（已按配置决定是否解密）
         * @param driver  驱动类名
         * @param filters Druid filters
         */
        public Spec(String ds, String url, String user, String pwd, String driver, String filters) {
            this.ds = ds;
            this.url = url;
            this.user = user;
            this.pwd = pwd;
            this.driver = driver;
            this.filters = filters;
        }

        /** 取数据源名 */
        public String getDs() {
            return ds;
        }

        /** 取 JDBC URL */
        public String getUrl() {
            return url;
        }

        /** 取用户名 */
        public String getUser() {
            return user;
        }

        /** 取密码 */
        public String getPwd() {
            return pwd;
        }

        /** 取驱动类名 */
        public String getDriver() {
            return driver;
        }

        /** 取 Druid filters */
        public String getFilters() {
            return filters;
        }
    }

    /** 已解析的数据源坐标（按解析顺序） */
    private static final List<Spec> SPECS = new ArrayList<>();

    private LegacyDataSourceWiring() {
    }

    /**
     * 登记一个数据源坐标（由 ported {@code EovaDataSource.create} 调用）。
     *
     * @param spec 坐标
     */
    public static void add(Spec spec) {
        SPECS.add(spec);
    }

    /**
     * 取已解析的数据源坐标。
     *
     * @return 不可变列表
     */
    public static List<Spec> specs() {
        return Collections.unmodifiableList(SPECS);
    }

    /** 清空（供判据隔离） */
    public static void clear() {
        SPECS.clear();
    }

}
