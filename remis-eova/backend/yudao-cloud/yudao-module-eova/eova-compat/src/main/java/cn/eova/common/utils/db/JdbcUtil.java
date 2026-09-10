/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.db;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * <p>ported from: cn.eova.common.utils.db.JdbcUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>R17：常量 `com.mysql.jdbc.Driver` 为旧 MySQL 驱动类名（非 `com.mysql.cj.jdbc.Driver`），属既有缺陷，原样保留；驱动类名硬编码在 R17 单独处置</li>
 * </ol>
 */
/**
 * JDBC工具类
 *
 * @author Jieven
 * @date 2014-9-12
 */
public class JdbcUtil {

    /**
     * 获取数据库连接,返回提示信息
     *
     * @param url
     * @param username
     * @param password
     * @return
     */
    public static String initConnection(String url, String username, String password) {
        // String oracleDrivers = "oracle.jdbc.driver.OracleDriver";
        String mysqlDrivers = "com.mysql.jdbc.Driver";
        System.setProperty("jdbc.drivers", mysqlDrivers);
        try {
            Class.forName(mysqlDrivers);
            Connection conn = DriverManager.getConnection(url, username, password);
            if (conn == null) {
                return "创建JDBC连接失败";
            }
            return null;
        } catch (Exception ex) {
            ex.printStackTrace();
            return ex.getMessage();
        }
    }

}