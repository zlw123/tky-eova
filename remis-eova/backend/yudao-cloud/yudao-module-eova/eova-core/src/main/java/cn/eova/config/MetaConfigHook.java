package cn.eova.config;

import cn.eova.tools.x;
import cn.eova.aop.AopContext;
import cn.eova.common.Ds;
import cn.eova.hook.EovaMetaHook;
import cn.eova.compat.jfinal.kit.LegacyLogKit;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.config.MetaConfigHook
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>元配置 Hook（63 行）</li>
 * </ol>
 */
public class MetaConfigHook implements EovaMetaHook {

    @Override
    public String invoke(Action action, AopContext ac) throws Exception {
        switch (action) {
            case ADD_SUCCEED:
                return addSucceed(ac);
            case UPDATE_SUCCEED:
                return updateSucceed(ac);
            case EDIT_BEFORE:
                return editBefore(ac);
        }
        return null;
    }

    public String addSucceed(AopContext ac) throws Exception {
        reload(ac.record);
        return null;
    }

    public String updateSucceed(AopContext ac) throws Exception {
        reload(ac.record);
        return null;
    }

    // 修改后更新配置缓存
    public String editBefore(AopContext ac) throws Exception {
        String pk = ac.record.getStr("pk");
        EovaRecord e = EovaGateways.get(Ds.EOVA).findById("eova_config", pk);
        reload(e);
        return null;
    }

    public void reload(EovaRecord e) {
        String key = e.getStr("code");
        // 测试值 || 默认值
        String val = e.get("test", e.getStr("value"));

        LegacyLogKit.info("更新配置文件[%s=%s]", key, val);
        x.conf.addConfig(key, val);

//        // 页面模版常量 动态更新配置
//        EovaConst.getPageConst().forEach((k, v) -> {
//            if (v.equalsIgnoreCase(key)) {
//                EovaConfig.putSharedVar(k, val);
//                LegacyLogKit.info("页面模版常量[%s=%s]更新", k, key);
//            }
//        });

    }

}

