/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.json;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.json.MixedJsonFactory} 的等价接缝（**标识面**）。
 *
 * <p>ported from: com.jfinal.json.MixedJsonFactory（jfinal 5.2.6 制品）
 *
 * <p><b>为什么只保留 {@code me()}：</b>旧 {@code EovaConfig.configConstant} 调
 * {@code me.setJsonFactory(MixedJsonFactory.me())} 只是把工厂装进 Constants；
 * EOVA 从不直接调用工厂的 {@code getJson()}（序列化全部走 {@code JsonKit}/{@code Json.getJson()}，
 * 已由 {@code LegacyJsonKit} 承担，见 R59 的修复）。故本接缝只提供单例标识
 * （{@code setJsonFactory} 记录类名即可），<b>不</b>在此重建一套 JSON 工厂 —— 那会形成第二套序列化路径。</p>
 */
public class LegacyMixedJsonFactory {

    private static final LegacyMixedJsonFactory me = new LegacyMixedJsonFactory();

    /**
     * 取单例。
     *
     * @return 单例
     */
    public static LegacyMixedJsonFactory me() {
        return me;
    }

}
