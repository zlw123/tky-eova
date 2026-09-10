/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.base;

import cn.eova.tools.kit.HtmlKit;
import cn.eova.tools.x;
import cn.eova.compat.jfinal.kit.LegacyKv;

import static cn.eova.plugin.config.EovaConfigPlugin.UI_CONF_KEYS;

/**
 * <p>ported from: cn.eova.common.base.BaseSharedMethod
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>Enjoy 共享方法基类（70 行）：向前端模板暴露工具方法</li>
 *   <li>【已声明适配】com.jfinal.kit.Kv -> LegacyKv（R37/R40）</li>
 *   <li>静态 import EovaConfigPlugin.UI_CONF_KEYS —— 依赖上一单元</li>
 *   <li>cn.eova.tools.kit.HtmlKit 与 x 为 eova-tools 真实制品，沿用</li>
 * </ol>
 */
/**
 * 基础模版共享方法
 * #(conf('ui.include'))
 *
 * @author Jieven
 */
public class BaseSharedMethod {

    public String getUIConf() {
        LegacyKv kv = LegacyKv.create();
        UI_CONF_KEYS.forEach(k -> {
            kv.set(k, x.conf.get(k));
        });
        return kv.toJson();
    }

    public String conf(String key) {
        return x.conf.get(key);
    }

    public String conf(String key, String defaultValue) {
        return x.conf.get(key, defaultValue);
    }

    public String ws(String domain) {
        return "ws://" + x.conf.get(domain);
    }

    public String wss(String domain) {
        return "wss://" + x.conf.get(domain);
    }

    public String htt(String domain) {
        return "//" + x.conf.get(domain);
    }

    public String http(String domain) {
        return "http://" + x.conf.get(domain);
    }

    public String https(String domain) {
        return "https://" + x.conf.get(domain);
    }

    public String dir(String dirName) {
        return x.conf.get("dir.static") + x.conf.get("dir." + dirName);
    }

    //	 非法内容过滤,适用于纯文本输出
    public String xss(String s) {
        return HtmlKit.XSSEncode(s);
    }

    // html内容转码
    public String html(String s) {
        return HtmlKit.HTMLEncode(s);
    }
}