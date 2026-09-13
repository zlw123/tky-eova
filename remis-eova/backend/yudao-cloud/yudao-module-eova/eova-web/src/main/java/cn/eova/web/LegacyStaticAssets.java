/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 旧静态空间 {@code /eova/**} 的宿主供给（切片 S3，第 247 轮）。
 *
 * <p><b>旧栈语义（实测，不是推断）</b>：旧 demo 上
 * {@code /eova/lib/eova/eovaui.css}、{@code /eova/ui/css/common.css}、
 * {@code /eova/_view/template/eova.template.js} 全部 **200**，而 {@code /ui/css/common.css} 是 **404**
 * ⇒ 静态空间之一是 {@code /eova/**}，其来源是 classpath 的 {@code webapp/eova/**}
 * （旧 {@code undertow.resourcePath=classpath:webapp}）。</p>
 *
 * <p>★★ <b>第 304 轮纠正一处错记</b>：本文档原写"静态空间**只有** {@code /eova/**}（{@code /_eova/**} 404）"，
 * 那是**错**的 —— 复测旧栈：{@code /_eova/assets/eova.ui.ext.js} **200**、
 * {@code /_eova/theme/eova.theme.js} **200**（只有 {@code /_eova/include.html} 是 404，
 * 它是模板片段、从不按 URL 取）。**这条错记有实际后果**：SPA 因此没有供给 {@code /_eova/**}，
 * 而 `eova.ui.ext.js` 正是注册**表格单元格渲染器** `eova-table-cell` 的资产 ⇒
 * 新栈**每一个列表页数据都在、格子却全空**（第 304 轮用户实测反馈；真浏览器实测
 * `EovaUI.me.render.get('eova-table-cell')` 旧栈"有"、新栈"无"）。
 * ⇒ 静态空间是**两个**：{@code /eova/**} 与 {@code /_eova/**}，各自映射到 web 根下的同名目录。</p>
 *
 * <p><b>为什么前端要用旧原路径</b>：{@code remis-eova-ui} 的 {@code index.html} 与
 * {@code compat/legacy-runtime.ts} 按契约纪律直接引用 {@code /eova/lib/**}（vendor 级运行时
 * 与产物解耦），dev 期由 Vite 代理到本层，生产期由本层供给 ⇒ 这条链路必须由后端补齐。</p>
 *
 * <p><b>命中口径</b>：路径在静态空间内且**文件真实存在**才直出；不存在则返回 false 交给动作路由
 * （旧栈就是"资源处理器 miss 后交给 jfinal 动作"），故动作 URL 不受影响。</p>
 *
 * <p><b>安全</b>：解析后的**规范路径**必须仍在静态根之内，否则拒绝（目录穿越不得读出根外文件）。</p>
 */
public class LegacyStaticAssets {

    private static final Logger log = LoggerFactory.getLogger(LegacyStaticAssets.class);

    /** 静态空间前缀之一：框架资源（旧栈实测 200） */
    public static final String PREFIX = "/eova/";

    /**
     * 静态空间前缀之二：**工程级扩展资源**（旧栈实测 200）。
     *
     * <p>{@code /_eova/**} 是"项目自定义"槽位：旧 demo 里 {@code _eova/assets/eova.ui.ext.js} 注册
     * 表格单元格渲染器（`eova-table-cell`/`eova-table-island`）、{@code _eova/theme/eova.theme.js} 切主题。
     * 二者都是**核心页面显示数据所必需**（详见类文档第 304 轮纠正）。</p>
     */
    public static final String PRIVATE_PREFIX = "/_eova/";

    /**
     * 静态空间之三：**SPA 打包产物**（{@code /eova-assets/**}，第 305 轮 U1 新增）。
     *
     * <p>前端工程把 {@code build.assetsDir} 设为 {@code eova-assets}（不用 Vite 默认的
     * {@code assets}）—— 这样后端只需认领一个**带命名空间的前缀**，不会与将来任何后端路由
     * （{@code /assets/...}）撞名。壳 {@code index.html} 由 {@code LegacySpaShellRender} 供给，
     * 它引用的就是这些资源。</p>
     */
    public static final String SPA_ASSETS_PREFIX = "/eova-assets/";

    /**
     * 静态空间之四：**demo 工程静态资产**（{@code /demo/**}，第 305 轮 U1 新增）。
     *
     * <p><b>为什么它必须被供给</b>：种子数据里的自定义按钮把 {@code eova_button.ui} 指向
     * {@code /demo/test/btn.js}（demo webapp 的静态文件）—— 页面加载按钮脚本时按该 URL 取。
     * 实测旧栈：{@code GET /demo/test/btn.js} → **200 application/javascript**；
     * 而新栈此前落进"动作/兜底"层 ⇒ 返回 HTML ⇒ 浏览器执行 HTML 当脚本 ⇒
     * {@code SyntaxError: Unexpected token '<'}`（本轮 S5 ⑨ 被它打红）。</p>
     *
     * <p>与 {@code /_eova/**} 同一判例（r304）：这是**被核心页面按 URL 引用的静态资产**，
     * 不是"迁移 demo 应用"（口径④不迁的是 demo 的**页面/路由**）。</p>
     *
     * <p>⚠️ 顺带纠正一条错记：既有笔记写"旧栈此处同样是 404" —— <b>实测是 200</b>
     * （{@code application/javascript}）。错记会让人把"资产没供给"当成"两端一致"而放过。</p>
     */
    public static final String DEMO_PREFIX = "/demo/";

    /**
     * 静态空间之五：**仍由后端渲染的旧页的页面级脚本**（{@code /_view/**}，r307 U3 新增）。
     *
     * <p><b>为什么必须供给</b>：`/main` 是 demo 的 EovaUI 主题演示页，而 **SPA 首页把它当 iframe 内容**
     * （`Home.vue` 的 `<iframe :src="m.link">`，初始页签 link 就是 `/main`；旧首页
     * `eova/_view/index/index.js:21` 同款）⇒ 它必须由**后端渲染真页面**。
     * 实测旧栈 `/_view/theme/index.js` **200**、新栈 **404** ⇒ 页面即便渲染出来，其脚本也取不到。</p>
     *
     * <p>⚠️ 这条缺口是"**页面 200 但子资源 404**"型缺陷：任何只比页面状态码的判据都看不见它
     * （见 `docs/.local/spikes` 的子资源等价探针）。</p>
     */
    public static final String VIEW_PREFIX = "/_view/";

    /**
     * 静态空间之六：**Excel 导入页的脚本**（{@code /excel/**}，r307 U3 新增）。
     *
     * <p>{@code /excel/imports/<objectCode>} 是 U2 清点后**唯一仍由后端渲染的活页面**，它引用
     * {@code /excel/import/app.js} 与 {@code /excel/import/btn.js}（旧栈均 200、新栈此前 **404**）。
     * {@code /excel} 同时是动作路由前缀（`ExcelController`）—— 静态层只在**文件真实存在**时直出
     * ⇒ {@code /excel/imports/<code>} 这类动作 URL 不受影响（判据见 `LegacyStaticAssetsTest`）。</p>
     */
    public static final String EXCEL_PREFIX = "/excel/";

    /**
     * 静态空间之七：**页面引用的可下载静态文件**（{@code /_static/**}，r307 U3 新增）。
     *
     * <p>实测来源：Excel 导入页里的"下载导入模板"链接指向
     * {@code /_static/excel/酒店导入模版.xlsx}（旧栈 200）。</p>
     *
     * <p>★ 该文件是**非 ASCII 文件名** ⇒ 静态层必须对请求路径做 **URL 解码**（浏览器发的是
     * 百分号编码的 UTF-8），否则即便文件在也永远命中不了 —— 这类"文件存在但取不到"的缺口
     * 与"文件不存在"表现相同（都 404），只有带编码的实测能分辨。</p>
     */
    public static final String STATIC_PREFIX = "/_static/";

    /** 扩展名 → Content-Type（旧栈实测：css=text/css、js=application/javascript） */
    private static final Map<String, String> TYPES = new HashMap<>();

    static {
        TYPES.put("css", "text/css");
        TYPES.put("js", "application/javascript");
        TYPES.put("mjs", "application/javascript");
        TYPES.put("json", "application/json");
        TYPES.put("html", "text/html;charset=UTF-8");
        TYPES.put("htm", "text/html;charset=UTF-8");
        TYPES.put("txt", "text/plain;charset=UTF-8");
        TYPES.put("map", "application/json");
        TYPES.put("png", "image/png");
        TYPES.put("jpg", "image/jpeg");
        TYPES.put("jpeg", "image/jpeg");
        TYPES.put("gif", "image/gif");
        TYPES.put("svg", "image/svg+xml");
        TYPES.put("ico", "image/x-icon");
        TYPES.put("woff", "font/woff");
        TYPES.put("woff2", "font/woff2");
        TYPES.put("ttf", "font/ttf");
        TYPES.put("eot", "application/vnd.ms-fontobject");
    }

    /** 静态根（{@code <web 根>/eova}）；不可用时为 null */
    private final File root;

    /** 私有静态根（{@code <web 根>/_eova}）；不可用时为 null */
    private final File privateRoot;

    /** SPA 产物静态根（{@code <dist>/eova-assets}）；不可用时为 null */
    private final File spaAssetsRoot;

    /** demo 静态资产根（{@code <web 根>/demo}）；不可用时为 null */
    private final File demoRoot;

    /** 旧页页面脚本根（{@code <web 根>/_view}）；不可用时为 null */
    private final File viewRoot;

    /** Excel 导入页脚本根（{@code <web 根>/excel}）；不可用时为 null */
    private final File excelRoot;

    /** 可下载静态文件根（{@code <web 根>/_static}）；不可用时为 null */
    private final File staticRoot;

    /**
     * 静态空间表：**前缀 → 根目录**（顺序即匹配顺序；各前缀互不包含，故顺序无歧义）。
     *
     * <p>★ 用表而不是 if/else 链的原因（r247 M6 的教训）：`resolve` 必须**自己**判定前缀，
     * 一旦"判定用的前缀集合"与"剥离时用的前缀"不一致，就会产生**路径别名**
     * （例如把 {@code /xxxxx/lib/x.css} 当成 {@code lib/x.css} 命中静态文件）。表让两者天然同一份事实。</p>
     */
    private final Map<String, File> spaces = new LinkedHashMap<>();

    /**
     * 构造。
     *
     * @param webRoot web 根目录（含 {@code eova/} 的那一层）；为 null 时本组件不供给任何资源
     */
    public LegacyStaticAssets(File webRoot) {
        this(webRoot, null);
    }

    /**
     * 构造（含 SPA 产物空间）。
     *
     * @param webRoot       web 根目录（含 {@code eova/} 与 {@code _eova/} 的那一层）
     * @param spaDistRoot   前端打包产物根（含 index.html）；为 null 时 {@code /eova-assets/**} 不供给
     */
    public LegacyStaticAssets(File webRoot, File spaDistRoot) {
        this.root = webRoot == null ? null : new File(webRoot, "eova");
        this.privateRoot = webRoot == null ? null : new File(webRoot, "_eova");
        this.spaAssetsRoot = spaDistRoot == null ? null : new File(spaDistRoot, "eova-assets");
        this.demoRoot = webRoot == null ? null : new File(webRoot, "demo");
        this.viewRoot = webRoot == null ? null : new File(webRoot, "_view");
        this.excelRoot = webRoot == null ? null : new File(webRoot, "excel");
        this.staticRoot = webRoot == null ? null : new File(webRoot, "_static");
        spaces.put(PREFIX, root);
        spaces.put(PRIVATE_PREFIX, privateRoot);
        spaces.put(SPA_ASSETS_PREFIX, spaAssetsRoot);
        spaces.put(DEMO_PREFIX, demoRoot);
        spaces.put(VIEW_PREFIX, viewRoot);
        spaces.put(EXCEL_PREFIX, excelRoot);
        spaces.put(STATIC_PREFIX, staticRoot);
        if (root != null && !root.isDirectory()) {
            log.warn("Eova Web 层：静态空间根不存在（{}）⇒ /eova/** 静态资源不会被供给", root.getAbsolutePath());
        }
        if (privateRoot != null && !privateRoot.isDirectory()) {
            log.warn("Eova Web 层：扩展静态根不存在（{}）⇒ /_eova/** 静态资源不会被供给"
                    + "（后果：表格单元格渲染器缺失 ⇒ 列表页有数据但格子空）", privateRoot.getAbsolutePath());
        }
        if (viewRoot != null && !viewRoot.isDirectory()) {
            log.warn("Eova Web 层：旧页脚本根不存在（{}）⇒ /_view/** 不会被供给"
                    + "（后果：/main 主题页渲染出来但脚本 404 ⇒ 空白页）", viewRoot.getAbsolutePath());
        }
        if (excelRoot != null && !excelRoot.isDirectory()) {
            log.warn("Eova Web 层：Excel 导入页脚本根不存在（{}）⇒ /excel/** 不会被供给"
                    + "（后果：唯一活旧页 /excel/imports/<code> 的 app.js/btn.js 取不到）", excelRoot.getAbsolutePath());
        }
        if (staticRoot != null && !staticRoot.isDirectory()) {
            log.warn("Eova Web 层：可下载静态文件根不存在（{}）⇒ /_static/** 不会被供给"
                    + "（后果：Excel 导入页的「下载导入模板」链接 404）", staticRoot.getAbsolutePath());
        }
    }

    /**
     * 是否属于静态空间前缀（旧栈：只有 {@code /eova/**}）。
     *
     * @param path 请求路径
     * @return 是否静态空间
     */
    public boolean isStaticSpace(String path) {
        return spacePrefixOf(path) != null;
    }

    /**
     * 取该路径所属的静态空间前缀。
     *
     * @param path 请求路径
     * @return 命中的前缀；不属于任何静态空间返回 null
     */
    private String spacePrefixOf(String path) {
        if (path == null) {
            return null;
        }
        for (String p : spaces.keySet()) {
            if (path.startsWith(p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * **路径用的**百分号解码（只解 {@code %XX}，**不**把 {@code '+'} 当空格）。
     *
     * <p>为什么需要：{@code /_static/excel/酒店导入模版.xlsx} 这类**非 ASCII 文件名**，浏览器发的是
     * 百分号编码的 UTF-8；不解码则永远命中不了（表现与"文件不存在"完全一样，都是 404）。
     * 而 {@code URLDecoder} 是**表单**语义（会把 {@code +} 解成空格）⇒ 先把 {@code +} 保护成 {@code %2B}。</p>
     *
     * @param s 原始片段
     * @return 解码后的片段；解码失败（非法转义）时返回 null
     */
    private static String decodePath(String s) {
        try {
            return java.net.URLDecoder.decode(s.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("Eova Web 层：静态请求路径解码失败（非法转义）：{}", s);
            return null;
        }
    }

    /**
     * 解析请求路径对应的静态文件，并做**目录穿越**校验。
     *
     * @param path 请求路径（形如 {@code /eova/lib/eova/eovaui.css} 或 {@code /_eova/assets/eova.ui.ext.js}）
     * @return 命中且未越界返回文件；否则 null
     */
    public File resolve(String path) {
        String prefix = spacePrefixOf(path);
        File spaceRoot = prefix == null ? null : spaces.get(prefix);
        // ★ 前缀判断必须**由本方法自己做**，不能依赖调用方：下面是"按固定长度剥离前缀"，
        //   一旦调用方用了更宽的前缀判断（例如放宽成"任意路径"），固定剥离就会产生**路径别名**
        //   （`/xxxxx/lib/x.css` 被当成 `lib/x.css` 命中静态文件）——r247 的 M6 变异实测暴露了这个风险。
        if (spaceRoot == null) {
            return null;
        }
        // ★ r307（U3）：先解码再校验越界 —— 顺序不能反：`%2e%2e%2f` 必须先变成 `../`
        //   才会被下面的规范化 + 前缀校验拦住（解码在后的话，编码形式的穿越就漏了）。
        String rel = decodePath(path.substring(prefix.length()));
        if (rel == null || rel.isEmpty() || rel.indexOf('\0') >= 0) {
            return null;
        }
        try {
            Path base = spaceRoot.getCanonicalFile().toPath();
            Path target = base.resolve(rel).normalize();
            // ★ 必须做规范化后的**前缀**校验：`/eova/../x` 这类请求不得读到根外文件
            if (!target.startsWith(base)) {
                log.warn("Eova Web 层：拒绝越界的静态请求 {}", path);
                return null;
            }
            File f = target.toFile();
            return f.isFile() ? f : null;
        } catch (IOException e) {
            log.warn("Eova Web 层：静态资源解析失败 {}：{}", path, e.toString());
            return null;
        }
    }

    /**
     * 按扩展名取 Content-Type（未知类型给 application/octet-stream）。
     *
     * @param path 请求路径
     * @return Content-Type
     */
    public String contentType(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return "application/octet-stream";
        }
        String ext = path.substring(dot + 1).toLowerCase();
        String type = TYPES.get(ext);
        return type != null ? type : "application/octet-stream";
    }

    /**
     * 供给静态资源：命中则写响应并返回 true；未命中返回 false（调用方继续走动作路由）。
     *
     * @param path     请求路径
     * @param response 响应
     * @return 是否已直出
     * @throws IOException 写响应失败
     */
    public boolean serve(String path, HttpServletResponse response) throws IOException {
        File f = resolve(path);
        if (f == null) {
            return false;
        }
        byte[] bytes = Files.readAllBytes(f.toPath());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(contentType(path));
        response.setContentLength(bytes.length);
        response.setDateHeader("Last-Modified", f.lastModified());
        response.getOutputStream().write(bytes);
        return true;
    }
}
