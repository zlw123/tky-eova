/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.jfinal.upload.LegacyMultipartRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * **把 Spring 解析出的 multipart 注入旧口径的上传部件容器**（DES-012 P1-U4，r332）。
 *
 * <p><b>它补的是什么缺口（实测，不是推断）</b>：旧栈的 multipart 由 jfinal 的
 * {@code com.jfinal.upload.MultipartRequest}（内部 COS）**从原始 HTTP 体自己解析**；
 * 新栈把这层交给 Spring（Boot 的 {@code StandardServletMultipartResolver}），
 * 于是 {@code LegacyController} 只持有"部件容器"句柄，需要一个**宿主入口**在 action 之前注入
 * —— 这就是 {@code LegacyController#setMultipartRequest}（r77 声明的接缝）。
 * 但该入口**此前从未被调用**：{@code POST /upload/file}（合法文件）在旧栈是
 * **200 + {@code {"fileName":…,"uploadDir":"/","state":"ok","oldFileName":"probe.png"}}**，
 * 在新栈是 **500**（{@code IllegalStateException: multipart 部件容器未注入}），
 * 而既有判据全是单元级（自己调 {@code setMultipartRequest}）⇒ 缺口对全量扫描不可见。</p>
 *
 * <p><b>分工（不越界）</b>：本类只做"Spring 部件 → {@link LegacyMultipartRequest.Part}"的**搬运**
 * （字段名 / 浏览器原名 / Content-Type / 临时文件）。落盘语义（最终目录 {@code baseUploadPath + uploadDir}、
 * COS 重名策略、{@code jsp/jspx} 的 {@code _unsafe} 防护、54 项扩展名白名单）**原样**留在
 * {@link LegacyMultipartRequest}（既有判据 {@code UploadFamilyGoldenTest} 钉着）。</p>
 *
 * <p><b>★ 一个刻意的等价选择</b>：只有**真的存在上传部件**（{@code n > 0}）时才注入容器。
 * 请求是 multipart 但没有文件部件时（旧栈 COS 解析即报错 ⇒ 500），不注入 ⇒
 * {@code LegacyController#getFiles} 抛"未注入" ⇒ 500，与旧栈同一失败面（不把缺陷改成 200 空列表）。</p>
 *
 * @see StaticResourceHandlerMapping
 */
public final class LegacyMultipartInjector {

    private static final Logger log = LoggerFactory.getLogger(LegacyMultipartInjector.class);

    /** 临时文件前缀（沿用"上传临时文件"语义；落盘由 LegacyMultipartRequest 负责） */
    private static final String TEMP_PREFIX = "eova-upload-";

    private LegacyMultipartInjector() {
    }

    /**
     * 若请求是 Spring 已解析的 multipart 且确有上传部件，则注入旧口径容器。
     *
     * @param controller 本次请求的控制器实例
     * @param request    原始请求（**必须在 JSON 包装之前**调用：包装类型不再实现 MultipartHttpServletRequest）
     */
    public static void inject(LegacyController controller, HttpServletRequest request) {
        if (!(request instanceof MultipartHttpServletRequest multipart)) {
            // 非 multipart：旧栈由 COS 在 getFiles 时报错 ⇒ 这里保持"不注入"，由调用方同口径失败
            return;
        }
        LegacyMultipartRequest container = new LegacyMultipartRequest();
        int count = 0;
        for (Map.Entry<String, List<MultipartFile>> entry : multipart.getMultiFileMap().entrySet()) {
            for (MultipartFile file : entry.getValue()) {
                File source = toTempFile(file);
                if (source == null) {
                    continue;
                }
                container.addPart(entry.getKey(), file.getOriginalFilename(), file.getContentType(), source);
                count++;
            }
        }
        if (count > 0) {
            controller.setMultipartRequest(container);
        }
    }

    /**
     * 把 Spring 的 {@link MultipartFile} 落到临时文件（旧栈对应"COS 解析出的临时文件"）。
     *
     * @param file Spring 部件
     * @return 临时文件；搬运失败返回 null（调用方跳过该部件并已告警）
     */
    private static File toTempFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        String suffix = "";
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                suffix = name.substring(dot);
            }
        }
        try {
            File temp = File.createTempFile(TEMP_PREFIX, suffix);
            file.transferTo(temp);
            return temp;
        } catch (IOException | RuntimeException e) {
            // 旧栈 COS 解析失败同样是异常路径；这里只记录并跳过，避免吞掉整请求的语义
            log.warn("Eova Web 层：上传部件搬运到临时文件失败（字段={}，原名={}）：{}", name, name, e.toString());
            return null;
        }
    }
}
