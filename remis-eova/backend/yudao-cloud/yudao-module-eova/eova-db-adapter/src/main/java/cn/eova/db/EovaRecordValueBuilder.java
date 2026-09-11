/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

import cn.eova.common.utils.util.RegexUtil;
import cn.eova.config.EovaConfig;

/**
 * <p>ported from: cn.eova.ext.jfinal.OracleRecordBuilder#buildValue（旧 EOVA，逐行等价）
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：方法体与旧实现逐行一致，仅去掉 jfinal 包装类型。
 *
 * <p><b>为什么 port 的是"方法"而不是"那个类"（第 79 轮裁定，见 DES-002-R4 §r79）：</b>
 * 旧栈里这段逻辑的载体是 {@code OracleRecordBuilder extends com.jfinal.plugin.activerecord.RecordBuilder}，
 * 它由 {@code EovaOracleDialect} 挂在 {@code recordBuilder} 字段上，**由 jfinal 的 AR 在查询时回调**。
 * 新栈没有 jfinal 的 AR 查询链（{@link JdbcEovaDbGateway} 自己读 {@code ResultSet}），
 * 因此"那个子类"没有回调点、port 出来就是死代码（同 {@code EovaMysqlDialect} 的裁定）；
 * 而<b>它承载的类型判定语义是功能</b>，必须保住 —— 故按"方法级 port"落到本类，
 * 由 {@link JdbcEovaDbGateway} 在装配记录时调用。</p>
 *
 * <p><b>它解决什么（否则会丢的行为）：</b>Oracle 的 JDBC 驱动把几乎所有列都报成
 * {@code Types.NUMERIC}、并让 {@code getObject} 返回 {@code BigDecimal}：
 * <ol>
 *   <li>{@code NUMBER(p,0)} 会退化成 {@code BigDecimal}（前端拿到 {@code 1} vs {@code 1.00} 的差别）；</li>
 *   <li>{@code CLOB/BLOB} 会返回驱动对象而非字符串/字节；</li>
 *   <li>业务注册的 {@link EovaConfig#getConvertor(String)} 转换器（如 {@code NUMBER(1) -> Boolean}）
 *       根本不会被调用。</li>
 * </ol>
 * 精度判定<b>先看驱动报的 precision/scale，再对"聚合函数列"用字符串值兜底</b>
 * （聚合列 precision=0/scale=0 会丢精度，故用 {@code "123.45"} 这类字面形态反推）。</p>
 *
 * <p><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>{@code value == null} 时 <b>continue</b>（不写入 columns）—— 与 jfinal 默认
 *       {@code RecordBuilder}（null 也 put）<b>不同</b>。故本方法只在"旧栈会走
 *       OracleRecordBuilder"的数据源上启用，见 {@link JdbcEovaDbGateway} 的分支。</li>
 *   <li>精度反推只在字符串形如 {@code ^\d+\.\d+$} 时生效（{@code RegexUtil.isTrue}）。</li>
 *   <li>{@code NUMBER} 的分档是 {@code p<=10 → int}、{@code p<=18 → long}、否则 {@code BigDecimal}；
 *       小数列按 {@code p+s<=8 → float}、{@code p+s<=16 → double}、否则 {@code BigDecimal}。
 *       <b>禁止"顺手"改成统一 BigDecimal</b> —— 那会改变前端列渲染与 JSON 形态。</li>
 *   <li>{@code Types.TIMESTAMP} 用 {@code getTimestamp}（Oracle 的 DATE 也报 93）、
 *       {@code Types.DATE} 用 {@code getDate}、{@code CLOB/NCLOB} 走
 *       CLOB/NCLOB 走 {@code ModelBuilder.handleClob}、{@code BLOB} 走 {@code ModelBuilder.handleBlob}
 *       —— 这两个 jfinal 工具方法已<b>就地逐字节 port</b>到本类（见 {@link #handleClob}/{@link #handleBlob}）：db-adapter 的 jfinal 依赖是 <b>test 作用域</b>，生产代码不得引用 jfinal（ARCH 约束）。</li>
 *   <li>其余类型交给 {@code EovaConfig.getConvertor(ds).convertValue(value, type)}
 *       —— 转换器未注册时 {@code getConvertor} 返回默认转换器（见 {@code EovaConfig}）。</li>
 * </ol>
 */
public final class EovaRecordValueBuilder {

    private EovaRecordValueBuilder() {
    }

    /**
     * 把结果集当前行的值按"精准类型"写入 columns（旧 {@code OracleRecordBuilder.buildValue} 逐行等价）。
     *
     * @param ds         数据源名（供 {@code EovaConfig.getConvertor(ds)} 使用）
     * @param rs         结果集（已定位到当前行）
     * @param rsmd       元数据
     * @param columnCount 列数
     * @param labelNames 列名数组（下标从 1 开始）
     * @param types      列类型数组（下标从 1 开始，来自 {@code buildLabelNamesAndTypes}）
     * @param columns    目标列容器
     * @throws SQLException 取值失败
     */
    public static void buildValue(String ds, ResultSet rs, ResultSetMetaData rsmd, int columnCount,
                                  String[] labelNames, int[] types, Map<String, Object> columns)
            throws SQLException {
        for (int i = 1; i <= columnCount; i++) {
            Object value = rs.getObject(i);
            if (value == null) {
                continue;
            }

            if (types[i] == Types.NUMERIC) {
                int p = rsmd.getPrecision(i);// 整数位
                int s = rsmd.getScale(i);// 小数

                // 聚合函数获取数据，Precision=0，Scale=0，会出现丢失精度问题, 所以需要通过字符串值获取精度
                String val = value.toString();
                if (RegexUtil.isTrue("^\\d+\\.\\d+$", val)) {
                    String[] ss = val.split("\\.");
                    p = ss[0].length();
                    s = ss[1].length();
                }

                if (s == 0) {
                    if (p <= 10) {
                        value = rs.getInt(i);
                    } else if (p <= 18) {
                        value = rs.getLong(i);
                    } else {
                        value = rs.getBigDecimal(i);
                    }
                } else {
                    if (p + s <= 8) {
                        value = rs.getFloat(i);
                    } else if (p + s <= 16) {
                        value = rs.getDouble(i);
                    } else {
                        value = rs.getBigDecimal(i);
                    }
                }
            } else {
                if (types[i] == Types.TIMESTAMP) {// Oracle Date dbtype=93=timestamp
                    // 只能在前台根据控件做格式化显示
                    value = rs.getTimestamp(i);
                } else if (types[i] == Types.DATE) {
                    value = rs.getDate(i);
                } else if (types[i] == Types.CLOB) {
                    value = handleClob(rs.getClob(i));
                } else if (types[i] == Types.NCLOB) {
                    value = handleClob(rs.getNClob(i));
                } else if (types[i] == Types.BLOB) {
                    value = handleBlob(rs.getBlob(i));
                } else {
                    // Eova Oracle 特殊处理
                    value = EovaConfig.getConvertor(ds).convertValue(rs.getObject(i), types[i]);
                }
            }

            columns.put(labelNames[i], value);
        }
    }

    /**
     * 装配列名与列类型（逐字节等价 jfinal {@code RecordBuilder.buildLabelNamesAndTypes}）。
     *
     * <p>旧字节码的循环是 {@code for (int i = 1; i < labelNames.length; i++)}——
     * <b>下标从 1 开始、到数组长度前一位</b>（数组按 {@code columnCount + 1} 分配）：
     * 故 {@code labelNames[0]}/{@code types[0]} 恒为默认值，属既有语义。</p>
     *
     * @param rsmd       元数据
     * @param labelNames 列名数组（长度须为 columnCount+1）
     * @param types      列类型数组（长度须为 columnCount+1）
     * @throws SQLException 取元数据失败
     */
    public static void buildLabelNamesAndTypes(ResultSetMetaData rsmd, String[] labelNames, int[] types)
            throws SQLException {
        for (int i = 1; i < labelNames.length; i++) {
            labelNames[i] = rsmd.getColumnLabel(i);
            types[i] = rsmd.getColumnType(i);
        }
    }

    /**
     * Clob → String（逐字节等价 jfinal {@code ModelBuilder.handleClob}）。
     *
     * @param clob 输入（可为 null）
     * @return 文本；null 入参返回 null
     * @throws SQLException 读取失败
     */
    public static String handleClob(Clob clob) throws SQLException {
        return clob == null ? null : clob.getSubString(1, (int) clob.length());
    }

    /**
     * Blob → byte[]（逐字节等价 jfinal {@code ModelBuilder.handleBlob}）。
     *
     * <p>既有语义逐条：null 入参 ⇒ null；{@code getBinaryStream()} 返回 null ⇒ null；
     * 长度 0 ⇒ null；{@code is.read(bytes)} 的<b>返回值被忽略</b>（不校验读满）；
     * 流在 finally 关闭，关闭失败包成 {@code RuntimeException}。</p>
     *
     * @param blob 输入（可为 null）
     * @return 字节；上述任一 null 分支返回 null
     * @throws SQLException 取流/长度失败
     */
    public static byte[] handleBlob(Blob blob) throws SQLException {
        if (blob == null) {
            return null;
        }
        InputStream is = null;
        try {
            is = blob.getBinaryStream();
            if (is == null) {
                return null;
            }
            byte[] bytes = new byte[(int) blob.length()];
            if (bytes.length == 0) {
                return null;
            }
            is.read(bytes);
            return bytes;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
