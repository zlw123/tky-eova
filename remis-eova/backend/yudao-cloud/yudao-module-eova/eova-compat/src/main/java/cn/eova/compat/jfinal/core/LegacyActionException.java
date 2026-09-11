/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.core;

import cn.eova.compat.render.LegacyRender;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.core.ActionException} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.core.ActionException}（jfinal 5.2.6）。</p>
 *
 * <p><b>契约（EOVA 侧可见）：</b>{@code Controller.toInt/toLong/toBoolean/toDate} 在
 * 参数解析失败时抛出本异常，携带
 * {@code errorCode = 400} 与一条固定格式的消息
 * （如 {@code Can not parse the parameter "abc" to Integer value.}），
 * 以及一个错误渲染。EOVA 的 {@code ExceptionInterceptor} 会捕获它并按 {@code errorCode} 处理。</p>
 *
 * <p>构造器与旧实现一一对应：{@code (int, Render, String)}、{@code (int, Render, String, Throwable)}、
 * {@code (int, Render)}；{@code getErrorCode()} / {@code getErrorRender()} 为读取口。</p>
 */
public class LegacyActionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int errorCode;

    private final LegacyRender errorRender;

    /**
     * 三参构造。
     *
     * @param errorCode   HTTP 状态码
     * @param errorRender 错误渲染
     * @param message     消息
     */
    public LegacyActionException(int errorCode, LegacyRender errorRender, String message) {
        super(message);
        this.errorCode = errorCode;
        this.errorRender = errorRender;
    }

    /**
     * 四参构造。
     *
     * @param errorCode   HTTP 状态码
     * @param errorRender 错误渲染
     * @param message     消息
     * @param cause       原因
     */
    public LegacyActionException(int errorCode, LegacyRender errorRender, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorRender = errorRender;
    }

    /**
     * 两参构造（无消息）。
     *
     * @param errorCode   HTTP 状态码
     * @param errorRender 错误渲染
     */
    public LegacyActionException(int errorCode, LegacyRender errorRender) {
        super();
        this.errorCode = errorCode;
        this.errorRender = errorRender;
    }

    /**
     * 取 HTTP 状态码。
     *
     * @return 状态码
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * 取错误渲染。
     *
     * @return 错误渲染
     */
    public LegacyRender getErrorRender() {
        return errorRender;
    }

}
