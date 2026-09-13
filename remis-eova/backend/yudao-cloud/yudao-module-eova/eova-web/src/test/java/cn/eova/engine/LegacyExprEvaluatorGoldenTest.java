/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.engine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.compat.template.LegacyExprEvaluator;
import cn.eova.model.Role;
import cn.eova.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **T04 第二段判据：替代求值器与 Enjoy 的差分等价**（不是"看着像"，是逐字节比对）。
 *
 * <p><b>判据口径</b>：同一条模板，**两边都跑** —— 左边是当前实现（Enjoy：
 * `ExpUtil.parseSql`），右边是本仓替代实现（`LegacyExprEvaluator`）⇒ 结果必须逐字节相同；
 * 若 Enjoy 抛错，替代实现也必须抛错（**失败也要等价**，否则"宽容"会掩盖语义差异）。</p>
 *
 * <p>比对语料 = <b>真库语料</b>（`eova_object.filter` / `eova_field.defaulter` / `eova_field.exp` /
 * `eova_field.config` —— 语料与构件集合由 `ExpUtilCorpusTest` 冻结）+ <b>语义边界样例</b>
 * （插值/条件/`#else`/空白/比较/算术/属性链/缺失属性）。</p>
 *
 * <p><b>装配态（r310）：</b>不再需要安装 `LegacyRowFieldGetter` —— 该类是**挂进 enjoy 引擎**的
 * Model/Record 字段读取器，随引擎一起按口径授权删除；本判据的语料全是 `LegacyKv`（Map），
 * 两侧的属性解析顺序由 `LegacyExprEvaluator` 自持（`getter → 公有字段 → Model.get(String) → Map`）。</p>
 *
 * <p><b>fail-closed</b>：baseline 库不可达 ⇒ 本判据红。</p>
 */
class LegacyExprEvaluatorGoldenTest {

    private static final String META_URL = System.getProperty("eova.mysql.meta.url",
            "jdbc:mysql://127.0.0.1:13306/eova_meta?useUnicode=true&characterEncoding=UTF-8");
    private static final String DB_USER = System.getProperty("eova.mysql.user", "root");
    private static final String DB_PWD = System.getProperty("eova.mysql.pwd", "root");

    /** 两边都跑，返回 [旧实现结果, 新实现结果]（异常以 `EX:类型` 表示） */
    private static String[] both(String template, LegacyKv kv) {
        String oldOut;
        try {
            oldOut = ExpUtil.parseSql(template, kv);
        } catch (Exception e) {
            oldOut = "EX:" + e.getClass().getSimpleName();
        }
        String newOut;
        try {
            newOut = LegacyExprEvaluator.render(template, kv);
        } catch (Exception e) {
            newOut = "EX:" + e.getClass().getSimpleName();
        }
        return new String[]{oldOut, newOut};
    }

    private static LegacyKv probeKv() {
        User user = new User();
        user.set("id", 7);
        user.set("name", "eova");
        Role role = new Role();
        role.set("lv", 2);
        user.setRole(role);
        return LegacyKv.of("user", user);
    }

    /** 语义边界样例（含本轮实测真值表里的关键形态） */
    private static List<String> battery() {
        List<String> cases = new ArrayList<>(List.of(
                // 插值
                "#(user.id)", "x=#(user.id)", "#(user.name)", "#(user.role.lv)", "#(user.company_id??0)",
                "#(user.id??9)", "#(user.no.field??9)", "#(1+2)", "#(1.50)", "#(true)", "#(null)",
                // 条件 + else
                "#if(user.id != 0)uid = #(user.id) #end",
                "#if(user.id != 0)uid = #(user.id)#end",
                "#if(user.id == 0)A#end",
                "#if(user.id == 0)A#else B#end",
                "#if(user.id != 0)A#else B#end",
                "head #if(user.id != 0)mid#end tail",
                "#if(user.role.lv != 0)lv > #(user.role.lv) #end",
                "#if(user.role.lv != 0)\nlv > #(user.role.lv)\n#end",
                // 比较
                "#if(user.id > 0)gt#end", "#if(user.id >= 7)ge#end", "#if(user.id <= 7)le#end",
                "#if(user.id != 7)ne#end", "#if(user.id == 7)eq#end",
                // 纯字面量（语料里占绝大多数）
                "and id > 1", "and id > 999", "NOW", "CURRENT_TIMESTAMP", "0.00", "${user.company_id!0}",
                // 嵌套条件
                "#if(user.id != 0)a#if(user.role.lv == 2)b#end c#end",
                // 空白边界（继续让判据校准实现：这些是差分比对里最容易分歧的地方）
                "#if(user.id != 0)a#end\nb",
                "#if(user.id != 0)a #else b#end",
                "#if(user.id == 0)a #else b#end",
                "x\n#if(user.id != 0)y#end\nz",
                // ★ 缺键/缺属性（差分判据逼出来的：样例只覆盖"有键"的 Map 不够 —— 见 T04-21）
                "#(conf.object_code)", "#(conf.object_code??fallback)", "#(conf.no.such.key)"));
        return cases;
    }

    /** 真库语料 */
    private static List<String> dbCorpus() throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(META_URL, DB_USER, DB_PWD);
             Statement st = c.createStatement()) {
            for (String sql : new String[]{
                    "select filter from eova_object where filter is not null and filter <> ''",
                    "select defaulter from eova_field where defaulter is not null and defaulter <> ''",
                    "select exp from eova_field where exp is not null and exp <> ''",
                    "select config from eova_field where config is not null and config <> ''"}) {
                try (ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next()) {
                        String v = rs.getString(1);
                        if (v != null && !v.isBlank()) {
                            out.add(v);
                        }
                    }
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("★ T04-17：边界样例上两边**逐字节等价**（失败也要等价）")
    void batteryMatchesEnjoy() {
        LegacyKv kv = probeKv();
        List<String> diff = new ArrayList<>();
        for (String tpl : battery()) {
            String[] r = both(tpl, kv);
            if (!r[0].equals(r[1])) {
                diff.add("模板[" + tpl.replace("\n", "\\n") + "] 旧=[" + r[0] + "] 新=[" + r[1] + "]");
            }
        }
        assertTrue(diff.isEmpty(), "★ 与 Enjoy 不等价：\n    " + String.join("\n    ", diff));
        // 反空断言：样例必须真的跑过（否则"无差异"是空话）
        assertTrue(battery().size() >= 25, "样例数不得少于 25");
    }

    @Test
    @DisplayName("★ T04-18：**真库语料**上两边逐字节等价（语料是最硬的规格）")
    void dbCorpusMatchesEnjoy() throws Exception {
        LegacyKv kv = probeKv();
        List<String> corpus = dbCorpus();
        // 反空断言：语料取不到就红（fail-closed）
        assertTrue(corpus.size() >= 60, "★ 语料条数异常少（" + corpus.size() + "）⇒ baseline 库未就绪或查询失效");
        List<String> diff = new ArrayList<>();
        for (String tpl : corpus) {
            String[] r = both(tpl, kv);
            if (!r[0].equals(r[1])) {
                diff.add("语料[" + tpl.replace("\n", "\\n").substring(0, Math.min(60, tpl.length())) + "] 旧=["
                        + r[0] + "] 新=[" + r[1] + "]");
            }
        }
        assertTrue(diff.isEmpty(), "★ 真库语料上与 Enjoy 不等价：\n    " + String.join("\n    ", diff));
        assertEquals(0, diff.size());
    }

    /** 从 `AuthUri.java` 抽取的模板字面量（切换实现前必须有等价证据 —— 鉴权规则错一条就是权限事故） */
    private static List<String> authUriTemplates() throws Exception {
        Path src = moduleDir().getParent().resolve("eova-core/src/main/java/cn/eova/auth/AuthUri.java");
        assertTrue(Files.isRegularFile(src), "★ fail-closed：找不到 AuthUri.java " + src);
        Pattern p = Pattern.compile("\"(/[^\"]*#\\([^\"]*\\)[^\"]*)\"");
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(Files.readString(src, StandardCharsets.UTF_8));
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static Path moduleDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        assertTrue(Files.isDirectory(dir.resolve("src/main/java")), "★ fail-closed：找不到模块源码根");
        return dir;
    }

    @Test
    @DisplayName("★ T04-20：`AuthUri` 的模板字面量上两边等价（鉴权规则是权限契约，错一条就是事故）")
    void authUriTemplatesMatchEnjoy() throws Exception {
        // AuthUri 的 kv 形如 {menu: <Menu 模型>, conf: <对象/配置>} —— 这里用同形状的探针数据
        User user = new User();
        user.set("id", 7);
        // `LegacyKv.of` 只接受一对键值 ⇒ 用 set 链（与旧 jfinal Kv 的用法一致）
        LegacyKv menu = LegacyKv.of("code", "demo_menu");
        LegacyKv conf = LegacyKv.of("object_code", "meta_product").set("tree_object_code", "meta_product");
        LegacyKv kv = LegacyKv.of("user", user).set("menu", menu).set("conf", conf);

        List<String> templates = authUriTemplates();
        assertTrue(templates.size() >= 12, "★ 抽取到的 AuthUri 模板异常少（" + templates.size() + "）⇒ 抽取规则失效");
        List<String> diff = new ArrayList<>();
        for (String tpl : templates) {
            String[] r = both(tpl, kv);
            if (!r[0].equals(r[1])) {
                diff.add("模板[" + tpl + "] 旧=[" + r[0] + "] 新=[" + r[1] + "]");
            }
        }
        assertTrue(diff.isEmpty(), "★ AuthUri 模板与 Enjoy 不等价：\n    " + String.join("\n    ", diff));
    }

    @Test
    @DisplayName("★ T04-21：**Map/Kv 缺键 ⇒ null（渲染空串）**，不得抛错 —— 生产登录 500 就是栽在这")
    void missingMapKeyIsNullNotError() {
        // 生产现场：`AuthUri.parseAuthUri` 的 kv 里 `conf = Menu#getMenuConfig()`（JSON 解析出的 Kv），
        // 而不少菜单的 config 里没有 `object_code` 键 ⇒ Enjoy 给 null ⇒ `/api/meta/form/`（空段）。
        // 我首版把"缺键"当"未找到"并抛错 ⇒ `/user/doLogin` 直接 500（真浏览器/列表页判据全绿、
        // 只有**单元/进程内 HTTP 判据**才照出来 —— 因为常驻后端跑的是切换前的类）。
        LegacyKv kv = LegacyKv.of("conf", LegacyKv.of("other", "x"));
        String[] r = both("#(conf.object_code)", kv);
        assertEquals(r[0], r[1], "★ 缺键时两边必须一致（Enjoy 给 null ⇒ 空串）");
        assertEquals("", r[1], "缺键渲染为空串");
        // ★ 缺键 + `??`：**实测两边都渲染空串**（`??` 在这里没有生效）—— 与"左值 null 取右值"的直觉
        //   不符，但这是 Enjoy 的真实行为；本实现与之一致（差分判据保证）。
        //   ⚠️ 我先前**猜**它应该取 `fb` ⇒ 判据当场判我错。凡"以为应该怎样"的地方一律以实测为准。
        String[] r2 = both("#(conf.object_code??fb)", kv);
        assertEquals(r2[0], r2[1], "★ 缺键 + `??` 两边必须一致");
        assertEquals("", r2[1], "缺键 + `??` 实测渲染空串（Enjoy 的 `??` 此形态未生效）");
    }

    @Test
    @DisplayName("★ T04-19：不支持的页面渲染指令**响亮抛错**（绝不静默输出错内容）")
    void unsupportedDirectivesFailLoudly() {
        LegacyKv kv = probeKv();
        for (String tpl : new String[]{"#for(x : list)body#end", "#include(\"/a/b.html\")", "#set(x = 1)",
                "#define foo()bar#end", "#(user.id)" + "#for(a : b)c#end"}) {
            UnsupportedOperationException ex = org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class,
                    () -> LegacyExprEvaluator.render(tpl, kv),
                    "★ 页面渲染指令必须响亮抛错（本类只覆盖业务表达式子集）：" + tpl);
            assertTrue(ex.getMessage() != null && ex.getMessage().contains("不支持"),
                    "异常信息应说明不支持：" + ex.getMessage());
        }
    }
}
