/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.render;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;

import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyRenderException;

/**
 * <p>ported from: cn.eova.common.render.Html2XlsRender
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>HTML 转 Excel 下载渲染（旧实现只写出 HTML 表格）</li>
 *   <li>【已声明适配】Kv -> LegacyKv；com.jfinal.render.{Render,RenderException} -> 接缝</li>
 * </ol>
 */
public class Html2XlsRender extends LegacyRender {

    private final static String CONTENT_TYPE = "application/msexcel;charset=" + getEncoding();

    private final String xls;
    private final String fileName;

    /**
     * 渲染Xls
     * @param fileName 下载文件名
     * @param path XLS文件路径
     * @param kv 模版参数
     */
    public Html2XlsRender(String fileName, String path, LegacyKv kv) {
        this.fileName = fileName;
        this.xls = RenderUtil.renderFile(path, kv);
    }

    @Override
    public void render() {
        PrintWriter writer = null;
        try {
            response.setHeader("Pragma", "no-cache"); // HTTP/1.0 caches might not implement Cache-Control and might only implement Pragma: no-cache
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Content-disposition", "attachment; filename=" + URLEncoder.encode(fileName, "UTF-8"));
            response.setDateHeader("Expires", 0);
            response.setContentType(CONTENT_TYPE);
            response.setCharacterEncoding(getEncoding());

            writer = response.getWriter();
            writer.write(xls);
            writer.flush();
        } catch (IOException e) {
            throw new LegacyRenderException(e);
        } finally {
            if (writer != null)
                writer.close();
        }
    }

}