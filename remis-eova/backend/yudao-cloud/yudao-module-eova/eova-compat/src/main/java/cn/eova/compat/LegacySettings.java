/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat;

/**
 * 旧宿主全局设置的兼容接缝。
 *
 * <p><b>存在理由：</b>旧系统有一批"全局静态设置"由 JFinal 宿主提供，
 * 例如 {@code JFinal.me().getConstants().getEncoding()}、{@code PathKit.getWebRootPath()}。
 * 新宿主（Spring Boot）没有对应物，且这些值无法通过构造参数传递
 * （调用方是静态工具方法）。故在此集中提供，作为显式的宿主适配点。
 *
 * <p><b>等价性依据：</b>默认值必须与 JFinal 的默认值一致 ——
 * {@code com.jfinal.config.Constants} 的 {@code encoding} 字段默认 {@code "UTF-8"}
 * （jfinal 5.2.6 字节码实测：{@code ldc "UTF-8"; putfield encoding}）。
 *
 * <p><b>不得在此加入业务语义。</b>本类只承载"旧宿主全局值"的等价替身。
 */
public final class LegacySettings {

    /** 文本读写默认编码；与 JFinal Constants 默认值一致 */
    private static volatile String encoding = "UTF-8";

    private LegacySettings() {
    }

    /**
     * 取文本读写默认编码。
     *
     * <p>等价于旧实现的 {@code JFinal.me().getConstants().getEncoding()}。
     */
    public static String getEncoding() {
        return encoding;
    }

    /**
     * 设置文本读写默认编码。
     *
     * @param value 编码名；为 null 或空串时忽略并保持原值
     */
    public static void setEncoding(String value) {
        if (value != null && !value.isEmpty()) {
            encoding = value;
        }
    }
}
