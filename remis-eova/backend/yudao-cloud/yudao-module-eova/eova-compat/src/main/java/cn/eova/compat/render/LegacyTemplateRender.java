/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import com.jfinal.template.Engine;
import jakarta.servlet.ServletOutputStream;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.render.TemplateRender} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.render.TemplateRender}（jfinal 5.2.6）。
 * EOVA 的 {@code render(String)} 共 <b>41 处</b>调用，是本族里第二多的
 * （仅次于 {@code renderJson} 的 103 处）。</p>
 *
 * <p><b>语义逐条取自旧字节码：</b>
 * <ol>
 *   <li>{@link #render()}：先 {@code setContentType(getContentType())}；
 *       然后把请求属性**全部**灌入一个 {@code HashMap} 作为模板数据
 *       （遍历 {@code request.getAttributeNames()}，逐项
 *       {@code data.put(name, request.getAttribute(name))}）；
 *       再取 {@code response.getOutputStream()}（<b>流</b>，不是 Writer），
 *       {@code engine.getTemplate(view).render(data, os)} 并 {@code flush()}。
 *       异常包成 {@link LegacyRenderException}。</li>
 *   <li>{@link #getContentType()} 返回<b>静态</b> {@code contentType}
 *       （{@code "text/html; charset=" + 当前编码}）。</li>
 *   <li>{@link #toString()} 返回 <b>{@code view} 本身</b> —— <b>不是</b>
 *       {@code "TemplateRender: " + view}（若凭直觉会加前缀，那就错了）。</li>
 *   <li>{@link #init(Engine)}：入参为 null 时抛
 *       {@code IllegalArgumentException("engine can not be null")}（消息逐字）。</li>
 * </ol>
 *
 * <p><b>视图路径不在本类处理：</b>旧 {@code RenderFactory.getTemplateRender(view)}
 * 与 {@code MainRenderFactory.getRender(view)} 都<b>只是</b>
 * {@code new TemplateRender(view)}（逐字节码确认，无后缀分派、无扩展名拼接）。
 * 视图前缀由 {@link LegacyRender#setContext(jakarta.servlet.http.HttpServletRequest,
 * jakarta.servlet.http.HttpServletResponse, String)} 负责 —— 与本接缝一致。</p>
 *
 * <p><b>底座：</b>{@code engine} 的类型 {@code com.jfinal.template.Engine} 由
 * <b>enjoy 5.3.0</b> 提供（在 classpath 上），与旧 jfinal 5.2.6 内嵌的引擎同源同名，
 * 故此处直接复用，无需自造引擎。</p>
 */
public class LegacyTemplateRender extends LegacyRender {

    /** contentType 前缀（后缀拼当前编码） */
    protected static final String CONTENT_TYPE = "text/html; charset=";

    /** 模板引擎；由宿主启动时经 {@link #init(Engine)} 注入 */
    protected static Engine engine;

    /**
     * 注入模板引擎（宿主启动时调用）。
     *
     * @param engine 引擎；不得为 null
     */
    public static void init(Engine engine) {
        if (engine == null) {
            throw new IllegalArgumentException("engine can not be null");
        }
        LegacyTemplateRender.engine = engine;
    }

    /**
     * 取当前模板引擎。
     *
     * @return 引擎
     */
    public static Engine getEngine() {
        return engine;
    }

    /**
     * 构造。
     *
     * @param view 视图名
     */
    public LegacyTemplateRender(String view) {
        this.view = view;
    }

    /**
     * 取内容类型（静态常量）。
     *
     * @return contentType
     */
    public String getContentType() {
        return CONTENT_TYPE + getEncoding();
    }

    /**
     * 渲染模板：请求属性全部作为模板数据，结果写到响应输出流。
     */
    @Override
    public void render() {
        response.setContentType(getContentType());

        Map<Object, Object> data = new HashMap<>();
        Enumeration<String> attrs = request.getAttributeNames();
        while (attrs.hasMoreElements()) {
            String name = attrs.nextElement();
            data.put(name, request.getAttribute(name));
        }

        ServletOutputStream os = null;
        try {
            os = response.getOutputStream();
            engine.getTemplate(view).render(data, os);
            os.flush();
        } catch (Exception e) {
            throw new LegacyRenderException(e);
        }
    }

    /**
     * 与旧实现一致：返回 {@code view} 本身。
     *
     * @return view
     */
    @Override
    public String toString() {
        return view;
    }

}
