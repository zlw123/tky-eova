/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cn.eova.tools.x;

/**
 * **档位文件判据（r326 · DES-010）**：`eova/{dev,dev-kingbase,prd}.txt` 是同一套键的
 * "环境 × 数据库类型"档，必须**结构同构、差异闭合、坐标自洽**。
 *
 * <p><b>为什么必须有</b>：档位是**部署期唯一的事实源**（`configConstant` 把它整表 `addProp` 进
 * `x.conf`）——"档里少了一个 `main.pwd`"这类缺陷**既不编译错、也不影响既有 MySQL 基线**
 * （那套判据读的是 dev.txt），只会在换成金仓/生产档时炸。⇒ 必须逐档结构校验。</p>
 *
 * <p>三条口径：
 * <ol>
 *   <li><b>键集同构</b>：三个档的键集必须**完全等于** `dev.txt`（防"档里少键 ⇒ 静默用兜底"）；</li>
 *   <li><b>坐标自洽</b>：`db.datasource` 声明的每个 ds 四元组齐全，且 `driver` 与 `url` 前缀匹配；</li>
 *   <li><b>差异闭合</b>：新档与 `dev.txt` 的差异键必须落在**允许集**内（防"顺手改了别的键"）。</li>
 * </ol>
 *
 * <p>★ 资源一律**从 classpath 读**（`target/classes`），因为那才是**运行时真正装载的东西**；
 * 顺带断言"目录里的 .txt 集合 == 声明的档集合"——新增档却忘了更新判据清单时判据会红。</p>
 */
class LegacyDeployProfileFilesTest {

    /** 声明的档集合（`dev.txt` = MySQL 验收基线，与旧栈 9090 同库；另两个 = 本轮新增） */
    private static final List<String> PROFILE_FILES = List.of(
            "eova/dev.txt", "eova/dev-kingbase.txt", "eova/prd.txt");

    /** `driver` 与 `url` 前缀的对应（加新库类型必须同步加到这里 —— fail-closed 而不是"未校验"） */
    private static final Map<String, String> DRIVER_BY_URL_PREFIX = Map.of(
            "jdbc:mysql:", "com.mysql.cj.jdbc.Driver",
            "jdbc:kingbase8:", "com.kingbase8.Driver",
            // 金仓兼容 PG 协议（docs/DES-001-*-kingbase-*.md 里两套 URL 都出现过），驱动仍是 kingbase8
            "jdbc:postgresql:", "com.kingbase8.Driver");

    /** 代码真的会读的键（缺一个就是"运行期静默走默认值/兜底"） */
    private static final List<String> REQUIRED_KEYS = List.of(
            "devMode", "env", "file.dir.base", "db.transaction_level", "db.datasource",
            "db.pwd.encrypt", "job.enable", "app.status.token", "login.user.session");

    private static final Set<String> LEGAL_ENVS = Set.of("DEV", "TEST", "PRE", "PRO", "PRD");

    /** 允许与 `dev.txt` 不同的键（值可不同，键集必须相同） */
    private static final Set<String> COORD_KEYS = Set.of(
            "eova.url", "eova.user", "eova.pwd", "eova.driver",
            "main.url", "main.user", "main.pwd", "main.driver");

    @Test
    @DisplayName("★ 档集合：classpath 上的 eova/*.txt 必须与声明的三个档逐个对应（防漏判据/漏发布）")
    void declaredProfileSetMatchesArtifact() throws Exception {
        Set<String> onDisk = new TreeSet<>();
        for (File f : profileDir().listFiles()) {
            if (f.getName().endsWith(".txt")) {
                onDisk.add("eova/" + f.getName());
            }
        }
        assertEquals(new TreeSet<>(PROFILE_FILES), onDisk,
                "★ classpath 上的档集合必须与判据声明一致（新增档请同时登记判据）");
        for (String p : PROFILE_FILES) {
            assertNotNull(readText(p), "档必须真实存在于 classpath：" + p);
        }
    }

    @Test
    @DisplayName("★ 键集同构 + 必需键齐全 + 坐标自洽 + env 合法（逐档）")
    void everyProfileIsStructurallySound() throws Exception {
        Map<String, String> base = parse(readText("eova/dev.txt"));
        Set<String> baseKeys = base.keySet();
        assertTrue(baseKeys.containsAll(REQUIRED_KEYS), "基线档缺必需键：" + REQUIRED_KEYS);

        for (String p : PROFILE_FILES) {
            Map<String, String> kv = parse(readText(p));
            assertEquals(baseKeys, kv.keySet(),
                    "★ " + p + " 的键集必须与 dev.txt 完全相同（缺键 = 运行期静默用兜底）");
            for (String k : REQUIRED_KEYS) {
                assertTrue(!kv.get(k).isEmpty(), p + " 的必需键不得为空：" + k);
            }
            assertTrue(LEGAL_ENVS.contains(kv.get("env")),
                    p + " 的 env 非法：" + kv.get("env") + "（合法：" + LEGAL_ENVS + "）");
            assertTrue(parseIntOrFail(kv.get("db.transaction_level"), p) > 0,
                    p + " 的 db.transaction_level 必须是正整数");

            List<String> dsList = new ArrayList<>();
            for (String ds : kv.get("db.datasource").split(",")) {
                dsList.add(ds.trim());
            }
            assertTrue(dsList.size() >= 2, p + " 的 db.datasource 至少应有 eova+main：" + dsList);
            for (String ds : dsList) {
                for (String suffix : List.of("url", "user", "pwd", "driver")) {
                    String key = ds + "." + suffix;
                    assertTrue(kv.containsKey(key) && !kv.get(key).isEmpty(),
                            "★ " + p + " 里数据源 " + ds + " 缺坐标键或值为空：" + key);
                }
                String url = kv.get(ds + ".url");
                String driver = kv.get(ds + ".driver");
                String expect = urlPrefixDriver(url);
                assertEquals(expect, driver,
                        "★ " + p + " 里 " + ds + " 的 driver 与 url 不匹配：url=" + url + " driver=" + driver);
            }
        }
    }

    @Test
    @DisplayName("★ 差异闭合：新档 vs dev.txt 只允许差异在「坐标键 + 环境标识」上（可变异）")
    void profileDiffsAreClosed() throws Exception {
        Map<String, String> dev = parse(readText("eova/dev.txt"));

        // ① DEV × 金仓：**只**允许 8 个坐标键不同（env 也是 DEV）
        Map<String, String> kb = parse(readText("eova/dev-kingbase.txt"));
        assertEquals(Set.of(), diffKeys(dev, kb, COORD_KEYS),
                "★ dev-kingbase.txt 与 dev.txt 的差异必须闭合在坐标键上");
        assertEquals(dev.get("env"), kb.get("env"), "金仓档仍是 DEV 环境");
        for (String ds : List.of("eova", "main")) {
            assertTrue(kb.get(ds + ".url").startsWith("jdbc:kingbase8:"),
                    "★ 金仓档的 " + ds + ".url 必须是金仓：" + kb.get(ds + ".url"));
        }

        // ② PRD × 金仓：另允许 env / file.dir.base / devMode 三键（理由见档头注释与 DES-010 §4）
        Map<String, String> prd = parse(readText("eova/prd.txt"));
        Set<String> allowed = new LinkedHashSet<>(COORD_KEYS);
        allowed.addAll(List.of("env", "devMode", "file.dir.base"));
        assertEquals(Set.of(), diffKeys(dev, prd, allowed),
                "★ prd.txt 与 dev.txt 的差异必须闭合在允许集内");
        assertEquals("PRD", prd.get("env"), "★ prd 档的 env 必须是 PRD");
        assertEquals("false", prd.get("devMode"),
                "★ prd 档不得带 devMode=true：configConstant 自己会为「PRD + 开发者模式」告警，"
                        + "旧模板留 true 属复制粘贴残留（差异已登记在 DES-010 §4）");
        assertEquals("/data/eova", prd.get("file.dir.base"), "★ prd 档的上传根沿用旧 prd 模板值");
    }

    /**
     * **金标（外部依赖语义，非变异面）**：`x.conf` 是 `Map.put` ⇒ **后写者胜**。
     *
     * <p>钉它的理由：r323/r324 的代码注释曾断言"`addConfig` **不覆盖**"并据此推断
     * "宿主兜底会赢 / 档值永远赢不了"，与 `eova-tools 1.1.5` 的字节码相反，害得后续推理全偏
     * （见 `LegacyWebBootstrap` ① ② 处的更正注释与 DES-010 §2）。这条判据让"注释再次说谎"不可能：
     * 只要真实语义变了（或有人把装载顺序改成宿主后写），下面两条断言立刻红。</p>
     */
    @Test
    @DisplayName("★ 金标：x.conf 装载顺序 = 后写者胜（宿主兜底先写 ⇒ 档后写 ⇒ 档值生效）")
    void confIsLastWriteWins() {
        String k = "eova.judge.precedence";
        try {
            x.conf.addConfig(k, "host-fallback");
            assertEquals("host-fallback", x.conf.get(k), "宿主兜底先写 ⇒ 先行生效（此时档还没装载）");
            Properties p = new Properties();
            p.setProperty(k, "from-profile");
            x.conf.addProp(p);
            assertEquals("from-profile", x.conf.get(k),
                    "★ 档在 ④ 后装载 ⇒ 必须覆盖宿主兜底（`ConfigTool` = Map.put，后写者胜）");
        } finally {
            x.conf.getProps().remove(k);
        }
    }

    // ---------- 工具 ----------

    /** 差异键：`a` 与 `b` 中值不同或键集不齐的键（`allowed` 内的键不计） */
    private static Set<String> diffKeys(Map<String, String> a, Map<String, String> b, Set<String> allowed) {
        Set<String> diff = new TreeSet<>();
        for (String k : new TreeSet<>(union(a.keySet(), b.keySet()))) {
            if (allowed.contains(k)) {
                continue;
            }
            if (!java.util.Objects.equals(a.get(k), b.get(k))) {
                diff.add(k);
            }
        }
        return diff;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> u = new LinkedHashSet<>(a);
        u.addAll(b);
        return u;
    }

    private static String urlPrefixDriver(String url) {
        for (Map.Entry<String, String> e : DRIVER_BY_URL_PREFIX.entrySet()) {
            if (url.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        throw new AssertionError("未登记的 url 前缀（请把驱动对应关系加进判据）：" + url);
    }

    private static int parseIntOrFail(String v, String who) {
        try {
            return Integer.parseInt(v);
        } catch (RuntimeException e) {
            throw new AssertionError(who + " 的 db.transaction_level 不是整数：" + v);
        }
    }

    /**
     * 档所在的 classpath 目录（= 构建产物 `target/classes/eova`，运行时真正装载的那一份）。
     *
     * @return 目录
     */
    private static File profileDir() throws Exception {
        URL u = LegacyDeployProfileFilesTest.class.getClassLoader().getResource("eova/dev.txt");
        assertNotNull(u, "判据资源 eova/dev.txt 不在 classpath 上 ⇒ 先 mvn process-resources");
        assertTrue("file".equals(u.getProtocol()),
                "本判据要求资源以目录形式提供（构建产物），实测：" + u);
        File d = new File(u.toURI()).getParentFile();
        assertTrue(d.isDirectory(), "档目录不存在：" + d);
        return d;
    }

    /** 读 classpath 文本资源 */
    private static String readText(String name) throws Exception {
        URL u = LegacyDeployProfileFilesTest.class.getClassLoader().getResource(name);
        if (u == null) {
            return null;
        }
        return new String(Files.readAllBytes(new File(u.toURI()).toPath()), StandardCharsets.UTF_8);
    }

    /** 解析 properties 文本（跳过 `#`/`!` 注释与空行；按第一个 `=` 切分并 trim） */
    private static Map<String, String> parse(String text) {
        Map<String, String> kv = new LinkedHashMap<>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            int i = line.indexOf('=');
            assertTrue(i > 0, "非法配置行：'" + raw + "'");
            kv.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
        }
        return kv;
    }

    /**
     * **判据自检（防恒真）**：未登记的 url 前缀 / 非整数的档值必须**抛**，不得"默默通过"。
     */
    @Test
    @DisplayName("判据自检：未登记的 url 前缀与非法档值必须被拒（防恒真）")
    void judgeItselfIsDiscriminating() {
        assertThrows(AssertionError.class, () -> urlPrefixDriver("jdbc:dm://x/y"));
        assertThrows(AssertionError.class, () -> parseIntOrFail("not-a-number", "probe"));
    }
}
