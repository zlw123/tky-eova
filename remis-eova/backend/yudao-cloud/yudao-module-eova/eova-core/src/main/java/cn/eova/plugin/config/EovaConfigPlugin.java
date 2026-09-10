/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.plugin.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.eova.tools.x;
import cn.eova.common.Ds;
import cn.eova.config.EovaConst;
import cn.eova.compat.jfinal.kit.LegacyLogKit;
import cn.eova.compat.jfinal.plugin.LegacyPlugin;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.plugin.config.EovaConfigPlugin
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>EOVA 数据库配置装载插件（78 行）：启动时读 eova_config 表填充前端可用配置</li>
 *   <li>【已声明适配 1】com.jfinal.plugin.IPlugin -> cn.eova.compat.jfinal.plugin.LegacyPlugin</li>
 *   <li>【已声明适配 2】Db.use(Ds.EOVA) -> EovaGateways.get(Ds.EOVA)；Record -> EovaRecord；LogKit -> LegacyLogKit</li>
 *   <li>UI_CONF_KEYS 为 public static final Set，属对外契约（BaseSharedMethod 直接 import 它）</li>
 *   <li>配置装载规则（非 PRD 环境优先取 test 值）属既有语义，不得改写</li>
 * </ol>
 */
/**
 * 加载EOVA数据库配置
 * @author Jieven
 *
 */
public class EovaConfigPlugin implements LegacyPlugin {

    /**
     * 前端可用配置名
     */
    public static final Set<String> UI_CONF_KEYS = new HashSet<String>();

    @Override
    public boolean start() {
        try {
            List<EovaRecord> list = EovaGateways.get(Ds.EOVA).find("select * from eova_config where status = 1");
            x.log.info("load eova config：enabled = " + list.size());
            // 优先读取测试值, 否则取默认值
            list.forEach(o -> {
                String value = o.getStr("value");
                // 非线上优先使用测试值
                if (!x.conf.get("env", "DEV").equals("PRD")) {
                    String test = o.getStr("test");
                    if (!x.isEmpty(test)) {
                        value = test;
                    }
                }
                // EovaConfig.addConfig(o.getStr("code"), value);
                // 切换为 x.ConfigTool

                x.conf.addConfig(o.getStr("code"), value);
                // 记录UI配置项
                if (!o.getBoolean("is_server")) {
                    UI_CONF_KEYS.add(o.getStr("code"));
                    // System.out.println("前端配置项:" + o.getStr("code"));
                }
            });

            // 读取DB配置后进行配置初始化(动态更新某些静态变量)
            initConfig();
        } catch (Exception e) {
            LegacyLogKit.warn("读取eova_config异常:" + e.getMessage());
        }
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    /**
     * 初始化全局配置项
     */
    public void initConfig() {
        // EovaConst.ADMIN_UID = x.conf.get("admin_uid", "1");
        EovaConst.ADMIN_RID = x.conf.getInt("admin_rid", 1);
        EovaConst.SYS_ADMIN_UID = x.conf.getInt("sys_admin_uid", 2);
    }
}