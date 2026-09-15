/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **DES-012 P1-U1 单元判据：静态供给的 Spring 机制承载物**（不起容器）。
 *
 * <p>它钉三件事（判据三型）：</p>
 * <ol>
 *   <li><b>行为</b>：命中真实文件 ⇒ 交出 Spring 的 {@link ResourceHttpRequestHandler}；媒体类型仍是
 *       冻结口径（{@code .css ⇒ text/css}、{@code .js ⇒ application/javascript}）。</li>
 *   <li><b>结构</b>：URL 模式 = {@link LegacyStaticAssets#spacePrefixes()} 的**同一份**前缀表；
 *       order 早于 {@code RequestMappingHandlerMapping}（= 0）。</li>
 *   <li><b>★ 反向 / 语义护栏</b>：**未命中必须返回 null（放行给动作路由）**，不得由本层终结为 404；
 *       且 {@link LegacyDispatcher} 不再持有 {@link LegacyStaticAssets}（旧供给机制已不在请求路径上）。</li>
 * </ol>
 *
 * <p><b>为什么"放行"是本单元最容易写错的地方</b>：Spring 的注册式资源处理
 * （{@code addResourceHandlers}）是**无条件抢占前缀**并在未命中时直接 404；而旧语义是"文件存在才直出"。
 * 落在静态前缀内却没有文件的路径会因此变红，例如：{@code /eova/admin}（动作路由本身在 {@code /eova/} 前缀内）、
 * {@code /excel/imports/<code>}（动作 URL 在 {@code /excel/} 前缀内 —— 实跑：未带会话 **302** 登录、
 * 带会话 **200**；被静态层终结就成 404）。</p>
 */
class StaticResourceHandlerMappingTest {

    /**
     * 造临时 web 根：{@code <root>/eova/lib/x.css}、{@code <root>/eova/lib/eova.meta.js}
     * 与根外目标 {@code <root>/secret.txt}。
     *
     * @param dir JUnit 提供的临时目录
     * @return 临时 web 根
     * @throws Exception 写文件失败
     */
    private static File fixture(Path dir) throws Exception {
        Path webRoot = Files.createDirectories(dir.resolve("web"));
        Path eovaLib = Files.createDirectories(webRoot.resolve("eova/lib"));
        Files.write(eovaLib.resolve("x.css"), "body{color:red}".getBytes(StandardCharsets.UTF_8));
        Files.write(eovaLib.resolve("eova.meta.js"), "var a=1".getBytes(StandardCharsets.UTF_8));
        Files.write(eovaLib.resolve("blob.unknown_ext"), "BLOB".getBytes(StandardCharsets.UTF_8));
        Files.write(webRoot.resolve("secret.txt"), "ROOT-OUTSIDE-SECRET".getBytes(StandardCharsets.UTF_8));
        return webRoot.toFile();
    }

    /**
     * 造**已初始化**的 Spring 供给器。
     *
     * <p>生产里由容器调用 {@code afterPropertiesSet()}；单元判据不起容器，故显式补上这一步
     * （否则消息转换器/解析器链是空的，{@code handleRequest} 会 NPE —— 那不是被测对象的问题）。</p>
     *
     * @param assets 静态空间解析器
     * @return 已初始化的供给器
     * @throws Exception 初始化失败
     */
    private static ResourceHttpRequestHandler initializedHandler(LegacyStaticAssets assets) throws Exception {
        ResourceHttpRequestHandler handler = StaticResourceHandlerMapping.newResourceHandler(assets);
        handler.afterPropertiesSet();
        return handler;
    }

    /**
     * 造被测映射（用真实构造路径），并**补上容器的那一步**。
     *
     * <p>★ 为什么必须 {@code setApplicationContext}：Spring 的 URL 模式表是在
     * {@code initApplicationContext()} → {@code registerHandlers()} 里建起来的；不补这一步，
     * {@code getHandlerInternal} 对**任何**路径都返回 null —— 于是本类最早的"未命中 ⇒ 放行"三条断言
     * 其实是**空跑绿**（自我保护见 {@link #missFallsThroughToActionRouting} 里的先命中后未命中护栏）。</p>
     *
     * @param assets  静态空间解析器
     * @param handler 已初始化的供给器
     * @return 已注册模式表的静态映射
     */
    private static StaticResourceHandlerMapping mapping(LegacyStaticAssets assets,
            ResourceHttpRequestHandler handler) {
        StaticResourceHandlerMapping mapping = new StaticResourceHandlerMapping(assets, handler);
        mapping.setApplicationContext(new StaticApplicationContext());
        return mapping;
    }

    /**
     * 造一个 GET 请求。
     *
     * @param uri 请求 URI
     * @return mock 请求
     */
    private static MockHttpServletRequest get(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    /**
     * 从查找结果里取出真正的处理器。
     *
     * <p>★ 为什么可能是 {@code HandlerExecutionChain}：Spring 6.2 的 {@code AbstractUrlHandlerMapping}
     * 一旦有适用拦截器（如容器注入的 {@code ConversionServiceExposingInterceptor}）就把处理器包进执行链；
     * 生产路径由 {@code DispatcherServlet} 统一解包，判据这边自己解。</p>
     *
     * @param resolved 查找结果
     * @return 真正的处理器（未包装时原样返回）
     */
    private static Object unwrap(Object resolved) {
        return resolved instanceof HandlerExecutionChain chain ? chain.getHandler() : resolved;
    }

    @Test
    @DisplayName("行为：命中真实文件 ⇒ 交出 Spring 的 ResourceHttpRequestHandler")
    void hitHandsOverSpringResourceHandler(@TempDir Path dir) throws Exception {
        LegacyStaticAssets assets = new LegacyStaticAssets(fixture(dir));
        StaticResourceHandlerMapping mapping = mapping(assets, initializedHandler(assets));

        Object handler = unwrap(mapping.getHandlerInternal(get("/eova/lib/x.css")));
        assertNotNull(handler, "静态空间内真实文件必须由本层接管");
        assertTrue(handler instanceof ResourceHttpRequestHandler,
                "供给器必须是 Spring 的 ResourceHttpRequestHandler，实际=" + handler.getClass().getName());
    }

    @Test
    @DisplayName("★ 反向：未命中 ⇒ 返回 null（放行给动作路由/兜底降级），不得由本层判 404")
    void missFallsThroughToActionRouting(@TempDir Path dir) throws Exception {
        LegacyStaticAssets assets = new LegacyStaticAssets(fixture(dir));
        StaticResourceHandlerMapping mapping = mapping(assets, initializedHandler(assets));

        // ★ 防空跑护栏：先证明本映射在**同一夹具**下确实能命中；否则下面几条 null 断言毫无意义
        //   （本轮实测过该形态：映射未初始化时 getHandlerInternal 恒 null，未命中断言全部假绿）。
        assertNotNull(mapping.getHandlerInternal(get("/eova/lib/x.css")),
                "护栏：同一夹具下命中必须成立（否则下面的未命中断言是空跑）");

        assertNull(mapping.getHandlerInternal(get("/eova/no-such.js")),
                "文件不存在 ⇒ 必须放行（旧栈 resource handler miss 后交给动作层）");
        assertNull(mapping.getHandlerInternal(get("/eova/admin")),
                "★ /eova/admin 是动作路由，落在 /eova/ 前缀内 ⇒ 静态层不得吞掉");
        assertNull(mapping.getHandlerInternal(get("/excel/imports/sys_hotel")),
                "★ /excel/imports/<code> 是动作 URL，落在 /excel/ 前缀内 ⇒ 静态层不得吞掉");
        assertNull(mapping.getHandlerInternal(get("/demo/unknown")),
                "★ 静态前缀下但无文件的路径必须放行（交给动作层的退化/404 语义，静态层不得替它判 404）");
    }

    @Test
    @DisplayName("★ 越界守卫：目录穿越与根外目标不得被供给（沿用 LegacyStaticAssets 的判定）")
    void traversalIsNeverServed(@TempDir Path dir) throws Exception {
        LegacyStaticAssets assets = new LegacyStaticAssets(fixture(dir));
        StaticResourceHandlerMapping mapping = mapping(assets, initializedHandler(assets));

        assertNull(mapping.getHandlerInternal(get("/eova/../secret.txt")), "★ 单级回退必须放行（等于不供给）");
        assertNull(mapping.getHandlerInternal(get("/eova/lib/../../secret.txt")), "★ 多级回退同上");
        assertNull(mapping.getHandlerInternal(get("/_static/%2e%2e%2f%2e%2e%2fpom.xml")),
                "★ 编码形式的穿越（解码在越界校验之前）");
    }

    @Test
    @DisplayName("结构：URL 模式 = 同一份七前缀表，且 order 早于请求映射（= 0）")
    void patternsComeFromSamePrefixTableAndOrderPrecedesRequestMapping(@TempDir Path dir) throws Exception {
        LegacyStaticAssets assets = new LegacyStaticAssets(fixture(dir));
        StaticResourceHandlerMapping mapping = mapping(assets, initializedHandler(assets));

        Set<String> expected = new HashSet<>();
        for (String prefix : assets.spacePrefixes()) {
            expected.add(prefix + "**");
        }
        assertEquals(expected, new HashSet<>(mapping.getUrlMap().keySet()),
                "★ 注册前缀必须与解析层的前缀表**同一份事实**（否则产生路径别名，r247 M6）");
        assertEquals(7, assets.spacePrefixes().size(), "静态空间是七个前缀（判据 LegacyStaticAssetsTest 同步钉着）");
        assertTrue(mapping.getOrder() < 0,
                "★ 必须早于 RequestMappingHandlerMapping（order = 0），实际=" + mapping.getOrder());
    }

    @Test
    @DisplayName("行为：供给出的状态/Content-Type/字节仍是冻结契约（.css=text/css、.js=application/javascript）")
    void servesWithFrozenMediaTypeAndBytes(@TempDir Path dir) throws Exception {
        LegacyStaticAssets assets = new LegacyStaticAssets(fixture(dir));
        ResourceHttpRequestHandler handler = initializedHandler(assets);
        StaticResourceHandlerMapping mapping = mapping(assets, handler);

        assertServed(mapping, "/eova/lib/x.css", "text/css", "body{color:red}");
        assertServed(mapping, "/eova/lib/eova.meta.js", "application/javascript", "var a=1");
        assertServed(mapping, "/eova/lib/blob.unknown_ext", "application/octet-stream", "BLOB");
    }

    /**
     * 驱动一次真实供给并断言状态 / 媒体类型 / 字节。
     *
     * <p>为什么用 {@code handleRequest} 而不是读常量：媒体类型的决定点在 Spring 的
     * {@code getMediaType} 钩子里（protected，外部只能通过供给行为观察）。这条断言正是
     * "M3（不覆盖 ⇒ 回落到容器 mime 表，`.js` 漂成 `text/javascript`）"的捕获点。</p>
     *
     * @param mapping      静态映射
     * @param path         请求路径
     * @param expectedType 期望 Content-Type
     * @param expectedBody 期望正文字符串（UTF-8）
     * @throws Exception 供给失败
     */
    private static void assertServed(StaticResourceHandlerMapping mapping, String path,
            String expectedType, String expectedBody) throws Exception {
        MockHttpServletRequest request = get(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Object handler = unwrap(mapping.getHandlerInternal(request));
        assertNotNull(handler, path + " 应能命中（夹具里文件存在）");
        // ★ Spring 的资源处理器要从"映射内路径"属性取路径；真实分发时该属性由
        //   AbstractUrlHandlerMapping.exposePathWithinMapping 写入，这里直调处理器故自行补上
        //   （不补会 IllegalState：Required request attribute ...pathWithinHandlerMapping is not set）。
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, path);
        ((HttpRequestHandler) handler).handleRequest(request, response);
        assertEquals(200, response.getStatus(), path + " 必须 200");
        assertEquals(expectedType, response.getContentType(), "★ " + path + " 的 Content-Type 是冻结契约");
        assertArrayEquals(expectedBody.getBytes(StandardCharsets.UTF_8), response.getContentAsByteArray(),
                path + " 的直出字节必须与文件逐字节一致");
    }

    /**
     * **反向判据（结构）**：{@link LegacyDispatcher} 不得再持有静态供给组件
     * —— 旧机制若回到请求路径上（有人把 {@code staticAssets} 加回去并调用 {@code serve}），这里立刻红。
     */
    @Test
    @DisplayName("★ 反向：LegacyDispatcher 不再持有 LegacyStaticAssets（旧供给机制已离开请求路径）")
    void dispatcherNoLongerHoldsLegacyStaticAssets() {
        boolean holds = Stream.of(LegacyDispatcher.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(t -> t == LegacyStaticAssets.class);
        assertFalse(holds, "★ LegacyDispatcher 不得再持有 LegacyStaticAssets（静态供给已由 StaticResourceHandlerMapping 承担）");
    }
}
