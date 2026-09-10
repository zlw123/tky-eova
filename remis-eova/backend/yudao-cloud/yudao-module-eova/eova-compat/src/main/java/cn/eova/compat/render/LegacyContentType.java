/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

import java.util.HashMap;
import java.util.Map;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.render.ContentType} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.render.ContentType}（jfinal 5.2.6）。</p>
 *
 * <p><b>常量与取值逐条取自旧字节码：</b>
 * TEXT={@code text/plain}、HTML={@code text/html}、XML={@code text/xml}、
 * JSON={@code application/json}、JAVASCRIPT={@code application/javascript}、
 * EVENTSTREAM={@code text/event-stream}。
 * 旧枚举<b>只有这 6 个常量</b>（{@code $VALUES} 长度 6）；{@code js} 只是
 * {@code parse} 映射里指向 JAVASCRIPT 的键，<b>不是</b>枚举常量 —— 不得新增。</p>
 *
 * <p>{@link #parse(String)} 的映射键共 16 条（小写别名 8 条 + 枚举名 8 条），
 * 逐条照抄旧实现；键<b>区分大小写</b>，未命中返回 {@code null}。</p>
 */
public enum LegacyContentType {

    TEXT("text/plain"),
    HTML("text/html"),
    XML("text/xml"),
    JSON("application/json"),
    JAVASCRIPT("application/javascript"),
    EVENTSTREAM("text/event-stream");

    private final String value;

    private static final Map<String, LegacyContentType> mapping = initMapping();

    /**
     * 构造枚举常量。
     *
     * @param value Content-Type 取值
     */
    private LegacyContentType(String value) {
        this.value = value;
    }

    /**
     * 取 Content-Type 取值。
     *
     * @return MediaType 字符串
     */
    public String value() {
        return value;
    }

    /**
     * 与 {@link #value()} 同结果（旧实现覆写了 toString）。
     *
     * @return MediaType 字符串
     */
    @Override
    public String toString() {
        return value;
    }

    /**
     * 建立解析映射（8 条小写别名 + 8 条枚举名，逐条照抄旧实现）。
     *
     * @return 映射表
     */
    private static Map<String, LegacyContentType> initMapping() {
        Map<String, LegacyContentType> map = new HashMap<String, LegacyContentType>();
        map.put("text", TEXT);
        map.put("plain", TEXT);
        map.put("html", HTML);
        map.put("xml", XML);
        map.put("json", JSON);
        map.put("javascript", JAVASCRIPT);
        map.put("js", JAVASCRIPT);
        map.put("eventStream", EVENTSTREAM);
        map.put("TEXT", TEXT);
        map.put("PLAIN", TEXT);
        map.put("HTML", HTML);
        map.put("XML", XML);
        map.put("JSON", JSON);
        map.put("JAVASCRIPT", JAVASCRIPT);
        map.put("JS", JAVASCRIPT);
        map.put("EVENTSTREAM", EVENTSTREAM);
        return map;
    }

    /**
     * 按名称或别名解析；未命中返回 {@code null}（旧实现无兜底）。
     *
     * @param name 名称或别名
     * @return 枚举常量或 null
     */
    public static LegacyContentType parse(String name) {
        return mapping.get(name);
    }

}
