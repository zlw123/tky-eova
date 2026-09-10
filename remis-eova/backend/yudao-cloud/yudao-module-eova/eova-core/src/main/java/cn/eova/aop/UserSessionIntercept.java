/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.aop;

import com.alibaba.fastjson.JSONObject;
import cn.eova.model.User;

/**
 * <p>ported from: cn.eova.aop.UserSessionIntercept
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>用户会话拦截接口；默认方法体属契约</li>
 * </ol>
 */
/**
 * 用户会话拦截器
 * @author Jieven
 *
 */
public interface UserSessionIntercept {

    /**
     * 登录前置拦截
     * eg. 用户登录状态判定
     * @param user
     * @return
     */
    public String loginBefore(User user);

    /**
     * 登录时拦截
     * eg. 给登录用户域添加其它属性 比如企业 user.company
     * @param user
     */
    public void login(User user);

    /**
     * 注销时
     * eg. 注销时 在线人数-1
     * @param user
     */
    public void logout(User user);

    /**
     * 用户超级切换时拦截
     * eg. 非业务所必须, 自行发挥.
     * @param user
     * @param switchUser
     */
    public void su(User user, JSONObject switchUser);
}