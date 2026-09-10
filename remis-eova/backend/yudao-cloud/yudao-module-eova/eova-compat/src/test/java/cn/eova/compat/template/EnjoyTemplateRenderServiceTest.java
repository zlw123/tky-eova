/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.template;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板渲染接缝的 golden 字节比对测试（acceptanceProfile: golden-render-equivalence）。
 *
 * <p>判据来自 DES-002-R4 §7.4：阶段 1 的页面行为证据是「与旧系统 golden 逐字节一致」。
 * 本测试直接对旧 demo 录制的真实响应正文做比对，而不是断言自造的期望值。
 *
 * <p>golden 证据位于 {@code docs/.local/baseline/evidence/}（本地维护，不 commit）。
 * 证据缺失时本测试会 <b>skip</b> 而非失败 —— 跳过状态本身即是"未执行"的记录，
 * 符合 §7.1「未执行必须标记，不得隐藏」。
 */
class EnjoyTemplateRenderServiceTest {

    /** 旧 demo 的 golden 响应（curl -i 录制，需剥离 HTTP 头部） */
    private static final String LOGIN_GOLDEN = "02-get-login-200.html";
    private static final String INDEX_GOLDEN = "05-get-index-200.html";

    private static Path repoRoot;
    private static Path mergedWebRoot;
    private static Path evidenceDir;

    @BeforeAll
    static void setUp() throws IOException {
        repoRoot = locateRepoRoot();
        evidenceDir = repoRoot.resolve("docs/.local/baseline/evidence");
        // 合并 web 根：旧系统运行时 resourcePath = 文件系统 webapp + classpath:webapp，
        // 源码布局下等价于「demo webapp 根 + view webapp 根」的合并
        mergedWebRoot = repoRoot.resolve("docs/.local/spikes/sp2-webroot");
        buildMergedWebRoot(
                repoRoot.resolve("meta-eova/eova/demo/src/main/webapp"),
                repoRoot.resolve("meta-eova/eova/view/src/main/resources/webapp"),
                mergedWebRoot);
    }

    @Test
    @DisplayName("登录页渲染与旧系统 golden 逐字节一致")
    void loginPageMatchesGoldenGolden() throws IOException {
        Path golden = evidenceDir.resolve(LOGIN_GOLDEN);
        Assumptions.assumeTrue(Files.exists(golden),
                "golden 缺失（未执行 record-baseline.sh）：" + golden);

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("app_name", "EOVA低代码开发平台");
        scope.put("login_id", "eova");
        scope.put("login_pwd", "000000");
        scope.put("isCaptcha", "false");
        scope.put("copyright", "© 2015-2026 EOVA.CN");

        String html = service().render("/eova/_view/index/login.html", scope);
        byte[] got = html.getBytes(StandardCharsets.UTF_8);
        byte[] want = stripHttpHeaders(Files.readAllBytes(golden));

        assertArrayEquals(want, got,
                "登录页渲染与 golden 不一致：golden=" + want.length + " bytes, rendered=" + got.length + " bytes");
    }

    @Test
    @DisplayName("首页渲染与旧系统 golden 逐字节一致（覆盖 #renderOrElse 指令）")
    void indexPageMatchesGolden() throws IOException {
        Path golden = evidenceDir.resolve(INDEX_GOLDEN);
        Assumptions.assumeTrue(Files.exists(golden),
                "golden 缺失（未执行 record-baseline.sh）：" + golden);

        Map<String, Object> scope = new LinkedHashMap<>();
        // app_name / app_logo 在配置中未设置 -> 走模板 ?? 默认值
        scope.put("LOGIN_INFO", "超管[曹雪芹]");
        scope.put("loginUser", new LoginUser());

        String html = service().render("/eova/_view/index/index.html", scope);
        byte[] got = html.getBytes(StandardCharsets.UTF_8);
        byte[] want = stripHttpHeaders(Files.readAllBytes(golden));

        assertArrayEquals(want, got,
                "首页渲染与 golden 不一致：golden=" + want.length + " bytes, rendered=" + got.length + " bytes");
        // 首页依赖 #renderOrElse 注入 _eova/include_index.html，缺失时该断言先行失败，
        // 便于区分"指令静默失效"与"其他渲染差异"
        String text = new String(got, StandardCharsets.UTF_8);
        assertTrue(text.contains("/_eova/theme/eova.theme.js"),
                "首页缺少 #renderOrElse 注入的扩展块 —— 检查 PathKit.setWebRootPath 是否生效");
    }

    /** 构造渲染服务，注册模板所需的共享方法 */
    private TemplateRenderService service() {
        return new EnjoyTemplateRenderService(mergedWebRoot.toString(), new SharedMethods());
    }

    /** 与旧系统 BaseSharedMethod 等价的共享方法子集（模板实际调用的部分） */
    public static class SharedMethods {
        /** 与旧系统一致的前端 UI 配置 JSON（值取自 baseline 库 eova_config） */
        private static final String UI_CONF =
                "{\"web_file\":\"http://127.0.0.1:9090\",\"web_cdn\":\"http://127.0.0.1:9090\","
                        + "\"ver\":\"3.8.0.2\",\"ui.skin\":\"ele\",\"app_domain\":\"https://www.eova.cn\","
                        + "\"ui.zoom\":\"100%\",\"web_static\":\"http://127.0.0.1:9090\","
                        + "\"app_main_title\":\"EovaMeta\"}";

        /** eova_config 表中与模板注入相关的配置项 */
        private static final Map<String, String> CONF = new LinkedHashMap<>();

        static {
            CONF.put("ui.include", "/_eova/include.html");
            CONF.put("ui.include.index", "/_eova/include_index.html");
            CONF.put("ui.include.nav", "/_eova/include_nav.html");
        }

        /** 返回前端 UI 配置 JSON，供 me.conf.putAll 注入 */
        public String getUIConf() {
            return UI_CONF;
        }

        /** 取配置项 */
        public String conf(String key) {
            return CONF.get(key);
        }

        /** 带默认值的取配置 */
        public String conf(String key, String defaultValue) {
            String v = CONF.get(key);
            return v != null ? v : defaultValue;
        }
    }

    /** 登录用户视图对象：仅提供 index.html 用到的 isAdmin */
    public static class LoginUser {
        /** 旧 demo 的 eova 用户为超管，故恒为 true */
        public boolean getIsAdmin() {
            return true;
        }
    }

    /** 从当前模块目录向上定位仓库根（以 meta-eova 目录为标志） */
    private static Path locateRepoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("meta-eova/eova"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("未能定位仓库根（找不到 meta-eova/eova）");
    }

    /** 重建合并 web 根：demo webapp 与 view webapp 合并到同一目录 */
    private static void buildMergedWebRoot(Path demoWebapp, Path viewWebapp, Path target) throws IOException {
        if (Files.exists(target)) {
            deleteRecursively(target);
        }
        Files.createDirectories(target);
        copyRecursively(demoWebapp, target);
        copyRecursively(viewWebapp, target);
    }

    /** 递归复制目录内容；同名文件以先复制者为准（模拟 Undertow 先命中先服务） */
    private static void copyRecursively(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path dst = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dst);
                } else if (!Files.exists(dst)) {
                    Files.createDirectories(dst.getParent());
                    Files.copy(p, dst);
                }
            }
        }
    }

    /** 递归删除目录 */
    private static void deleteRecursively(Path p) throws IOException {
        try (Stream<Path> walk = Files.walk(p)) {
            for (Path q : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(q);
            }
        }
    }

    /** 剥离 HTTP 响应头部，返回正文（golden 由 curl -i 录制） */
    private static byte[] stripHttpHeaders(byte[] raw) {
        for (int i = 0; i + 3 < raw.length; i++) {
            if (raw[i] == '\r' && raw[i + 1] == '\n' && raw[i + 2] == '\r' && raw[i + 3] == '\n') {
                return java.util.Arrays.copyOfRange(raw, i + 4, raw.length);
            }
        }
        return raw;
    }
}
