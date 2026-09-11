/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core;

import cn.eova.common.base.BaseController;
import cn.eova.model.User;
import cn.eova.tools.x;
import cn.eova.compat.jfinal.aop.LegacyClear;
import cn.eova.compat.jfinal.core.LegacyController;

/**
 * <p>ported from: cn.eova.core.IndexController
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>首页控制器（73 行）</li>
 * </ol>
 */
/**
 * 系统入口
 *
 * @author Jieven
 */
public class IndexController extends BaseController {

    public void index() {

        User user = getUser();
        // 已经登录
        if (user != null) {

            // 首页初始化
            indexInit(this, user);

            set("app_logo", x.conf.get("app.logo", null));
            set("app_name", x.conf.get("app.name", null));

            render("/eova/index/index.html");
            return;
        }

        // 未登录
        // login();

        redirect("/user/login");
    }


    public void code() {
        setAttr("exp1", "select id UID,login_id CN from users where <%if(user.id != 0){%>  id > ${user.id}<%}%> order by id desc");
        render("/eova/code.html");
    }

    /**
     * 首页初始化
     * @param ctrl
     * @param user 当前用户
     * @throws Exception
     */
    protected void indexInit(LegacyController ctrl, User user) {
//        set("LOGIN_INFO", String.format("%s %s", "超级管理员", "林羽"));
        setAttr("LOGIN_INFO", String.format("%s[%s]", user.role.getStr("name"), user.get("name")));
    }

    @LegacyClear
    public void diy() {
        // 特别注意， 此处仅限于与业务无关，且无动态业务参数，防止注入。

        String cmd = get(0);

        // 动态切换迷你皮肤 服务端是全局的, 不能切换....
        if (cmd.equals("mini")) {
            x.conf.addConfig("SKIN", "mini");
        }

        OK();
    }
}