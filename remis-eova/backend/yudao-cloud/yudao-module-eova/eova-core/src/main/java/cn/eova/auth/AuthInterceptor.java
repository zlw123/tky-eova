/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.auth;

import java.util.Set;

import cn.eova.tools.x;
import cn.eova.common.base.BaseController;
import cn.eova.common.utils.util.AntPathMatcher;
import cn.eova.common.utils.xx;
import cn.eova.config.EovaConfig;
import cn.eova.config.EovaFieldAuth;
import cn.eova.interceptor.LoginInterceptor;
import cn.eova.model.User;
import cn.eova.ops.OpsConst;
import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInvocation;
import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import cn.eova.compat.jfinal.kit.LegacyRet;

/**
 * <p>ported from: cn.eova.auth.AuthInterceptor
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>权限验证拦截器（121 行）：免登录/超管放行 → 503 维护态 → 免鉴权 URI → 角色授权匹配 → 403</li>
 *   <li>【已声明适配 1】com.jfinal.aop.Interceptor/Invocation -> Legacy 接缝</li>
 *   <li>【已声明适配 2】com.jfinal.kit.JsonKit -> cn.eova.compat.jfinal.kit.LegacyJsonKit；com.jfinal.kit.Ret -> cn.eova.compat.jfinal.kit.LegacyRet</li>
 *   <li>【既有缺陷，原样保留 1】OpsConst.WAITING 分支在 renderError(503) 之后仍然 inv.invoke()—— renderError 在旧实现里是【抛异常】（由框架渲染错误页），故该 invoke 实际不可达；新接缝同语义（renderError 抛 LegacyActionException），不得删掉那行也不得改成不抛</li>
 *   <li>【既有缺陷，原样保留 2】鉴权通过前会把 EovaConfig.getAuthUris().get(rid) 的集合【就地 addAll】进 user.get("auths") 的 Set —— 即鉴权会改写用户权限集合（旧实现如此）</li>
 *   <li>【既有缺陷，原样保留 3】UPDATE_FIELD_AUTH 的 5 秒节流用 xx.isTimeout(user.getLong(...), 5)，写回的是 System.currentTimeMillis()（毫秒）—— 两者单位不同但配合 isTimeout 的秒级语义成立，不得"统一"成同一单位</li>
 *   <li>【既有缺陷，原样保留 4】未授权时先 AuthUri.build(user) 再判 URI：/api/table/query 前缀返回 renderJson(Ret.fail("未授权资源, 请检查权限配置:" + uri))，其余 renderError(403)</li>
 *   <li>免登录 URI 判定复用 LoginInterceptor.excludes（跨类静态耦合，属既有设计）</li>
 * </ol>
 */
/**
 * 权限验证
 *
 * @author Jieven
 * @date 2014-9-18
 */
public class AuthInterceptor implements LegacyInterceptor {

    /** 上一次更新时间(字段授权)**/
    public static final String UPDATE_FIELD_AUTH = "update_field_auth";

    @Override
    public void intercept(LegacyInvocation inv) {
        BaseController ctrl = (BaseController) inv.getController();
        User user = ctrl.getUser();

        // 免登录URI和超管 免鉴权
        if (user == null || user.isAdmin()) {
            inv.invoke();
            return;
        }

        // 服务暂不可用 => HTTP 503
        if (OpsConst.WAITING) {
            ctrl.set("info", OpsConst.WAIT_INFO);
            ctrl.renderError(503);
            inv.invoke();
            return;
        }

        String uri = inv.getController().getRequest().getRequestURI();

        AntPathMatcher pm = new AntPathMatcher();

        // 免登录免鉴权
        for (String pattern : LoginInterceptor.excludes) {
            if (pm.match(pattern, uri)) {
                inv.invoke();
                return;
            }
        }

        // 登录后免鉴权
        for (String pattern : AuthUri.loginAuth) {
            if (pm.match(pattern, uri)) {
                inv.invoke();
                return;
            }
        }

        // 当前角色分配授权
        Set<String> authUriPattern = user.get("auths");
        if (x.isEmpty(authUriPattern)) {
            ((BaseController) inv.getController()).renderMsg("用户未分配权限，请获得授权后<a href=\"logout\">重新登录</a>");
            return;
        }

        // 每5s更新字段权限
        if (xx.isTimeout(user.getLong(UPDATE_FIELD_AUTH), 5)) {
            user.setDisableFields(EovaFieldAuth.getDisableFields(user));
            x.log.debug("{}更新用户列权限:{}", user.getId(), LegacyJsonKit.toJson(user.getDisableFields()));
            user.put(UPDATE_FIELD_AUTH, System.currentTimeMillis());
        }

        // 当前角色自定义授权
        Set<String> temp = EovaConfig.getAuthUris().get(user.getRid());
        if (!x.isEmpty(temp)) {
            authUriPattern.addAll(temp);
        }
        // 所有角色公共授权
//        temp = EovaConfig.getAuthUris().get(0);
//        if (!x.isEmpty(temp)) {
//            authUriPattern.addAll(temp);
//        }
        // 检查授权
        for (String pattern : authUriPattern) {
            if (pm.match(pattern, uri)) {
                inv.invoke();
                return;
            }
        }

        x.log.warn("403 访问未授权资源: {} , User[id={} rid={}]", uri, user.getId(), user.getRid());

        // 无权限时, 自动刷新权限
        AuthUri.build(user);

        if (uri.startsWith("/api/table/query")) {
            // Ajax友好提示
            inv.getController().renderJson(LegacyRet.fail("未授权资源, 请检查权限配置:" + uri));
            return;
        }

        ctrl.set("info", uri);
        ctrl.renderError(403);
    }

}