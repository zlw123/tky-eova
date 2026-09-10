/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.mod;

import java.io.File;
import java.util.ArrayList;

import com.jfinal.kit.PathKit;

/**
 * <p>ported from: cn.eova.mod.EovaModConst
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>Mod 常量；唯一宿主依赖 PathKit（enjoy 提供）—— 逐字节即语义等价</li>
 *   <li>注意 DIR 类常量在【静态初始化】里调 PathKit.getWebRootPath()，新栈必须先 PathKit.setWebRootPath 才能取到正确值（同 EovaConst）</li>
 * </ol>
 */
public class EovaModConst {
    /** MOD Jar 目录 **/
    public static final String DIR_MOD = dirMod();
    /** MOD View 目录 **/
    public static final String DIR_MOD_VIEW = dirModView();

    /**
     * 获取资源目录
     * @return
     */
    public static String getResourcesPath() {
        return new File(PathKit.getWebRootPath()).getParent() + File.separator + "resources";
    }

    public static String dirMod() {
        StringBuilder sb = new StringBuilder(PathKit.getWebRootPath());
        sb.append(File.separator);
        sb.append("WEB-INF");
        sb.append(File.separator);
        sb.append("mod");
        sb.append(File.separator);
        return sb.toString();
    }

    public static String dirModView() {
        StringBuilder sb = new StringBuilder(PathKit.getWebRootPath());
        sb.append(File.separator);
        sb.append("_mod");
        sb.append(File.separator);
        return sb.toString();
    }

    /**
     * 合法资源文件白名单
     * @return
     */
    public static String[] getResourceSuffix() {
        ArrayList<String> config = new ArrayList<>();
        config.add(".html");
        config.add(".htm");
        config.add(".css");
        config.add(".js");
        config.add(".jpg");
        config.add(".jpeg");
        config.add(".png");
        config.add(".bmp");
        config.add(".gif");
        config.add(".webp");
        config.add(".svg");
        config.add(".ttf");
        config.add(".woff");
        config.add(".woff2");
        config.add(".webp");
        config.add(".sql");

        return config.toArray(new String[config.size()]);
    }

}