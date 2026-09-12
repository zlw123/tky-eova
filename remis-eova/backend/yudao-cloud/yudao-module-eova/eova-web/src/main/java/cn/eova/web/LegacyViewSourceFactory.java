/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.File;

import com.jfinal.template.source.FileSource;
import com.jfinal.template.source.ISource;
import com.jfinal.template.source.ISourceFactory;

/**
 * 宿主模板源工厂：**对外契约等价** {@code cn.eova.ext.jfinal.EovaRenderSourceFactory}
 * （见 eova-core 的同名 port），只把"资产从哪来"换成新栈的落点。
 *
 * <p><b>旧实现（逐字节）</b>：</p>
 * <pre>
 * if (fileName.startsWith("/eova")) return new ClassPathSource(null, "/webapp" + fileName, encoding);
 * else                              return new FileSource(baseTemplatePath, fileName, encoding);
 * </pre>
 *
 * <p><b>旧栈为什么有两个根（实证，不是推断）</b>：旧部署里
 * ① {@code eova-meta-view-*.jar} 把 <b>183 个</b> {@code webapp/eova/**}（渲染模板）打进 classpath；
 * ② undertow 的 webroot 是 {@code <runDir>/webapp}，里面装的是 {@code _eova/**}、{@code _view/**}、
 * {@code _component/**} 等<b>被 include 的片段</b>（旧 demo 实测：{@code /_eova/include.html} 从
 * 这里解析，而 {@code /eova/_view/index/login.html} 从 classpath 解析）。</p>
 *
 * <p><b>新栈为什么塌缩成一个根</b>：移植后的资产树 {@code remis-eova/front/remis-eova-ui/src/legacy}
 * 顶层同时含 {@code eova/**} 与 {@code _eova/**}（157 个文件，逐字来自旧栈两处）——即它<b>就是</b>
 * 合并后的 web 根。故本工厂把两条分支都按"相对该根解析"处理：
 * {@code /eova/x ⇒ <root>/eova/x}、{@code /_eova/x ⇒ <root>/_eova/x}，与旧栈的解析结果<b>逐一相同</b>。</p>
 *
 * <p><b>踩过的坑（实测）</b>：直接把根设成 {@code meta-eova/eova/view/src/main/resources} 并用
 * {@code /eova/... → /webapp + fileName} 去拼，登录页能找到，但 {@code #include("/_eova/include.html")}
 * 会去找 {@code <root>/_eova/include.html}（少一层 {@code webapp}）⇒ 500。根必须选"两棵树合并"
 * 的那一层。</p>
 */
public class LegacyViewSourceFactory implements ISourceFactory {

    /** web 根目录（顶层同时含 {@code eova/} 与 {@code _eova/}） */
    private final File webRoot;

    /**
     * 构造。
     *
     * @param webRoot web 根目录（顶层含 {@code eova/} 与 {@code _eova/}）
     */
    public LegacyViewSourceFactory(File webRoot) {
        this.webRoot = webRoot;
    }

    /**
     * 取模板源：统一相对 web 根解析（旧栈两条分支的解析结果在合并根下等价）。
     *
     * @param baseTemplatePath enjoy 传进来的基准路径（本工厂不用，保留签名）
     * @param fileName         模板名（形如 {@code /eova/_view/index/login.html}）
     * @param encoding         编码
     * @return 模板源
     */
    @Override
    public ISource getSource(String baseTemplatePath, String fileName, String encoding) {
        File base = webRoot != null ? webRoot : new File(baseTemplatePath);
        return new FileSource(base.getAbsolutePath(), fileName, encoding);
    }
}
