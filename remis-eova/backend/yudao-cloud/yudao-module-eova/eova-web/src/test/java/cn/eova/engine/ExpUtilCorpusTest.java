/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.engine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **T04 第二段判据：表达式的"真实语料"与"所需语法子集"**（EXPR 腿的规格来源）。
 *
 * <p><b>为什么要它</b>：`ExpUtil` 用 Enjoy 的 `Engine.getTemplateByString(...)` 做**业务表达式求值**
 * （`eova_object.filter` 查询过滤、`eova_field.defaulter` 字段默认值、`eova_field.exp` 字典/内联 SQL、
 * `eova_field.config` JSON 占位、`AuthUri` 鉴权 URI 规则）—— 这是退役 `com.jfinal:enjoy` 的**最大阻塞项**。
 * 要判断"能不能用自研求值器等价替代"，先得知道**数据里到底出现了哪些语法构件**：
 * 靠读代码猜不出来，必须**从真库语料里统计**。</p>
 *
 * <p><b>r308 第 4 轮实测结论</b>（本判据就是它的钉子）：
 * 语料只用到 <b>4 类构件</b> —— `#(expr)` 插值 · `#if(cond)…#end` 条件 · `${expr}` 参数 · `??` 合并；
 * 其余绝大多数是**纯字面量**（`0`、`NOW`、`CURRENT_TIMESTAMP`…）。字段访问只出现
 * `user.role.lv`、`user.company_id`、`user.id` 这类**属性链**。</p>
 *
 * <p><b>fail-closed</b>：baseline 库不可达 ⇒ 本判据**红**（不是跳过）—— 与 `LegacyHttpContractTest` 同口径：
 * 语料取不到时"通过"毫无意义。</p>
 */
class ExpUtilCorpusTest {

    private static final String META_URL = System.getProperty("eova.mysql.meta.url",
            "jdbc:mysql://127.0.0.1:13306/eova_meta?useUnicode=true&characterEncoding=UTF-8");
    private static final String DB_USER = System.getProperty("eova.mysql.user", "root");
    private static final String DB_PWD = System.getProperty("eova.mysql.pwd", "root");

    /** 语料来源：列 → 取值 SQL（只取非空） */
    private static final Map<String, String> SOURCES = new LinkedHashMap<>();

    static {
        SOURCES.put("eova_object.filter", "select filter from eova_object where filter is not null and filter <> ''");
        SOURCES.put("eova_menu.filter", "select filter from eova_menu where filter is not null and filter <> ''");
        SOURCES.put("eova_field.defaulter",
                "select defaulter from eova_field where defaulter is not null and defaulter <> ''");
        SOURCES.put("eova_field.exp", "select exp from eova_field where exp is not null and exp <> ''");
        SOURCES.put("eova_field.config", "select config from eova_field where config is not null and config <> ''");
    }

    /** 声明：语法构件 → 匹配正则（顺序无关，各自统计命中数） */
    private static final Map<String, Pattern> CONSTRUCTS = new LinkedHashMap<>();

    static {
        CONSTRUCTS.put("#(...) 插值", Pattern.compile("#\\("));
        CONSTRUCTS.put("#if(...) 条件", Pattern.compile("#if\\s*\\("));
        CONSTRUCTS.put("#end", Pattern.compile("#end\\b"));
        CONSTRUCTS.put("${...} 参数", Pattern.compile("\\$\\{"));
        CONSTRUCTS.put("?? 合并/占位", Pattern.compile("\\?\\?"));
    }

    /** 语料里出现的构件（r308 第 4 轮实测；新增构件必须显式改表 —— 那意味着替代求值器的规格要扩） */
    private static final List<String> EXPECTED_CONSTRUCTS = List.of(
            "#(...) 插值", "#if(...) 条件", "#end", "${...} 参数", "?? 合并/占位");

    private static Map<String, List<String>> loadCorpus() throws Exception {
        Map<String, List<String>> corpus = new LinkedHashMap<>();
        try (Connection c = DriverManager.getConnection(META_URL, DB_USER, DB_PWD);
             Statement st = c.createStatement()) {
            for (Map.Entry<String, String> e : SOURCES.entrySet()) {
                List<String> values = new ArrayList<>();
                try (ResultSet rs = st.executeQuery(e.getValue())) {
                    while (rs.next()) {
                        String v = rs.getString(1);
                        if (v != null && !v.isBlank()) {
                            values.add(v);
                        }
                    }
                }
                corpus.put(e.getKey(), values);
            }
        }
        return corpus;
    }

    @Test
    @DisplayName("★ T04-13：表达式语料非空（真库取证；取不到就红，不跳过）")
    void corpusIsAvailable() throws Exception {
        Map<String, List<String>> corpus = loadCorpus();
        assertEquals(5, corpus.size(), "语料来源列数");
        assertTrue(corpus.get("eova_object.filter").size() >= 5,
                "★ `eova_object.filter` 语料异常少（" + corpus.get("eova_object.filter").size()
                        + "）⇒ baseline 库未就绪或查询失效（本判据 fail-closed）");
        assertTrue(corpus.get("eova_field.defaulter").size() >= 50, "★ `eova_field.defaulter` 语料异常少");
        assertTrue(corpus.get("eova_field.exp").size() >= 20, "★ `eova_field.exp` 语料异常少");
    }

    @Test
    @DisplayName("★ T04-14：语料只用到**声明的 4 类构件**（用新构件 ⇒ 替代求值器的规格要扩，必须显式改表）")
    void corpusUsesOnlyDeclaredConstructs() throws Exception {
        Map<String, List<String>> corpus = loadCorpus();
        Map<String, Integer> hits = new TreeMap<>();
        for (Map.Entry<String, Pattern> e : CONSTRUCTS.entrySet()) {
            int n = 0;
            for (List<String> values : corpus.values()) {
                for (String v : values) {
                    Matcher m = e.getValue().matcher(v);
                    while (m.find()) {
                        n++;
                    }
                }
            }
            hits.put(e.getKey(), n);
        }
        // 反空断言：语料必须真的被扫过（全 0 会让"构件集合相等"变成空话）
        int total = hits.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(total >= 10, "★ 构件统计总数异常少（" + total + "）⇒ 语料或正则失效");

        List<String> unexpected = hits.entrySet().stream()
                .filter(e -> e.getValue() > 0 && !EXPECTED_CONSTRUCTS.contains(e.getKey()))
                .map(Map.Entry::getKey).toList();
        assertTrue(unexpected.isEmpty(),
                "★ 语料里出现了未声明的语法构件：" + unexpected + " ⇒ 替代 Enjoy 的求值器规格必须覆盖它们");
        List<String> gone = EXPECTED_CONSTRUCTS.stream().filter(k -> hits.getOrDefault(k, 0) == 0).toList();
        assertTrue(gone.isEmpty(),
                "★ 已声明的构件在语料里消失了：" + gone + " ⇒ 若语料变更，请同步本判据（它就是替代实现的规格）");
    }

    @Test
    @DisplayName("★ T04-15：语料的构件分布（把'规格'钉成具体数字：哪类构件出现在哪一列）")
    void corpusDistributionIsFrozen() throws Exception {
        Map<String, List<String>> corpus = loadCorpus();
        Map<String, Map<String, Integer>> perColumn = new TreeMap<>();
        for (Map.Entry<String, List<String>> e : corpus.entrySet()) {
            Map<String, Integer> row = new TreeMap<>();
            for (Map.Entry<String, Pattern> c : CONSTRUCTS.entrySet()) {
                int n = 0;
                for (String v : e.getValue()) {
                    Matcher m = c.getValue().matcher(v);
                    while (m.find()) {
                        n++;
                    }
                }
                row.put(c.getKey(), n);
            }
            perColumn.put(e.getKey(), row);
        }
        // 实测（r308 第 4 轮）：插值/条件/合并只出现在 `eova_object.filter`；`${...}` 出现在 exp 与 defaulter。
        assertTrue(perColumn.get("eova_object.filter").get("#(...) 插值") > 0, "filter 里应有 `#(...)` 插值");
        assertTrue(perColumn.get("eova_object.filter").get("#if(...) 条件") > 0, "filter 里应有 `#if` 条件");
        assertTrue(perColumn.get("eova_object.filter").get("?? 合并/占位") > 0, "filter 里应有 `??`");
        assertTrue(perColumn.get("eova_field.exp").get("${...} 参数") > 0, "exp 里应有 `${...}`");
        assertTrue(perColumn.get("eova_field.defaulter").get("${...} 参数") > 0, "defaulter 里应有 `${...}`");
        // 反向：`eova_menu.filter` 在本 baseline 里为空 ⇒ 声明为 0（防"以为菜单也带表达式"）
        assertEquals(0, corpus.get("eova_menu.filter").size(), "本 baseline 的 eova_menu.filter 为空（实测）");
    }

    @Test
    @DisplayName("★ T04-16：代表性语料在**当前实现**下可求值（未来替代实现的对照基线）")
    void representativeExpressionsAreEvaluable() {
        // 当前实现 = Enjoy（`Engine.getTemplateByString`）。本用例把"必须能算出来"的样本与结果形态固定下来，
        // 作为替代求值器的等价性对照基线（不是写死输出，而是钉"能算 + 与手算一致"）。
        // ⚠️ 用 **baseline 真库真实存在**的列：`eova_user` 只有
        //   id/status/login_id/login_pwd/rid/name/memo/openid/org_id（**没有 company_id**）
        //   —— 这一点很关键：语料里的 `company_id = #(user.company_id??0)` 正是靠 `??` 容忍"列不存在"。
        User user = new User();
        user.set("id", 7);
        user.set("name", "eova");
        LegacyKv kv = LegacyKv.of("user", user);

        // ① 纯字面量：原样返回（语料里占绝大多数）
        assertEquals("and id > 1", ExpUtil.parseSql("and id > 1", kv), "纯 SQL 字面量必须原样返回");
        assertEquals("NOW", ExpUtil.parseSql("NOW", kv), "纯关键字字面量原样返回");
        assertEquals("0.00", ExpUtil.parseSql("0.00", kv), "纯数值字面量原样返回");

        // ② `#(...)` 插值：取 user 的属性（含属性链 `user.role.lv` —— 语料 filter 里就是这么写的）
        assertEquals("uid = 7", ExpUtil.parseSql("uid = #(user.id)", kv), "`#(user.id)` 应插入 id");
        // 属性解析口径实测：Enjoy 按 **getter 名**解析（`user.name` → `getName()`），
        //   而不是按下划线映射列名 ⇒ `user.company_id` 找不到 `getCompany_id()`（类里只有 `getCompanyId()`）
        //   —— 这也解释了语料为什么必须写成 `#(user.company_id??0)`（靠 `??` 吞掉"取不到"）。
        assertEquals("name = eova", ExpUtil.parseSql("name = #(user.name)", kv), "字符串属性插值（getter 口径）");
        // ★ 属性链（语料 filter 的写法 `#(user.role.lv)`）：这里用**嵌套 Kv**钉"链式取值"这条语法；
        //   Model/Record 的字段语义（`user.role.lv` 走 `LegacyRowFieldGetter`）由既有判据
        //   `cn.eova.compat.template.LegacyRowFieldGetterOrderGoldenTest` 专测 —— 它需要**应用装配过的引擎**
        //   （本判据不启 Spring，故不重复覆盖），分工写在这里以免日后误判"没人测"。
        LegacyKv nested = LegacyKv.of("a", LegacyKv.of("b", 5));
        assertEquals("b = 5", ExpUtil.parseSql("b = #(a.b)", nested), "嵌套属性链必须能求值");

        // ③ ★★ `??` 是**承重的**：语料用 `#(user.company_id??0)` 来容忍"该列不存在"
        //   （baseline 的 eova_user 没有 company_id）。这里把两侧语义都钉住：
        assertEquals("company_id = 0", ExpUtil.parseSql("company_id = #(user.company_id??0)", kv),
                "★ `??` 在属性**不存在**时应取右值（语料正是靠它容忍列不存在）");
        //   ⚠️ **不要**断言"去掉 `??` 会抛错" —— 实测该行为**依赖引擎配置**且跨测试共享：
        //     `Engine.use()` 是全局单例，应用启动（其它判据会启 Spring 上下文）会装入
        //     `LegacyRowFieldGetter` ⇒ 之后 Model 的缺失属性返回 null（不抛）；
        //     而在没启过上下文的裸环境里，同样的表达式会抛 "public field not found"。
        //     本判据首版断言了"必须抛错"，单独跑绿、**整模块跑红**（测试间状态串扰）。
        //     ⇒ 这里只钉**稳健**的那条（`??` 两条路径都成立）；Model 字段语义由
        //     `cn.eova.compat.template.LegacyRowFieldGetterOrderGoldenTest` 在装配态下专测。

        // ④ `#if(...)#end` 条件：表达式为真/假两种形态都要能算
        String tpl = "#if(user.id != 0)uid = #(user.id) #end";
        // ★ 空白语义**按实测钉**（首版我写成 "uid = 7 " 被判不等价）：Enjoy 会吞掉 `#end` 前的那个空格
        //   ⇒ 输出是 `uid = 7`。这是"就近指令"的既有行为，替代实现必须一致（否则 SQL 拼接会出现多余空格）。
        assertEquals("uid = 7", ExpUtil.parseSql(tpl, kv), "`#if` 为真时应输出分支体（尾空格被吞）");
        assertEquals("", ExpUtil.parseSql("#if(user.id == 0)uid = #(user.id) #end", kv),
                "`#if` 为假时应输出空");

        // ⑤ ★★ `${...}` 的真实语义（实测推翻了"它是占位符"的直觉）：
        //   `ExpUtil.parse("${file_upload}", props)` **原样返回**，`#(file_upload)` 才替换 ——
        //   两种入参（`Map` 与 `LegacyKv`）都一样。即 `ExpUtil` 走的是 **Enjoy 的 `#(...)` 语法**，
        //   `${...}` 是**Beetl 时代遗留**（`EovaConfig` 里的 Beetl 分支早已注释掉）。
        //   ⇒ 语料里 `eova_field.exp`/`eova_field.defaulter` 的 `${...}` 在当前实现下是**惰性**的；
        //     替代实现**不必**支持它，但必须同样"原样保留"（否则会改动既有 SQL/默认值文本）。
        Map<String, String> props = new LinkedHashMap<>();
        props.put("file_upload", "/upload/img/product");
        assertEquals("${file_upload}", ExpUtil.parse("${file_upload}", props),
                "★ `${...}` 不被替换（惰性保留，实测口径）");
        assertEquals("{up: '${file_upload}'}", ExpUtil.parse("{up: '${file_upload}'}", props),
                "★ JSON 里的 `${...}` 同样原样保留");
        assertEquals("/upload/img/product", ExpUtil.parse("#(file_upload)", props),
                "★ 真正会替换的是 `#(...)` 语法");
    }
}
