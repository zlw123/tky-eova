/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

/**
 * **静态空间供给的 Spring 机制承载物**（DES-012 P1-U1，r332）。
 *
 * <p><b>它替换掉的是什么</b>：U1 之前，静态供给是自研的 {@code LegacyStaticAssets.serve(path, response)}
 * —— 自己判前缀、自己写字节、自己设 {@code Content-Type}/{@code Content-Length}/{@code Last-Modified}，
 * 并且由 {@link LegacyDispatcher} 的 catch-all 处理器**内联**在分发流程里调用。
 * 现在：供给由 Spring 的 {@link ResourceHttpRequestHandler} 完成（响应头、条件请求、字节写出都是 Spring 的），
 * 而"静态优先"由本类（Spring 的 {@link SimpleUrlHandlerMapping} 扩展点）承载。</p>
 *
 * <p><b>旧栈语义（必须等价，实测来源见 {@link LegacyStaticAssets} 类文档）</b>：资源处理器**先于**动作分发，
 * 但**只有文件真实存在时**才直出；未命中就交给动作层（旧 undertow resource handler miss 后落到 jfinal 动作）。
 * 因此"落在静态前缀内、却没有对应文件"的路径**必须放行**，否则会改掉既有结构里"静态前缀与动作路由重叠"
 * 的那几个 URL：{@code /eova/admin}（动作路由本身就在 {@code /eova/} 前缀内）、
 * {@code /excel/imports/<code>}（动作 URL 在 {@code /excel/} 前缀内；判据
 * {@code LegacySubResourceHttpTest} 钉着 —— 未带会话实测 **302** 登录、带会话 **200**，
 * 若被静态层终结就变成 404）。</p>
 *
 * <p><b>★ 已声明的适配（1 处，非静默偏离）</b>：本类**没有**用
 * {@code WebMvcConfigurer#addResourceHandlers}/{@code ResourceHandlerRegistry} 注册式映射 ——
 * 那对 API 构造的正是本类继承的 {@code SimpleUrlHandlerMapping} + {@code ResourceHttpRequestHandler}，
 * 但它的映射是**无条件抢占前缀**的：一旦注册 {@code /eova/**}，{@code /eova/admin} 会因"文件不存在"而
 * 被资源处理器**终结为 404**，{@code /excel/**} 同理；且注册式映射的 order 默认排在
 * {@code RequestMappingHandlerMapping}（order = 0）**之后**，而本项目路由是 catch-all
 * {@code @RequestMapping("/**")} ⇒ 资源永远不会被选中。
 * 所以本单元采用"同一对 Spring 类 + 条件式查找"：命中才返回处理器，未命中返回 {@code null} 交下一个映射。
 * 待 P2（路由显式化）后，动作路由不再是 catch-all，注册式 {@code ResourceHandlerRegistry} 才具备落地前提；
 * 届时应改回注册式并删除本类的"条件"部分（登记在 {@code DES-012-R1} §6 与台账）。</p>
 *
 * <p><b>为什么不是"两套机制"</b>：路径→文件的解析（解码口径、越界守卫、七前缀）仍由
 * {@link LegacyStaticAssets} 承担，因为它是既有单元判据（{@code LegacyStaticAssetsTest}）直接钉住的
 * 冻结语义（红线 R1：判据一行不改）；本类只负责"何时交给 Spring 供给"。即：**解析是政策，供给是机制**。</p>
 *
 * @see RequestPath
 * @see LegacyStaticAssets
 */
public class StaticResourceHandlerMapping extends SimpleUrlHandlerMapping {

    /** 命中时暂存"规范化路径 + 已解析文件"的请求属性名（供资源解析器/媒体类型取用） */
    static final String RESOLVED_ATTRIBUTE = StaticResourceHandlerMapping.class.getName() + ".resolved";

    /** 路径→文件的解析政策（冻结语义的持有者） */
    private final LegacyStaticAssets assets;

    /**
     * 构造：把七个静态空间注册成 URL 模式，并把自己排在请求映射**之前**。
     *
     * @param assets  静态空间解析器（前缀表与命中判定都取自它 —— 单一事实源）
     * @param handler Spring 资源供给器（由 {@link #newResourceHandler(LegacyStaticAssets)} 构造并由容器初始化）
     */
    public StaticResourceHandlerMapping(LegacyStaticAssets assets, ResourceHttpRequestHandler handler) {
        this.assets = assets;
        // ★ 必须早于 RequestMappingHandlerMapping（Spring Boot 里其 order = 0）：
        //   旧栈顺序是"资源处理器先于 jfinal 动作"；晚于它则永远命中不到（路由是 catch-all）。
        setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        Map<String, Object> urlMap = new LinkedHashMap<>();
        // ★ 前缀表取自 assets（不是本类另写一份）：判定前缀与注册前缀必须是同一份事实，否则产生路径别名（r247 M6）
        for (String prefix : assets.spacePrefixes()) {
            urlMap.put(prefix + "**", handler);
        }
        setUrlMap(urlMap);
    }

    /**
     * 条件式查找：**文件真实存在**才交 Spring 供给；否则返回 {@code null} 让分发继续（交给动作路由/兜底降级）。
     *
     * @param request 请求
     * @return 命中的 Spring 资源供给器；未命中返回 null（= 放行给下一个 HandlerMapping）
     * @throws Exception 查找失败
     */
    @Override
    protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
        String path = RequestPath.of(request);
        File file = assets.resolve(path);
        if (file == null) {
            // ★ 旧栈语义：静态未命中 ⇒ 继续走动作路由。
            //   这条不是"优化"：`/eova/admin`（动作路由就在 /eova/ 前缀内）与
            //   `/excel/imports/<code>`（动作 URL 在 /excel/ 前缀内，判据 LegacySubResourceHttpTest）
            //   都靠它才不会被静态层改写成 404。
            return null;
        }
        request.setAttribute(RESOLVED_ATTRIBUTE, new Resolved(path, file));
        return super.getHandlerInternal(request);
    }

    /**
     * 造一个 Spring 资源供给器：解析器直接取本类暂存的已解析文件，媒体类型取冻结口径。
     *
     * <p>返回的对象**必须交给 Spring 容器**（它是 {@code InitializingBean}，靠容器调用
     * {@code afterPropertiesSet()} 完成初始化），不要在别处手工 new 出来用。</p>
     *
     * @param assets 静态空间解析器（媒体类型口径来源）
     * @return 资源供给器（Spring 的 {@link ResourceHttpRequestHandler} 子类）
     */
    public static ResourceHttpRequestHandler newResourceHandler(LegacyStaticAssets assets) {
        ResourceHttpRequestHandler handler = new EovaResourceHttpRequestHandler(assets);
        handler.setResourceResolvers(List.of(RESOLVER));
        // 旧实现会带 Last-Modified；Spring 用同一头支持条件请求（新增能力，台账登记）
        handler.setUseLastModified(true);
        return handler;
    }

    /** 已解析的静态命中（规范化路径 + 文件） */
    static final class Resolved {
        final String path;
        final File file;

        Resolved(String path, File file) {
            this.path = path;
            this.file = file;
        }
    }

    /**
     * 资源解析器：路径→文件已由 {@link LegacyStaticAssets#resolve(String)} 判定（含解码与越界守卫），
     * 这里只把结果交给 Spring 的供给链，**不做第二次解析**（否则两套口径会打架）。
     */
    private static final ResourceResolver RESOLVER = new ResourceResolver() {

        @Override
        public Resource resolveResource(HttpServletRequest request, String requestPath,
                List<? extends Resource> locations, ResourceResolverChain chain) {
            Resolved resolved = (Resolved) request.getAttribute(RESOLVED_ATTRIBUTE);
            return resolved == null ? null : new FileSystemResource(resolved.file);
        }

        @Override
        public String resolveUrlPath(String resourcePath, List<? extends Resource> locations,
                ResourceResolverChain chain) {
            // 旧实现不提供"URL 反查"（模板按 /eova/** 原路径直引）⇒ 保持不提供
            return null;
        }
    };

    /**
     * 媒体类型取 {@link LegacyStaticAssets#contentType(String)} 的**冻结口径**。
     *
     * <p>为什么必须覆盖：Spring 默认走 Servlet 容器的 mime 表，Tomcat 10.1 把 {@code .js} 映射成
     * {@code text/javascript}，而旧栈实测契约是 {@code application/javascript}
     * （判据 {@code LegacyStaticAssetContractTest#contentTypeMatchesLegacy} 钉着）。</p>
     */
    private static final class EovaResourceHttpRequestHandler extends ResourceHttpRequestHandler {

        private final LegacyStaticAssets assets;

        EovaResourceHttpRequestHandler(LegacyStaticAssets assets) {
            this.assets = assets;
        }

        @Override
        protected MediaType getMediaType(HttpServletRequest request, Resource resource) {
            Resolved resolved = (Resolved) request.getAttribute(RESOLVED_ATTRIBUTE);
            String path = resolved != null ? resolved.path : resource.getFilename();
            return MediaType.parseMediaType(assets.contentType(path == null ? "" : path));
        }
    }
}
