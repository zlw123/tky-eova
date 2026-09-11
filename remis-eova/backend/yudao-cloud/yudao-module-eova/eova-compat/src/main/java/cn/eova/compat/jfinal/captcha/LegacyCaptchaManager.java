/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.captcha;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.captcha.CaptchaManager} 的等价接缝。
 *
 * <p>ported from: com.jfinal.captcha.CaptchaManager（jfinal 5.2.6 制品）
 *
 * <p><b>逐条取自旧字节码：</b>单例 {@code me()}（静态初始化即 new）；字段
 * {@code private volatile ICaptchaCache captchaCache}；{@code setCaptchaCache} 赋值、
 * {@code getCaptchaCache} 直读 —— <b>静态初始化【不】给默认实现</b>，
 * 故未装配时 {@link #getCaptchaCache()} 返回 {@code null}（旧实现如此）。</p>
 *
 * <p><b>谁负责装配（旧栈实证）：</b>{@code EovaConfig.configConstant()} 第 244 行
 * {@code me.setCaptchaCache(new DbCaptchaCache())} —— 即 EOVA 用<b>库表实现</b>
 * （{@code DbCaptchaCache} 已 port，位于 eova-core）。
 * 新栈里这条装配属于 W3 接缝族（{@code EovaConfig}）的职责，故本类<b>刻意不</b>给默认值 ——
 * 给了会掩盖"宿主忘记装配"，与旧栈行为不再等价。</p>
 */
public class LegacyCaptchaManager {

    private static final LegacyCaptchaManager me = new LegacyCaptchaManager();

    private volatile LegacyCaptchaCache captchaCache;

    private LegacyCaptchaManager() {
    }

    /**
     * 取单例（与旧实现同名同语义）。
     *
     * @return 单例
     */
    public static LegacyCaptchaManager me() {
        return me;
    }

    /**
     * 装配验证码缓存（旧栈由 EovaConfig 在启动时注入 DbCaptchaCache）。
     *
     * @param captchaCache 缓存实现
     */
    public void setCaptchaCache(LegacyCaptchaCache captchaCache) {
        this.captchaCache = captchaCache;
    }

    /**
     * 取验证码缓存。
     *
     * @return 缓存实现；未装配时为 null（旧实现如此）
     */
    public LegacyCaptchaCache getCaptchaCache() {
        return captchaCache;
    }

}
