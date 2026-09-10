/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.ext.jfinal;

import cn.eova.common.Ds;
import cn.eova.config.EovaConst;
import cn.eova.compat.jfinal.captcha.LegacyCaptcha;
import cn.eova.compat.jfinal.captcha.LegacyCaptchaCache;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.ext.jfinal.DbCaptchaCache
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>数据库验证码实现（69 行）：implements ICaptchaCache，把验证码存入数据库</li>
 *   <li>【已声明适配 1】com.jfinal.captcha.ICaptchaCache -> cn.eova.compat.jfinal.captcha.LegacyCaptchaCache</li>
 *   <li>【已声明适配 2】com.jfinal.captcha.Captcha -> LegacyCaptcha</li>
 *   <li>【已声明适配 3】Db.use(Ds.EOVA) -> EovaGateways.get(Ds.EOVA)；Record -> EovaRecord</li>
 *   <li>覆盖写（先 remove 再 save）与过期时间列语义属既有行为，不得改写</li>
 * </ol>
 */
/**
 * <pre>
 * 分布式验证码(eova.eova_cache)
 * 支持集群部署多实例
 * 性能优越:基于DB内存表
 * 无第三方依赖
 * 自动回收,无内存泄漏
 * </pre>
 */
public class DbCaptchaCache implements LegacyCaptchaCache {

    private static int BIZ = 1;

    @Override
    public void put(LegacyCaptcha captcha) {
        // 验证码Key有可能重复
        String key = captcha.getKey();

        // 保证每次都生成新鲜的验证码
        remove(captcha.getKey());

        EovaRecord e = new EovaRecord();
        e.set("biz", BIZ);
        e.set("id", key);
        e.set("val", captcha.getValue());
        e.set("expire", captcha.getExpireAt());
        EovaGateways.get(Ds.EOVA).save(EovaConst.EOVA_CACHE, e);
    }

    @Override
    public LegacyCaptcha get(String key) {

        // 取最新的有效验证码
        EovaRecord e = EovaGateways.get(Ds.EOVA).findFirst(String.format("select * from %s where biz = ? and id = ? order by expire desc", EovaConst.EOVA_CACHE), BIZ, key);
        if (e == null) {
            return null;
        }

        // 获取成功后 回收过期验证码
        long t = System.currentTimeMillis();
        EovaGateways.get(Ds.EOVA).delete(String.format("delete from %s where biz = ? and expire < ?", EovaConst.EOVA_CACHE), BIZ, t);

        return new LegacyCaptcha(e.getStr("id"), e.getStr("val"));
    }

    @Override
    public void remove(String key) {
        EovaGateways.get(Ds.EOVA).delete(String.format("delete from %s where id = ?", EovaConst.EOVA_CACHE), key);
    }

    @Override
    public void removeAll() {
        EovaGateways.get(Ds.EOVA).delete(String.format("delete from %s where biz = ?", EovaConst.EOVA_CACHE), BIZ);
    }

}