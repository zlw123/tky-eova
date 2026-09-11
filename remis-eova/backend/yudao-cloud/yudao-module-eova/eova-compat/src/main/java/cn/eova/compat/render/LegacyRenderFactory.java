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

}
