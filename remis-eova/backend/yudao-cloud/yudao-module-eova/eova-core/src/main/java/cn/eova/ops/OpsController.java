/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.ops;

import cn.eova.auth.AuthInterceptor;
import cn.eova.common.base.BaseController;
import cn.eova.interceptor.LoginInterceptor;
import cn.eova.tools.x;
import cn.eova.compat.jfinal.aop.LegacyBefore;
import cn.eova.compat.jfinal.aop.LegacyClear;
import cn.eova.compat.jfinal.kit.LegacyRet;

/**
 * <p>ported from: cn.eova.ops.OpsController
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>运维服务控制器（59 行）：info/pause/update 三个 action</li>
 *   <li>【已声明适配 1】com.jfinal.aop.Clear -> cn.eova.compat.jfinal.aop.LegacyClear；com.jfinal.aop.Before -> cn.eova.compat.jfinal.aop.LegacyBefore；com.jfinal.kit.Ret -> cn.eova.compat.jfinal.kit.LegacyRet</li>
 *   <li>类级 @Clear({LoginInterceptor.class, AuthInterceptor.class}) + @Before(OpsInterceptor.class)属对外契约（运维接口必须免登录免鉴权，否则维护态下无法恢复）</li>
 *   <li>【缺失能力，原样保留】update() 写 OpsConst.DEPLOY_VER/DEPLOY_GIT/DEPLOY_TIME 三个静态字段：旧实现是"内存内改、重启即失"（没有落盘/回写配置文件），不得补持久化</li>
 *   <li>pause() 的 flag 取 getInt("flag", 0)，仅 flag==1 视为暂停；info 为空时不覆盖 WAIT_INFO</li>
 *   <li>OK(...) 文案 "服务已暂停"/"服务已恢复" 属对外契约</li>
 * </ol>
 */
/**
 * 运维服务
 *
 * @author Jieven
 */
@LegacyClear({LoginInterceptor.class, AuthInterceptor.class})
@LegacyBefore(OpsInterceptor.class)
public class OpsController extends BaseController {

    // 运维信息查看
    public void info() {
        LegacyRet ret = LegacyRet.ok();
        ret.set("ver", OpsConst.DEPLOY_VER);
        ret.set("time", OpsConst.DEPLOY_TIME);

        renderJson(ret);
    }

    // 服务暂停
    public void pause() {

        int flag = getInt("flag", 0);
        OpsConst.WAITING = flag == 1;

        // 自定义维护文案
        String info = get("info");
        if (!x.isEmpty(info)) {
            OpsConst.WAIT_INFO = info;
        }

        OK(OpsConst.WAITING ? "服务已暂停" : "服务已恢复");
    }

    // 运维信息更新
    public void update() {

        OpsConst.DEPLOY_VER = get("ver", "-.-.-");
        OpsConst.DEPLOY_GIT = get("git", "");
        OpsConst.DEPLOY_TIME = x.time.formatNowTime();

        OK();
    }

}