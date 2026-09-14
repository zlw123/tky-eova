/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * **阶段三技术栈对齐（A 级）的静态判据**（r328 · `docs/DES-011-R1-phase3-stack-alignment.md`）。
 *
 * <p><b>它钉的是什么</b>：六个 A 级缺口里，有四个是"**配置文件/pom 里必须存在的形态**"——
 * 一旦被谁顺手删掉（或改名、或把 Nacos 塞进默认档），运行期就会退化，而**既有 583 条判据全绿**
 * （它们都不看这些键）。⇒ 必须有静态判据把它们钉住：</p>
 *
 * <ol>
 *   <li><b>注册/配置中心</b>：SC / SCA BOM（**与平台同版本**）+ nacos starter 必须存在；</li>
 *   <li><b>服务身份</b>：`spring.application.name = base-platform-eova-server`（LC-002 已登记口径）
 *       + 默认档 `local`（**默认档必须不连 Nacos**，否则扫描在无注册中心的机器上就崩）；</li>
 *   <li><b>健康检查**：`/actuator` 基址 + 只暴露 health + probes 打开（平台 k8s 三探针口径）；</li>
 *   <li><b>发布形态</b>：`spring-boot-maven-plugin:repackage` + `finalName`（`java -jar`）；</li>
 *   <li>★ <b>事实源边界（U9）</b>：我们的 profile 配置文件里**不得**出现 EOVA 自己的坐标键
 *       （`eova.*` / `db.datasource`）—— 那些键的事实源是 `eova/*.txt` 档位，档值优先
 *       （实测：Nacos 下发同名 `eova.db.url` 也**覆盖不了**档值，见 `verify-phase3-alignment.sh`）。</li>
 * </ol>
 *
 * <p>运行期面（端点真 200 / jar 真起得来 / Nacos 真注册）**不在本判据**，由
 * `docs/.local/spikes/verify-phase3-alignment.sh`（扫描第 19 步）实跑。</p>
 */
class Phase3StackAlignmentTest {

    /** 本模块资源里的 Spring 配置（构建产物里读，保证判据看的是**真正装载的那一份**） */
    private static final String APP_YAML = "application.yaml";
    private static final String LOCAL_YAML = "application-local.yaml";
    private static final String DEV_YAML = "application-dev.yaml";
    private static final String PROD_YAML = "application-prod.yaml";

    @Test
    @DisplayName("★ A4：服务身份与默认档 —— 服务名取 LC-002 口径、默认档 local")
    void serviceIdentityAndDefaultProfile() throws Exception {
        String y = readResource(APP_YAML);
        assertTrue(y.contains("name: base-platform-eova-server"),
                "★ `spring.application.name` 必须是已登记口径 base-platform-eova-server（Nacos 实例名/网关目标/dataId 前缀都由它决定）");
        assertTrue(y.contains("active: local"),
                "★ 默认档必须是 local（不连 Nacos）—— 否则无注册中心的机器上扫描/单测会退化");
    }

    @Test
    @DisplayName("★ A5：Actuator 口径 —— /actuator 基址 + 只暴露 health + probes 打开")
    void actuatorConvention() throws Exception {
        String y = readResource(APP_YAML);
        assertTrue(y.contains("base-path: /actuator"), "平台口径：base-path=/actuator");
        assertTrue(y.contains("include: health"),
                "平台只暴露 health（多暴露等于把内部端点开给运维面之外的人）");
        assertTrue(y.contains("probes:") && y.contains("enabled: true"),
                "★ k8s 探针打 /actuator/health/{liveness,readiness} ⇒ 必须显式开 probes（实测不开时这两个子路径 404）");
        assertTrue(y.contains("show-details: never"), "平台口径：show-details=never");
    }

    @Test
    @DisplayName("★ A2/A3：Nacos —— 默认档关掉；dev/prod 档键路齐备（含 file-extension）")
    void nacosProfiles() throws Exception {
        String local = readResource(LOCAL_YAML);
        // ★ 注意写法：必须**逐个**断言"section 紧跟 enabled: false"，不能只查"文件里出现过 enabled: false"
        //   —— 实测（r328 变异 M5）只把 discovery 那条改成 true 时，旧写法仍会通过（假的"已覆盖"）。
        assertTrue(local.replace("\r", "").contains("discovery:\n        enabled: false"),
                "★ 默认档（local）必须显式关掉 Nacos discovery（`discovery:` 下紧跟 `enabled: false`）");
        assertTrue(local.replace("\r", "").contains("config:\n        enabled: false"),
                "★ 默认档必须显式关掉 Nacos config（`config:` 下紧跟 `enabled: false`）");

        for (String profile : List.of(DEV_YAML, PROD_YAML)) {
            String y = readResource(profile);
            for (String key : List.of("server-addr:", "namespace:", "group:", "username:",
                    "password:", "file-extension: yaml")) {
                assertTrue(y.contains(key), profile + " 缺 Nacos 键：" + key);
            }
            assertTrue(y.contains("spring.config.import") || y.contains("import:"),
                    profile + " 缺 `spring.config.import`（平台按 `${app}-${profile}.yaml` 拉配置）");
        }
    }

    @Test
    @DisplayName("★★ U9 事实源边界：配置档里**不得**出现 EOVA 自己的坐标键（档位才是事实源）")
    void noEovaKeysInSpringProfiles() throws Exception {
        // 为什么：`x.conf` 是 Map.put、档在 ④ 后装载 ⇒ 档里出现的键一律胜出；反过来说，
        // 若我们把 `eova.url` 一类键写进 Spring 配置，就会造出"两处事实源"的假象（并可能在下发时被误读）。
        // 允许的写法只有：`eova/*.txt` 档位（含 `-Deova.prop` 选档）。
        List<String> offenders = new ArrayList<>();
        for (String f : List.of(APP_YAML, LOCAL_YAML, DEV_YAML, PROD_YAML)) {
            for (String line : readResource(f).split("\n")) {
                String t = line.trim();
                if (t.startsWith("#")) {
                    continue;
                }
                if (t.startsWith("eova.") || t.startsWith("main.") || t.startsWith("db.datasource")
                        || t.startsWith("eova:")) {
                    offenders.add(f + " → " + t);
                }
            }
        }
        assertEquals(List.of(), offenders,
                "★ Spring 配置里不得出现 EOVA 的坐标键（它们的事实源是 eova/*.txt 档位）：" + offenders);
    }

    @Test
    @DisplayName("★ A1/A6：pom —— SC/SCA BOM（与平台同版本）· nacos starter · repackage+finalName · -parameters")
    void pomsCarryPlatformShapedBuild() throws Exception {
        String root = readRepoFileNoComments("remis-eova/backend/pom.xml");
        for (String s : List.of("spring-cloud-dependencies", "2024.0.1",
                "spring-cloud-alibaba-dependencies", "2023.0.3.2", "<parameters>true</parameters>")) {
            assertTrue(root.contains(s), "根 pom 缺：" + s + "（DES-011 U1/U8；版本要与平台逐字一致）");
        }
        // SC/SCA 必须 import 成 BOM（不是普通依赖）
        assertTrue(root.contains("<type>pom</type>") && root.contains("<scope>import</scope>"),
                "SC/SCA 必须以 import 方式引 BOM");

        String web = readRepoFileNoComments("remis-eova/backend/yudao-cloud/yudao-module-eova/eova-web/pom.xml");
        for (String s : List.of("spring-cloud-starter-alibaba-nacos-discovery",
                "spring-cloud-starter-alibaba-nacos-config", "spring-boot-starter-actuator",
                "spring-boot-maven-plugin", "<goal>repackage</goal>", "<finalName>${project.artifactId}</finalName>")) {
            assertTrue(web.contains(s), "eova-web pom 缺：" + s + "（DES-011 U2/U5/U6）");
        }
        // 硬编码 Spring Boot 版本必须已消除（交 BOM 管）
        assertFalse(web.contains("<version>3.4.5</version>"),
                "★ eova-web 不得再硬编码 Spring Boot 版本（U8：交父 pom 的 BOM 管）");
        // 不引平台内部库（拿哥 r327 口径①）
        assertFalse(web.contains("<groupId>cn.iocoder.cloud</groupId>"),
                "★ 不得**声明**平台内部 starter 依赖（口径①：独立 reactor + 边缘集成；注释里提到它不算）");
    }

    @Test
    @DisplayName("★ U6 前置：eova-core 必须产出 test-jar（否则 `mvn package` 解析失败）")
    void coreProducesTestJar() throws Exception {
        String core = readRepoFileNoComments("remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/pom.xml");
        assertTrue(core.contains("<goal>test-jar</goal>"),
                "★ eova-db-adapter 以 `eova-core:jar:tests` 依赖其测试基建 ⇒ 不产出 test-jar 时 `mvn test` 侥幸能跑、"
                        + "`mvn package` 直接解析失败（实测）——阶段三要出可执行 jar，必须补齐");
    }

    // ---------- 工具 ----------

    /** 读本模块 classpath 资源（构建产物里的那一份） */
    private static String readResource(String name) throws IOException {
        try (java.io.InputStream in = Phase3StackAlignmentTest.class.getClassLoader().getResourceAsStream(name)) {
            assertTrue(in != null, "classpath 上找不到：" + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 读仓库内文件（从工作目录向上找到仓库根）。
     *
     * <p>为什么不用相对路径写死：maven / IDE / 命令行三种姿势的工作目录不同（本项目已踩过）。</p>
     *
     * @param rel 仓库根下的相对路径
     * @return 文件内容
     */
    /**
     * 读仓库内文件并**剥掉 XML 注释**。
     *
     * <p>★ 为什么必须剥注释：本判据第一版直接在原文里查 `cn.iocoder.cloud`，结果命中的是 pom 里那句
     * **注释**"不引平台内部库（cn.iocoder.cloud:*）"⇒ **假红**（"扫到注释"这一类坑本项目已踩过多次：
     * 判据查的必须是**声明本身**，不是解释它的文字）。</p>
     *
     * @param rel 仓库根下的相对路径
     * @return 去掉 XML 注释后的内容
     */
    private static String readRepoFileNoComments(String rel) throws IOException {
        return readRepoFile(rel).replaceAll("(?s)<!--.*?-->", "");
    }

    private static String readRepoFile(String rel) throws IOException {
        File dir = new File("").getAbsoluteFile();
        for (int i = 0; i < 10 && dir != null; i++) {
            File f = new File(dir, rel);
            if (f.isFile()) {
                return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            }
            dir = dir.getParentFile();
        }
        throw new AssertionError("仓库内找不到文件（工作目录向上 10 层）：" + rel);
    }
}
