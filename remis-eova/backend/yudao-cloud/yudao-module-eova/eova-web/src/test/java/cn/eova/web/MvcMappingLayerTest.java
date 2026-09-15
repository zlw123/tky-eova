/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **DES-012 P2 判据：MVC 分层顺序不变量**（含"注册式静态资源"落点评估的实测依据）。
 *
 * <p><b>实测表</b>（r332，Spring Boot 3.4.5 / Spring MVC 6.2.6，容器里逐 bean 打印）：</p>
 * <pre>
 * staticResourceHandlerMapping          = -2147483638   ← 我们（条件式静态优先）
 * routerFunctionMapping                 = -1
 * requestMappingHandlerMapping          = 0             ← 显式 Spring 端点
 * legacyActionHandlerMapping            = 1             ← 我们（旧式动作兜底）
 * welcomePage/beanNameHandlerMapping    = 2
 * resourceHandlerMapping（注册式静态）  = 2147483646    ← Boot 自带，排在最后
 * </pre>
 *
 * <p><b>哪一条是承重的</b>：{@code legacyActionHandlerMapping < resourceHandlerMapping}。
 * 资源层（注册式静态）对**未命中**是**终结为 404**（不像我们那样放行），若它排到动作映射**之前**，
 * 那么"落在静态前缀内、但没有同名文件"的动作 URL（{@code /eova/admin}、{@code /excel/imports/<code>}）
 * 会被资源层直接 404 —— 正是 P1-U1 记录过的那个陷阱。故这里把它钉住。</p>
 *
 * <p><b>同时记录一条评估结论</b>：正因为注册式资源排在动作映射之后，"把七个前缀改用
 * {@code ResourceHandlerRegistry} 注册"在当前分层下**没有可观测收益**（存在文件的路径早被
 * {@link StaticResourceHandlerMapping} 供给；动作 URL 又轮不到它）⇒ 该路线保持"不做"，
 * 除非将来撤掉条件式映射（那会丢掉"命中才供给、未命中放行"的语义）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MvcMappingLayerTest {

    @Autowired
    private Map<String, HandlerMapping> mappings;

    @Autowired
    private StaticResourceHandlerMapping staticResourceHandlerMapping;

    @Autowired
    private LegacyActionHandlerMapping legacyActionHandlerMapping;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * 取某 bean 的 order（非 Ordered 视为最低优先级）。
     *
     * @param name bean 名
     * @return order 值
     */
    private int orderOf(String name) {
        HandlerMapping m = mappings.get(name);
        assertNotNull(m, "容器里必须存在 HandlerMapping bean：" + name);
        return m instanceof Ordered o ? o.getOrder() : Ordered.LOWEST_PRECEDENCE;
    }

    @Test
    @DisplayName("★ 不变量：静态(-) < 显式端点(0) < 旧式动作(1) < 注册式静态资源(最后)")
    void mappingLayerOrderIsLoadBearing() {
        assertTrue(staticResourceHandlerMapping.getOrder() < requestMappingHandlerMapping.getOrder(),
                "静态空间必须早于显式端点（字节金标资产优先）");
        assertTrue(requestMappingHandlerMapping.getOrder() < legacyActionHandlerMapping.getOrder(),
                "显式 Spring 端点必须早于旧式动作兜底（/api/page/bootstrap 不被根路由吃掉）");
        assertTrue(legacyActionHandlerMapping.getOrder() < orderOf("resourceHandlerMapping"),
                "★ 动作映射必须早于注册式静态资源：资源层未命中即 404，排在前面会把 "
                        + "/eova/admin、/excel/imports/<code> 这类动作 URL 直接打成 404");
    }

    @Test
    @DisplayName("实测记录：注册式静态资源（resourceHandlerMapping）确实是 LOWEST_PRECEDENCE 档")
    void registryMappingSitsAtLowestPrecedence() {
        int registry = orderOf("resourceHandlerMapping");
        assertEquals(Ordered.LOWEST_PRECEDENCE - 1, registry,
                "★ 注册式静态资源默认 order = LOWEST_PRECEDENCE - 1 ⇒ 排在动作映射之后（故注册式路线无落点）");
        assertEquals(1, legacyActionHandlerMapping.getOrder(), "旧式动作映射 order 固定为 1（判据同时钉值）");
    }
}
