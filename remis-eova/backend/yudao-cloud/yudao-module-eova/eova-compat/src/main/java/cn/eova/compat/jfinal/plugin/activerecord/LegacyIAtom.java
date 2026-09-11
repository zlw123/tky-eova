/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.plugin.activerecord;

import java.sql.SQLException;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.plugin.activerecord.IAtom} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.plugin.activerecord.IAtom}（jfinal 5.2.6）。</p>
 *
 * <p><b>语义：</b>事务体。{@code run()} 的<b>布尔返回值决定提交还是回滚</b> ——
 * 这与本工程的 {@code EovaDbGateway.Atom}（异常驱动：正常返回即提交）是<b>两套语义</b>，
 * 故必须单列一个接缝，不能合并（第 74 轮由 {@code GridController}/{@code TableController}
 * 等 5 个单元的真实用法逼出）。</p>
 *
 * <p><b>逐条取自 jfinal {@code DbPro.tx(Config, int, IAtom)} 字节码：</b></p>
 * <ul>
 *   <li>最外层：执行体返回 true ⇒ {@code commit}；返回 false ⇒ {@code rollback}；两种都返回该布尔值；</li>
 *   <li>嵌套（外层已在本线程事务中）：返回 true ⇒ 直接返回；返回 false ⇒ 抛
 *       {@link LegacyNestedTransactionHelpException}，消息为
 *       {@code "Notice the outer transaction that the nested transaction return false"} ——
 *       由最外层捕获后<b>回滚并静默返回 false</b>；</li>
 *   <li>嵌套分支里的 {@code SQLException} 包成 ActiveRecordException。</li>
 * </ul>
 */
@FunctionalInterface
public interface LegacyIAtom {

    /**
     * 事务体。
     *
     * @return true 表示提交，false 表示回滚
     * @throws SQLException SQL 异常
     */
    boolean run() throws SQLException;
}
