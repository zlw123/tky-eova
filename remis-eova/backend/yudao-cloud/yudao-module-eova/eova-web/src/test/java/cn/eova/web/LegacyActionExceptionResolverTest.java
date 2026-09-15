/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import cn.eova.compat.jfinal.core.LegacyActionException;
import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyRenderManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **DES-012 P2-U10 判据：动作异常改由 Spring 异常解析链渲染**。
 *
 * <p>它钉三件事：</p>
 * <ol>
 *   <li><b>行为</b>：{@link LegacyActionException} 经解析器后**响应已被完整写出**
 *       （状态码 + 旧信封正文），并返回**非空** {@code ModelAndView}（= 已处理，
 *       阻止 {@code BasicErrorController} 再写一次体）；</li>
 *   <li><b>放行</b>：非目标异常返回 {@code null}，交给后续解析器（既有 500 语义不变）；</li>
 *   <li><b>★ 反向 / 结构</b>：执行侧 {@link LegacyActionHandler} **不再** catch
 *       {@code LegacyActionException}（源码扫描），且解析器是容器 bean、order = 0。</li>
 * </ol>
 *
 * <p><b>为什么反向判据必须是"源码扫描"而不是行为断言</b>：把 catch 加回去，**行为完全一样**
 * （两条路径都渲染同一个错误）—— 只有"承接方是谁"变了。这类"机制归属"只能靠结构断言钉住，
 * 否则下一轮重构会把异常路径悄悄搬回执行侧而无人发现。</p>
 *
 * <p><b>网络面的既有护栏</b>：{@code MetaFormHttpTest}（未登录 401 信封逐字）与
 * {@code LegacyHttpContractTest}（未登录必须 401/403）会走**真实**的异常解析链，两条一起构成本单元的判据网。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegacyActionExceptionResolverTest {

    @Autowired
    private LegacyActionExceptionResolver resolver;

    @Autowired
    private HandlerExceptionResolver[] resolvers;

    /**
     * 造一次"带错误渲染的 401"并在解析器上跑一遍。
     *
     * @param errorCode 错误码
     * @return 两元组：解析结果 + 响应
     */
    private Object[] resolve(int errorCode) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/home/menu");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LegacyRender render = LegacyRenderManager.getRenderFactory().getErrorRender(errorCode);
        LegacyActionException ex = new LegacyActionException(errorCode, render);
        Object mv = resolver.resolveException(request, response, new Object(), ex);
        return new Object[]{mv, response};
    }

    @Test
    @DisplayName("行为：401 异常经解析器 ⇒ 响应已写出（401 + 旧信封），且返回非空 ModelAndView（已处理）")
    void legacyActionExceptionIsRenderedAndMarkedHandled() throws Exception {
        Object[] r = resolve(401);
        MockHttpServletResponse response = (MockHttpServletResponse) r[1];
        assertNotNull(r[0], "★ 必须返回非 null（已处理）⇒ 否则 BasicErrorController 会再写一次体，401 变成 500 形状");
        assertEquals(401, response.getStatus(), "★ 状态码必须来自错误渲染（旧栈实测 401）");
        String body = response.getContentAsString();
        assertTrue(body.contains("401 Unauthorized"),
                "★ 正文必须是旧信封形状（旧栈实测 {\"state\":\"fail\",\"msg\":\"401 Unauthorized\"}），实际=" + body);
    }

    @Test
    @DisplayName("放行：非 LegacyActionException ⇒ 返回 null（交后续解析器，500 语义不变）")
    void otherExceptionsArePassedThrough() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/home/menu");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertNull(resolver.resolveException(request, response, new Object(), new IllegalStateException("boom")),
                "★ 非目标异常必须放行（不得在这里吞掉）");
        assertEquals(200, response.getStatus(), "放行时不得动响应");
    }

    @Test
    @DisplayName("结构：解析器是容器 bean、order = 0（先于 Spring 默认解析器）")
    void resolverIsRegisteredFirst() {
        assertNotNull(resolver, "解析器必须在容器里（否则 401 会变成 500）");
        assertEquals(0, resolver.getOrder(), "order 必须为 0（DefaultHandlerExceptionResolver 是 LOWEST_PRECEDENCE）");
        assertTrue(resolvers.length > 0, "容器里必须至少注册了异常解析器");
    }

    @Test
    @DisplayName("★ 反向：执行侧不再 catch LegacyActionException（机制归属靠源码断言钉住）")
    void handlerNoLongerCatchesActionException() throws Exception {
        String src = Files.readString(
                new File("src/main/java/cn/eova/web/LegacyActionHandler.java").toPath(), StandardCharsets.UTF_8);
        assertTrue(src.contains("new LegacyInvocation(action, controller).invoke();"),
                "执行侧必须直接 invoke（异常向外抛）");
        assertTrue(!src.contains("catch (LegacyActionException"),
                "★ 执行侧不得再 catch LegacyActionException —— 错误渲染归 Spring 异常解析链（LegacyActionExceptionResolver）");
        assertTrue(!src.contains("handleActionException"),
                "★ 执行侧不得再有 handleActionException（已搬进 LegacyActionExceptionResolver）");
    }
}
