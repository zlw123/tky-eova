/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.config;

import cn.eova.tools.x;
import cn.eova.aop.AopContext;
import cn.eova.aop.MetaObjectIntercept;
import cn.eova.common.Ds;
import cn.eova.compat.jfinal.kit.LegacyLogKit;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.config.EovaConfigIntercept
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>配置拦截器（57 行）：读取 eova_config 配置项</li>
 * </ol>
 */
public class EovaConfigIntercept extends MetaObjectIntercept {

    @Override
    public String addSucceed(AopContext ac) throws Exception {
        reload(ac.record);
        return super.addSucceed(ac);
    }

    @Override
    public String updateSucceed(AopContext ac) throws Exception {
        reload(ac.record);
        return super.addSucceed(ac);
    }

    // 修改后更新配置缓存
    @Override
    public String updateCellAfter(AopContext ac) throws Exception {
        String pk = ac.record.getStr("pk");
        EovaRecord e = EovaGateways.get(Ds.EOVA).findById("eova_config", pk);
        reload(e);
        return super.updateCellAfter(ac);
    }

    public void reload(EovaRecord e) {
        String key = e.getStr("code");
        // 测试值 || 默认值
        String val = e.get("test", e.get("value"));

        LegacyLogKit.info("更新配置文件[%s=%s]", key, val);
        x.conf.addConfig(key, val);
//
//        // 页面模版常量 动态更新配置
//        EovaConst.getPageConst().forEach((k, v) -> {
//            if (v.equalsIgnoreCase(key)) {
//                EovaConfig.putSharedVar(k, val);
//                LegacyLogKit.info("页面模版常量[%s=%s]更新", k, key);
//            }
//        });

    }

}