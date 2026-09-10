/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.sql.ddl;

import java.util.HashMap;

import cn.eova.sql.ddl.dialect.DefineDialect;
import cn.eova.sql.ddl.dialect.MysqlDefineDialect;

/**
 * DDL(data define language)数据查询方言方言工厂
 * @author Jieven
 *
 * <p>ported from: cn.eova.sql.ddl.DefineDialectFactory
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br><b>刻意保留的既有语义：</b>{@link #getDialect(String)} 在未注册时
 * <b>每次调用都 new 一个新的 {@code MysqlDefineDialect}</b>（不做缓存），
 * 且 {@code ds} 为 null 时同样走该分支。此行为由测试在环比对确认。
 */
public class DefineDialectFactory {

    private static HashMap<String, DefineDialect> defineDialectMap = new HashMap<>();

    /**
     * 取指定数据源的方言；未注册时回退为 MySQL 方言
     */
    public static DefineDialect getDialect(String ds) {
        DefineDialect dd = defineDialectMap.get(ds);
        if (dd == null) {
            // 为了方面测试, 默认指定为Mysql
            dd = new MysqlDefineDialect();
        }
        return dd;
    }

    /**
     * 注册指定数据源的方言
     */
    public static void addDialect(String ds, DefineDialect defineDialect) {
        defineDialectMap.put(ds, defineDialect);
    }

    private DefineDialect dialect = null;
    private String ds = null;

    /**
     * 构造：按数据源解析方言
     */
    public DefineDialectFactory(String ds) {
        this.ds = ds;
        this.dialect = getDialect(ds);
    }

    /**
     * 执行脚本
     * @param tables
     */
//	public void createTable(List<DefineTable> tables) {
//
//		// 生成DDL方言
//		List<String> sqls = new ArrayList<String>();
//		tables.forEach(t -> {
//			String s = dialect.create(t);
//			sqls.add("DROP TABLE IF EXISTS `" + t.getEn() + "`;");
//			sqls.add(s);
//		});
//
//		// 执行SQL
//		sqls.forEach(x -> System.out.println(x));
//		Db.use(ds).batch(sqls, sqls.size());
//	}

}
