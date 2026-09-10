/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.captcha;

import java.io.Serializable;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.captcha.Captcha} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.captcha.Captcha}（jfinal 5.2.6）。</p>
 *
 * <p><b>逐条取自旧字节码：</b>
 * <ul>
 *   <li>{@code serialVersionUID = -2593323370708163022L}（序列化契约，不得改）</li>
 *   <li>{@code DEFAULT_EXPIRE_TIME = 180}（秒）</li>
 *   <li>三参构造器：{@code key} 或 {@code value} 为 null 时抛
 *       {@code IllegalArgumentException("key and value can not be null")}
 *       （<b>消息逐字</b>）；随后
 *       {@code expireAt = expireTime * 1000L + System.currentTimeMillis()}</li>
 *   <li>两参构造器委托三参并传 {@code DEFAULT_EXPIRE_TIME}</li>
 *   <li>{@code isExpired()} = {@code expireAt < System.currentTimeMillis()}
 *       —— 注意是<b>严格小于</b>，相等时<b>尚未</b>过期</li>
 *   <li>{@code notExpired()} = {@code !isExpired()}</li>
 * </ul>
 */
public class LegacyCaptcha implements Serializable {

    private static final long serialVersionUID = -2593323370708163022L;

    /** 默认过期秒数 */
    public static final int DEFAULT_EXPIRE_TIME = 180;

    private String key;

    private String value;

    /** 过期时刻（epoch 毫秒） */
    private long expireAt;

    /**
     * 三参构造器。
     *
     * @param key        键
     * @param value      值
     * @param expireTime 过期秒数
     */
    public LegacyCaptcha(String key, String value, int expireTime) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("key and value can not be null");
        }
        this.key = key;
        this.value = value;
        long expireTimeMillis = expireTime;
        this.expireAt = expireTimeMillis * 1000L + System.currentTimeMillis();
    }

    /**
     * 两参构造器，用默认过期秒数。
     *
     * @param key   键
     * @param value 值
     */
    public LegacyCaptcha(String key, String value) {
        this(key, value, DEFAULT_EXPIRE_TIME);
    }

    /**
     * 无参构造器。
     */
    public LegacyCaptcha() {
    }

    /** 取键 */
    public String getKey() {
        return key;
    }

    /** 设置键 */
    public void setKey(String key) {
        this.key = key;
    }

    /** 取值 */
    public String getValue() {
        return value;
    }

    /** 设置值 */
    public void setValue(String value) {
        this.value = value;
    }

    /** 取过期时刻（epoch 毫秒） */
    public long getExpireAt() {
        return expireAt;
    }

    /** 设置过期时刻（epoch 毫秒） */
    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }

    /**
     * 是否已过期（严格小于当前时间才算过期）。
     *
     * @return 已过期返回 true
     */
    public boolean isExpired() {
        return expireAt < System.currentTimeMillis();
    }

    /**
     * 是否未过期。
     *
     * @return 未过期返回 true
     */
    public boolean notExpired() {
        return !isExpired();
    }

    /**
     * 与旧实现同语义的文本形式：{@code key + " :" + value}。
     *
     * <p>分隔符为 <b>{@code " : "}</b>（空格 + 冒号 + 空格）。该类文本可能出现在
     * 日志与诊断输出中，属可观测输出。</p>
     *
     * <p><b>我在同一个方法上连错两次，记录如下：</b>
     * ① 先凭直觉写成 {@code "-"}；② 看 javap 注释 {@code // String  :} 后
     * 改判为 {@code " :"}（以为多出的是 javap 的对齐空格）—— 仍错。
     * 最终由 {@code LegacyCaptchaGoldenTest} 与旧制品逐字比对定为 {@code " : "}
     * （实测旧实现输出 {@code "k1 : v1"}）。教训：<b>反汇编注释里的空白不可数</b>，
     * 该靠跨实现比对定值，而不是靠读注释。</p>
     *
     * @return 文本
     */
    @Override
    public String toString() {
        return key + " : " + value;
    }

}
