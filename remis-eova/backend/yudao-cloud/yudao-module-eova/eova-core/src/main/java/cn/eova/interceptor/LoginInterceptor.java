/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.interceptor;

import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;

import cn.eova.tools.x;
import cn.eova.common.base.BaseController;
import cn.eova.common.utils.util.AntPathMatcher;
import cn.eova.common.utils.web.WebUtil;
import cn.eova.config.EovaConfig;
import cn.eova.config.EovaConst;
import cn.eova.i18n.I18NBuilder;
import cn.eova.model.User;
import cn.eova.service.LoginService;
import cn.eova.service.sm;
import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInvocation;

/**
 * <p>ported from: cn.eova.interceptor.LoginInterceptor
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>登录拦截器（129 行）：免登录 URI 放行 → SID 换用户 → 异步 401 / 同步跳登录页 → 续传用户与线程本地</li>
 *   <li>【已声明适配 1】com.jfinal.aop.Interceptor -> cn.eova.compat.jfinal.aop.LegacyInterceptor；com.jfinal.aop.Invocation -> cn.eova.compat.jfinal.aop.LegacyInvocation</li>
 *   <li>【已声明适配 2】javax.servlet.http.HttpServletResponse -> jakarta.servlet.http.HttpServletResponse（setCross 的两个响应头）</li>
 *   <li>excludes 是【可变静态 ArrayList】+ 静态初始化的四条免登录 URI：顺序与内容属对外契约（AuthInterceptor 直接遍历它），且允许运行期增删</li>
 *   <li>【既有缺陷，原样保留】setCross 只加 Access-Control-Allow-Origin/Allow-Credentials 两个头，不处理 OPTIONS 预检与 Allow-Methods/Headers</li>
 *   <li>SID 登录失败时：先 ctrl.removeCookie(LoginService.CKSID)，再按 WebUtil.isAjax 分流；同步分支用 getRequestURL()（不是 URI）拼 ?back=，属对外契约（回跳地址形态）</li>
 *   <li>登录成功分支只在 EovaConfig.getUserSessionIntercept() != null 时调 login(user) 并 updateUser(user)—— 顺序（先钩子后刷新缓存）不得调换</li>
 *   <li>syncThreadLocal 只在 isI18N=true 时生效，且 Cookie 为空时【不】覆盖 LOCAL（x.isEmpty 判定）</li>
 * </ol>
 */
/**
 * 登录拦截器
 * @author Jieven
 *
 */
public class LoginInterceptor implements LegacyInterceptor {

    /**
     * 登录拦截排除URI<br>
     * ?  匹配任何单字符<br>
     * *  匹配0或者任意数量的字符<br>
     * ** 匹配0或者更多的目录 <br>
     */
    public static ArrayList<String> excludes = new ArrayList<String>();

    static {
        // excludes.add(EovaConfig.EOVA_INDEX);
        excludes.add("/user/captcha");
        excludes.add("/user/login");
        excludes.add("/user/doLogin");
        excludes.add("/user/logout");
    }

    /**
     * 允许跨越
     */
    public static void setCross(HttpServletResponse response) {
        // System.err.println("允许跨域模式");
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Credentials", "true");
    }

    @Override
    public void intercept(LegacyInvocation inv) {

//        String uri = inv.getActionKey();

        String uri = inv.getController().getRequest().getRequestURI();

        AntPathMatcher pm = new AntPathMatcher();
        for (String pattern : excludes) {
            if (pm.match(pattern, uri)) {
                inv.invoke();
                return;
            }
        }

        BaseController ctrl = (BaseController) inv.getController();
        User user = ctrl.getUser();
        if (user == null) {
            String ip = WebUtil.getRealIp(ctrl.getRequest());
            user = sm.login.loginBySid(ctrl.SID(), ip);
            if (user == null) {
                // SID登录失败，销毁Cookie
                ctrl.removeCookie(LoginService.CKSID);

                // 异步请求 返回 401 状态码(未获得登录授权)
                if (WebUtil.isAjax(ctrl.getRequest())) {
                    ctrl.renderError(401);
                    return;
                }

                // 本地模式
                String loginUrl = "/user/login";
                // 获取来源页
                StringBuffer url = ctrl.getRequest().getRequestURL();
                if (url != null) {
                    loginUrl += "?back=" + url.toString();
                }
                //
                ctrl.redirect(loginUrl);
                return;

            }
            // 登录初始化
            if (EovaConfig.getUserSessionIntercept() != null) {
                EovaConfig.getUserSessionIntercept().login(user);
                // 更新用户缓存
                ctrl.updateUser(user);
            }
        }

        // 续传 登录用户 用于其他场景 比如模版的Web域
        ctrl.set(LoginService.USER, user);

        syncThreadLocal(inv);

        inv.invoke();
    }

    /**
     * 线程数据同步
     * PS:服务器网络模型不同导致新建线程策略不同, 可能每次请求都会被一个新线程处理, 需要再此续传.
     * @param inv
     */
    public void syncThreadLocal(LegacyInvocation inv) {
        if (x.conf.getBool("isI18N", false)) {
            String local = (String) inv.getController().getCookie(EovaConst.LOCAL);
            if (!x.isEmpty(local)) {
                I18NBuilder.setLocal(local);
                inv.getController().set("LOCAL", local);
            }
        }

    }
} 