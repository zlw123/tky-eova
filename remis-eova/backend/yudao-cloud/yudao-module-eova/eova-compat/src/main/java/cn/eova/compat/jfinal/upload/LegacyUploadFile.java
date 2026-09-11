/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.upload;

import java.io.File;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.upload.UploadFile} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.upload.UploadFile}（jfinal 5.2.6）。</p>
 *
 * <p><b>逐条取自旧字节码：</b>5 个字段
 * （{@code parameterName}/{@code uploadPath}/{@code fileName}/{@code originalFileName}/{@code contentType}）
 * + 五参构造器（顺序即字段顺序）+ 各 getter；
 * {@link #getFile()} 在 {@code uploadPath} 或 {@code fileName} 为 null 时返回 <b>null</b>，
 * 否则 {@code new File(uploadPath + File.separator + fileName)}。</p>
 */
public class LegacyUploadFile {

    private String parameterName;

    private String uploadPath;

    private String fileName;

    private String originalFileName;

    private String contentType;

    /**
     * 五参构造。
     *
     * @param parameterName    表单参数名
     * @param uploadPath       上传目录
     * @param fileName         落盘文件名
     * @param originalFileName 原始文件名
     * @param contentType      内容类型
     */
    public LegacyUploadFile(String parameterName, String uploadPath, String fileName,
                            String originalFileName, String contentType) {
        this.parameterName = parameterName;
        this.uploadPath = uploadPath;
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
    }

    /** 取表单参数名 */
    public String getParameterName() {
        return parameterName;
    }

    /** 取上传目录 */
    public String getUploadPath() {
        return uploadPath;
    }

    /** 取落盘文件名 */
    public String getFileName() {
        return fileName;
    }

    /** 取原始文件名 */
    public String getOriginalFileName() {
        return originalFileName;
    }

    /** 取内容类型 */
    public String getContentType() {
        return contentType;
    }

    /**
     * 取落盘文件；{@code uploadPath} 或 {@code fileName} 为 null 时返回 null。
     *
     * @return 文件
     */
    public File getFile() {
        if (uploadPath == null || fileName == null) {
            return null;
        }
        return new File(uploadPath + File.separator + fileName);
    }

}
