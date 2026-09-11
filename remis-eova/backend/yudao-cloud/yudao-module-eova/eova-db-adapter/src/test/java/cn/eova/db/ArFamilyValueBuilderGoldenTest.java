/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.eova.common.utils.db.SqlUtil;
import cn.eova.config.EovaConfig;
import cn.eova.config.EovaConst;
import cn.eova.config.EovaDataSource;
import com.alibaba.druid.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR 族"方法级 port"的判据（第 79 轮）：{@link EovaRecordValueBuilder}（旧
 * {@code OracleRecordBuilder.buildValue}）+ 旧 {@code EovaDbPro} 的三处方言行为
 * （Oracle 序列 / 关键字转义 / 记录装配分支）。
 *
 * <p><b>为什么这一轮是"port 方法而不是 port 类"：</b>旧栈的
 * {@code OracleRecordBuilder}/{@code OracleModelBuilder}/{@code EovaOracleDialect}/{@code EovaDbPro}
 * 都是 <b>jfinal 扩展点</b>（{@code extends RecordBuilder/DbPro}…），由 jfinal 的 AR
 * 在查询/写库时回调。新栈没有 jfinal 的 AR 链（{@link JdbcEovaDbGateway} 自己读
 * {@code ResultSet}、自己拼 SQL），故那四个"子类"没有回调点 —— port 出来即死代码。
 * 但它们承载的<b>语义</b>必须保住，于是按方法级落到本判据的目标类里。</p>
 *
 * <p><b>判据为什么能用"无库"方式覆盖：</b>类型判定只依赖
 * {@code ResultSetMetaData} 的 label/type/precision/scale 与 {@code ResultSet} 的取值口，
 * 故用动态代理造出行即可逐格比对；关键字转义与 Oracle 序列都是**纯字符串变换**，
 * 只差一次 {@code prepareStatement} 的入参捕获。</p>
 */
class ArFamilyValueBuilderGoldenTest {

    /** 一列的规格：列名 / JDBC 类型 / precision / scale / 值 */
    static final class Col {
        final String label;
        final int type;
        final int precision;
        final int scale;
        final Object value;

        /**
         * @param label     列名
         * @param type      {@link Types}
         * @param precision 精度（NUMERIC 用）
         * @param scale     小数位（NUMERIC 用）
         * @param value     值
         */
        Col(String label, int type, int precision, int scale, Object value) {
            this.label = label;
            this.type = type;
            this.precision = precision;
            this.scale = scale;
            this.value = value;
        }
    }

    /** 造行（列规格列表） */
    private static Col col(String label, int type, int precision, int scale, Object value) {
        return new Col(label, type, precision, scale, value);
    }

    /**
     * 造 ResultSet 替身（单行）。
     *
     * @param cols 列规格
     * @return 替身
     */
    private static ResultSet resultSet(List<Col> cols) {
        Flag first = new Flag();
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getMetaData":
                    return metaData(cols);
                case "next":
                    return first.consume();
                case "getObject":
                    return cols.get((Integer) args[0] - 1).value;
                case "getInt":
                    return ((Number) cols.get((Integer) args[0] - 1).value).intValue();
                case "getLong":
                    return ((Number) cols.get((Integer) args[0] - 1).value).longValue();
                case "getBigDecimal":
                    return new BigDecimal(cols.get((Integer) args[0] - 1).value.toString());
                case "getFloat":
                    return ((Number) cols.get((Integer) args[0] - 1).value).floatValue();
                case "getDouble":
                    return ((Number) cols.get((Integer) args[0] - 1).value).doubleValue();
                case "getTimestamp":
                    return cols.get((Integer) args[0] - 1).value;
                case "getDate":
                    return cols.get((Integer) args[0] - 1).value;
                case "getClob":
                    return cols.get((Integer) args[0] - 1).value;
                case "getNClob":
                    return cols.get((Integer) args[0] - 1).value;
                case "getBlob":
                    return cols.get((Integer) args[0] - 1).value;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (ResultSet) Proxy.newProxyInstance(
                ArFamilyValueBuilderGoldenTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, h);
    }

    /** 单次 consumed 标记 */
    static final class Flag {
        private boolean used;

        /**
         * 第一次 true，之后 false。
         *
         * @return 是否还有行
         */
        boolean consume() {
            if (used) {
                return false;
            }
            used = true;
            return true;
        }
    }

    /**
     * 造 ResultSetMetaData 替身。
     *
     * @param cols 列规格
     * @return 替身
     */
    private static ResultSetMetaData metaData(List<Col> cols) {
        InvocationHandler h = (p, m, args) -> {
            // 注意：getColumnCount() 无参 ⇒ args 为 null，故索引必须在需要的分支里再算
            switch (m.getName()) {
                case "getColumnCount":
                    return cols.size();
                case "getColumnLabel":
                    return cols.get((Integer) args[0] - 1).label;
                case "getColumnType":
                    return cols.get((Integer) args[0] - 1).type;
                case "getPrecision":
                    return cols.get((Integer) args[0] - 1).precision;
                case "getScale":
                    return cols.get((Integer) args[0] - 1).scale;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (ResultSetMetaData) Proxy.newProxyInstance(
                ArFamilyValueBuilderGoldenTest.class.getClassLoader(),
                new Class<?>[]{ResultSetMetaData.class}, h);
    }

    /** Clob 替身 */
    private static Clob clob(String text) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "length":
                    return (long) text.length();
                case "getSubString": {
                    int pos = (int) (long) (Long) args[0];
                    int len = (Integer) args[1];
                    return text.substring(pos - 1, pos - 1 + len);
                }
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        // 同时实现 Clob 与 NClob：rs.getNClob() 的返回类型是 NClob，只实现 Clob 会 ClassCastException
        return (Clob) Proxy.newProxyInstance(
                ArFamilyValueBuilderGoldenTest.class.getClassLoader(),
                new Class<?>[]{Clob.class, java.sql.NClob.class}, h);
    }

    /** Blob 替身 */
    private static Blob blob(byte[] bytes) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "length":
                    return (long) bytes.length;
                case "getBinaryStream":
                    return new ByteArrayInputStream(bytes);
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (Blob) Proxy.newProxyInstance(
                ArFamilyValueBuilderGoldenTest.class.getClassLoader(),
                new Class<?>[]{Blob.class}, h);
    }

    /** 造记录：把一行按 buildValue 装配进 map */
    private static Map<String, Object> build(List<Col> cols) throws Exception {
        return build(cols, "main");
    }

    /** 指定 ds 的装配（转换器按 ds 取，故其它类型分支需要一个已注册转换器的 ds） */
    private static Map<String, Object> build(List<Col> cols, String ds) throws Exception {
        ResultSet rs = resultSet(cols);
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        String[] labels = new String[n + 1];
        int[] types = new int[n + 1];
        EovaRecordValueBuilder.buildLabelNamesAndTypes(md, labels, types);
        Map<String, Object> out = new LinkedHashMap<>();
        while (rs.next()) {
            EovaRecordValueBuilder.buildValue(ds, rs, md, n, labels, types, out);
        }
        return out;
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        savedDbTypes.putAll(EovaDataSource.map());
        EovaDataSource.map().clear();
    }

    @AfterEach
    void tearDown() {
        EovaDataSource.map().clear();
        EovaDataSource.map().putAll(savedDbTypes);
        cn.eova.tools.x.conf.getProps().remove("db.keyword");
    }

    private final Map<String, DbType> savedDbTypes = new HashMap<>();

    // ------------------------------------------------------------ 类型判定（旧 OracleRecordBuilder.buildValue）

    @Test
    @DisplayName("NUMBER 整数位分档：p<=10 → Integer，p<=18 → Long，否则 BigDecimal")
    void numericIntegerBuckets() throws Exception {
        Map<String, Object> row = build(List.of(
                col("c10", Types.NUMERIC, 10, 0, new BigDecimal("123")),
                col("c11", Types.NUMERIC, 11, 0, new BigDecimal("123")),
                col("c18", Types.NUMERIC, 18, 0, new BigDecimal("123")),
                col("c19", Types.NUMERIC, 19, 0, new BigDecimal("123"))));
        assertInstanceOf(Integer.class, row.get("c10"), "p<=10 必须是 Integer");
        assertInstanceOf(Long.class, row.get("c11"), "10<p<=18 必须是 Long");
        assertInstanceOf(Long.class, row.get("c18"));
        assertInstanceOf(BigDecimal.class, row.get("c19"), "p>18 必须退回 BigDecimal");
        assertEquals(Integer.valueOf(123), row.get("c10"));
    }

    @Test
    @DisplayName("NUMBER 小数位分档：p+s<=8 → Float，<=16 → Double，否则 BigDecimal")
    void numericDecimalBuckets() throws Exception {
        Map<String, Object> row = build(List.of(
                col("f", Types.NUMERIC, 4, 2, new BigDecimal("12.34")),
                col("d", Types.NUMERIC, 10, 4, new BigDecimal("123456789.123456")),
                col("bd", Types.NUMERIC, 20, 10, new BigDecimal("1234567890123456.123456"))));
        // 注意：驱动报的 precision/scale 会被"按字符串形态反推"覆盖（聚合函数保护），
        // 故这里的期望值取决于【值的字符串形态】：2+2=4 ⇒ Float；9+6=15 ⇒ Double；16+6=22 ⇒ BigDecimal
        assertInstanceOf(Float.class, row.get("f"));
        assertInstanceOf(Double.class, row.get("d"));
        assertInstanceOf(BigDecimal.class, row.get("bd"));
    }

    @Test
    @DisplayName("聚合列精度反推：precision=0/scale=0 时按字符串形态还原 p/s")
    void aggregateColumnPrecisionRecovery() throws Exception {
        // 驱动报 p=0,s=0（聚合函数），值形如 "1234.56" ⇒ 反推 p=4,s=2 ⇒ p+s=6<=8 ⇒ Float
        Map<String, Object> agg = build(List.of(
                col("sum1", Types.NUMERIC, 0, 0, new BigDecimal("1234.56"))));
        assertInstanceOf(Float.class, agg.get("sum1"), "按 4+2<=8 判定为 Float：" + agg);

        // 值形如 "1234567890123.123456"（13+6=19）⇒ BigDecimal
        Map<String, Object> big = build(List.of(
                col("sum2", Types.NUMERIC, 0, 0, new BigDecimal("1234567890123.123456"))));
        assertInstanceOf(BigDecimal.class, big.get("sum2"), "13+6>16 ⇒ BigDecimal：" + big);
    }

    @Test
    @DisplayName("时间/CLOB/BLOB/其它类型的处理")
    void timeLobAndConvertor() throws Exception {
        Timestamp ts = Timestamp.valueOf("2026-01-02 03:04:05");
        java.sql.Date d = java.sql.Date.valueOf("2026-01-02");
        String ds = "conv-" + System.nanoTime();
        EovaConfig.addConvertor(ds, new EovaConfigConvertorProbe());
        Map<String, Object> row = build(List.of(
                col("t", Types.TIMESTAMP, 0, 0, ts),
                col("d", Types.DATE, 0, 0, d),
                col("c", Types.CLOB, 0, 0, clob("clob-文本")),
                col("nc", Types.NCLOB, 0, 0, clob("nclob-文本")),
                col("b", Types.BLOB, 0, 0, blob(new byte[]{1, 2, 3})),
                col("v", Types.VARCHAR, 0, 0, "v-文本")), ds);
        assertEquals(ts, row.get("t"));
        assertEquals(d, row.get("d"));
        assertEquals("clob-文本", row.get("c"), "CLOB 必须转成 String");
        assertEquals("nclob-文本", row.get("nc"), "NCLOB 走同一条 handleClob");
        assertInstanceOf(byte[].class, row.get("b"));
        assertEquals(3, ((byte[]) row.get("b")).length);
        assertEquals("V-文本", row.get("v"), "其它类型走 EovaConfig.getConvertor(ds).convertValue（探针转大写）");
    }

    @Test
    @DisplayName("其它类型分支：转换器已注册 ⇒ 走 convertValue；未注册 ⇒ NPE（旧实现同，属既有脆弱点）")
    void convertorBranch() throws Exception {
        // ds 用唯一名，避免污染其它判据对 "main" 的期望（EovaConfig 没有 removeConvertor，
        // 故本判据只对自建 ds 名登记；「宿主必须在启动时按 ds 注册转换器」是一条既有待办，见 §r79）
        String ds = "conv-" + System.nanoTime();
        EovaConfig.addConvertor(ds, new EovaConfigConvertorProbe());
        Map<String, Object> row = build(List.of(
                col("n1", Types.NUMERIC, 1, 0, new BigDecimal("1")),
                col("s", Types.VARCHAR, 0, 0, "x")), ds);
        assertEquals(Integer.valueOf(1), row.get("n1"),
                "NUMERIC 在分档分支就被消化（p<=10 ⇒ Integer），【不会】走到转换器");
        assertEquals("X", row.get("s"), "非 NUMERIC 类型才交给转换器（探针转大写）");

        // 未注册的 ds ⇒ getConvertor 返回 null ⇒ 旧实现同样 NPE（忠实保留，不得在 port 里加兜底）
        assertThrows(NullPointerException.class, () -> build(List.of(
                col("s", Types.VARCHAR, 0, 0, "x")), "no-such-ds-" + System.nanoTime()),
                "转换器缺失时的 NPE 属旧实现的既有脆弱点");
    }

    @Test
    @DisplayName("null 列不入 map（与 jfinal 默认 RecordBuilder 相反 —— 这是 Oracle 分支的既有语义）")
    void nullColumnIsSkipped() throws Exception {
        Map<String, Object> row = build(List.of(
                col("a", Types.NUMERIC, 10, 0, new BigDecimal("1")),
                col("n", Types.VARCHAR, 0, 0, null)));
        assertTrue(row.containsKey("a"));
        assertFalse(row.containsKey("n"), "null 列必须【不】入 map：" + row.keySet());
    }

    @Test
    @DisplayName("handleClob/handleBlob 的边界：null 入参 → null；空 Blob → null")
    void lobEdgeCases() throws Exception {
        assertNull(EovaRecordValueBuilder.handleClob(null));
        assertNull(EovaRecordValueBuilder.handleBlob(null));
        assertEquals("", EovaRecordValueBuilder.handleClob(clob("")));
        assertNull(EovaRecordValueBuilder.handleBlob(blob(new byte[0])), "长度 0 ⇒ null（旧语义）");
    }

    @Test
    @DisplayName("buildLabelNamesAndTypes：下标从 1 起、到长度前一位（0 号位不写）")
    void labelNamesConvention() throws Exception {
        ResultSet rs = resultSet(List.of(col("X", Types.VARCHAR, 0, 0, "v")));
        ResultSetMetaData md = rs.getMetaData();
        String[] labels = new String[2];
        int[] types = new int[2];
        EovaRecordValueBuilder.buildLabelNamesAndTypes(md, labels, types);
        assertNull(labels[0], "0 号位不写（旧字节码 for 从 1 开始）");
        assertEquals(0, types[0]);
        assertEquals("X", labels[1]);
        assertEquals(Types.VARCHAR, types[1]);
    }

    // ------------------------------------------------------------ 网关行为（旧 EovaDbPro）

    /** 转换器探针：验证 buildValue 的"其它类型"分支确实经过 convertValue（Convertor 是抽象类，非接口） */
    static final class EovaConfigConvertorProbe extends cn.eova.core.type.Convertor {

        @Override
        public java.util.Map<String, Class> mapping() {
            return new LinkedHashMap<>();
        }

        @Override
        public Object convert(cn.eova.model.MetaField field, Object o) {
            return o;
        }

        @Override
        public Object convertValue(Object o, int type) {
            if (type == Types.NUMERIC) {
                return Boolean.TRUE;
            }
            return o == null ? null : o.toString().toUpperCase();
        }
    }

    /** 记录 prepareStatement 的 SQL，并给出一条单行结果 */
    static final class FakeJdbc {
        final List<String> sqls = new ArrayList<>();

        List<Col> cols = new ArrayList<>();

        /**
         * 建 DataSource 替身。
         *
         * @return 替身
         */
        javax.sql.DataSource dataSource() {
            InvocationHandler ds = (p, m, args) -> {
                if (m.getName().equals("getConnection")) {
                    return connection();
                }
                if (m.getName().equals("equals")) {
                    return p == args[0];
                }
                if (m.getName().equals("hashCode")) {
                    return System.identityHashCode(p);
                }
                return null;
            };
            return (javax.sql.DataSource) Proxy.newProxyInstance(
                    ArFamilyValueBuilderGoldenTest.class.getClassLoader(),
                    new Class<?>[]{javax.sql.DataSource.class}, ds);
        }

        private Connection connection() {
            InvocationHandler ch = (p, m, args) -> {
                switch (m.getName()) {
                    case "prepareStatement":
                        sqls.add((String) args[0]);
                        return statement();
                    case "setAutoCommit":
                    case "commit":
                    case "rollback":
                    case "close":
                        return null;
                    case "getAutoCommit":
                        return true;
                    case "equals":
                        return p == args[0];
                    case "hashCode":
                        return System.identityHashCode(p);
                    default:
                        return null;
                }
            };
            return (Connection) Proxy.newProxyInstance(
                    ArFamilyValueBuilderGoldenTest.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, ch);
        }

        private PreparedStatement statement() {
            InvocationHandler ph = (p, m, args) -> {
                switch (m.getName()) {
                    case "executeQuery":
                        return resultSet(cols);
                    case "executeUpdate":
                        return 1;
                    case "setObject":
                    case "setString":
                    case "close":
                        return null;
                    case "equals":
                        return p == args[0];
                    case "hashCode":
                        return System.identityHashCode(p);
                    default:
                        return null;
                }
            };
            return (PreparedStatement) Proxy.newProxyInstance(
                    ArFamilyValueBuilderGoldenTest.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, ph);
        }
    }

    @Test
    @DisplayName("网关：数据源为 Oracle 时按精准类型装配（含 null 列不入 map）")
    void gatewayUsesOracleBranch() {
        EovaDataSource.register("ora", "jdbc:oracle:thin:@localhost:1521/xe", null);
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.cols = List.of(
                col("NUM", Types.NUMERIC, 10, 0, new BigDecimal("7")),
                col("NULLCOL", Types.VARCHAR, 0, 0, null));

        JdbcEovaDbGateway gw = new JdbcEovaDbGateway(jdbc.dataSource(), "ora");
        List<EovaRecord> rows = gw.find("select * from t");
        assertEquals(1, rows.size());
        EovaRecord r = rows.get(0);
        assertInstanceOf(Integer.class, r.get("num"), "列名小写化 + 精准类型");
        assertFalse(r.getColumns().containsKey("nullcol"), "Oracle 分支 null 列不入 map：" + r.getColumns().keySet());
    }

    @Test
    @DisplayName("网关：非 Oracle 数据源维持 getObject 原样且 null 列保留（jfinal 默认语义）")
    void gatewayKeepsDefaultBranch() {
        EovaDataSource.register("my", "jdbc:mysql://localhost:3306/eova", null);
        FakeJdbc jdbc = new FakeJdbc();
        BigDecimal raw = new BigDecimal("7.00");
        jdbc.cols = List.of(
                col("NUM", Types.NUMERIC, 10, 0, raw),
                col("NULLCOL", Types.VARCHAR, 0, 0, null));

        JdbcEovaDbGateway gw = new JdbcEovaDbGateway(jdbc.dataSource(), "my");
        EovaRecord r = gw.find("select * from t").get(0);
        assertEquals(raw, r.get("num"), "非 Oracle 分支原样保留驱动返回值");
        assertTrue(r.getColumns().containsKey("nullcol"), "非 Oracle 分支 null 列要保留");
    }

    @Test
    @DisplayName("网关：Oracle 且主键为空 ⇒ save 自动填序列（旧 EovaDbPro.save 语义）")
    void gatewayFillsOracleSequence() {
        EovaDataSource.register("ora2", "jdbc:oracle:thin:@localhost:1521/xe", null);
        FakeJdbc jdbc = new FakeJdbc();
        JdbcEovaDbGateway gw = new JdbcEovaDbGateway(jdbc.dataSource(), "ora2");

        EovaRecord r = new EovaRecord();
        r.set("name", "n");
        assertTrue(gw.save("t_probe", "id", r));
        assertFalse(jdbc.sqls.isEmpty());
        String insert = jdbc.sqls.get(0);
        assertTrue(insert.toLowerCase().contains("insert into"), insert);
        assertEquals(EovaConst.SEQ_ + "t_probe.nextval", SqlUtil.getSequence("ora2", "t_probe"),
                "序列前缀取自 EovaConst.SEQ_（实测为小写 seq_）");
        assertEquals(EovaConst.SEQ_ + "t_probe.nextval", r.get("id"), "序列表达式必须写回 record");
        assertTrue(insert.contains(EovaConst.SEQ_ + "t_probe.nextval"),
                "序列表达式必须【内联】进 SQL（OralceDialect.forDbSave 语义），不能当参数绑定：" + insert);

        // 主键已有值 ⇒ 不覆盖
        FakeJdbc jdbc2 = new FakeJdbc();
        JdbcEovaDbGateway gw2 = new JdbcEovaDbGateway(jdbc2.dataSource(), "ora2");
        EovaRecord r2 = new EovaRecord();
        r2.set("id", 5);
        gw2.save("t_probe", "id", r2);
        assertEquals(Integer.valueOf(5), r2.get("id"), "已有主键不得被序列覆盖");

        // 复合主键（含逗号）⇒ 不填序列
        FakeJdbc jdbc3 = new FakeJdbc();
        JdbcEovaDbGateway gw3 = new JdbcEovaDbGateway(jdbc3.dataSource(), "ora2");
        EovaRecord r3 = new EovaRecord();
        r3.set("name", "n");
        gw3.save("t_probe", "id,code", r3);
        assertFalse(jdbc3.sqls.get(0).contains("nextval"), "复合主键不填序列：" + jdbc3.sqls.get(0));
    }

    @Test
    @DisplayName("网关：h2/dm 数据源的关键字转义（读路径生效、save/delete 不转义）")
    void gatewayEscapesKeywordsForH2() {
        EovaDataSource.register("h2ds", "jdbc:h2:mem:t", null);
        cn.eova.tools.x.conf.addConfig("db.keyword", "eova_dict.value");
        FakeJdbc jdbc = new FakeJdbc();
        JdbcEovaDbGateway gw = new JdbcEovaDbGateway(jdbc.dataSource(), "h2ds");

        gw.find("select value from eova_dict where value = 'value'");
        String escaped = jdbc.sqls.get(0);
        assertFalse(escaped.equals("select value from eova_dict where value = 'value'"),
                "命中 eova_dict 且配置了关键字时必须发生转义");
        assertTrue(escaped.contains("`value`") || escaped.contains("\"value\""),
                "转义形态来自 DefineDialect.escape：" + escaped);
        assertTrue(escaped.contains("'value'"), "字符串字面量内的同名字符串必须还原：" + escaped);

        // 写路径（update(sql)）同样要转义（旧 EovaDbPro 覆写了 update）
        jdbc.sqls.clear();
        gw.update("update eova_dict set value = ? where value = ?", "v", "w");
        assertFalse(jdbc.sqls.get(0).equals("update eova_dict set value = ? where value = ?"),
                "update 路径必须转义：" + jdbc.sqls.get(0));

        // save 不转义（旧 EovaDbPro 只覆写 find/query/update）：
        // 用【差分断言】—— 同一 save 在"有关键字配置"与"无配置"下必须产出同一条 SQL
        // （不能只搜反引号：INSERT 的列名本来就被 SQL 构造器加反引号/不加，会误判）
        jdbc.sqls.clear();
        EovaRecord r = new EovaRecord();
        r.set("value", "v");
        gw.save("eova_dict", r);
        String withKeyword = jdbc.sqls.get(0);

        cn.eova.tools.x.conf.getProps().remove("db.keyword");
        jdbc.sqls.clear();
        EovaRecord r2 = new EovaRecord();
        r2.set("value", "v");
        gw.save("eova_dict", r2);
        assertEquals(withKeyword, jdbc.sqls.get(0), "save 路径不得受 db.keyword 影响：" + withKeyword);
    }

    @Test
    @DisplayName("网关：未提供 ds 名 ⇒ 不启用任何方言行为（转义/序列均不生效）")
    void gatewayWithoutDsIsInert() {
        FakeJdbc jdbc = new FakeJdbc();
        JdbcEovaDbGateway gw = new JdbcEovaDbGateway(jdbc.dataSource());
        assertNull(gw.ds());
        gw.find("select * from eova_dict");
        assertEquals("select * from eova_dict", jdbc.sqls.get(0), "无 ds ⇒ SQL 原样");

        EovaRecord r = new EovaRecord();
        r.set("name", "n");
        gw.save("t", "id", r);
        assertNull(r.get("id"), "无 ds ⇒ 不填序列");
    }

    @Test
    @DisplayName("网关：ds 名为 null 时 escapeSql 直接返回原 SQL（不触库、不抛异常）")
    void escapeSqlNullDs() {
        JdbcEovaDbGateway gw = new JdbcEovaDbGateway(new FakeJdbc().dataSource());
        assertEquals("select 1", gw.escapeSql("select 1"));
    }

    @Test
    @DisplayName("自检：SqlUtil.getSequence 的两种方言形态（判据依赖它，故先钉住）")
    void sequenceExpressionShapes() {
        EovaDataSource.register("ora3", "jdbc:oracle:thin:@localhost:1521/xe", null);
        EovaDataSource.register("pg3", "jdbc:postgresql://localhost:5432/eova", null);
        assertEquals("seq_t.nextval", SqlUtil.getSequence("ora3", "t"), "实测形态（EovaConst.SEQ_ = seq_）");
        assertEquals("nextval('seq_t'::regclass)", SqlUtil.getSequence("pg3", "t"));
        assertNull(SqlUtil.getSequence("h2x", "t"));
        assertNull(SqlUtil.getSequence("missing", "t"),
                "未注册数据源 ⇒ getDbType 为 null ⇒ 两个分支都不命中 ⇒ 返回 null（实测）");
    }
}
