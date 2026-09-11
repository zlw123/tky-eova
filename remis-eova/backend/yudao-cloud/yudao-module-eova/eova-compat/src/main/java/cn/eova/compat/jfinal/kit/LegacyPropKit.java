/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.util.concurrent.ConcurrentHashMap;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.kit.PropKit} 的等价接缝。
 *
 * <p>ported from: com.jfinal.kit.PropKit（jfinal 5.2.6 制品）
 *
 * <p><b>逐条取自旧字节码：</b>
 * <ul>
 *   <li>{@code use(fileName, encoding)}：按文件名<b>缓存</b>（{@code ConcurrentHashMap}）并置为"当前 prop"，
 *       同一文件重复调用返回同一实例。</li>
 *   <li>{@code useFirstFound(String...)}：逐个 {@code use(name, "UTF-8")}，
 *       <b>任一异常即跳过</b>，全部失败才抛 {@code IllegalArgumentException("没有配置文件可被使用")}（消息逐字）。</li>
 *   <li>{@code getProp()}：未装配时抛
 *       {@code IllegalStateException("Load propties file by invoking PropKit.use(String fileName) method first.")}
 *       （消息逐字，含旧实现的拼写错误 {@code propties}）。</li>
 * </ul>
 *
 * <p>EOVA 只用 {@code useFirstFound}（{@code EovaConfig:201} 加载 eova/dev|test|pre|pro|prd.txt），
 * 故 {@code append}/{@code appendIfExists}/{@code setEnvKey} 一族<b>未包含</b>。</p>
 */
public class LegacyPropKit {

    /** 按文件名缓存（旧实现同名同语义） */
    private static final ConcurrentHashMap<String, LegacyProp> cache = new ConcurrentHashMap<>();

    /** 当前 prop（旧实现为静态字段） */
    private static LegacyProp prop;

    private LegacyPropKit() {
    }

    /**
     * 加载 classpath 属性文件（UTF-8）。
     *
     * @param fileName 资源名
     * @return 属性对象（同文件返回同一实例）
     */
    public static LegacyProp use(String fileName) {
        return use(fileName, "UTF-8");
    }

    /**
     * 加载 classpath 属性文件。
     *
     * @param fileName 资源名
     * @param encoding 编码
     * @return 属性对象（同文件返回同一实例）
     */
    public static LegacyProp use(String fileName, String encoding) {
        LegacyProp ret = cache.get(fileName);
        if (ret == null) {
            ret = new LegacyProp(fileName, encoding);
            cache.put(fileName, ret);
            prop = ret;
        }
        return ret;
    }

    /**
     * 依次尝试多个文件（旧实现：任一异常即跳过；全失败抛异常）。
     *
     * @param fileNames 候选资源名
     * @return 第一个可加载的属性对象
     */
    public static LegacyProp useFirstFound(String... fileNames) {
        for (String name : fileNames) {
            try {
                return use(name, "UTF-8");
            } catch (Exception e) {
                // 旧字节码：捕获 Exception 后继续尝试下一个
            }
        }
        throw new IllegalArgumentException("没有配置文件可被使用");
    }

    /**
     * 取当前属性对象。
     *
     * @return 当前属性对象
     */
    public static LegacyProp getProp() {
        if (prop == null) {
            throw new IllegalStateException(
                    "Load propties file by invoking PropKit.use(String fileName) method first.");
        }
        return prop;
    }

    /** 清空当前与缓存（旧实现 {@code clear()}） */
    public static void clear() {
        prop = null;
        cache.clear();
    }

}
