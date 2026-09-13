/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * **T04 第二段收官：不依赖 Enjoy 的「极简页面渲染器」**（只覆盖活页用到的指令子集）。
 *
 * <p><b>规格从哪来</b>：不是猜的，而是从**仍在渲染的活页**统计出来的（判据 `LegacyPageRenderSpecTest` 冻结）：
 * 活页（`/main` 与 Excel 导入页）的 `include` 闭包只用
 * {@code #(expr)} · {@code #if(cond)} · {@code #else} · {@code #end} · {@code #include("path")} ·
 * {@code #render(expr)}，表达式里只调用共享方法 {@code conf('…')} 与 {@code getUIConf()}。</p>
 *
 * <p><b>与 Enjoy 对齐的语义（都是实跑钉住的，不是照文档写的）</b>：</p>
 * <ul>
 *   <li>空白：`#if(cond)` 后吞**全部**空白（含换行）；`#end` 后只吞空格/制表符（换行保留）；
 *       `#end` **前**裁空格，`#else` 前**不裁**；</li>
 *   <li>**未取分支不参与求值**（惰性）—— 但**必须照样扫描**：Enjoy 不认 JS 注释，
 *       模板里的 `// #if(object)` / `// #end` **照样参与配对**（`_view/_page/form.html` 就靠这个）；</li>
 *   <li>不支持的指令一律**响亮抛错**（绝不静默输出错内容）；未取分支里的未知指令同样抛错
 *       （Enjoy 是"整篇编译"，未知指令在未取分支里也会编译失败）。</li>
 * </ul>
 *
 * <p><b>等价性</b>：由差分判据 `LegacyPageRendererGoldenTest` 对**真实活页模板**与 Enjoy 逐字节比对。</p>
 */
public final class LegacyPageRenderer {

    /** 模板源解析：视图名（形如 {@code /eova/_view/_page/form.html}）→ 模板文本；不存在返回 null */
    public interface SourceResolver {
        /**
         * 读取模板源。
         *
         * @param viewPath 视图名
         * @return 模板文本；不存在返回 null
         */
        String read(String viewPath);
    }

    /**
     * **Enjoy 已知指令名**（实跑取证：`#name` 只有落在这些名字上才算指令，其余（`#f1f1f1`、`#app`、
     * `#foo`、`#call f()`）Enjoy 一律当**普通文本**）。
     *
     * <p>本渲染器只支持其中 5 个（`if`/`else`/`end`/`include`/`render`）；撞上其余已知指令**响亮抛错**，
     * 既不静默渲染错内容，也不会把 `#fff` 这种颜色值误当指令。</p>
     */
    private static final java.util.Set<String> ENJOY_DIRECTIVES = java.util.Set.of(
            "if", "else", "elseif", "end", "for", "set", "define", "include", "render",
            "number", "date", "escape", "string", "json");

    /** 本渲染器支持的指令 */
    private static final java.util.Set<String> SUPPORTED = java.util.Set.of(
            "if", "else", "end", "include", "render");

    private final SourceResolver resolver;

    /** 共享方法注册表（方法名 → 承载对象） */
    private final Map<String, Object> shared;

    /**
     * 构造。
     *
     * @param resolver 模板源解析器；null ⇒ 不支持 include/render
     * @param shared   共享方法注册表；null ⇒ 无共享方法
     */
    public LegacyPageRenderer(SourceResolver resolver, Map<String, Object> shared) {
        this.resolver = resolver;
        this.shared = shared == null ? new LinkedHashMap<>() : shared;
    }

    /**
     * 渲染模板文件。
     *
     * @param viewPath 视图名
     * @param data     数据根
     * @return 渲染结果
     */
    public String render(String viewPath, Object data) {
        if (resolver == null) {
            throw new IllegalStateException("未提供模板源解析器，无法渲染：" + viewPath);
        }
        String text = resolver.read(viewPath);
        if (text == null) {
            // 与旧栈一致：模板缺失时**响亮失败**（旧栈是 500）
            throw new IllegalStateException("模板不存在：" + viewPath);
        }
        return renderText(text, data);
    }

    /**
     * 渲染模板文本。
     *
     * @param text 模板文本
     * @param data 数据根
     * @return 渲染结果
     */
    public String renderText(String text, Object data) {
        StringBuilder out = new StringBuilder();
        Cursor c = new Cursor(text, data);
        String term = c.scan(out, true);
        if (term != null) {
            throw new IllegalStateException("模板里出现了多余的 #" + term + "（没有匹配的 #if）：" + text);
        }
        return out.toString();
    }

    /**
     * 游标：**同一实例递归**，`renderMode=false` 时只做结构扫描（不求值、不输出）。
     *
     * <p>为什么"未取分支也要扫描"：Enjoy 不认 JS 注释，模板里的 `// #if(object)` / `// #end`
     * 照样参与配对（`_view/_page/form.html` 正是这么写的）⇒ 跳过一个分支必须按同样口径数括号。</p>
     */
    private final class Cursor {
        private final String src;
        private final Object data;
        private int pos;

        /**
         * 本行（自上一个换行以来）是否**只出现过指令与空白**（没有普通文本/输出）。
         *
         * <p>★ 实跑定的规则（变体矩阵见接手文档）：Enjoy 对"**独占一行的块指令**"会吞掉该行行尾的换行 ——
         * `A\n#if(true)x#end\nB` ⇒ `A\nx\nB`（行内有文本 ⇒ 不吞），
         * 而 `A\n#if(true)\nx\n#end\nB` ⇒ `A\nx\nB`（`#end` 独占一行 ⇒ 吞）。
         * `#include` **不适用**该规则（实测 `A\n#include(...)\nB` 的换行保留）。</p>
         */
        Cursor(String src, Object data) {
            this.src = src;
            this.data = data;
        }

        /**
         * 扫描到 `#end` / `#else` 为止。
         *
         * @param out        输出缓冲（仅 renderMode 为真时写入）
         * @param renderMode 是否渲染（false ⇒ 只扫描结构）
         * @return 遇到的终止符（"end"/"else"）；到串尾返回 null
         */
        String scan(StringBuilder out, boolean renderMode) {
            while (pos < src.length()) {
                int hash = src.indexOf('#', pos);
                if (hash < 0) {
                    if (renderMode) {
                        out.append(src, pos, src.length());
                    }
                    pos = src.length();
                    return null;
                }
                if (renderMode) {
                    out.append(src, pos, hash);
                }
                pos = hash;
                // ★ 注释指令（差分判据逼出来的）：`##` 是**行注释**（连同换行一起吞掉）、
                //   `#-- … --#` 是块注释。`_view/_page/form.html` 首行就是 `### 表单页`
                //   —— 首版当普通文本 ⇒ 直接多输出一行。
                if (src.startsWith("##", pos)) {
                    int nl = src.indexOf('\n', pos);
                    pos = nl < 0 ? src.length() : nl + 1;
                    continue;
                }
                if (src.startsWith("#--", pos)) {
                    int end = src.indexOf("--#", pos + 3);
                    if (end < 0) {
                        throw new IllegalStateException("块注释 `#--` 未闭合：" + src.substring(pos, Math.min(pos + 40, src.length())));
                    }
                    pos = end + 3;
                    continue;
                }
                if (!src.startsWith("#(", pos)) {
                    // ★ 名字感知分派（差分判据逼出来的）：只有**已知指令名**才算指令；
                    //   否则 `#f1f1f1`（CSS 颜色）/`#app`（JS 选择器）会被误判成指令而抛错。
                    String name = peekName();
                    if (name.isEmpty()) {
                        if (renderMode) {
                            out.append('#');
                        }
                        pos++;
                        continue;
                    }
                    if (!ENJOY_DIRECTIVES.contains(name)) {
                        // 未知名字 ⇒ 与 Enjoy 一致：**普通文本**
                        if (renderMode) {
                            out.append('#');
                        }
                        pos++;
                        continue;
                    }
                    if (!SUPPORTED.contains(name)) {
                        throw new UnsupportedOperationException(
                                "极简渲染器只支持 #if/#else/#end/#include/#render，不支持 Enjoy 指令：#" + name
                                        + "（活页模板里不该出现它；出现即意味着规格面变化，必须显式更新判据）");
                    }
                }
                if (src.startsWith("#end", pos)) {
                    boolean onlyDirectives = isDirectiveOnlyLine(pos);
                    trimTrailingSpaces(out);   // ★ 实测：`#end` 前**无条件**裁空格
                    pos += 4;
                    skipSpacesTabs();
                    if (onlyDirectives && pos < src.length() && src.charAt(pos) == '\n') {
                        pos++;                 // ★ 实测：独占一行时才吞行尾换行
                    }
                    return "end";
                }
                if (src.startsWith("#else", pos) && !"elseif".equals(peekName())) {
                    // ★ 实测口径（两种情形都量过）：
                    //   · 行内有普通文本（`#if(c)a #else b#end`）⇒ `#else` 前的空格**保留**；
                    //   · `#else` **独占一行**（缩进+指令，见 form.html/app.html）⇒ 该行缩进与行尾换行**都被吞掉**。
                    boolean onlyDirectives = isDirectiveOnlyLine(pos);
                    if (onlyDirectives) {
                        trimTrailingSpaces(out);
                    }
                    pos += 5;
                    skipSpacesTabs();
                    if (onlyDirectives && pos < src.length() && src.charAt(pos) == '\n') {
                        pos++;                 // ★ 实测：独占一行时吞行尾换行
                    }
                    return "else";
                }
                if (src.startsWith("#if", pos) && pos + 3 < src.length() && src.charAt(pos + 3) == '(') {
                    boolean onlyDirectives = isDirectiveOnlyLine(pos);
                    if (onlyDirectives) {
                        trimTrailingSpaces(out);   // 独占一行 ⇒ 吞掉行首缩进（实测 e1/e4/e5）
                    }
                    pos += 3;
                    String cond = readParenRaw();
                    skipSpacesTabs();
                    if (pos < src.length() && src.charAt(pos) == '\n') {
                        pos++;                      // ★ 实测：只吞到行尾（**不**吞下一行的缩进，见 e1）
                    }
                    boolean taken = renderMode && LegacyExprEvaluator.truthy(
                            LegacyExprEvaluator.eval(cond, data, shared));
                    StringBuilder body = new StringBuilder();
                    String term = scan(body, taken);
                    if (term == null) {
                        throw new IllegalStateException("`#if` 缺少 `#end`：" + src);
                    }
                    StringBuilder other = new StringBuilder();
                    if ("else".equals(term)) {
                        String term2 = scan(other, renderMode && !taken);
                        if (!"end".equals(term2)) {
                            throw new IllegalStateException("`#if` 缺少 `#end`：" + src);
                        }
                    }
                    if (renderMode) {
                        out.append(taken ? body : other);
                    }
                    continue;
                }
                if (src.startsWith("#(", pos)) {
                    pos += 1;
                    String expr = readParenRaw();
                    if (renderMode) {
                        out.append(LegacyExprEvaluator.format(
                                LegacyExprEvaluator.eval(expr, data, shared)));
                    }
                    continue;
                }
                if (src.startsWith("#include", pos) && isDirectiveHead("#include")) {
                    pos += "#include".length();
                    String path = unquote(readParenRaw().trim());
                    if (renderMode) {
                        out.append(LegacyPageRenderer.this.render(path, data));
                    }
                    continue;
                }
                if (src.startsWith("#render", pos) && isDirectiveHead("#render")) {
                    pos += "#render".length();
                    String expr = readParenRaw();
                    if (renderMode) {
                        Object v = LegacyExprEvaluator.eval(expr, data, shared);
                        out.append(LegacyPageRenderer.this.render(LegacyExprEvaluator.format(v), data));
                    }
                    continue;
                }
                int nameEnd = pos + 1;
                while (nameEnd < src.length() && (Character.isLetterOrDigit(src.charAt(nameEnd))
                        || src.charAt(nameEnd) == '_')) {
                    nameEnd++;
                }
                throw new UnsupportedOperationException(
                        "极简渲染器只覆盖活页用到的指令子集"
                                + "（#(expr)/#if/#else/#end/#include/#render），不支持："
                                + src.substring(pos, Math.min(nameEnd, src.length())));
            }
            return null;
        }

        /**
         * 该块指令是否坐在**独占一行**的行上（词法判定，paren 感知）。
         *
         * <p>实测口径（变体矩阵 e1–e5）：行内除空白与**块指令**外什么都没有 ⇒ 该行的**行首缩进**
         * 与该行**行尾换行**都被吞（`#else`/`#end`）；一旦行内出现普通文本或**输出指令** `#(`，
         * 则整行原样保留。`#include` 不适用该规则。</p>
         *
         * <p>⚠️ 必须**词法**判定，不能按"是否输出了内容"判定：`#if(false)x#end` 里的 `x` 虽不输出，
         * 但它在模板文本里就是普通文本 ⇒ 该行不算独占（实测 `/e3.html` ⇒ `A\n  X\nB`，缩进保留）。</p>
         *
         * @param at 指令 token 的起始下标
         * @return 是否独占一行
         */
        private boolean isDirectiveOnlyLine(int at) {
            int lineStart = src.lastIndexOf('\n', at - 1) + 1;
            int lineEnd = src.indexOf('\n', at);
            if (lineEnd < 0) {
                lineEnd = src.length();
            }
            for (int i = lineStart; i < lineEnd; ) {
                char c = src.charAt(i);
                if (c != '#') {
                    if (!Character.isWhitespace(c)) {
                        return false;
                    }
                    i++;
                    continue;
                }
                if (i + 1 >= lineEnd) {
                    return false;
                }
                char next = src.charAt(i + 1);
                if (next == '(') {
                    return false;                 // 输出指令 ⇒ 不算"只有块指令"
                }
                int j = i + 1;
                while (j < lineEnd && Character.isJavaIdentifierPart(src.charAt(j))) {
                    j++;
                }
                int k = j;
                while (k < lineEnd && Character.isWhitespace(src.charAt(k))) {
                    k++;
                }
                if (k < lineEnd && src.charAt(k) == '(') {
                    int depth = 0;
                    while (k < lineEnd) {
                        char d = src.charAt(k);
                        if (d == '(') {
                            depth++;
                        } else if (d == ')') {
                            depth--;
                            if (depth == 0) {
                                k++;
                                break;
                            }
                        }
                        k++;
                    }
                }
                i = k;
            }
            return true;
        }

        /** 取 `#` 之后的指令名（空串 ⇒ `#` 后不是标识符） */
        private String peekName() {
            int i = pos + 1;
            if (i >= src.length() || !Character.isJavaIdentifierStart(src.charAt(i))) {
                return "";
            }
            int j = i;
            while (j < src.length() && Character.isJavaIdentifierPart(src.charAt(j))) {
                j++;
            }
            return src.substring(i, j);
        }

        /** 指令头判定：`#includeX` 这类不算（后须紧跟空白或 `(`） */
        private boolean isDirectiveHead(String name) {
            int i = pos + name.length();
            while (i < src.length() && Character.isWhitespace(src.charAt(i))) {
                i++;
            }
            return i < src.length() && src.charAt(i) == '(';
        }

        /** 读一对圆括号内的原文（先跳过指令名与 `(` 间的空白） */
        private String readParenRaw() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
            if (pos >= src.length() || src.charAt(pos) != '(') {
                throw new IllegalStateException("期望 '(' ：" + src.substring(Math.max(0, pos - 8)));
            }
            int depth = 0;
            int start = pos + 1;
            for (int i = pos; i < src.length(); i++) {
                char ch = src.charAt(i);
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                    if (depth == 0) {
                        String inner = src.substring(start, i);
                        pos = i + 1;
                        return inner;
                    }
                }
            }
            throw new IllegalStateException("括号不闭合：" + src);
        }

        /** 裁掉缓冲末尾的空格/制表符（不含换行） */
        private void trimTrailingSpaces(StringBuilder sb) {
            int i = sb.length();
            while (i > 0 && (sb.charAt(i - 1) == ' ' || sb.charAt(i - 1) == '\t')) {
                i--;
            }
            sb.setLength(i);
        }

        /** 吞掉全部空白（含换行） */
        private void skipAllWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        /** 吞掉空格与制表符（换行保留） */
        private void skipSpacesTabs() {
            while (pos < src.length() && (src.charAt(pos) == ' ' || src.charAt(pos) == '\t')) {
                pos++;
            }
        }
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && (s.charAt(0) == '"' || s.charAt(0) == '\'')
                && s.charAt(s.length() - 1) == s.charAt(0)) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * 由视图根 + 共享方法对象构造渲染器（宿主用）。
     *
     * @param viewRoot      视图根目录
     * @param sharedObjects 共享方法对象列表（其 public 方法按名注册）
     * @return 渲染器
     */
    public static LegacyPageRenderer of(java.io.File viewRoot, List<Object> sharedObjects) {
        Map<String, Object> methods = new LinkedHashMap<>();
        if (sharedObjects != null) {
            for (Object o : sharedObjects) {
                if (o == null) {
                    continue;
                }
                for (java.lang.reflect.Method m : o.getClass().getMethods()) {
                    if (!m.getDeclaringClass().equals(Object.class)) {
                        methods.putIfAbsent(m.getName(), o);
                    }
                }
            }
        }
        SourceResolver resolver = viewPath -> {
            String rel = viewPath.startsWith("/") ? viewPath.substring(1) : viewPath;
            java.io.File f = new java.io.File(viewRoot, rel);
            if (!f.isFile()) {
                return null;
            }
            try {
                return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                return null;
            }
        };
        return new LegacyPageRenderer(resolver, methods);
    }
}
