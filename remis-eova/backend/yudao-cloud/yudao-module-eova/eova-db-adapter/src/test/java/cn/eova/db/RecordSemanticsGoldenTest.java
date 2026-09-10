/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `EovaDbGateway` 与 golden 规格的逐条比对（acceptanceProfile: golden-record-semantics）。
 *
 * <p><b>这是「实跑判据」而非自述判据：</b>golden 来自 SP6 探针在 JFinal 5.2.6 上的实测捕获
 * （{@code docs/.local/ledger/record-semantics.golden.jsonl}，128 条）。
 * 本测试用<b>同一套操作</b>驱动新实现，按<b>同一种 JSON 编码</b>输出，再逐行比对。
 *
 * <p>采用"同编码后比文本"而非"在 Java 里解析 JSON"：前者的比对粒度到字节，
 * 且与 SP6 探针共用同一套渲染规则，避免两侧各自解释 JSON 造成假差异。
 *
 * <p><b>明确排除项（附理由，非"为通过而放宽"）</b>见 {@link #EXCLUDED}。
 */
class RecordSemanticsGoldenTest {

    private static final String URL = "jdbc:mysql://127.0.0.1:13306/demo"
            + "?useUnicode=true&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull"
            + "&useSSL=false&serverTimezone=Asia/Shanghai";

    /**
     * 排除项与理由。
     *
     * <p>三条排除都是**方法学结论**：
     * <ol>
     *   <li>{@code env|*} —— 探针环境描述（制品版本 / 容器工厂名），非行为契约；</li>
     *   <li>键序类（{@code accessor|getColumns.keySet}、{@code getColumnNames}、
     *       {@code json|toJson*}、{@code null|toJson}）—— SP6 实测旧实现
     *       {@code toJson} 键序<b>非插入序</b>，即旧系统自身也不稳定，故键序不可作契约（§3.8 第 3 条）；</li>
     *   <li>实现内部类型名（{@code find|record.class}、{@code json|getColumns 类型}）
     *       与 {@code 诊断|*} —— 属实现细节，非对外行为。</li>
     * </ol>
     */
    private static final Set<String> EXCLUDED = Set.of(
            "env|config", "env|探针表已创建",
            "find|record.class",
            "accessor|getColumns.keySet", "accessor|getColumnNames", "case|getColumns.keys",
            "json|getColumns 类型", "json|toJson(单条)", "json|toJson(带null字段)", "null|toJson",
            "update|诊断.内存Record.modifyFlag", "update|诊断.findById整行",
            "update|诊断.findFirst整行", "update(byId)|诊断.整行"
    );

    private static MysqlDataSource ds;
    private static JdbcEovaDbGateway gw;
    private static final List<String> EMITTED = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(Files.exists(goldenFile()), "golden 缺失：" + goldenFile());
        try {
            // 用 MySQL 驱动自带的 DataSource，避免为测试引入连接池依赖
            ds = new MysqlDataSource();
            ds.setUrl(URL);
            ds.setUser("root");
            ds.setPassword("root");
        } catch (Exception e) {
            Assumptions.abort("MySQL 不可用（跳过）：" + e.getMessage());
        }
        gw = new JdbcEovaDbGateway(ds);
        gw.update("drop table if exists sp6_probe");
        gw.update("create table sp6_probe (id int primary key, k varchar(50), v varchar(50))");
    }

    @AfterAll
    static void tearDown() {
        try {
            if (gw != null) {
                gw.update("drop table if exists sp6_probe");
            }
        } catch (Exception ignored) {
            // 清理失败不影响结论
        }
        // MysqlDataSource 无 close()；连接由网关按需取用并即时关闭
    }

    @Test
    @DisplayName("新实现与 SP6 golden 逐行比对：排除项之外差异应为 0")
    void matchesGolden() throws Exception {
        runProbe();

        Map<String, String> expected = loadGoldenLines();
        Map<String, String> actual = new LinkedHashMap<>();
        for (String line : EMITTED) {
            actual.put(keyOf(line), line);
        }

        List<String> diffs = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int compared = 0;
        for (Map.Entry<String, String> e : new TreeMap<>(expected).entrySet()) {
            if (isExcluded(e.getKey())) {
                continue;
            }
            compared++;
            String got = actual.get(e.getKey());
            if (got == null) {
                missing.add("  " + e.getKey() + "（新实现未产生该证据）");
            } else if (!e.getValue().equals(got)) {
                diffs.add("  " + e.getKey() + "\n      旧=" + brief(valueOf(e.getValue()))
                        + "\n      新=" + brief(valueOf(got)));
            }
        }
        System.out.println("[golden 比对] 比对 " + compared + " 条；值差异 " + diffs.size()
                + "；缺失 " + missing.size());

        // 把新证据落盘，便于人工核查
        Path out = goldenFile().resolveSibling("record-semantics.new.jsonl");
        Files.write(out, EMITTED, StandardCharsets.UTF_8);
        System.out.println("[golden 比对] 新证据已写出: " + out);

        assertTrue(missing.isEmpty(), "存在未产生的证据：" + missing.size() + " 条\n" + join(missing));
        assertTrue(diffs.isEmpty(), "与 golden 值差异 " + diffs.size() + " 条：\n" + join(diffs));
    }

    // ---------------- 探测（与 SP6 逐项对应） ----------------

    /** 执行与 SP6 相同的操作序列 */
    private void runProbe() {
        probeFindAndAccessors();
        probeCoercion();
        probeCase();
        probeNull();
        probeFindFirst();
        probePaginate();
        probeSaveUpdateDelete();
        probeTx();
    }

    private void probeFindAndAccessors() {
        List<EovaRecord> list = gw.find("select * from users order by id limit 3");
        emit("find", "list.size", list.size());
        EovaRecord r = list.get(0);
        for (String col : new String[]{"id", "info", "login_id", "status"}) {
            emit("accessor", "get(" + col + ")", sr(() -> r.get(col)));
            emit("accessor", "getStr(" + col + ")", sr(() -> r.getStr(col)));
            emit("accessor", "getInt(" + col + ")", sr(() -> r.getInt(col)));
            emit("accessor", "getLong(" + col + ")", sr(() -> r.getLong(col)));
            emit("accessor", "getBoolean(" + col + ")", sr(() -> r.getBoolean(col)));
            emit("accessor", "getBigDecimal(" + col + ")", sr(() -> r.getBigDecimal(col)));
            emit("accessor", "getDate(" + col + ")", sr(() -> r.getDate(col)));
            emit("accessor", "getNumber(" + col + ")", sr(() -> r.getNumber(col)));
            emit("accessor", "getObject(" + col + ")", sr(() -> r.getObject(col)));
            emit("accessor", "getBigInteger(" + col + ")", sr(() -> r.getBigInteger(col)));
            emit("accessor", "getTime(" + col + ")", sr(() -> r.getTime(col)));
            emit("accessor", "getLocalDateTime(" + col + ")", sr(() -> r.getLocalDateTime(col)));
            emit("accessor", "get(" + col + ",默认值)", sr(() -> r.get(col, "DEFAULT")));
        }
        emit("accessor", "get(不存在的列).isNull", r.get("no_such_column") == null);
        emit("accessor", "getStr(不存在的列)", sr(() -> r.getStr("no_such_column")));
        emit("accessor", "getInt(不存在的列)", sr(() -> r.getInt("no_such_column")));
        emit("accessor", "getBoolean(不存在的列)", sr(() -> r.getBoolean("no_such_column")));
    }

    private void probeCoercion() {
        EovaRecord r = gw.find("select id, info, login_id, reg_time from users order by id limit 2").get(0);
        emit("coercion", "int列.getStr", sr(() -> r.getStr("id")));
        emit("coercion", "int列.getInt", sr(() -> r.getInt("id")));
        emit("coercion", "varchar列.getInt(非数字)", sr(() -> r.getInt("nickname")));
        emit("coercion", "datetime列.getStr", sr(() -> r.getStr("reg_time")));
        emit("coercion", "datetime列.getDate", sr(() -> r.getDate("reg_time")));
        emit("coercion", "datetime列.getTimestamp", sr(() -> r.getDate("reg_time")));
        emit("coercion", "getNumber(int列)", sr(() -> r.getNumber("id")));
        emit("coercion", "getNumber(非数字列)", sr(() -> r.getNumber("nickname")));

        EovaRecord t = new EovaRecord();
        t.set("a", 1);
        t.set("b", "2");
        t.set("c", true);
        t.set("d", null);
        emit("record.set", "get(a)", sr(() -> t.get("a")));
        emit("record.set", "getStr(a)", sr(() -> t.getStr("a")));
        emit("record.set", "getInt(b)", sr(() -> t.getInt("b")));
        emit("record.set", "getBoolean(c)", sr(() -> t.getBoolean("c")));
        emit("record.set", "get(d)", sr(() -> t.get("d")));
        emit("record.set", "getStr(d)", sr(() -> t.getStr("d")));
        emit("record.set", "getInt(d)", sr(() -> t.getInt("d")));
        emit("record.set", "getInt(字符串abc)", sr(() -> {
            EovaRecord q = new EovaRecord();
            q.set("x", "abc");
            return q.getInt("x");
        }));
    }

    private void probeCase() {
        EovaRecord r = gw.find("select id, nickname, LOGIN_ID from users order by id limit 1").get(0);
        emit("case", "get(Login_Id)", sr(() -> r.get("Login_Id")));
        emit("case", "get(login_id)", sr(() -> r.get("login_id")));
        emit("case", "get(LOGIN_ID)", sr(() -> r.get("LOGIN_ID")));
        emit("case", "getStr(LOGIN_ID)", sr(() -> r.getStr("LOGIN_ID")));

        EovaRecord t = new EovaRecord();
        t.set("MyKey", "v");
        emit("case", "set(MyKey)后 getColumns.keys", new ArrayList<>(t.getColumns().keySet()));
        emit("case", "get(mykey)", sr(() -> t.get("mykey")));
        emit("case", "get(MYKEY)", sr(() -> t.get("MYKEY")));
        emit("case", "get(MyKey)", sr(() -> t.get("MyKey")));
    }

    private void probeNull() {
        EovaRecord r = gw.find("select id, nickname, null as n_col from users order by id limit 1").get(0);
        emit("null", "get(NULL列)", sr(() -> r.get("n_col")));
        emit("null", "getStr(NULL列)", sr(() -> r.getStr("n_col")));
        emit("null", "getInt(NULL列)", sr(() -> r.getInt("n_col")));
        emit("null", "getLong(NULL列)", sr(() -> r.getLong("n_col")));
        emit("null", "getBoolean(NULL列)", sr(() -> r.getBoolean("n_col")));
        emit("null", "getDate(NULL列)", sr(() -> r.getDate("n_col")));
        emit("null", "getBigDecimal(NULL列)", sr(() -> r.getBigDecimal("n_col")));
        emit("null", "getNumber(NULL列)", sr(() -> r.getNumber("n_col")));
    }

    private void probeFindFirst() {
        emit("findFirst", "notNull", gw.findFirst("select * from users order by id limit 1") != null);
        emit("findFirst", "无命中返回null", gw.findFirst("select * from users where id = -1") == null);
        EovaRecord byId = gw.findById("users", 1);
        emit("findById", "notNull", byId != null);
        emit("findById", "id值", sr(() -> byId == null ? null : byId.get("id")));
        emit("findById", "无命中返回null", gw.findById("users", -1) == null);
    }

    private void probePaginate() {
        EovaPage<EovaRecord> p = gw.paginate(1, 5, "select *", "from users order by id");
        emit("paginate", "pageNumber", p.getPageNumber());
        emit("paginate", "pageSize", p.getPageSize());
        emit("paginate", "totalRow", p.getTotalRow());
        emit("paginate", "totalPage", p.getTotalPage());
        emit("paginate", "isFirstPage", p.isFirstPage());
        emit("paginate", "isLastPage", p.isLastPage());
        emit("paginate", "list.size", p.getList().size());
        EovaPage<EovaRecord> last = gw.paginate(999, 5, "select *", "from users order by id");
        emit("paginate", "越界页.list.size", last.getList().size());
        emit("paginate", "越界页.isFirstPage", last.isFirstPage());
        emit("paginate", "带参数.list.size",
                gw.paginate(1, 3, "select *", "from users where id > ? order by id", 0).getList().size());
    }

    private void probeSaveUpdateDelete() {
        String table = "sp6_probe";
        int before = gw.find("select * from " + table).size();

        EovaRecord r = new EovaRecord();
        r.set("id", 990001);
        r.set("k", "sp6_probe");
        r.set("v", "v1");
        emit("save", "return", gw.save(table, r));
        emit("save", "行数增加", gw.find("select * from " + table).size() - before);

        r.set("v", "v2");
        emit("update", "return", gw.update(table, r));
        emit("update", "值已变更", sr(() -> gw.findById(table, 990001).get("v")));
        emit("update", "诊断.内存Record.modifyFlag", new TreeSet<>(r.getModifyFlag()));

        emit("update(byId)", "return", gw.update(table, "id", r));

        emit("delete", "return", gw.delete(table, r));
        emit("delete", "行数复原", gw.find("select * from " + table).size() - before);
        emit("deleteById", "无命中返回", gw.deleteById(table, 990001));
    }

    private void probeTx() {
        String table = "sp6_probe";
        int before = gw.find("select * from " + table).size();
        try {
            gw.tx(() -> {
                EovaRecord r = new EovaRecord();
                r.set("id", 990002);
                r.set("k", "tx_probe");
                r.set("v", "x");
                gw.save(table, r);
                throw new RuntimeException("force rollback");
            });
        } catch (RuntimeException ignored) {
            // 预期抛出
        }
        emit("tx", "回滚后行数未变", gw.find("select * from " + table).size() - before);

        try {
            gw.tx(() -> {
                EovaRecord r = new EovaRecord();
                r.set("id", 990003);
                r.set("k", "tx_probe2");
                r.set("v", "y");
                gw.save(table, r);
                return true;
            });
        } catch (RuntimeException ignored) {
            // 不应发生
        }
        emit("tx", "提交后行数+1", gw.find("select * from " + table).size() - before);
        gw.deleteById(table, 990003);
    }

    // ---------------- 与 SP6 完全一致的编码 ----------------

    /** 追加一条证据（编码格式与 SP6 的 record(...) 一致） */
    private void emit(String group, String key, Object value) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("group", group);
        o.put("key", key);
        o.put("value", value);
        EMITTED.add(toJson(o));
    }

    /** 与 SP6 相同的渲染：null -> null；其余 -> {"type":..,"value":..} 字符串 */
    private static Object render(Object v) {
        if (v == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", v.getClass().getName());
        m.put("value", String.valueOf(v));
        return toJson(m);
    }

    /** 与 SP6 相同的安全求值：异常渲染为 "throw <类名>: <消息>" */
    private static Object sr(Probe p) {
        try {
            return render(p.get());
        } catch (Throwable t) {
            return "throw " + t.getClass().getName() + ": " + t.getMessage();
        }
    }

    /** 允许受检异常的求值接口 */
    @FunctionalInterface
    private interface Probe {
        /** 求值 */
        Object get() throws Throwable;
    }

    /** 与 SP6 相同的极简 JSON 序列化（键序稳定） */
    private static String toJson(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }

    /** 写 JSON 片段 */
    private static void write(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String s) {
            sb.append('"').append(s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")).append('"');
        } else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object e : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, e);
            }
            sb.append(']');
        } else {
            sb.append(o);
        }
    }

    // ---------------- 工具 ----------------

    /** 读 golden 为 key（group|key）-> 整行 映射 */
    private static Map<String, String> loadGoldenLines() throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        for (String line : Files.readAllLines(goldenFile(), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                m.put(keyOf(line), line);
            }
        }
        return m;
    }

    /** 从一行 JSON 中取 group|key */
    private static String keyOf(String line) {
        return field(line, "group") + "|" + field(line, "key");
    }

    /** 从一行 JSON 中取指定字符串字段 */
    private static String field(String line, String name) {
        int i = line.indexOf("\"" + name + "\":");
        int a = line.indexOf('"', i + name.length() + 3);
        int b = line.indexOf('"', a + 1);
        return line.substring(a + 1, b);
    }

    /** 取整行的 value 片段（用于展示差异） */
    private static String valueOf(String line) {
        int i = line.indexOf("\"value\":");
        return i < 0 ? line : line.substring(i + 8, line.length() - 1);
    }

    /** 是否属于排除项 */
    private static boolean isExcluded(String groupKey) {
        return EXCLUDED.contains(groupKey);
    }

    /** 截断长文本 */
    private static String brief(String s) {
        return s != null && s.length() > 170 ? s.substring(0, 170) + "…" : s;
    }

    /** 拼接差异列表 */
    private static String join(List<String> list) {
        return String.join("\n", list.size() > 25 ? list.subList(0, 25) : list)
                + (list.size() > 25 ? "\n  … 共 " + list.size() + " 条" : "");
    }

    /** golden 文件路径 */
    private static Path goldenFile() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("meta-eova/eova"))) {
                return p.resolve("docs/.local/ledger/record-semantics.golden.jsonl");
            }
            p = p.getParent();
        }
        throw new IllegalStateException("未能定位仓库根");
    }
}
