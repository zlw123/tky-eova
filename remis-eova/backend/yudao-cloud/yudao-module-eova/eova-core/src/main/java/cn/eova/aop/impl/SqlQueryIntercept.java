/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 *
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.aop.impl;

import java.util.Map;

import cn.eova.aop.AopContext;
import cn.eova.aop.MetaObjectIntercept;
import cn.eova.engine.ExpUtil;
import cn.eova.compat.jfinal.kit.LegacyKv;

/**
 * <p>ported from: cn.eova.aop.impl.SqlQueryIntercept
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>SQL 查询业务拦截器（51 行）：把自定义 SQL 注入元对象查询</li>
 * </ol>
 */
/**
 * 通用SQL查询解析
 * <pre>
 * eova_object.view_sql 中配置SQL
 * </pre>
 * @author Jieven
 *
 */
public class SqlQueryIntercept extends MetaObjectIntercept {

    @Override
    public void queryBefore(AopContext ac) throws Exception {

        String sql = ac.object.getStr("view_sql");

        // 语法
        // FROM xxx where vsi.sid = ${sid} and '${start_v_day} 00:00:00' <= update_time and update_time < '${end_v_day} 23:59:59'");

        // 循环撸参
        Map<String, String[]> paraMap = ac.ctrl.getParaMap();

        LegacyKv kv = new LegacyKv();
        paraMap.forEach((key, value) -> {
            kv.set(key, value[0]);
        });
        // 循环参数
        kv.set("user", ac.user);

        // 解析查询值 + 用户值
        sql = ExpUtil.parseSql(sql, kv);

        ac.sql = sql;
    }


}