/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

/**
 * **SPA 壳渲染**（第 305 轮 · U1=「生产态供给 + 页面入口退役」）。
 *
 * <p>阶段 2 的终局是"服务端不再渲染页面"：页面 URL 由 SPA 接管，服务端只供给**打包产物**。
 * 本类就是"页面入口退役"的落点 —— 旧 {@code renderEnjoy("/eova/...html")} 换成
 * {@code render(new LegacySpaShellRender())}，服务端返回 {@code dist/index.html}，
 * 由前端路由决定渲染哪一页。</p>
 *
 * <p><b>为什么不做成"把 dist 塞进 classpath"</b>：产物由前端工程 {@code pnpm build} 生成、
 * 位置随工作目录变化；故与 {@code LegacyStaticAssets} 同法 —— 由 web 层在引导期解析出
 * <b>产物根目录</b>并用 {@link #setDistRoot(File)} 注入（{@code eova.ui.dist} 配置优先，
 * 否则按"向上最多 6 层找 {@code remis-eova/front/remis-eova-ui/dist}"解析）。</p>
 *
 * <p><b>响亮失败</b>：产物缺失时**不静默返回空白** —— 写 500 并在响应体里写明原因
 * （与 {@code loadLegacyRuntime} 的纪律一致：装配没到位就炸，而不是让用户看见白屏）。</p>
 */
public class LegacySpaShellRender extends LegacyRender {

    /** 前端打包产物根目录（含 index.html）；由 web 层在引导期注入 */
    private static volatile File distRoot;

    /** 注入产物根目录（引导期调用；判据可直接驱动） */
    public static void setDistRoot(File root) {
        distRoot = root;
    }

    /** 取产物根目录（可为 null = 未配置/不存在） */
    public static File getDistRoot() {
        return distRoot;
    }

    @Override
    public void render() {
        HttpServletResponse resp = response;
        File root = distRoot;
        File index = root == null ? null : new File(root, "index.html");
        resp.setContentType("text/html;charset=UTF-8");
        // 壳是入口页：不得缓存（否则前端发版后用户仍拿到旧壳）
        resp.setHeader("Cache-Control", "no-store");
        if (index == null || !index.isFile()) {
            resp.setStatus(500);
            try {
                resp.getWriter().write("SPA 壳缺失：未找到前端打包产物 index.html"
                        + "（dist=" + (root == null ? "未配置" : root.getAbsolutePath())
                        + "）。请先执行前端构建并把 eova.ui.dist 指到 dist 目录。");
            } catch (IOException ignored) {
                // 写不出去也无更多可做
            }
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(index.toPath());
            resp.setContentLength(bytes.length);
            ServletOutputStream os = resp.getOutputStream();
            os.write(bytes);
            os.flush();
        } catch (IOException e) {
            throw new LegacyRenderException(e);
        }
    }

    /** 与旧实现一致：返回 {@code view} 本身（本类无 view） */
    @Override
    public String toString() {
        return "LegacySpaShellRender" + (distRoot == null ? "" : "(" + distRoot + ")");
    }

    /** 产物根目录下的相对文件（供 web 层做 {@code /eova-assets/**} 静态供给时复用解析） */
    public static File resolveInDist(String relative) {
        File root = distRoot;
        return root == null ? null : new File(root, relative);
    }

    /** 便于判据构造：把 UTF-8 字符串转字节（避免各处置乱） */
    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
