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

    @Test
    @DisplayName("★ r329（容器实跑抓到）：配置了 `eova.ui.dist` 时必须**注入** SPA 壳渲染器")
    void configuredSpaDistIsInjected() throws Exception {
        // 缺陷形态（r329 容器里实测）：配置分支只 `return f` 而**没有** setDistRoot ⇒ bean 日志照打
        // "SPA 壳产物根 = /ui-dist"，但请求 /user/login 时外壳渲染拿到 null ⇒ 500
        // "SPA 壳缺失…（dist=未配置）"。⇒ 任何按文档设置 eova.ui.dist 的部署都会踩到。
        java.nio.file.Path tmp = Files.createTempDirectory("eova-dist-");
        Files.writeString(tmp.resolve("index.html"), "<html>probe</html>");
        // ★ 必须**还原现场**（而不是一律 setDistRoot(null)）：该静态是全局的，而 Spring 上下文启动时
        //   会由 `spaDistRoot()` bean 写进去（走"向上找"分支）⇒ 本用例的 finally 若清成 null，
        //   后面的 `SpaShellHttpTest`/`LegacyTemplateFaceHttpTest` 就会 500
        //   —— 实测（r329 扫描第 1 步）：7 条 HTTP 判据因"SPA 壳缺失…dist=未配置"红。
        java.io.File before = cn.eova.compat.render.LegacySpaShellRender.getDistRoot();
        try {
            java.io.File resolved = LegacyWebBootstrap.resolveSpaDistRootOf(
                    tmp.toAbsolutePath().toString(), new java.io.File("").getAbsoluteFile());
            assertEquals(tmp.toFile().getAbsolutePath(), resolved.getAbsolutePath(),
                    "配置了 dist 就该解析出它");
            assertEquals(tmp.toFile().getAbsolutePath(),
                    cn.eova.compat.render.LegacySpaShellRender.getDistRoot().getAbsolutePath(),
                    "★ 命中后**必须注入** LegacySpaShellRender（否则渲染 500）");

            // 反向：配置指向不存在的目录 ⇒ 返回 null（不静默退回"向上找"，避免"以为生效了"）
            cn.eova.compat.render.LegacySpaShellRender.setDistRoot(null);
            assertEquals(null, LegacyWebBootstrap.resolveSpaDistRootOf("/definitely/not/here", new java.io.File("/")),
                    "配置指错时必须是 null（响亮失败，不猜）");
        } finally {
            cn.eova.compat.render.LegacySpaShellRender.setDistRoot(before);
        }
    }

    @Test
    @DisplayName("★ B 级：logback 骨架存在且**不带** SkyWalking/`%tid`（不引 SW ⇒ 带上就 ClassNotFound）")
    void logbackSkeleton() throws Exception {
        // ★ 必须先剥 XML 注释：本文件头把"为何去掉 skywalking/%tid"写成了注释，不去注释就会**假红**
        //   （同一类坑本轮已踩第二次：pom 判据也曾命中注释里的 cn.iocoder.cloud）。
        String y = stripXmlComments(readResource("logback-spring.xml"));
        assertTrue(y.contains("org/springframework/boot/logging/logback/defaults.xml"),
                "必须 include Spring Boot 的 defaults.xml（平台同形）");
        assertTrue(y.contains("${LOG_FILE}"), "文件 appender 必须落在 ${LOG_FILE}（= logging.file.name）");
        assertTrue(y.contains("ASYNC"), "平台同形：同步写盘外包一层 AsyncAppender");
        // ★ 反向：不引 SkyWalking ⇒ 这两样必须不在（否则 logback 配置整体失效）
        assertFalse(y.contains("skywalking"), "★ 不得出现 skywalking（本服务不引 SW：class 不存在 ⇒ 配置失效）");
        assertFalse(y.contains("%tid"), "★ 不得出现 %tid（没有对应 converter 时会原样输出并告警）");
        assertTrue(y.contains("eova.info.base-package"), "springProperty 必须换成本服务自己的键");
    }

    @Test
    @DisplayName("★ B 级：Dockerfile 与平台同基础镜像 + 驱动外挂的启动形态")
    void dockerfileShape() throws Exception {
        // ★ 必须剥掉 `#` 注释行：本 Dockerfile 的文件头把"启动用 -cp 「app.jar:/app/lib/*」"写成了注释，
        //   不剥注释时把 CMD 改成 `-jar app.jar` 判据照样通过（r329 变异 M13 实测的**第二次**假绿）。
        String d = readRepoFileSansHashComments(
                "remis-eova/backend/yudao-cloud/yudao-module-eova/eova-web/Dockerfile");
        assertTrue(d.contains("FROM eclipse-temurin:17-jre"),
                "与平台 `yudao-module-infra/.../Dockerfile:3` 同基础镜像（平台根那份用 21-jre，与它自己 java.version=17 不一致 ⇒ 不照抄）");
        assertTrue(d.contains("COPY target/eova-web.jar"),
                "拷贝 `spring-boot-maven-plugin` 产出的可执行 jar");
        // ★ 必须断言**启动命令行本身**（`-cp "app.jar:/app/lib/*"`），不能只查 `/app/lib/*`：
        //   后者在 `COPY docker/lib/ /app/lib/` 那一行里也有 ⇒ 把 CMD 改成 `-jar app.jar`（驱动就丢了）
        //   判据照样通过（r329 变异 M13 实测的假绿）。
        assertTrue(d.contains("-cp \"app.jar:/app/lib/*\""),
                "★ 启动命令行必须是 `-cp 「app.jar:/app/lib/*」`（驱动是 test 作用域、不进 fat jar —— 既有口径；改回 -jar 就连不上库）");
        assertTrue(d.contains("EXPOSE 48090"), "端口与 application.yaml 的 server.port 一致");
    }

    @Test
    @DisplayName("★ B 级：k8s 清单与平台同形（三探针/端口/发布策略），且**不伪造环境值**")
    void k8sManifestShape() throws Exception {
        String text = readRepoFile(
                "remis-eova/backend/yudao-cloud/yudao-module-eova/eova-web/k8s/eova-web.yaml");
        // 真解析（不是子串匹配）：snakeyaml 由 spring-boot-starter-web 传递引入
        java.util.List<Object> docs = new java.util.ArrayList<>();
        for (Object d : new org.yaml.snakeyaml.Yaml().loadAll(text)) {
            docs.add(d);
        }
        assertEquals(2, docs.size(), "清单应是 Deployment + Service 两份");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> dep = (java.util.Map<String, Object>) docs.get(0);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> svc = (java.util.Map<String, Object>) docs.get(1);
        assertEquals("Deployment", dep.get("kind"));
        assertEquals("Service", svc.get("kind"));

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> podSpec = at(dep, "spec", "template", "spec");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> container =
                (java.util.Map<String, Object>) ((java.util.List<?>) podSpec.get("containers")).get(0);

        // 端口契约（来自 application.yaml）
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> ports =
                (java.util.List<java.util.Map<String, Object>>) container.get("ports");
        assertTrue(ports != null && !ports.isEmpty(), "container 必须声明 ports");
        assertEquals(48090, ports.get(0).get("containerPort"), "containerPort 必须是 48090");
        // 三探针必须打 Actuator 的 health 分组（平台 k8s 口径；路径是本服务的契约）
        // ★ 探针路径必须**逐个精确**（不能只断言前缀）：readiness 指到 `/actuator/health/ready`
        //   这种"少一个后缀"的写法也满足前缀断言，但 k8s 会永远判不 ready（r330 变异 M14 实测的假绿）。
        java.util.Map<String, String> expectProbe = new java.util.LinkedHashMap<>();
        expectProbe.put("startupProbe", "/actuator/health/liveness");
        expectProbe.put("readinessProbe", "/actuator/health/readiness");
        expectProbe.put("livenessProbe", "/actuator/health/liveness");
        for (java.util.Map.Entry<String, String> e : expectProbe.entrySet()) {
            java.util.Map<?, ?> p = (java.util.Map<?, ?>) container.get(e.getKey());
            assertTrue(p != null, "缺探针：" + e.getKey());
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> httpGet = (java.util.Map<String, Object>) p.get("httpGet");
            assertEquals(e.getValue(), String.valueOf(httpGet.get("path")),
                    e.getKey() + " 必须打平台口径的探针分组（k8s 就靠这两个分组判活/判就绪）");
            assertEquals("http-0", String.valueOf(httpGet.get("port")),
                    e.getKey() + " 的端口名要与 ports[].name 一致");
        }
        // JAVA_OPTS 必须显式给端口（平台 k8s 就是这么覆盖 server.port 的）
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> env =
                (java.util.List<java.util.Map<String, Object>>) container.get("env");
        String javaOpts = String.valueOf(env.get(0).get("value"));
        assertTrue(javaOpts.contains("-Dserver.port=48090"),
                "JAVA_OPTS 必须带 -Dserver.port=48090（与容器端口一致）");
        // hostAliases 必须含 base.platform（Nacos/金仓都靠它）
        assertTrue(String.valueOf(podSpec.get("hostAliases")).contains("base.platform"),
                "hostAliases 必须含 base.platform（Nacos 与金仓都用这个域名）");
        // 发布策略与平台同形
        assertEquals(0, ((Number) scalar(dep, "spec", "strategy", "rollingUpdate", "maxSurge")).intValue(),
                "maxSurge 必须 0（平台口径）");
        // ★ 字符串类断言一律在**剥掉 `#` 注释**的文本上做：本清单的文件头把"为何不挂 SkyWalking"写成了注释，
        //   不剥注释时 `text.contains("skywalking")` 会**假红**（本轮第 4 次同类坑）。
        String bare = readRepoFileSansHashComments(
                "remis-eova/backend/yudao-cloud/yudao-module-eova/eova-web/k8s/eova-web.yaml");
        assertFalse(bare.toLowerCase().contains("skywalking"),
                "本服务不引 SkyWalking ⇒ 清单里不得有它的 agent volume");
        // ★ 不伪造环境值：命名空间/镜像 tag 必须是平台自己的占位符约定（`{{…}}`）
        // 占位符必须**两份文档都在**（Deployment + Service 各有 namespace）—— 只数一次会让
        // "把其中一处写实"漏过去（r330 变异 M16 正是为钉这一点而设计）。
        assertEquals(2, countOf(bare, "{{NAMESPACE}}"),
                "★ 命名空间占位符必须出现 2 次（Deployment + Service 各一），不得编造具体值");
        assertTrue(bare.contains("{{IMAGE_TAG}}"), "★ 镜像 tag 必须留占位符（平台清单的既有约定）");
    }

    /** 数某个子串在文本里出现几次（占位符审计用） */
    private static int countOf(String text, String needle) {
        int n = 0;
        int i = text.indexOf(needle);
        while (i >= 0) {
            n++;
            i = text.indexOf(needle, i + needle.length());
        }
        return n;
    }

    /** 逐层取标量值（供 Integer/Boolean 这类叶子用） */
    @SuppressWarnings("unchecked")
    private static Object scalar(java.util.Map<String, Object> root, String... keys) {
        Object cur = root;
        for (String k : keys) {
            assertTrue(cur instanceof java.util.Map, "配置层级缺键：" + String.join("/", keys));
            cur = ((java.util.Map<String, Object>) cur).get(k);
        }
        return cur;
    }

    /** 逐层取 map 里的键（缺键即断言失败，避免 NPE 掩盖问题） */
    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> at(java.util.Map<String, Object> root, String... keys) {
        Object cur = root;
        for (String k : keys) {
            if (cur == null) {
                break;
            }
            cur = ((java.util.Map<String, Object>) cur).get(k);
        }
        assertTrue(cur instanceof java.util.Map, "配置层级缺键：" + String.join("/", keys));
        return (java.util.Map<String, Object>) cur;
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
        return stripXmlComments(readRepoFile(rel));
    }

    /** 读仓库内文件并剥掉以 `#` 开头的注释行（Dockerfile / shell 等） */
    private static String readRepoFileSansHashComments(String rel) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String line : readRepoFile(rel).split("\n")) {
            if (!line.trim().startsWith("#")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /** 剥掉 XML 注释（判据查的必须是**声明本身**，不是解释它的文字） */
    private static String stripXmlComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?-->", "");
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
