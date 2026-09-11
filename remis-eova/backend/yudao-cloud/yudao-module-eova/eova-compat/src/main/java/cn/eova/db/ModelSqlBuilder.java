/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import cn.eova.compat.table.TableMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型层 SQL 生成：等价于 jfinal 5.2.6 的
 * {@code com.jfinal.plugin.activerecord.dialect.MysqlDialect}
 * 的 {@code forModelSave} / {@code forModelUpdate} / {@code forModelDeleteById}。
 *
 * <p>ported from: com.jfinal.plugin.activerecord.dialect.MysqlDialect（第三方制品）
 * <br>source artifact: com.jfinal:jfinal:5.2.6
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 *
 * <p><b>为什么单独成类而不是塞进 gateway：</b>旧栈的 SQL 形态由 <b>Dialect</b> 决定
 * （EOVA 还派生了 {@code EovaMysqlDialect}/{@code EovaOracleDialect}），
 * 而 gateway 只负责执行。把"生成"与"执行"分开，将来补 Kingbase/Oracle 方言时
 * 只需新增一个 builder，不动执行路径 —— 与 §4 的方言抽象层一致。
 *
 * <p><b>实测的旧规则（据 MysqlDialect 完整方法体，非片段推断 —— R43）：</b>
 * <ol>
 *   <li><b>save</b>：{@code insert into `表`(`列`,...) values(?,...)} ——
 *       <b>只遍历当前已有属性</b>（不是表的所有列），
 *       <b>跳过不是表列的键</b>（不报错），
 *       <b>null 值照常作为参数写入</b>（不过滤 null）。</li>
 *   <li><b>update</b>：{@code update `表` set `列` = ?, ... where 主键 = ?} ——
 *       只更新<b>在 modifyFlag 中</b>的列，且<b>跳过主键列</b>与<b>非表列</b>。</li>
 *   <li>标识符一律用反引号引用 —— 这不是装饰：列名/表名撞上 SQL 保留字时，
 *       不加反引号会生成非法 SQL，而旧实现不会。</li>
 *   <li>{@code set} 子句的首列前不加逗号（用"参数已加入"判断，而不是列下标），
 *       故被跳过的列不会留下悬空逗号。</li>
 * </ol>
 *
 * <p><b>与旧实现的一处有意差异（已声明）：</b>旧 {@code forModelUpdate} 在
 * 没有任何列可更新时会生成 {@code update `表` set where ...} 这种残缺 SQL；
 * 本实现直接抛 {@link IllegalStateException} 明确报错。
 * 但调用方 {@link EovaModel#update()} 已按旧语义在 modifyFlag 为空时
 * <b>提前返回 false</b>，故该分支实际不可达。
 */
public final class ModelSqlBuilder {

    private ModelSqlBuilder() {
    }

    /** SQL 与其参数 */
    public static final class Sql {

        private final String sql;
        private final List<Object> paras;

        Sql(String sql, List<Object> paras) {
            this.sql = sql;
            this.paras = paras;
        }

        /** SQL 文本 */
        public String sql() {
            return sql;
        }

        /** 参数（顺序与占位符一致） */
        public Object[] paras() {
            return paras.toArray();
        }

        /** 参数列表（只读视图） */
        public List<Object> parasList() {
            return new ArrayList<>(paras);
        }

        @Override
        public String toString() {
            return sql + " <- " + paras;
        }
    }

    /**
     * 生成 insert（对应旧 {@code MysqlDialect.forModelSave}）
     *
     * @param table 表元数据
     * @param attrs 当前属性（<b>只有已设置的列会进 SQL</b>）
     * @return SQL 与参数
     */
    public static Sql forModelSave(TableMetadata table, Map<String, Object> attrs) {
        StringBuilder names = new StringBuilder("insert into `")
                .append(table.getName()).append("`(");
        StringBuilder marks = new StringBuilder(") values(");
        List<Object> paras = new ArrayList<>();
        for (Map.Entry<String, Object> e : attrs.entrySet()) {
            String col = e.getKey();
            if (!table.hasColumnLabel(col)) {
                // 非表列直接跳过（旧实现如此），不抛异常
                continue;
            }
            if (!paras.isEmpty()) {
                names.append(",");
                marks.append(",");
            }
            names.append('`').append(col).append('`');
            marks.append('?');
            // null 照常写入 —— 旧实现不过滤 null
            paras.add(e.getValue());
        }
        return new Sql(names.append(marks).append(")").toString(), paras);
    }

    /**
     * 生成 update（对应旧 {@code MysqlDialect.forModelUpdate}）
     *
     * @param table      表元数据
     * @param attrs      当前属性
     * @param modifyFlag 已修改列（<b>只有这里的列会被更新</b>）
     * @param idValue    主键值（生成 where 条件）
     * @return SQL 与参数
     */
    public static Sql forModelUpdate(TableMetadata table, Map<String, Object> attrs,
                                     Set<String> modifyFlag, Object idValue) {
        String[] pks = table.getPrimaryKey();
        if (pks.length == 0) {
            throw new IllegalStateException("表 [" + table.getName() + "] 无主键，无法生成 update");
        }
        StringBuilder sql = new StringBuilder("update `")
                .append(table.getName()).append("` set");
        List<Object> paras = new ArrayList<>();
        for (Map.Entry<String, Object> e : attrs.entrySet()) {
            String col = e.getKey();
            if (!modifyFlag.contains(col)) {
                continue;
            }
            if (isPrimaryKey(col, pks)) {
                continue;
            }
            if (!table.hasColumnLabel(col)) {
                continue;
            }
            if (!paras.isEmpty()) {
                sql.append(",");
            }
            sql.append('`').append(col).append("` = ?");
            paras.add(e.getValue());
        }
        if (paras.isEmpty()) {
            throw new IllegalStateException(
                    "表 [" + table.getName() + "] 没有任何可更新列（modifyFlag 与属性不匹配）——"
                            + "旧实现会生成残缺 SQL；调用方应在 modifyFlag 为空时提前返回");
        }
        sql.append(" where `").append(pks[0]).append("` = ?");
        paras.add(idValue);
        return new Sql(sql.toString(), paras);
    }

    /**
     * 生成按主键删除（对应旧 {@code MysqlDialect.forModelDeleteById}）
     *
     * @param table   表元数据
     * @param idValue 主键值
     * @return SQL 与参数
     */
    public static Sql forModelDeleteById(TableMetadata table, Object idValue) {
        String[] pks = table.getPrimaryKey();
        if (pks.length == 0) {
            throw new IllegalStateException("表 [" + table.getName() + "] 无主键，无法按主键删除");
        }
        List<Object> paras = new ArrayList<>();
        paras.add(idValue);
        return new Sql("delete from `" + table.getName() + "` where `" + pks[0] + "` = ?", paras);
    }

    /** 是否主键列（大小写不敏感，与表元数据的列判定一致） */
    private static boolean isPrimaryKey(String column, String[] pks) {
        for (String pk : pks) {
            if (pk.equalsIgnoreCase(column)) {
                return true;
            }
        }
        return false;
    }

    /** 供验证判据构造属性映射（保序） */
    /**
     * 去掉 SQL 中的 ORDER BY 子句（对应 jfinal {@code Dialect.replaceOrderBy(String)}）。
     *
     * <p><b>正则逐字取自旧制品常量池</b>（{@code Dialect$Holder.ORDER_BY_PATTERN}）：
     * {@code order\s+by\s+[^,\s]+(\s+asc|\s+desc)?(\s*,\s*[^,\s]+(\s+asc|\s+desc)?)*}，
     * 编译标志 {@code CASE_INSENSITIVE | MULTILINE}，替换为空串。</p>
     *
     * <p><b>三条实测行为（直接跑旧制品得到，勿凭直觉"修正"）：</b></p>
     * <ol>
     *   <li>{@code "select * from t order by id desc"} ⇒ {@code "select * from t "}
     *       —— <b>保留 order 前的空格</b>（模式不含前导空格）；</li>
     *   <li>子查询中的 {@code order by} 也会被去掉，且列名模式 {@code [^,\s]+}
     *       会<b>吞掉紧跟的 {@code )}</b>：{@code "select * from (select * from t order by id) x order by y"}
     *       ⇒ {@code "select * from (select * from t  x "}；</li>
     *   <li>没有 ORDER BY 时原样返回。</li>
     * </ol>
     *
     * @param sql 原 SQL
     * @return 去掉 ORDER BY 后的 SQL
     */
    public static String replaceOrderBy(String sql) {
        return ORDER_BY_PATTERN.matcher(sql).replaceAll("");
    }

    /** ORDER BY 模式（逐字取自旧制品 {@code Dialect$Holder} 的常量池） */
    private static final java.util.regex.Pattern ORDER_BY_PATTERN =
            java.util.regex.Pattern.compile(
                    "order\\s+by\\s+[^,\\s]+(\\s+asc|\\s+desc)?"
                            + "(\\s*,\\s*[^,\\s]+(\\s+asc|\\s+desc)?)*",
                    java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.MULTILINE);

    public static Map<String, Object> orderedMap() {
        return new LinkedHashMap<>();
    }
}
