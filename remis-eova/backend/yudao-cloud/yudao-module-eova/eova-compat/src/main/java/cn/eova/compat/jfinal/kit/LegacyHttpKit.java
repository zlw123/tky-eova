/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.io.InputStreamReader;
import jakarta.servlet.http.HttpServletRequest;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.kit.HttpKit} 的等价接缝（仅读请求体部分）。
 *
 * <p>{@code ported from} {@code com.jfinal.kit.HttpKit}（jfinal 5.2.6）。</p>
 *
 * <p><b>逐条取自旧字节码的语义：</b></p>
 * <ol>
 *   <li>{@link #readData(HttpServletRequest)} 用
 *       {@code request.getCharacterEncoding()} 作为编码；<b>为 null 时回落到静态 {@code CHARSET}</b>
 *       （默认 {@code "UTF-8"}，可由 {@link #setCharSet(String)} 改）；</li>
 *   <li>缓冲为 {@code char[1024]}，按 {@code read(buf, 0, buf.length)} 循环读取并 append；</li>
 *   <li>任何异常都包成 {@code RuntimeException(e)}（旧实现即如此）。</li>
 * </ol>
 *
 * <p><b>刻意不实现的部分（显式声明）：</b>旧 {@code HttpKit} 的 HTTP <b>客户端</b>能力
 * （{@code get/post/upload/download} 族，含自签证书信任的 {@code SSLSocketFactory}、
 * {@code connectTimeout=19000}）—— EOVA 全树<b>只用 readData</b>（唯一调用点
 * {@code ApiRouterHandler:76}）。新栈的 HTTP 客户端应由 Spring 提供，
 * 故这里不留空壳：若将来需要，编译期就会报错，而不是静默走向一个假实现。</p>
 */
public final class LegacyHttpKit {

    /** 默认编码（旧实现的静态字段初值，clinit 里为 "UTF-8"） */
    private static String charset = "UTF-8";

    private LegacyHttpKit() {
    }

    /**
     * 设置默认编码（旧 {@code HttpKit.setCharSet}）。
     *
     * @param charset 编码
     */
    public static void setCharSet(String charset) {
        LegacyHttpKit.charset = charset;
    }

    /**
     * 读取请求体为字符串（旧 {@code HttpKit.readData(HttpServletRequest)}）。
     *
     * <p><b>注意顺序：</b>编码取自 {@code request.getCharacterEncoding()}，
     * 为 null 才回落到 {@link #charset}；且读取用的是<b>原始 InputStream</b>。</p>
     *
     * @param request 请求
     * @return 请求体文本
     */
    public static String readData(HttpServletRequest request) {
        try {
            String enc = request.getCharacterEncoding();
            InputStreamReader reader = new InputStreamReader(request.getInputStream(),
                    enc != null ? enc : charset);
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int len;
            while ((len = reader.read(buf, 0, buf.length)) != -1) {
                sb.append(buf, 0, len);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
