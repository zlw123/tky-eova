/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.upload;

import java.util.Set;
import java.util.TreeSet;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.upload.UploadConfig} 的等价接缝。
 *
 * <p>ported from: com.jfinal.upload.UploadConfig + com.jfinal.config.Constants（jfinal 5.2.6 制品）。
 *
 * <p><b>为什么需要它：</b>旧栈的上传落盘路径由 {@code MultipartRequest.getFinalPath()} 决定，
 * 而它读的是本类的静态字段；这些字段在旧栈由
 * {@code JFinal.initUploadConfig()} → {@code UploadConfig.init(constants.getBaseUploadPath(),
 * constants.getMaxPostSize(), constants.getEncoding())} 装配，其中
 * {@code baseUploadPath} 被 EOVA 在 {@code EovaConfig.configConstant()} 里
 * 用 {@code me.setBaseUploadPath(x.conf.get("file.dir.base"))} 覆盖。
 * 新栈没有 jfinal 的启动引导，故本接缝提供同名字段与 {@link #init}，
 * 由宿主（W3 接缝族的 {@code EovaConfig}）按同一顺序装配。</p>
 *
 * <p><b>默认值取自旧字节码（{@code com.jfinal.config.Constants} 构造器）：</b>
 * {@code baseUploadPath = "upload"}、{@code encoding = "UTF-8"}、
 * {@code maxPostSize = 10485760L}（10MB）。<b>注意</b>：{@code UploadConfig} 自身的静态字段
 * 在 {@code init} 之前为 {@code null}，旧栈靠启动引导保证非 null ——
 * 本接缝为避免"忘记装配 ⇒ NPE"，把三个字段的初值固定为上述 Constants 默认值，
 * 属<b>已声明适配</b>（装配缺失时行为等价于"用了默认配置"，而不是崩溃）。</p>
 *
 * <p><b>白名单（54 项，逐字取自静态初始化的 {@code anewarray} 常量池）</b>，
 * 容器为 {@code TreeSet(String.CASE_INSENSITIVE_ORDER)} ⇒ <b>大小写不敏感</b>、
 * 迭代序为字典序。判定规则见 {@code MultipartRequest.isSafeFile}：
 * 取 {@code fileName.trim()} 的<b>最后一个 {@code '.'} 之后</b>的部分，
 * 命中即通过；未命中则删除该文件并抛
 * {@code RuntimeException("上传文件类型白名单不支持上传该文件: \"<name>\"")}。</p>
 */
public class LegacyUploadConfig {

    /**
     * 上传根目录（旧栈由 {@code Constants.setBaseUploadPath} 注入，EOVA 取 {@code file.dir.base}）。
     */
    static String baseUploadPath = "upload";

    /** 上传体大小上限（字节） */
    static long maxPostSize = 10485760L;

    /** 编码 */
    static String encoding = "UTF-8";

    /** 扩展名白名单（大小写不敏感；与旧栈同为 TreeSet + CASE_INSENSITIVE_ORDER） */
    static Set<String> whitelist = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        String[] exts = {
                // 逐字取自 com.jfinal.upload.UploadConfig 静态初始化（54 项，顺序即字节码顺序）
                "apk", "rar", "zip", "gzip", "tar", "gz", "dmg",
                "jpg", "png", "jpeg", "webp", "svg", "bmp",
                "css", "js", "json", "xml", "md", "txt",
                "pdf", "doc", "docx", "xls", "xlsx", "pot", "ppt", "pptx", "wps",
                "mp3", "mp2", "m3u", "m3u8", "ra", "mpga", "ram", "wav", "wax", "wma",
                "mp4", "mpeg", "avi", "wvm", "3gp", "asf", "asx", "flv", "mps", "pmv",
                "mov", "mpa", "mpe", "m4e", "m2v", "ts"};
        for (String e : exts) {
            whitelist.add(e);
        }
    }

    private LegacyUploadConfig() {
    }

    /**
     * 装配（等价旧栈 {@code UploadConfig.init(baseUploadPath, maxPostSize, encoding)}）。
     *
     * @param baseUploadPath 上传根目录
     * @param maxPostSize    上传体上限（字节）
     * @param encoding       编码
     */
    public static void init(String baseUploadPath, long maxPostSize, String encoding) {
        LegacyUploadConfig.baseUploadPath = baseUploadPath;
        LegacyUploadConfig.maxPostSize = maxPostSize;
        LegacyUploadConfig.encoding = encoding;
    }

    /**
     * 追加白名单（等价 {@code UploadConfig.addWhitelist}）。
     *
     * @param exts 扩展名
     */
    public static void addWhitelist(String... exts) {
        for (String e : exts) {
            whitelist.add(e);
        }
    }

    /**
     * 移除白名单项（等价 {@code UploadConfig.removeWhitelist}）。
     *
     * @param ext 扩展名
     */
    public static void removeWhitelist(String ext) {
        whitelist.remove(ext);
    }

    /** 清空白名单（等价 {@code UploadConfig.clearWhitelist}） */
    public static void clearWhitelist() {
        whitelist.clear();
    }

    /**
     * 取上传根目录。
     *
     * @return 上传根目录
     */
    public static String getBaseUploadPath() {
        return baseUploadPath;
    }

    /**
     * 取上传体上限。
     *
     * @return 字节数
     */
    public static long getMaxPostSize() {
        return maxPostSize;
    }

    /**
     * 取编码。
     *
     * @return 编码
     */
    public static String getEncoding() {
        return encoding;
    }

}
