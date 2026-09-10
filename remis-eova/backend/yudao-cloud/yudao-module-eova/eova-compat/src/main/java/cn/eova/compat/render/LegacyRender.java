/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

import cn.eova.compat.jfinal.kit.LegacyLogKit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.render.Render} 的等价接缝（阶段 1 `S-RENDER` 切片）。
 *
 * <p><b>制品追溯：</b>
 * <ul>
 *   <li>{@code ported from} {@code com.jfinal.render.Render}</li>
 *   <li>旧制品：{@code com.jfinal:jfinal:5.2.6}，
 *       sha256 {@code 6afdb3b059624f8f41d07a6b6d9f4c889c8e77791f79e4f954af5cfee97d757b}</li>
 *   <li>来源：旧 demo 运行期 classpath（{@code meta-eova/eova} 依赖 jfinal 5.2.6）</li>
 * </ul>
 * 本类<b>不是</b> EOVA 旧树单元，而是按 §2.1「把宿主关进接缝」原则为
 * {@code cn.eova.common.render.*} 建立的宿主等价物；语义逐条取自旧 classpath 上
 * jfinal 5.2.6 的字节码，<b>不得按设计重写</b>。
 *
 * <p><b>为什么必须建接缝（而不是判 R 类重实现）：</b>
 * {@code cn.eova.common.render} 下 12 个类的<b>源码都在旧树里</b>，缺的只是基类
 * {@code Render}。补出基类后这 12 个类即可「逐字节 port + 少量已声明替换」，
 * 判据是<b>导出字节流一致</b>；若改判 R 类黑盒重实现，则丢掉逐行对应关系且成本更高。
 *
 * <p><b>底座替换（相对旧 classpath）：</b>
 * <ul>
 *   <li>{@code javax.servlet.http.*} → {@code jakarta.servlet.http.*}
 *       （Spring Boot 3 强制 jakarta，{@code javax.servlet} 不在新栈 classpath 上；
 *        Servlet 5→6 该次迁移主要是命名空间，API 形状不变）</li>
 *   <li>{@code com.jfinal.log.Log} → {@code cn.eova.compat.jfinal.kit.LegacyLogKit}
 *       （R37：jfinal 的日志门面不在 enjoy 制品中）</li>
 * </ul>
 *
 * <p><b>静态状态说明：</b>旧实现里 {@code encoding} / {@code devMode} 由
 * {@code Render.init(encoding, devMode)} 从 JFinalConfig 生命周期写入。新栈没有
 * JFinal 生命周期，故 {@link #init(String, boolean)} 改为 public，由 Spring Boot 侧的
 * 配置接缝调用；未调用时取旧实现的静态初值 {@code "UTF-8"} / {@code false}。
 */
public abstract class LegacyRender {

    protected String view;

    protected HttpServletRequest request;

    protected HttpServletResponse response;

    /** 旧实现静态初值为 "UTF-8"（见 jfinal 5.2.6 Render 的 static 初始化块） */
    private static String encoding = "UTF-8";

    /** 旧实现静态初值为 false */
    private static boolean devMode = false;

    /**
     * 由宿主配置接缝写入编码与 devMode（旧实现为包级方法，仅 JFinalConfig 可调）。
     *
     * @param encoding 响应编码
     * @param devMode  开发模式标记
     */
    public static void init(String encoding, boolean devMode) {
        LegacyRender.encoding = encoding;
        LegacyRender.devMode = devMode;
    }

    /**
     * 取响应编码（旧实现直接返回静态字段，无 null 兜底）。
     *
     * @return 当前编码
     */
    public static String getEncoding() {
        return encoding;
    }

    /**
     * 取开发模式标记。
     *
     * @return devMode
     */
    public static boolean getDevMode() {
        return devMode;
    }

    /**
     * 绑定请求响应（旧实现不做任何校验）。
     *
     * @param request  请求
     * @param response 响应
     * @return this
     */
    public LegacyRender setContext(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
        return this;
    }

    /**
     * 绑定请求响应，并在 view 非绝对路径（首字符非 '/'）时补上前缀。
     *
     * <p>旧实现分支逐条保留：仅当 {@code view != null && view.length() > 0
     * && view.charAt(0) != '/'} 时拼接。
     *
     * @param request    请求
     * @param response   响应
     * @param viewPrefix 视图前缀
     * @return this
     */
    public LegacyRender setContext(HttpServletRequest request, HttpServletResponse response, String viewPrefix) {
        this.request = request;
        this.response = response;
        if (view != null && view.length() > 0 && view.charAt(0) != '/') {
            this.view = viewPrefix + view;
        }
        return this;
    }

    /**
     * 取视图名。
     *
     * @return view
     */
    public String getView() {
        return view;
    }

    /**
     * 设置视图名。
     *
     * @param view 视图名
     */
    public void setView(String view) {
        this.view = view;
    }

    /** 执行渲染（子类实现） */
    public abstract void render();

    /**
     * 关闭资源，吞掉异常但记录日志（旧实现语义：非 null 才关，异常不外抛）。
     *
     * @param c 待关闭资源
     */
    protected void close(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                // 旧实现用 Log.getLog(getClass()) 记录；新栈走 LegacyLogKit（R37）
                LegacyLogKit.error(e.getMessage(), e);
            }
        }
    }

}
