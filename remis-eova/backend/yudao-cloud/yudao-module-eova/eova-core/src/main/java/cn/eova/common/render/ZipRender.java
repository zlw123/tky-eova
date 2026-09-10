/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.render;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import cn.hutool.core.util.ZipUtil;
import cn.eova.compat.render.LegacyRender;

/**
 * <p>ported from: cn.eova.common.render.ZipRender
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>目录打包下载渲染：hutool ZipUtil 压缩到临时文件后写出</li>
 *   <li>【已声明适配】com.jfinal.render.Render -> cn.eova.compat.render.LegacyRender</li>
 * </ol>
 */
/**
 *
 * @author Sagittaria(shuyang_x @ hotmail.com)
 *
 */
public class ZipRender extends LegacyRender {
    private String targetDirectoryPath;
    private String filename;

    /**
     * 文件夹打包下载
     *
     * @param targetDirectoryPath 目标文件夹
     * @param filename 下载文件名
     */
    public ZipRender(String targetDirectoryPath, String filename) {
        this.targetDirectoryPath = targetDirectoryPath;
        this.filename = filename;
    }

    @Override
    public void render() {
        // String dest = targetDirectoryPath + File.separator + filename + ".zip";

        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "close");
        response.setHeader("Content-Type", "application/octet-stream");

        OutputStream ops = null;
        FileInputStream fis = null;
        File zip = null;

        try {

            // 压缩指定路径

            zip = ZipUtil.zip(targetDirectoryPath);

            response.setHeader("Content-Disposition",
                    "attachment;filename=" + URLEncoder.encode(filename, "UTF-8") + ".zip");

            byte[] buffer = new byte[8192];
            int bytesRead = 0;

            ops = response.getOutputStream();
            fis = new FileInputStream(zip);

            while ((bytesRead = fis.read(buffer, 0, 8192)) != -1) {
                ops.write(buffer, 0, bytesRead);
            }
            ops.flush();

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } // 应在finally里关流，确保close方法被执行到
            if (ops != null) {
                try {
                    ops.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (!zip.delete()) {
                System.err.println("warning: failed to remove zip [" + targetDirectoryPath + "]");
            }
        }
    }

}