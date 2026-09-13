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
 *   <li><s>{@code init(Engine)}：入参为 null 时抛 {@code IllegalArgumentException("engine can not be null")}</s>
 *       —— ★ r310 按口径授权**移除**：新栈不再有 enjoy 引擎，模板渲染改由
 *       {@link cn.eova.compat.template.LegacyPageRenderer}（与 enjoy 逐字节等价）承担。</li>
 * </ol>
 *
 * <p><b>视图路径不在本类处理：</b>旧 {@code RenderFactory.getTemplateRender(view)}
 * 与 {@code MainRenderFactory.getRender(view)} 都<b>只是</b>
 * {@code new TemplateRender(view)}（逐字节码确认，无后缀分派、无扩展名拼接）。
 * 视图前缀由 {@link LegacyRender#setContext(jakarta.servlet.http.HttpServletRequest,
 * jakarta.servlet.http.HttpServletResponse, String)} 负责 —— 与本接缝一致。</p>
 *
 * <p><b>底座（r310 起）：</b>模板渲染**不再经过 enjoy**：无指令模板走"源文本直出"快路径，
 * 有指令模板走 {@link cn.eova.compat.template.LegacyPageRenderer}；
 * 遇到极简渲染器不支持的指令 ⇒ **响亮抛错**（不再回退引擎）—— 这是"引擎兜底实测 0 次"的收尾动作。</p>
 */
public class LegacyTemplateRender extends LegacyRender {

    /** 日志（回退引擎等关键路径必须留痕） */
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(LegacyTemplateRender.class);

    /** contentType 前缀（后缀拼当前编码） */
    protected static final String CONTENT_TYPE = "text/html; charset=";

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
     * 极简页面渲染器（宿主注入）。**有指令的模板由它渲染**（与 enjoy 逐字节等价，差分判据钉住）。
     */
    private static cn.eova.compat.template.LegacyPageRenderer pageRenderer;

    /**
     * 「不支持指令」计数（r310：原来叫引擎兜底计数）。
     *
     * <p>为什么保留这个计数（而不是删掉）：**引擎已经没有了**，所以"某活页用到了不支持的指令"
     * 现在表现为**渲染抛错（500）**；计数是**唯一能在实跑里证明"没有活页踩到这条线"**的东西
     * （判据在请求活页后断言它为 0；扫描第 4d 步看"当前后端"的日志里有没有这条 ERROR）。</p>
     */
    private static final java.util.concurrent.atomic.AtomicLong UNSUPPORTED_TEMPLATE_COUNT =
            new java.util.concurrent.atomic.AtomicLong();

    /** 极简渲染器实际渲染次数（判据用它证明"接缝真的切过去了"，而不只是"装了但没用"） */
    private static final java.util.concurrent.atomic.AtomicLong MINI_RENDER_COUNT =
            new java.util.concurrent.atomic.AtomicLong();

    /** 指令 token：`#(`（输出指令）或 `#name(`（块/扩展指令） */
    private static final java.util.regex.Pattern DIRECTIVE =
            java.util.regex.Pattern.compile("#\\(|#[A-Za-z_][A-Za-z0-9_]*\\s*\\(");

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
     * 取「不支持指令」计数（判据用：活页应为 0）。
     *
     * @return 累计次数
     */
    public static long getUnsupportedTemplateCount() {
        return UNSUPPORTED_TEMPLATE_COUNT.get();
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
            // ★ r310：**引擎已按口径授权摘除**（原兜底实测 0 次 + 可达模板面已逐面枚举）。
            //   不支持 ⇒ 计数 + **响亮抛错**（绝不再"悄悄渲染成空"）：500 是显式的，比静默空白安全。
            if (pageRenderer == null) {
                throw new IllegalStateException("模板渲染器未装配（宿主未调用 initPageRenderer）：" + view);
            }
            try {
                MINI_RENDER_COUNT.incrementAndGet();
                os.write(pageRenderer.render(view, data).getBytes(getEncoding()));
                os.flush();
            } catch (UnsupportedOperationException e) {
                UNSUPPORTED_TEMPLATE_COUNT.incrementAndGet();
                log.error("Eova Web 层：极简渲染器不支持该模板的指令，渲染失败 —— view={}，原因={}",
                        view, e.getMessage());
                throw new IllegalStateException("极简渲染器不支持该模板的指令：" + view, e);
            }
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
