/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.sql.ddl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import cn.eova.mod.EovaModUtil;
import cn.eova.sql.ddl.dialect.DefineDialect;

/**
 * <p>ported from: cn.eova.sql.ddl.DataDefine
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>DDL 定义读取（93 行）：从 mod 目录读 .sql 并解析成 DefineTable</li>
 *   <li>无宿主依赖、无内部替换（仅 JDK + EovaModUtil + DefineDialect）—— 逐字节即语义等价</li>
 *   <li>全树四通道无引用（第 59 轮审计），属按决策仍 port 的遗留单元</li>
 * </ol>
 */
public abstract class DataDefine {

    protected abstract String getDs();

    protected abstract List<DefineTable> createTable();

    protected abstract Set<String> dropTable();

    // protected abstract void createTableBefore(DefineDialect dd, List<String> sqls);

    // protected abstract void createTableAfter(DefineDialect dd, List<String> sqls);

    /**
     * 建表
     * @return
     */
    public void create() {
        List<String> sqls = new ArrayList<>();

        // 获取DDL方言
        DefineDialect dd = DefineDialectFactory.getDialect(getDs());

        // 建表
        createTable().forEach(t -> {
            sqls.add(dd.create(t));
        });

        EovaModUtil.executeSql(getDs(), sqls);
    }

    /**
     * 删表
     * @return
     */
    public void drop() {
        List<String> sqls = new ArrayList<>();
        DefineDialect dd = DefineDialectFactory.getDialect(getDs());

        for (String tableName : dropTable()) {
            sqls.add(dd.dropTable(tableName));
        }

        EovaModUtil.executeSql(getDs(), sqls);
    }

    /**
     * 生成建表DDL
     * @return
     */
    public void generateCreateDDL() {
        List<String> sqls = new ArrayList<>();

        // 获取DDL方言
        DefineDialect dd = DefineDialectFactory.getDialect(getDs());

        // 建表
        createTable().forEach(t -> {
            sqls.add(dd.create(t));
        });

        // 生成SQL日志
        StringBuffer sb = new StringBuffer();
        sqls.forEach(x -> sb.append(x).append("\n"));// 空行格式化

        System.out.println(sb.toString());
    }

    /**
     * 生成删表DDL
     * @return
     */
    public void generateDropDDL() {
        DefineDialect dd = DefineDialectFactory.getDialect(getDs());
        for (String tableName : dropTable()) {
            System.out.println(dd.dropTable(tableName));
        }
    }

}
