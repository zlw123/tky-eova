/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.upload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * jfinal 5.2.6 {@code com.jfinal.upload.MultipartRequest} 的等价接缝（**落盘语义层**）。
 *
 * <p>ported from: com.jfinal.upload.MultipartRequest（jfinal 5.2.6 制品）+ com.jfinal:cos 2022.2
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08（锁定上述制品版本）
 *
 * <p><b>接缝边界（已声明适配，唯一一处）</b>：旧栈的 multipart 解析由
 * {@code com.jfinal.upload.MultipartRequest}（内部用 {@code com.jfinal:cos} 的
 * {@code com.oreilly.servlet.MultipartRequest}）**从原始 HTTP 体**解析；
 * 新栈由 **Spring Boot 的 multipart 解析器**完成同一件事（DES-002-R4 §2 上传口径：
 * {@code com.jfinal.upload}(3 处) → Spring {@code MultipartFile}，字段名与响应契约不变）。
 * 故本类<b>不解析字节流</b>，只承担旧实现里<b>与 EOVA 可观测行为相关</b>的那一半：
 * <ol>
 *   <li><b>落盘目录解析</b> {@code getFinalPath(uploadPath)}（含 {@code baseUploadPath} 拼接规则）；</li>
 *   <li><b>目录创建</b>与失败消息 {@code "Directory <path> not exists and can not create directory."}；</li>
 *   <li><b>落盘文件名</b>：COS {@code DefaultFileRenamePolicy} 的"已存在则 {@code body+count+ext}"，
 *       外加 jfinal 对 {@code .jsp}/{@code .jspx} 的 {@code _unsafe} 前缀防护；</li>
 *   <li><b>扩展名白名单校验</b>（{@link LegacyUploadConfig#whitelist}）与失败消息
 *       {@code "上传文件类型白名单不支持上传该文件: \"<name>\""}，且失败时删除全部已落盘文件；</li>
 *   <li><b>"只解析一次"</b>：旧实现首次 {@code getFiles(uploadPath)} 会把 request 包成
 *       MultipartRequest 并缓存，后续调用<b>复用</b>同一份列表（换路径也不再解析）。</li>
 * </ol>
 *
 * <p><b>刻意保留的既有语义</b>：
 * <ol>
 *   <li>{@code getFinalPath} 对 {@code null} 抛 {@code IllegalArgumentException("uploadPath can not be null.")}；
 *       {@code uploadPath} 先 {@code trim()}；以 {@code '/'} 或 {@code '\'} 开头时**直接拼接**
 *       {@code baseUploadPath}（不再补分隔符），否则补 {@code File.separator}；
 *       特例：{@code baseUploadPath} 恰为 {@code "/"} 时原样返回。</li>
 *   <li>{@code UploadFile.getUploadPath()} 返回的是<b>解析后的最终目录</b>
 *       （{@code getFinalPath} 的结果），故 EOVA 的
 *       {@code file.getUploadPath() + File.separator + newFileName} 与落盘位置一致。</li>
 *   <li>白名单判定取 <b>最后一个点之后</b>的片段（{@code FileName.trim()} 后），
 *       无点或未命中一律判非法；<b>大小写不敏感</b>。</li>
 * </ol>
 *
 * <p><b>未纳入（记录在案）</b>：{@code maxPostSize} 超限时旧实现抛
 * {@code com.jfinal.upload.ExceededSizeException}（EOVA 全树无捕获点，且新栈由
 * Spring 的 {@code spring.servlet.multipart.max-*} 在进入 action 前拒绝）；
 * 参数读取（{@code getParameter}/{@code getParameterMap}）旧实现委派给被包装的 request，
 * 新栈直接走 Servlet/Spring 的请求对象，故本类不重复提供。
 */
public class LegacyMultipartRequest {

    /** 宿主注入的原始部件（对应 COS 解析出的一个 multipart 段） */
    public static class Part {

        private final String parameterName;

        private final String originalFileName;

        private final String contentType;

        private final File source;

        /**
         * @param parameterName    表单参数名
         * @param originalFileName 客户端原始文件名
         * @param contentType      内容类型
         * @param source           已落地的临时文件（宿主负责从 MultipartFile 转出）
         */
        public Part(String parameterName, String originalFileName, String contentType, File source) {
            this.parameterName = parameterName;
            this.originalFileName = originalFileName;
            this.contentType = contentType;
            this.source = source;
        }
    }

    /** 宿主注入的部件（顺序即解析顺序；EOVA 只用第一个命中参数名的部件） */
    private final List<Part> parts = new ArrayList<>();

    /** 解析结果缓存（旧实现"首次解析后缓存"的等价物） */
    private List<LegacyUploadFile> uploadFiles;

    /** 非法文件（旧实现记录后统一删除并抛出） */
    private String illegalUploadFile;

    /**
     * 追加一个部件（宿主在 action 执行前调用）。
     *
     * @param parameterName    表单参数名
     * @param originalFileName 原始文件名
     * @param contentType      内容类型
     * @param source           临时文件
     */
    public void addPart(String parameterName, String originalFileName, String contentType, File source) {
        parts.add(new Part(parameterName, originalFileName, contentType, source));
    }

    /**
     * 取解析后的上传文件列表（等价 {@code MultipartRequest.getFiles()}）。
     *
     * @return 不可变列表
     */
    public List<LegacyUploadFile> getFiles() {
        if (uploadFiles == null) {
            throw new IllegalStateException("LegacyMultipartRequest 尚未按 uploadPath 落盘："
                    + "请先经 LegacyController.getFiles(uploadPath) 触发解析");
        }
        return Collections.unmodifiableList(uploadFiles);
    }

    /**
     * 按上传目录落盘并返回文件列表（等价旧实现 {@code new MultipartRequest(request, uploadPath)} + {@code getFiles()}）。
     *
     * @param uploadPath 上传目录（可为相对目录；{@code null} 时按旧实现抛异常）
     * @return 不可变列表
     */
    public List<LegacyUploadFile> getFiles(String uploadPath) {
        if (uploadFiles != null) {
            return Collections.unmodifiableList(uploadFiles);
        }
        List<LegacyUploadFile> list = new ArrayList<>();
        String finalPath = getFinalPath(uploadPath);

        File dir = new File(finalPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Directory " + finalPath + " not exists and can not create directory.");
        }

        try {
            for (Part p : parts) {
                File target = rename(new File(finalPath, p.originalFileName));
                Files.copy(p.source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                LegacyUploadFile uf = new LegacyUploadFile(p.parameterName, finalPath, target.getName(),
                        p.originalFileName, p.contentType);
                if (!isSafeFile(uf)) {
                    break;  // 旧实现：一旦发现非法文件，后续不再接收
                }
                list.add(uf);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.uploadFiles = list;
        handleIllegalUpload();
        return Collections.unmodifiableList(uploadFiles);
    }

    /**
     * 解析最终落盘目录（逐字节等价旧实现 {@code MultipartRequest.getFinalPath}）。
     *
     * @param uploadPath 上传目录
     * @return 最终目录
     */
    static String getFinalPath(String uploadPath) {
        if (uploadPath == null) {
            throw new IllegalArgumentException("uploadPath can not be null.");
        }
        uploadPath = uploadPath.trim();
        if (uploadPath.startsWith("/") || uploadPath.startsWith("\\")) {
            if (LegacyUploadConfig.baseUploadPath.equals("/")) {
                return uploadPath;
            }
            return LegacyUploadConfig.baseUploadPath + uploadPath;
        }
        return LegacyUploadConfig.baseUploadPath + File.separator + uploadPath;
    }

    /**
     * 落盘文件名决策（等价 COS {@code DefaultFileRenamePolicy} + jfinal {@code MultipartRequest$1}）。
     *
     * <p>规则：{@code .jsp}/{@code .jspx}（大小写不敏感、去空白后比较）先改成
     * {@code <name>_unsafe}；随后若目标已存在，则按 {@code body + count + ext}
     * 递增 {@code count}（上限 9999）直到可用。</p>
     *
     * @param file 目标文件
     * @return 可用文件名对应的 File
     */
    private static File rename(File file) {
        String name = file.getName();
        String body = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            ext = name.substring(dot).toLowerCase().trim();
            if (ext.equals(".jsp") || ext.equals(".jspx")) {
                file = new File(file.getParent(), name + "_unsafe");
            }
            body = name.substring(0, dot);
            ext = name.substring(dot);
        }
        if (!file.exists()) {
            return file;
        }
        int count = 0;
        while (file.exists() && count < 9999) {
            count++;
            file = new File(file.getParent(), body + count + ext);
        }
        return file;
    }

    /**
     * 扩展名白名单判定（逐字节等价 {@code MultipartRequest.isSafeFile}）。
     *
     * @param uploadFile 待判定文件
     * @return true 表示命中白名单；否则记录非法名并删除该文件
     */
    protected boolean isSafeFile(LegacyUploadFile uploadFile) {
        String name = uploadFile.getFileName().trim();
        int i = name.lastIndexOf('.');
        if (i != -1) {
            String ext = name.substring(i + 1);
            if (LegacyUploadConfig.whitelist.contains(ext)) {
                return true;
            }
        }
        this.illegalUploadFile = name;
        try {
            uploadFile.getFile().delete();
        } catch (Exception ignored) {
            // 旧字节码：删除异常被吞掉
        }
        return false;
    }

    /**
     * 非法文件统一处置（逐字节等价 {@code MultipartRequest.handleIllegalUpload}）：
     * 删除**全部**已落盘文件，然后抛出带文件名的运行时异常。
     */
    protected void handleIllegalUpload() {
        if (illegalUploadFile != null) {
            for (LegacyUploadFile uf : uploadFiles) {
                try {
                    uf.getFile().delete();
                } catch (Exception ignored) {
                    // 旧字节码：删除异常被吞掉
                }
            }
            throw new RuntimeException("上传文件类型白名单不支持上传该文件: \"" + illegalUploadFile + "\"");
        }
    }

}
