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
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 旧静态空间 {@code /eova/**} 的宿主供给（切片 S3，第 247 轮）。
 *
 * <p><b>旧栈语义（实测，不是推断）</b>：旧 demo 上
 * {@code /eova/lib/eova/eovaui.css}、{@code /eova/ui/css/common.css}、
 * {@code /eova/_view/template/eova.template.js} 全部 **200**，而 {@code /ui/css/common.css}、
 * {@code /_eova/include.html} 是 **404** ⇒ 静态空间**只有 {@code /eova/**}</b>，其来源是
 * classpath 的 {@code webapp/eova/**}（旧 {@code undertow.resourcePath=classpath:webapp}）。</p>
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

    /** 静态空间前缀（旧栈实测的唯一一个） */
    public static final String PREFIX = "/eova/";

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

    /**
     * 构造。
     *
     * @param webRoot web 根目录（含 {@code eova/} 的那一层）；为 null 时本组件不供给任何资源
     */
    public LegacyStaticAssets(File webRoot) {
        this.root = webRoot == null ? null : new File(webRoot, "eova");
        if (root != null && !root.isDirectory()) {
            log.warn("Eova Web 层：静态空间根不存在（{}）⇒ /eova/** 静态资源不会被供给", root.getAbsolutePath());
        }
    }

    /**
     * 是否属于静态空间前缀（旧栈：只有 {@code /eova/**}）。
     *
     * @param path 请求路径
     * @return 是否静态空间
     */
    public boolean isStaticSpace(String path) {
        return path != null && path.startsWith(PREFIX);
    }

    /**
     * 解析请求路径对应的静态文件，并做**目录穿越**校验。
     *
     * @param path 请求路径（形如 {@code /eova/lib/eova/eovaui.css}）
     * @return 命中且未越界返回文件；否则 null
     */
    public File resolve(String path) {
        // ★ 前缀判断必须**由本方法自己做**，不能依赖调用方：下面是"按固定长度剥离前缀"，
        //   一旦调用方用了更宽的前缀判断（例如放宽成"任意路径"），固定剥离就会产生**路径别名**
        //   （`/xxxxx/lib/x.css` 被当成 `lib/x.css` 命中静态文件）——r247 的 M6 变异实测暴露了这个风险。
        if (root == null || !isStaticSpace(path)) {
            return null;
        }
        String rel = path.substring(PREFIX.length());
        if (rel.isEmpty() || rel.indexOf('\0') >= 0) {
            return null;
        }
        try {
            Path base = root.getCanonicalFile().toPath();
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
