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

    /** 日志（回退引擎等关键路径必须留痕） */
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(LegacyTemplateRender.class);

    /** contentType 前缀（后缀拼当前编码） */
    protected static final String CONTENT_TYPE = "text/html; charset=";

    /** 模板引擎；由宿主启动时经 {@link #init(Engine)} 注入 */
    protected static Engine engine;

    /**
     * 模板**源文本**读取器（宿主注入；用于"无指令模板直出"快路径）。
     *
     * <p>为 null 时总是走模板引擎（保持既有行为）。</p>
     */
    private static java.util.function.Function<String, String> sourceReader;

    /**
     * 直出计数（"无指令模板直出"快路径被真正走过的次数）。
     *
     * <p>为什么要它：**字节等价判据证明不了机制生效** —— 直出与引擎渲染本来就该字节相同
     * （那正是等价性的内容）。要证明"这条快路径真的在用"，只能数它。故判据断言"请求 `/main` 后计数增加"。</p>
     */
    private static final java.util.concurrent.atomic.AtomicLong DIRECT_RENDER_COUNT =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * 极简页面渲染器（宿主注入）。非 null 时：**有指令的模板也先走它**，
     * 只有它明确"不支持"（`UnsupportedOperationException`）才回退引擎。
     */
    private static cn.eova.compat.template.LegacyPageRenderer pageRenderer;

    /**
     * 引擎兜底计数（"有指令模板仍需帮助引擎"的次数）。
     *
     * <p>判据用它证明**活页已不再需要引擎**：请求活页后该计数必须仍为 0
     * （若某页悄悄用到了极简渲染器不支持的指令，计数会 >0 ⇒ 判据红 ⇒ 必须扩规格或修实现）。</p>
     */
    private static final java.util.concurrent.atomic.AtomicLong ENGINE_FALLBACK_COUNT =
            new java.util.concurrent.atomic.AtomicLong();

    /** 极简渲染器实际渲染次数（判据用它证明"接缝真的切过去了"，而不只是"装了但没用"） */
    private static final java.util.concurrent.atomic.AtomicLong MINI_RENDER_COUNT =
            new java.util.concurrent.atomic.AtomicLong();

    /** 指令 token：`#(`（输出指令）或 `#name(`（块/扩展指令） */
    private static final java.util.regex.Pattern DIRECTIVE =
            java.util.regex.Pattern.compile("#\\(|#[A-Za-z_][A-Za-z0-9_]*\\s*\\(");

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
     * 注入模板源读取器（宿主启动时调用）。
     *
     * @param reader 视图名 → 模板源文本；为 null 时关闭快路径
     */
    public static void initSourceReader(java.util.function.Function<String, String> reader) {
        sourceReader = reader;
    }

    /**
     * 注入极简页面渲染器（宿主启动时调用）。
     *
     * @param renderer 渲染器；null ⇒ 关闭（全部走引擎）
     */
    public static void initPageRenderer(cn.eova.compat.template.LegacyPageRenderer renderer) {
        pageRenderer = renderer;
    }

    /**
     * 取极简渲染器渲染次数（判据用：活页请求后必须增加）。
     *
     * @return 累计次数
     */
    public static long getMiniRenderCount() {
        return MINI_RENDER_COUNT.get();
    }

    /**
     * 取引擎兜底计数（判据用：活页应为 0）。
     *
     * @return 累计兜底次数
     */
    public static long getEngineFallbackCount() {
        return ENGINE_FALLBACK_COUNT.get();
    }

    /**
     * 取直出计数（判据用：证明快路径被真正走过）。
     *
     * @return 累计直出次数
     */
    public static long getDirectRenderCount() {
        return DIRECT_RENDER_COUNT.get();
    }

    /**
     * 模板是否**不含任何指令**。
     *
     * <p>判定只认指令 token（`#(` / `#name(`），不认 HTML/CSS/JS 里到处都是的裸 `#`
     * （`#app`、`#fff` 会被误判）。无指令时 enjoy 的渲染结果就是**逐字节原文**。</p>
     *
     * @param text 模板源文本
     * @return 是否无指令
     */
    static boolean hasNoDirective(String text) {
        return text != null && !DIRECTIVE.matcher(text).find();
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
            // ★ r308（T04 第二段 · 第 8 轮）：**无指令模板直出**（不经模板引擎）。
            //   依据（实测，判据 `LegacyPageRenderSpecTest#mainThemePageIsStatic`）：`/main` 主题页模板
            //   一条指令都没有 ⇒ enjoy 渲染它等于逐字节复制原文 ⇒ 直出**字节相同**
            //   （由页面 sha256 金标 + 真浏览器 iframe 面共同保证）。
            //   意义：把"活页面对 enjoy 的运行时依赖"从 2 个减到 1 个，也是将来自研渲染器的第一段。
            if (sourceReader != null) {
                String text = null;
                try {
                    text = sourceReader.apply(view);
                } catch (RuntimeException ignored) {
                    // 读不到就老实回退引擎（读失败不该把渲染打挂）
                }
                if (hasNoDirective(text)) {
                    DIRECT_RENDER_COUNT.incrementAndGet();
                    os.write(text.getBytes(getEncoding()));
                    os.flush();
                    return;
                }
            }
            // ★ r309 第 1 轮：**有指令的模板也先走极简渲染器**（与 Enjoy 逐字节等价，差分判据钉住）。
            //   只有它明确报"不支持"才回退引擎（并计数 + 响亮告警）——
            //   这样"退役 enjoy"推进的同时**不冒行为风险**，而计数 >0 就说明规格面变了。
            if (pageRenderer != null) {
                try {
                    MINI_RENDER_COUNT.incrementAndGet();
                    os.write(pageRenderer.render(view, data).getBytes(getEncoding()));
                    os.flush();
                    return;
                } catch (UnsupportedOperationException e) {
                    ENGINE_FALLBACK_COUNT.incrementAndGet();
                    log.warn("Eova Web 层：极简渲染器不支持该模板，回退 enjoy 引擎 —— {}", e.getMessage());
                }
            }
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
