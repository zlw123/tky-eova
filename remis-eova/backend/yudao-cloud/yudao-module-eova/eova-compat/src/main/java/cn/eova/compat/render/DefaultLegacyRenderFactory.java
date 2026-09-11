/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

/**
 * {@link LegacyRenderFactory} 的默认实现 —— 把四类渲染串起来。
 *
 * <p><b>为什么需要它：</b>{@code LegacyRenderFactory} 是<b>注入点</b>（宿主装配），
 * 但如果没有一个随本模块发布的默认实现，那么 {@code LegacyController} 的
 * {@code render*} 族就只能靠测试替身才能跑 —— 接缝"可注入但不可用"。
 * 本类提供与旧 jfinal {@code RenderFactory} 默认行为一致的实现。</p>
 *
 * <p><b>逐条对齐旧 {@code RenderFactory}（逐字节码确认）：</b>
 * <ul>
 *   <li>{@code getRender(view)} 与 {@code getTemplateRender(view)}
 *       <b>都是</b> {@code new TemplateRender(view)} —— 无后缀分派、无扩展名拼接；</li>
 *   <li>{@code getJsonRender(String jsonText)} → {@code new JsonRender(jsonText)}；
 *       {@code getJsonRender()} → {@code new JsonRender()}；
 *       {@code getJsonRender(String[] attrs)} → {@code new JsonRender(attrs)}；
 *       {@code getJsonRender(Object)} → {@code new JsonRender(object)}；
 *       {@code getJsonRender(String, Object)} → {@code new JsonRender(attr, object)}；</li>
 *   <li>{@code getErrorRender(code[,view])} → {@code new ErrorRender(code[,view])}；</li>
 *   <li>{@code getRedirectRender(url[,withQueryString])}
 *       → {@code new RedirectRender(url[,withQueryString])}。</li>
 * </ul>
 * 视图前缀**不在这里**处理 —— 由 {@link LegacyRender#setContext} 的三参重载负责。</p>
 */
public class DefaultLegacyRenderFactory implements LegacyRenderFactory {

    @Override
    public LegacyRender getErrorRender(int errorCode) {
        return new LegacyErrorRender(errorCode);
    }

    @Override
    public LegacyRender getErrorRender(int errorCode, String view) {
        return new LegacyErrorRender(errorCode, view);
    }

    @Override
    public LegacyRender getRender(String view) {
        return new LegacyTemplateRender(view);
    }

    @Override
    public LegacyRender getTemplateRender(String view) {
        return new LegacyTemplateRender(view);
    }

    @Override
    public LegacyRender getJsonRender() {
        return new LegacyJsonRender();
    }

    @Override
    public LegacyRender getJsonRender(String[] attrs) {
        return new LegacyJsonRender(attrs);
    }

    @Override
    public LegacyRender getJsonRender(String jsonText) {
        return new LegacyJsonRender(jsonText);
    }

    @Override
    public LegacyRender getJsonRender(Object object) {
        return new LegacyJsonRender(object);
    }

    @Override
    public LegacyRender getJsonRender(String attr, Object object) {
        return new LegacyJsonRender(attr, object);
    }

    @Override
    public LegacyRender getRedirectRender(String url) {
        return new LegacyRedirectRender(url);
    }

    @Override
    public LegacyRender getRedirectRender(String url, boolean withQueryString) {
        return new LegacyRedirectRender(url, withQueryString);
    }

}
