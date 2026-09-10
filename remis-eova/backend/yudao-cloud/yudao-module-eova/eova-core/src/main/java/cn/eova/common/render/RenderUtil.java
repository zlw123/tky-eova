/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.render;

import cn.eova.engine.ExpUtil;
import cn.eova.compat.jfinal.kit.LegacyKv;
import com.jfinal.template.Engine;
import com.jfinal.template.source.ClassPathSourceFactory;

/**
 * <p>ported from: cn.eova.common.render.RenderUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>模板渲染工具：renderFile 走 ExpUtil.parseTemplate；renderClasspath 走 enjoy Engine + ClassPathSourceFactory</li>
 *   <li>【已声明适配】com.jfinal.kit.Kv -> cn.eova.compat.jfinal.kit.LegacyKv（R37/R40）</li>
 * </ol>
 */
public class RenderUtil {

    /**
     * 渲染指定文件
     * @param path 文件路径
     * @param kv 动态参数
     * @return
     */
    public static String renderFile(String path, LegacyKv kv) {
        return ExpUtil.parseTemplate(path, kv);
    }

    /**
     * 渲染指定资源文件(比如Jar内资源文件)
     * @param classpath 资源文件路径
     * @param kv 动态参数
     * @return
     */
    public static String renderClasspath(String classpath, LegacyKv kv) {
        return Engine.use().setSourceFactory(new ClassPathSourceFactory()).getTemplate(classpath).renderToString(kv);
//
//        String text = "";
//        InputStream in = null;
//        try {
//            in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
//            if (in == null) {
//                in = RenderUtil.class.getResourceAsStream(resource);
//            }
//            if (in == null) {
//                return null;
//            }
//            text = TxtUtil.read(in);
//        } finally {
//            StreamUtil.close(in);
//        }
//        return RenderUtil.renderFile(text, kv);
    }

}