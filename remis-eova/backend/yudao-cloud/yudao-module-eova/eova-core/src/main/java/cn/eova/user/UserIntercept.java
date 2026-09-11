/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 *
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.user;

import cn.eova.tools.x;
import cn.eova.aop.AopContext;
import cn.eova.aop.MetaObjectIntercept;
import cn.eova.common.Ds;
import cn.eova.common.utils.EncryptUtil;
import cn.eova.db.EovaGateways;

/**
 * <p>ported from: cn.eova.user.UserIntercept
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>用户业务拦截器（46 行）：按角色层级过滤用户查询</li>
 * </ol>
 */
/**
 * 自定义用户管理拦截器
 * @author Jieven
 *
 */
public class UserIntercept extends MetaObjectIntercept {

    @Override
    public String addBefore(AopContext ac) throws Exception {
        // 数据服务端校验
        String loginId = ac.record.getStr("login_id");
        Long num = EovaGateways.get(Ds.EOVA).queryLong("select count(*) from eova_user where login_id = ?", loginId);
        if (num > 0) {
            return warn("帐号重复,请重新填写!");
        }

        // 新增时密码加密储存
        String str = ac.record.getStr("login_pwd");
        // 加密方式可配置
        String encrypt = x.conf.get("eova.pwd.encrypt", "SM32");
        if (encrypt.equals("SM32")) {
            str = EncryptUtil.getSM32(str);
        } else {
            str = EncryptUtil.getMd5(str);
        }
        ac.record.set("login_pwd", str);

        return null;
    }

}