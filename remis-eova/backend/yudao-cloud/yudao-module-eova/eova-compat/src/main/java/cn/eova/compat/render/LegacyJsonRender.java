/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import cn.eova.compat.jfinal.kit.LegacyKv;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.render.JsonRender} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.render.JsonRender}（jfinal 5.2.6）。
 * EOVA 的 {@code renderJson} 系列调用共 <b>103 处</b>，是本族里用得最多的。</p>
 *
 * <p><b>语义逐条取自旧字节码：</b>
 * <ol>
 *   <li>{@link #render()}：{@code jsonText == null} 时先 {@link #buildJsonText()}；
 *       设 contentType（{@code forIE} 时用 {@code text/html; charset=}，否则
 *       {@code application/json; charset=}，后缀为当前编码）；
 *       {@code getWriter().write(jsonText); flush();}；
 *       仅 {@code IOException} 时 {@code close(writer)}，随后一律包成
 *       {@link LegacyRenderException}。<b>成功路径不关闭 writer</b>（与
 *       {@link LegacyTextRender} 同一既有形态）。</li>
 *   <li>{@link #buildJsonText()}：<b>两条分支</b> ——
 *       {@code attrs != null} 时按 {@code attrs} 逐个取
 *       {@code request.getAttribute(name)}；否则遍历
 *       {@code request.getAttributeNames()}，<b>跳过 {@link #EXCLUDED_ATTRS} 中的名字</b>。
 *       最后 {@code jsonText = JsonKit.toJson(map)}（此处走 {@link LegacyJsonKit}，
 *       语义与旧 {@code JsonKit.toJson} 一致 —— R37/R42）。</li>
 *   <li>{@link #EXCLUDED_ATTRS} 的<b>初值逐字取自旧字节码</b>（6 项）。</li>
 * </ol>
 */
public class LegacyJsonRender extends LegacyRender {

    /** 默认排除的请求属性名（初值逐字取自旧字节码） */
    public static final Set<String> EXCLUDED_ATTRS = new HashSet<>();

    static {
        EXCLUDED_ATTRS.add("javax.servlet.request.ssl_session");
        EXCLUDED_ATTRS.add("javax.servlet.request.ssl_session_id");
        EXCLUDED_ATTRS.add("javax.servlet.request.ssl_session_mgr");
        EXCLUDED_ATTRS.add("javax.servlet.request.key_size");
        EXCLUDED_ATTRS.add("javax.servlet.request.cipher_suite");
        EXCLUDED_ATTRS.add("_res");
    }

    /** contentType 前缀（后缀拼当前编码） */
    protected static final String CONTENT_TYPE = "application/json; charset=";

    /** 兼容 IE 时使用的 contentType 前缀 */
    protected static final String CONTENT_TYPE_FOR_IE = "text/html; charset=";

    /** 是否按 IE 兼容输出 */
    protected boolean forIE;

    /** 待写出的 JSON 文本；为 null 时按需构建 */
    protected String jsonText;

    /** 指定要输出的请求属性名；为 null 表示输出全部（排除项除外） */
    protected String[] attrs;

    /**
     * 增加排除的请求属性名（旧实现的静态配置口，宿主启动时调用）。
     *
     * @param attrs 属性名
     */
    public static void addExcludedAttrs(String... attrs) {
        for (String a : attrs) {
            EXCLUDED_ATTRS.add(a);
        }
    }

    /**
     * 移除排除的请求属性名。
     *
     * @param attrs 属性名
     */
    public static void removeExcludedAttrs(String... attrs) {
        for (String a : attrs) {
            EXCLUDED_ATTRS.remove(a);
        }
    }

    /**
     * 清空排除集合。
     */
    public static void clearExcludedAttrs() {
        EXCLUDED_ATTRS.clear();
    }

    /**
     * 置为 IE 兼容输出。
     *
     * @return this
     */
    public LegacyJsonRender forIE() {
        this.forIE = true;
        return this;
    }

    /** 无参构造（输出全部请求属性） */
    public LegacyJsonRender() {
    }

    /**
     * 输出指定请求属性。
     *
     * @param attrs 属性名数组
     */
    public LegacyJsonRender(String[] attrs) {
        this.attrs = attrs;
    }

    /**
     * 直接输出给定 JSON 文本（不再构建）。
     *
     * @param jsonText JSON 文本
     */
    public LegacyJsonRender(String jsonText) {
        this.jsonText = jsonText;
    }

    /**
     * 输出单个对象。
     *
     * @param object 对象
     */
    public LegacyJsonRender(Object object) {
        this.jsonText = LegacyJsonKit.toJson(object);
    }

    /**
     * 输出"名 - 对象"的单键 JSON。
     *
     * <p>旧实现经 jfinal {@code JsonKit.toJson(String, Object)} 的便捷重载，
     * 其语义是"先包成单键 {@code Kv} 再序列化"。本接缝的 {@code LegacyJsonKit}
     * 只提供 {@code toJson(Object)}（全树普查：EOVA 未使用该便捷重载），
     * 故此处显式包成单键 {@link LegacyKv} —— <b>结果等价</b>，且不为此在
     * {@code LegacyJsonKit} 上新增未被使用的重载。</p>
     *
     * @param attr   键名
     * @param object 值
     */
    public LegacyJsonRender(String attr, Object object) {
        this.attrs = new String[]{attr};
        this.jsonText = LegacyJsonKit.toJson(LegacyKv.create().set(attr, object));
    }

    /**
     * 写出 JSON。
     */
    @Override
    public void render() {
        if (jsonText == null) {
            buildJsonText();
        }
        PrintWriter writer = null;
        try {
            response.setContentType(forIE ? CONTENT_TYPE_FOR_IE + getEncoding()
                    : CONTENT_TYPE + getEncoding());
            writer = response.getWriter();
            writer.write(jsonText);
            writer.flush();
        } catch (Exception e) {
            if (e instanceof IOException) {
                close(writer);
            }
            throw new LegacyRenderException(e);
        }
    }

    /**
     * 由请求属性构建 JSON 文本（两条分支，见类注释）。
     */
    protected void buildJsonText() {
        Map<Object, Object> map = new HashMap<>();
        if (attrs != null) {
            for (String attr : attrs) {
                map.put(attr, request.getAttribute(attr));
            }
        } else {
            Enumeration<String> e = request.getAttributeNames();
            while (e.hasMoreElements()) {
                String name = e.nextElement();
                if (EXCLUDED_ATTRS.contains(name)) {
                    continue;
                }
                map.put(name, request.getAttribute(name));
            }
        }
        jsonText = LegacyJsonKit.toJson(map);
    }

    /**
     * 取指定的属性名数组。
     *
     * @return 属性名数组；未指定时为 null
     */
    public String[] getAttrs() {
        return attrs;
    }

    /**
     * 取 JSON 文本（可能为 null，表示尚未构建）。
     *
     * @return JSON 文本
     */
    public String getJsonText() {
        return jsonText;
    }

    /**
     * 是否 IE 兼容输出。
     *
     * @return forIE
     */
    public Boolean getForIE() {
        return forIE;
    }

}
