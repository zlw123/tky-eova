/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.template;

import com.jfinal.kit.Kv;
import com.jfinal.kit.PathKit;
import com.jfinal.template.Engine;
import com.jfinal.template.source.FileSourceFactory;

import java.util.Map;

/**
 * 基于 Enjoy 的模板渲染接缝实现（阶段 1 使用）。
 *
 * <p>本实现把 SP2 实证的四个必需条件固化为代码 —— 缺任何一个都会导致
 * <b>渲染为空或静默失效</b>，而非报错：
 *
 * <ol>
 *   <li><b>引擎制品</b>：独立 {@code com.jfinal:enjoy:5.3.0}。
 *       5.3.0 是下限：只有它同时提供 {@code RenderOrElseDirective}（首页模板使用）
 *       与 {@code com.jfinal.kit.PathKit}；5.0.3–5.2.5 均缺失。</li>
 *   <li><b>{@code setBaseTemplatePath} + {@link FileSourceFactory}</b>：
 *       指向合并后的 web 根（{@code eova/} 与 {@code _eova/} 合并）。
 *       不能用 {@code ClassPathSourceFactory} —— 模板路径是文件系统根路径。</li>
 *   <li><b>{@link PathKit#setWebRootPath}</b>：<b>最隐蔽的一条</b>。
 *       {@code RenderOrElseDirective} 内部用它构造 {@code File} 并做
 *       {@code File.exists()} 判定；未设置时该指令永远走 else 分支渲染为空，
 *       <b>且不抛异常</b>。Spring Boot 没有 web 容器会初始化这个 JFinal 静态全局，
 *       因此必须在本类初始化时显式设置。</li>
 *   <li><b>共享方法注册</b>：{@code base.html} 调用 {@code getUIConf()} 与
 *       {@code conf(key)}，不注册则公共依赖块无法渲染。</li>
 * </ol>
 *
 * <p>ported from: cn.eova.common.render.RenderUtil（间接）
 * <br>旧 FQCN: cn.eova.common.render.RenderUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>说明：本类为宿主适配重写，非逐行 port；渲染语义由 golden 字节比对保证。
 */
public class EnjoyTemplateRenderService implements TemplateRenderService {

    /** Enjoy 引擎实例（线程安全，可复用） */
    private final Engine engine;

    /**
     * 构造渲染服务。
     *
     * <p>同一 {@code webRoot} 复用同一个 Enjoy 引擎实例（{@link Engine#createIfAbsent}）：
     * Enjoy 对命名引擎有全局注册表，重复 {@code Engine.create(name)} 会抛
     * {@code Engine already exists}。因此 <b>共享方法以首次构造时注册的为准</b>；
     * 生产环境应按 webRoot 保持单例（Spring 单例 Bean 即可）。
     *
     * @param webRoot       合并 web 根目录的绝对路径（同时含 {@code eova/} 与 {@code _eova/}）
     * @param sharedMethods 模板共享方法对象；其 public 方法将作为模板函数可用
     */
    public EnjoyTemplateRenderService(String webRoot, Object... sharedMethods) {
        // 关键：设置 JFinal 静态全局，否则 #renderOrElse 静默渲染为空
        PathKit.setWebRootPath(webRoot);

        // 引擎名由 webRoot 派生，保证「同根复用、异根隔离」
        String engineName = "eova-compat-" + Integer.toHexString(webRoot.hashCode());
        final Object[] methods = sharedMethods;
        this.engine = Engine.createIfAbsent(engineName, e -> {
            e.setDevMode(false)
                    .setBaseTemplatePath(webRoot)
                    .setSourceFactory(new FileSourceFactory());
            if (methods != null) {
                for (Object sm : methods) {
                    if (sm != null) {
                        e.addSharedMethod(sm);
                    }
                }
            }
        });
    }

    /** 渲染模板：把作用域装箱为 Enjoy 的 Kv 后交给引擎 */
    @Override
    public String render(String templatePath, Map<String, Object> scope) {
        Kv kv = Kv.create();
        if (scope != null) {
            kv.set(scope);
        }
        return engine.getTemplate(templatePath).renderToString(kv);
    }
}
