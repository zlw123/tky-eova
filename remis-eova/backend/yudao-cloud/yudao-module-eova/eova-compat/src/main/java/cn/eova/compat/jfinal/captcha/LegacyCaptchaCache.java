/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.captcha;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.captcha.ICaptchaCache} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.captcha.ICaptchaCache}（jfinal 5.2.6）。</p>
 *
 * <p><b>为什么需要：</b>EOVA 用 {@code DbCaptchaCache} 实现本接口，把验证码<b>存进数据库</b>
 * （表 {@code eova_user_captcha} 族），而不是 jfinal 默认的内存实现。
 * 故接口语义属契约，须固化。</p>
 */
public interface LegacyCaptchaCache {

    /**
     * 存入验证码
     *
     * @param captcha 验证码
     */
    void put(LegacyCaptcha captcha);

    /**
     * 按键取验证码
     *
     * @param key 键
     * @return 验证码；不存在返回 null
     */
    LegacyCaptcha get(String key);

    /**
     * 按键移除
     *
     * @param key 键
     */
    void remove(String key);

    /**
     * 全部移除
     */
    void removeAll();

}
