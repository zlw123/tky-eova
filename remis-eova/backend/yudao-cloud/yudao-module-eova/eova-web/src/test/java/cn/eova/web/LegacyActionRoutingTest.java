/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **DES-012 P2-U6/U11 判据：旧动作路由规则逐条冻结（在真路由表上驱动）**。
 *
 * <p>为什么必须在**真上下文**里驱动：本判据锁的是 jfinal 的动作匹配语义，而语义的输入是
 * **真实注册路由表**（`EovaWebRoutes` 24 条 + `WebAppConfig` 的根路由 + `LegacyRoutes` 静态子路由）。
 * 手搭路由表会漏掉"根路由兜底"这类只在真表里成立的行为，故这里直接调
 * {@link LegacyActionHandlerMapping#getHandlerInternal} —— 生产接线本身。</p>
 *
 * <p><b>每条断言的"旧栈真值"来源</b>：都是既往轮次在旧 demo（9090）上实测得到的
 * （`/meta/find/main-table/1` 404、`/zzz_unknown` 落首页、`/app/meta_product` index+urlPara、
 * `/demo/test/nope.js` 404 等），r332 的 U11 只是把它们从"内联在一个方法里"搬到 Spring 映射扩展点，
 * 语义**一字未改**。</p>
 *
 * <p><b>为什么它是 U11 的准入门槛</b>：U11 之前这些规则靠"catch-all 与显式端点在同一个
 * {@code RequestMappingHandlerMapping} 里按具体性排序"才成立；分层为独立 mapping 后改为靠 **order**
 * ⇒ 必须有判据同时钉住"规则不变"与"分层顺序正确"，否则把映射排到前面就会出现
 * `/api/page/bootstrap` 被根路由兜底吃掉（404）这类静默事故。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegacyActionRoutingTest {

    @Autowired
    private LegacyActionHandlerMapping actionMapping;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * 驱动一次匹配（走生产接线），返回匹配结果。
     *
     * @param path 请求路径
     * @return 匹配结果；未命中为 null
     */
    private LegacyActionHandlerMapping.Match match(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        Object handler = actionMapping.getHandlerInternal(request);
        if (handler == null) {
            return null;
        }
        return (LegacyActionHandlerMapping.Match) request.getAttribute(LegacyActionHandlerMapping.MATCH_ATTRIBUTE);
    }

    @Test
    @DisplayName("命中：`/meta/find` ⇒ 路由 /meta + actionKey=find（urlPara 为空）")
    void exactActionMatches() {
        LegacyActionHandlerMapping.Match m = match("/meta/find");
        assertNotNull(m, "/meta/find 必须命中");
        assertEquals("/meta", m.entry.controllerPath);
        assertEquals("find", m.actionKey);
        assertNull(m.urlPara, "无尾段 ⇒ urlPara 为空");
    }

    @Test
    @DisplayName("命中：`/meta/find/main-table` ⇒ urlPara=单段 `main-table`（多值用 `-` 分隔，不是 `/`）")
    void singleSegmentUrlParaIsCarried() {
        LegacyActionHandlerMapping.Match m = match("/meta/find/main-table");
        assertNotNull(m);
        assertEquals("find", m.actionKey);
        assertEquals("main-table", m.urlPara);
    }

    @Test
    @DisplayName("★ C-12：`/meta/find/main-table/1` ⇒ 未命中（旧栈实测 404；urlPara 只吃一段）")
    void multiSegmentUrlParaIsNotFound() {
        assertNull(match("/meta/find/main-table/1"),
                "★ 旧栈实测 404：方法名 + 2 段以上没有对应动作键，不得当整段 urlPara 去渲染");
    }

    @Test
    @DisplayName("★ C-12：单段降级 `/app/meta_product` ⇒ 命中 /app 的 index + urlPara=meta_product")
    void oneSegmentDegradationHitsIndex() {
        LegacyActionHandlerMapping.Match m = match("/app/meta_product");
        assertNotNull(m, "★ 旧栈实测 200：前缀 /app 是已注册路由 ⇒ 降级到该控制器 index + urlPara");
        assertEquals("/app", m.entry.controllerPath);
        assertEquals("index", m.method.getName(), "降级命中的是 index()");
        assertEquals("meta_product", m.urlPara);
    }

    @Test
    @DisplayName("★ C-12：单段未知 `/zzz_unknown` ⇒ 降级到根路由 index（旧栈实测 200 首页）")
    void unknownSingleSegmentFallsToRootRoute() {
        LegacyActionHandlerMapping.Match m = match("/zzz_unknown");
        assertNotNull(m, "★ 旧栈实测 200：前缀为空 ⇒ 根路由 index + urlPara");
        assertEquals("/", m.entry.controllerPath, "必须落到根路由");
        assertEquals("zzz_unknown", m.urlPara);
    }

    @Test
    @DisplayName("★ C-12：多段未知 `/a/b`、`/definitely/not/a/route` ⇒ 未命中（不得逐段剥离）")
    void unknownMultiSegmentIsNotFound() {
        assertNull(match("/a/b"), "★ 旧栈实测 404：前缀 /a 不是已注册路由 ⇒ 不降级");
        assertNull(match("/definitely/not/a/route"), "★ 旧栈实测 404：不得逐段剥离直到命中");
    }

    @Test
    @DisplayName("★ C-12：文件型路径不降级（`/demo/test/nope.js`、`/nope/x.css`，以及**前缀是注册路由**的 `/app/foo.js`）")
    void fileLikePathsAreNotFound() {
        assertNull(match("/demo/test/nope.js"),
                "★ 旧栈实测 404；不设这条会把资产请求降级成 HTML（曾致 SyntaxError: Unexpected token '<'）");
        assertNull(match("/nope/x.css"), "★ 同上（末段含 `.`）");
        // ★★ 判别性用例（r332 变异 M3 实测：上面两条对"删掉 fileLike 守卫"**不敏感** ——
        //    因为 /demo/test 与 /nope 本来都不是注册路由，删守卫后降级也照样失败；
        //    只有"前缀是注册路由"才分得出守卫在不在）：旧栈实测 `/app/foo.js` = **404**
        //    （对照 `/app/foo` 无扩展名 = 500：那是真的降级到 /app 的 index 了）。
        assertNull(match("/app/foo.js"),
                "★ 旧栈实测 404：前缀 /app 是已注册路由，但末段含 `.` ⇒ 仍不得降级");
        assertNull(match("/user/foo.js"), "★ 同上（/user 也是注册路由）");
    }

    @Test
    @DisplayName("★ C-12：空 actionKey ⇒ `index`（`GET /` 是首页，不是 404）")
    void emptyActionKeyMeansIndex() {
        LegacyActionHandlerMapping.Match m = match("/");
        assertNotNull(m, "★ `/` 必须命中根路由");
        assertEquals("index", m.actionKey, "空 actionKey 归一为 index（旧 jfinal 约定）");
        assertEquals("/", m.entry.controllerPath);
    }

    @Test
    @DisplayName("最长前缀优先：`/api/home/menu` ⇒ 命中 /api/home 而不是根路由")
    void longestPrefixWins() {
        LegacyActionHandlerMapping.Match m = match("/api/home/menu");
        assertNotNull(m);
        assertEquals("/api/home", m.entry.controllerPath, "★ 必须取最长前缀（根路由只能兜底）");
        assertEquals("menu", m.actionKey);
    }

    @Test
    @DisplayName("★ 结构：动作映射 order = 1（晚于显式请求映射 ⇒ 旧式分发兜底）")
    void actionMappingRunsAfterExplicitRequestMappings() {
        assertEquals(1, actionMapping.getOrder(), "★ 必须晚于 RequestMappingHandlerMapping（= 0）");
        assertTrue(actionMapping.getOrder() > requestMappingHandlerMapping.getOrder(),
                "显式 Spring 端点必须优先（否则 /api/page/bootstrap 会被根路由兜底吃掉）");
    }

    @Test
    @DisplayName("★ 反向：LegacyDispatcher 已不是 MVC 入口（无 @RestController/@RequestMapping）")
    void dispatcherIsNoLongerAnMvcEntry() {
        assertNull(LegacyDispatcher.class.getAnnotation(RestController.class),
                "★ catch-all 入口已摘除：不得再有 @RestController");
        for (Method method : LegacyDispatcher.class.getDeclaredMethods()) {
            assertNull(method.getAnnotation(RequestMapping.class),
                    "★ 不得再有 @RequestMapping：" + method.getName());
        }
    }

    @Test
    @DisplayName("红线 R1：被既有判据钉住的 `buildActionChain` 仍在（只能退化，不能删类）")
    void pinnedBuildActionChainStillExists() throws Exception {
        assertNotNull(LegacyDispatcher.class.getDeclaredMethod("buildActionChain",
                        java.util.List.class, cn.eova.compat.jfinal.aop.LegacyInterceptor[].class,
                        Class.class, Method.class),
                "★ ActionChainWiringTest 直接驱动它 ⇒ 不能删（删它需同步演进判据，属单独裁定）");
    }
}
