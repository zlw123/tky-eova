/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.render;

import cn.eova.compat.jfinal.kit.LegacyKv;
import com.jfinal.kit.PathKit;
import cn.eova.compat.render.LegacyHtmlRender;

/**
 * <p>ported from: cn.eova.common.render.ResourceRender
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>classpath 资源模板渲染：extends HtmlRender，构造期先渲染再交给父类</li>
 *   <li>buildResource 用 PathKit.getPackagePath(object) 定位包内 resources/</li>
 *   <li>【已声明适配 1】com.jfinal.render.HtmlRender -> cn.eova.compat.render.LegacyHtmlRender</li>
 *   <li>【已声明适配 2】com.jfinal.kit.Kv -> cn.eova.compat.jfinal.kit.LegacyKv</li>
 * </ol>
 */
public class ResourceRender extends LegacyHtmlRender {

    public ResourceRender(Object object, String view, LegacyKv attr) {
        super(RenderUtil.renderClasspath(buildResource(object, view), attr));
    }

    private static String buildResource(Object object, String filePath) {
        // 获取当前方法的上上级 也就是 调用
        // StackTraceElement[] ss = Thread.currentThread().getStackTrace();
        // StackTraceElement a = (StackTraceElement)ss[4];
        // String txt = Utils.readFromResource(filePath);
        String pack = PathKit.getPackagePath(object);
        return String.format("%s/resources/%s", pack, filePath);
    }

}