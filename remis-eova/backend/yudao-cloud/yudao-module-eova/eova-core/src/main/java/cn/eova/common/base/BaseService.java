/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.base;

import java.util.List;

import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.common.base.BaseService
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>⚠️ 既有缺陷（R32：必须保留，不得顺手修）：queryByCache(sql, paras) 里把参数拼到了【sql】而不是【key】（`sql += "_" + obj`），于是传给 findByCache 的 SQL 变成 "select ... ?_p1"，属【非法 SQL】。原样保留；已在计划中登记为待决策的既有缺陷。</li>
 *   <li>queryByCache(sql) 用 SQL 本身作缓存键</li>
 *   <li>updateAddN 直接拼 SQL（非参数化）—— 入参为内部常量场景，原样保留</li>
 * </ol>
 */
/**
 * 基础通用数据访问操作
 * @author Jieven
 *
 */
public class BaseService {

    /**
     * 查询自动缓存
     * @param sql
     * @return
     */
    public List<EovaRecord> queryByCache(String sql) {
        // 查询SQL作为Key值
        return EovaGateways.findByCache(BaseCache.SER, sql, sql);
    }

    /**
     * 查询自动缓存
     * @param sql
     * @param paras
     * @return
     */
    public List<EovaRecord> queryByCache(String sql, Object... paras) {
        // sql_xx_xx_xx
        String key = sql;
        for (Object obj : paras) {
            sql += "_" + obj.toString();
        }
        return EovaGateways.findByCache(BaseCache.SER, key, sql, paras);
    }

    /**
     * 字段数值+N
     * @param table 表名
     * @param field 字段名
     * @param pk 主键名
     * @param pkValue 主键值
     * @param num 数值
     * @return
     */
    public int updateAddN(String table, String field, String pk, int pkValue, int num) {
        StringBuilder sb = new StringBuilder();
        sb.append("update ");
        sb.append(table);
        sb.append(" set ");
        sb.append(field);
        sb.append(" = ");
        sb.append(field);
        sb.append(" + ");
        sb.append(num);
        sb.append(" where ");
        sb.append(pk);
        sb.append(" = ");
        sb.append(pkValue);
        return EovaGateways.update(sb.toString());
    }

    /**
     * 字段数值+1
     * @param table 表名
     * @param field 字段名
     * @param pk 主键名
     * @param pkValue 主键值
     * @return
     */
    public int updateAdd1(String table, String field, String pk, int pkValue) {
        return updateAddN(table, field, pk, pkValue, 1);
    }
}