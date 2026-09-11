/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.render.IRenderFactory} 的等价接缝（<b>W1 子集</b>）。
 *
 * <p>{@code ported from} {@code com.jfinal.render.IRenderFactory}（jfinal 5.2.6）。</p>
 *
 * <p><b>为什么 W1 就需要它：</b>它并非只有"渲染"才用到 ——
 * {@code Controller.toInt/toLong/toBoolean/toDate} 在解析失败时构造的
 * {@link cn.eova.compat.jfinal.core.LegacyActionException} <b>携带一个错误渲染</b>
 * （旧实现：{@code new ActionException(400, renderManager.getRenderFactory().getErrorRender(400), msg)}），
 * 而 EOVA 对 {@code getInt}/{@code getParaToInt} 的调用合计 45 处。
 * 故错误渲染必须在本波就位，否则参数族无法忠实 port。</p>
 *
 * <p><b>本波只声明已被调用的方法</b>（口径与既有接缝一致）：
 * {@code getErrorRender(int)}。W2 再补 {@code getRender(String)}（视图）与
 * {@code getRedirectRender(String)} 等 —— 它们分别服务 {@code render(String)} 与
 * {@code redirect(String)}，属 W2 的 {@code render*} 族。</p>
 */
public interface LegacyRenderFactory {

    /**
     * 取错误渲染（旧实现按状态码返回对应错误页渲染）。
     *
     * @param errorCode HTTP 状态码（如 400 / 404 / 500）
     * @return 错误渲染
     */
    LegacyRender getErrorRender(int errorCode);

    /**
     * 取错误渲染（指定错误页视图）。
     *
     * @param errorCode HTTP 状态码
     * @param view      错误页视图
     * @return 错误渲染
     */
    LegacyRender getErrorRender(int errorCode, String view);

    /**
     * 取视图渲染（对应旧 {@code getRender(view)}）。
     *
     * @param view 视图名
     * @return 渲染
     */
    LegacyRender getRender(String view);

    /**
     * 取模板渲染（对应旧 {@code getTemplateRender(view)}）。
     *
     * @param view 视图名
     * @return 渲染
     */
    LegacyRender getTemplateRender(String view);

    /**
     * 取"输出全部请求属性"的 JSON 渲染。
     *
     * @return 渲染
     */
    LegacyRender getJsonRender();

    /**
     * 取输出指定请求属性的 JSON 渲染。
     *
     * @param attrs 属性名数组
     * @return 渲染
     */
    LegacyRender getJsonRender(String[] attrs);

    /**
     * 取输出给定 JSON 文本的渲染。
     *
     * @param jsonText JSON 文本
     * @return 渲染
     */
    LegacyRender getJsonRender(String jsonText);

    /**
     * 取输出单个对象的 JSON 渲染。
     *
     * @param object 对象
     * @return 渲染
     */
    LegacyRender getJsonRender(Object object);

    /**
     * 取输出"名 - 对象"单键 JSON 的渲染。
     *
     * @param attr   键名
     * @param object 值
     * @return 渲染
     */
    LegacyRender getJsonRender(String attr, Object object);

    /**
     * 取文本渲染（对应旧 {@code getTextRender(text)}）。
     *
     * @param text 文本
     * @return 渲染
     */
    LegacyRender getTextRender(String text);

    /**
     * 取 HTML 渲染（对应旧 {@code getHtmlRender(text)}）。
     *
     * @param text HTML 文本
     * @return 渲染
     */
    LegacyRender getHtmlRender(String text);

    /**
     * 取空渲染（对应旧 {@code getNullRender()}）。
     *
     * @return 渲染
     */
    LegacyRender getNullRender();

    /**
     * 取跳转渲染（对应旧 {@code getRedirectRender(url)}）。
     *
     * @param url 目标 URL
     * @return 渲染
     */
    LegacyRender getRedirectRender(String url);

    /**
     * 取跳转渲染。
     *
     * @param url             目标 URL
     * @param withQueryString 是否附带原查询串
     * @return 渲染
     */
    LegacyRender getRedirectRender(String url, boolean withQueryString);

    /**
     * 取验证码渲染（对应旧 {@code IRenderFactory.getCaptchaRender()}）。
     *
     * <p>第 81 轮为 port {@code Controller.renderCaptcha()} 而补：旧 {@code Controller}
     * 的实现就是 {@code render = renderManager.getRenderFactory().getCaptchaRender()}。</p>
     *
     * @return 渲染
     */
    LegacyRender getCaptchaRender();

}
