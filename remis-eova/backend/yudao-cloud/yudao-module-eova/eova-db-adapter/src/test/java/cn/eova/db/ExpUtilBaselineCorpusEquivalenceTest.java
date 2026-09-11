/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import cn.eova.engine.ExpUtil;
import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **`ExpUtil` 的跨实现等价判据（第 176 轮）** —— 输入语料取**真实 baseline 库里的 65 条 exp**。
 *
 * <p><b>为什么要补（第 176 轮的系统性扫描结果，R79 的机械化）：</b>本轮对 ported 生产面做了
 * 一次"单元名是否被判据点名"的扫描（`docs/.local/spikes/judge-coverage-sweep.py`）：
 * **351 个生产类 / 52,869 行**里 **319 个被点名**、**32 个零出现（2,782 行，5%）**。
 * 其中 `ExpUtil`（177 行）不仅类名零出现，**6 个公开方法里 5 个也零出现**
 * （`parseSql`/`parseTemplate`/`getSqlParam`/`buildExpPara`/`buildSqlPara`）——
 * 而它是**查询表达式与 SQL 参数**的解析入口（`eova_field.exp` 里每条 exp 都走它），
 * 判错就意味着一整类"查询框/下拉框"取数错误。这正是 R79 说的第二种可能：
 * **不是"已覆盖"，而是整条没人管**。
 *
 * <p><b>本判据的做法（用真实语料 + 旧实现真身比对，而不是自己写期望值）：</b>
 * <ol>
 *   <li>语料 = 真实 baseline 库 `eova_meta.eova_field.exp` 里**全部非空 exp**（实测 65 条，
 *       其中 16 条带 `;`，形态如
 *       {@code select code 编码, name 名称 from eova_object where id > 1 order by id desc;ds=eova;cache=eova}）；</li>
 *   <li>旧侧 = **从 `meta-eova/eova/core/target/classes` 加载的旧 `ExpUtil` 真身**
 *       （{@link OldImplementationLoader}，与 r37 固化 `LegacyTypeKit` 用的是同一套基建）；</li>
 *   <li>对每条 exp 分别取**行为描述串**（成功 ⇒ 逐元素带类型标记；失败 ⇒ 异常类型 + 消息逐字），
 *       两侧必须**逐条一致**。</li>
 * </ol>
 *
 * <p><b>反空断言（第 144 轮口径 B）：</b>光有"逐条一致"是不够的 —— 若两侧对所有语料都返回空表，
 * 这条判据等于什么都没判。故额外断言：语料 ≥ 60 条、带 `;` 的 ≥ 10 条、
 * **且至少 30 条能解析出非空参数表**。
 *
 * <p><b>如实登记的分支缺口：</b>真实语料里 `%s`（硬编码参数，如
 * {@code selectEovaUser;@(1,2,3)}）与 `@` 形态**各为 0 条** ⇒ 这两条分支由
 * {@link #hardParameterAndAtBranchMatchOldImplementation()} 用**构造输入**覆盖，
 * 不得据此声称"真实数据已验证该分支"。
 *
 * <p><b>跳过口径（R77）：</b>baseline 或旧制品不可用时逐条跳过，报告为 {@code skipped=3}，
 * 不得记为通过。
 */
class ExpUtilBaselineCorpusEquivalenceTest {

    private static final String META_URL = System.getProperty("eova.mysql.meta.url",
            "jdbc:mysql://127.0.0.1:13306/eova_meta?useUnicode=true&characterEncoding=UTF-8"
                    + "&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
                    + "&connectTimeout=3000");
    private static final String DB_USER = System.getProperty("eova.mysql.user", "root");
    private static final String DB_PWD = System.getProperty("eova.mysql.pwd", "root");

    private static boolean reachable() {
        try (Connection c = DriverManager.getConnection(META_URL, DB_USER, DB_PWD)) {
            return c.isValid(3);
        } catch (Throwable e) {
            return false;
        }
    }

    private static final boolean BASELINE_UP = reachable();
    private static final boolean OLD_AVAILABLE = OldImplementationLoader.oldClassesAvailable();

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(BASELINE_UP,
                "baseline MySQL 不可达 ⇒ 逐条跳过（不得记为通过）：" + META_URL);
        Assumptions.assumeTrue(OLD_AVAILABLE,
                "旧制品不可用 ⇒ 无法跨实现比对，逐条跳过（不得记为通过）");
    }

    /** 读真实语料：`eova_field.exp` 全部非空值 */
    private static List<String> readCorpus() throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(META_URL, DB_USER, DB_PWD);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select exp from eova_field where exp is not null and exp <> '' order by id")) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    /** 旧 `ExpUtil` 真身（从旧编译产物加载） */
    private static Class<?> oldExpUtil() throws Exception {
        return OldImplementationLoader.create(OldImplementationLoader.locateRepoRoot())
                .loadClass("cn.eova.engine.ExpUtil");
    }

    /**
     * 行为描述串：成功 ⇒ `OK:[类型:值, ...]`；失败 ⇒ `ERR:异常类名:消息`。
     *
     * <p>带类型标记是刻意的：`getSqlParam` 的契约是"优先 Integer，不能转就当 String"，
     * 只比字符串值会让 `Integer:10` 与 `String:10` 看起来一样（那正是最容易漂移的一处）。
     *
     * @param supplier 待观测调用
     * @return 行为描述串
     */
    private static String describe(Supplier<Object> supplier) {
        try {
            Object v = supplier.get();
            if (v instanceof List<?> list) {
                StringBuilder sb = new StringBuilder("OK:[");
                for (int i = 0; i < list.size(); i++) {
                    Object e = list.get(i);
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(e == null ? "null" : e.getClass().getSimpleName() + ":" + e);
                }
                return sb.append(']').toString();
            }
            return v == null ? "OK:null" : "OK:" + v.getClass().getSimpleName() + ":" + v;
        } catch (Throwable t) {
            Throwable cause = t instanceof InvocationTargetException && t.getCause() != null ? t.getCause() : t;
            return "ERR:" + cause.getClass().getSimpleName() + ":" + cause.getMessage();
        }
    }

    /** 反射调用旧实现的静态方法并取描述串 */
    private static String describeOld(Class<?> oldClass, String method, Class<?> paramType, Object arg)
            throws Exception {
        Method m = oldClass.getMethod(method, paramType);
        return describe(() -> {
            try {
                return m.invoke(null, arg);
            } catch (InvocationTargetException e) {
                // ★ 必须**原样抛出 cause**：若再包一层 RuntimeException，描述串会变成
                //   "ERR:RuntimeException:java.lang.RuntimeException: …"，与直接调用侧的
                //   "ERR:RuntimeException: …" 不相等 —— 那是判据的假失败（本轮实测踩到）
                Throwable c = e.getCause();
                if (c instanceof RuntimeException re) {
                    throw re;
                }
                if (c instanceof Error err) {
                    throw err;
                }
                throw new RuntimeException(c);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("★ 真实语料 65 条 exp：新旧 buildSqlPara 行为逐条一致（含反空断言）")
    void corpusFromRealBaselineIsBehaviourallyEquivalent() throws Exception {
        List<String> corpus = readCorpus();

        // ★ 反空断言：语料必须够大、且真的覆盖到两条解析分支
        assertTrue(corpus.size() >= 60, "语料必须来自真实库且足够大，实际=" + corpus.size());
        long withSemi = corpus.stream().filter(s -> s.contains(";")).count();
        assertTrue(withSemi >= 10, "带 ';' 的语料必须 ≥10（分号分支），实际=" + withSemi);
        long withoutSemi = corpus.size() - withSemi;
        assertTrue(withoutSemi >= 10, "不带 ';' 的语料必须 ≥10（逗号分支），实际=" + withoutSemi);

        Class<?> oldClass = oldExpUtil();
        assertTrue(oldClass != ExpUtil.class, "旧实现必须来自旧制品（不同 ClassLoader），否则本判据是自比");

        int nonEmpty = 0;
        for (String exp : corpus) {
            String oldDesc = describeOld(oldClass, "buildSqlPara", String.class, exp);
            String newDesc = describe(() -> ExpUtil.buildSqlPara(exp));
            assertEquals(oldDesc, newDesc, "旧/新 buildSqlPara 必须逐条等价，exp=" + exp);
            if (oldDesc.startsWith("OK:[") && !oldDesc.equals("OK:[]")) {
                nonEmpty++;
            }
        }

        // ★ 反空断言之二：若两侧对所有语料都返回空参数表，这条判据等于什么都没判。
        //   阈值**不拍脑袋**（初稿写 30 是错的，实测只有 16 条能解析出参数 —— 因为 49 条是
        //   形如 `eova_option`/`eova_menu_dir` 的短表达式 key，既无逗号也无分号 ⇒ 参数表为空）。
        //   故断言两件事：① 带 ';' 的语料**每一条**都产出了参数；② 非空总数有下限。
        assertTrue(nonEmpty >= withSemi,
                "带 ';' 的语料应逐条产出参数：nonEmpty=" + nonEmpty + " withSemi=" + withSemi);
        assertTrue(nonEmpty >= 10,
                "至少 10 条语料必须解析出非空参数表，否则断言是空的；实际=" + nonEmpty + "/" + corpus.size());
    }

    @Test
    @DisplayName("★ 真实语料：新旧 getSqlParam 的类型契约一致（Integer 优先，否则 String）")
    void getSqlParamTypeContractMatchesOldImplementation() throws Exception {
        Class<?> oldClass = oldExpUtil();
        String[] inputs = {"0", "10", "-3", "2147483647", "2147483648", "1.5", "abc", "", " 7 ", "编码", "null"};
        for (String s : inputs) {
            assertEquals(describeOld(oldClass, "getSqlParam", String.class, s),
                    describe(() -> ExpUtil.getSqlParam(s)),
                    "getSqlParam 类型/取值契约必须一致，输入=" + s);
        }
        // 类型契约本身也要判：整数转成 Integer、越界/非数字保持 String
        assertEquals("OK:Integer:10", describe(() -> ExpUtil.getSqlParam("10")));
        assertEquals("OK:String:2147483648", describe(() -> ExpUtil.getSqlParam("2147483648")));
        assertEquals("OK:String:abc", describe(() -> ExpUtil.getSqlParam("abc")));
    }

    @Test
    @DisplayName("★ 语料未覆盖的两条分支：%s 硬编码参数与 @ 前缀（用构造输入，如实标注）")
    void hardParameterAndAtBranchMatchOldImplementation() throws Exception {
        Class<?> oldClass = oldExpUtil();
        String[] inputs = {
                "select * from t where id in %s;@(1,2,3)",
                "select * from t where id in %s;@(1, 2, 3);10000",
                "select * from t where uid in %s",
                "selectEovaUser;10,20,30",
                "selectEovaUser;@(1,2,3);10000",
                "a;@(x)",
                "a;@()",
                "a;",
                "",
                ";",
                "a,b,c",
                "有中文;@(甲,乙)",
                // ★ 刻意带**分隔符两侧空白**的输入：`buildExpPara` 的 `pm.trim()` 只有在
                //   分隔符旁有空白时才可观测。首轮 M4 变异（去掉 trim）**未被捕获**，
                //   根因就是夹具里所有分片都恰好没有首尾空白 ⇒ 变异与真实现等价（r170 口径：
                //   夹具必须让被断言的分支可判别）。
                "aa ; bb ; cc ",
                "  x  ;  y  ",
                "\t1\t;\t2\t",
            };
        for (String exp : inputs) {
            assertEquals(describeOld(oldClass, "buildSqlPara", String.class, exp),
                    describe(() -> ExpUtil.buildSqlPara(exp)),
                    "%s/@ 分支必须与旧实现逐条一致，exp=" + exp);
            String[] pms = exp.split(";");
            assertEquals(describeOld(oldClass, "buildExpPara", String[].class, pms),
                    describe(() -> ExpUtil.buildExpPara(pms)),
                    "buildExpPara 必须与旧实现一致，pms=" + java.util.Arrays.toString(pms));
        }
    }
}
