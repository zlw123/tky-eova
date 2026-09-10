/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.render;

import java.io.IOException;
import java.io.PrintWriter;

import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyRenderException;

/**
 * <p>ported from: cn.eova.common.render.XmlRender
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>XML 输出渲染：contentType 常量在静态初始化里拼接 getEncoding()</li>
 *   <li>【已声明适配】com.jfinal.render.{Render,RenderException} -> cn.eova.compat.render.{LegacyRender,LegacyRenderException}</li>
 * </ol>
 */
public class XmlRender extends LegacyRender {

    private static final String contentType = "text/xml;charset=" + getEncoding();

    private String xml;

    public XmlRender(String xml) {
        if (xml == null)
            throw new IllegalArgumentException("The parameter xml can not be null.");
        this.xml = xml;
    }

    public void render() {
        PrintWriter writer = null;
        try {
            response.setHeader("Pragma", "no-cache"); // HTTP/1.0 caches might not implement Cache-Control and might only implement Pragma: no-cache
            response.setHeader("Cache-Control", "no-cache");
            response.setDateHeader("Expires", 0);
            response.setContentType(contentType);
            response.setCharacterEncoding(getEncoding());

            writer = response.getWriter();
            writer.write(xml);
            writer.flush();
        } catch (IOException e) {
            throw new LegacyRenderException(e);
        } finally {
            if (writer != null)
                writer.close();
        }
    }
}