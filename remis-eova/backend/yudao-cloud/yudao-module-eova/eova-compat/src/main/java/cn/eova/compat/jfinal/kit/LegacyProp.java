/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.kit.Prop} 的等价接缝。
 *
 * <p>ported from: com.jfinal.kit.Prop（jfinal 5.2.6 制品）
 *
 * <p><b>逐条取自旧字节码：</b>
 * <ul>
 *   <li>资源来自 {@code getClassLoader().getResourceAsStream(name)}；找不到时抛
 *       {@code IllegalArgumentException("Properties file not found in classpath: " + name)}（消息逐字）。</li>
 *   <li>默认编码 {@code UTF-8}（构造器把 encoding 传给 {@code Properties.load(Reader)}）。</li>
 *   <li>{@code get(key)} 命中后 {@code trim()}，未命中返回 <b>null</b>；
 *       {@code get(key, defaultValue)} 未命中或空串时返回默认值。</li>
 *   <li>{@code getInt/getLong/getDouble/getBoolean} 解析失败 ⇒ 返回 {@code null}（不抛异常），
 *       带默认值的重载失败时返回默认值。</li>
 * </ul>
 *
 * <p><b>本类明确未包含：</b>{@code append}/{@code appendIfExists} 家族（EOVA 未用；
 * 旧栈 EOVA 只用 {@code PropKit.useFirstFound} + {@code getProperties()}）、
 * {@code Prop(File)} 家族。已包含部分即 {@code EovaConfig.configConstant} 的实际用面。</p>
 */
public class LegacyProp {

    /** 属性表（旧实现同名同语义；EOVA 用 {@code getProperties()} 交给 x.conf） */
    protected Properties properties;

    /**
     * 从 classpath 加载（UTF-8）。
     *
     * @param fileName classpath 资源名
     */
    public LegacyProp(String fileName) {
        this(fileName, "UTF-8");
    }

    /**
     * 从 classpath 按指定编码加载。
     *
     * @param fileName classpath 资源名
     * @param encoding 编码
     */
    public LegacyProp(String fileName, String encoding) {
        Properties p = new Properties();
        try (InputStream is = getClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                throw new IllegalArgumentException("Properties file not found in classpath: " + fileName);
            }
            p.load(new InputStreamReader(is, encoding));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.properties = p;
    }

    /**
     * 空属性表构造（旧实现有，供子类/组合使用）。
     */
    public LegacyProp() {
        this.properties = new Properties();
    }

    /**
     * 取类加载器（旧实现取当前类的 ClassLoader）。
     *
     * @return 类加载器
     */
    private ClassLoader getClassLoader() {
        ClassLoader ret = Thread.currentThread().getContextClassLoader();
        return ret != null ? ret : getClass().getClassLoader();
    }

    /**
     * 取字符串（命中后 trim）。
     *
     * @param key 键
     * @return 值；未命中为 null
     */
    public String get(String key) {
        String v = properties.getProperty(key);
        return v == null ? null : v.trim();
    }

    /**
     * 取字符串（未命中或空串时返回默认值）。
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 值
     */
    public String get(String key, String defaultValue) {
        String v = get(key);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    /**
     * 取整型。
     *
     * @param key 键
     * @return 值；未命中或解析失败为 null
     */
    public Integer getInt(String key) {
        return toInt(get(key), null);
    }

    /**
     * 取整型（带默认值）。
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 值
     */
    public Integer getInt(String key, Integer defaultValue) {
        return toInt(get(key), defaultValue);
    }

    /**
     * 取长整型。
     *
     * @param key 键
     * @return 值；未命中或解析失败为 null
     */
    public Long getLong(String key) {
        return toLong(get(key), null);
    }

    /**
     * 取长整型（带默认值）。
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 值
     */
    public Long getLong(String key, Long defaultValue) {
        return toLong(get(key), defaultValue);
    }

    /**
     * 取布尔（"true" 忽略大小写为真，其余为假；未命中为 null）。
     *
     * @param key 键
     * @return 值
     */
    public Boolean getBoolean(String key) {
        String v = get(key);
        return v == null ? null : Boolean.valueOf(v);
    }

    /**
     * 取布尔（带默认值）。
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 值
     */
    public Boolean getBoolean(String key, Boolean defaultValue) {
        Boolean v = getBoolean(key);
        return v == null ? defaultValue : v;
    }

    /**
     * 是否含键。
     *
     * @param key 键
     * @return 是否包含
     */
    public boolean containsKey(String key) {
        return properties.containsKey(key);
    }

    /**
     * 是否为空表。
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return properties.isEmpty();
    }

    /**
     * 取属性表（EOVA 把它交给 {@code x.conf.addProp}）。
     *
     * @return 内部属性表（旧实现返回同一实例）
     */
    public Properties getProperties() {
        return properties;
    }

    /**
     * 整型解析（失败返回默认值，不抛异常）。
     *
     * @param v   字符串
     * @param def 默认值
     * @return 值
     */
    private static Integer toInt(String v, Integer def) {
        if (v == null || v.isEmpty()) {
            return def;
        }
        try {
            return Integer.valueOf(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 长整型解析（失败返回默认值，不抛异常）。
     *
     * @param v   字符串
     * @param def 默认值
     * @return 值
     */
    private static Long toLong(String v, Long def) {
        if (v == null || v.isEmpty()) {
            return def;
        }
        try {
            return Long.valueOf(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

}
