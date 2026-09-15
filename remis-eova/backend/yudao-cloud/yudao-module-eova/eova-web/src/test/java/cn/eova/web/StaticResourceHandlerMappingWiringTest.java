/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **DES-012 P1-U1 接线判据：静态供给的 Spring 机制在真实 MVC 上下文里确实接管**。
 *
 * <p>为什么要单独一条"接线"判据（r305 P1 的教训）：装配器自身正确 ≠ 容器里生效 ——
 * 当时的真缺陷正是"装配器对、接线处没调用"。本判据在**真上下文**里断言：</p>
 * <ol>
 *   <li>{@link StaticResourceHandlerMapping} 是容器里的 bean，且 order 早于
 *       {@link RequestMappingHandlerMapping}（早于才可能被 {@code DispatcherServlet} 先选中）；</li>
 *   <li>资源供给器带着我们的路径解析器（{@code ResourceResolver} 已装配，而非空链）；</li>
 *   <li>供给器 bean 确实被映射引用（同一实例）。</li>
 * </ol>
 *
 * <p><b>为什么上下文注解必须与既有 {@code @SpringBootTest} 一致</b>：旧引导是"每 JVM 一次"语义，
 * 同 JVM 起第二个上下文会立刻 {@code Model mapping already exists}（r246 实测）。
 * 故本类只写 {@code @SpringBootTest(webEnvironment = RANDOM_PORT)}，与
 * {@code LegacyStaticAssetContractTest} / {@code LegacyHttpContractTest} 完全相同 ⇒ 复用同一上下文缓存。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StaticResourceHandlerMappingWiringTest {

    @Autowired
    private StaticResourceHandlerMapping staticResourceHandlerMapping;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private ResourceHttpRequestHandler eovaStaticResourceHandler;

    @Test
    @DisplayName("接线：静态映射在容器里，且 order 早于 RequestMappingHandlerMapping")
    void mappingIsWiredBeforeRequestMapping() {
        assertTrue(staticResourceHandlerMapping.getOrder() < requestMappingHandlerMapping.getOrder(),
                "★ 静态优先必须靠 order 实现：静态=" + staticResourceHandlerMapping.getOrder()
                        + "，请求映射=" + requestMappingHandlerMapping.getOrder());
    }

    @Test
    @DisplayName("接线：供给器带着路径解析器（解析器链非空），且 URL 模式引用同一个供给器实例")
    void handlerCarriesResolverAndIsReferencedByMapping() {
        assertFalse(eovaStaticResourceHandler.getResourceResolvers().isEmpty(),
                "★ 资源解析器必须已装配（空链 ⇒ 任何静态请求都 404）");
        assertTrue(staticResourceHandlerMapping.getUrlMap().values().stream()
                        .allMatch(v -> v == eovaStaticResourceHandler),
                "★ 映射引用的必须是容器里那个供给器 bean（不是另 new 的实例 —— 否则它没被容器初始化）");
        assertSame(eovaStaticResourceHandler,
                staticResourceHandlerMapping.getUrlMap().values().iterator().next(),
                "同上");
    }
}
