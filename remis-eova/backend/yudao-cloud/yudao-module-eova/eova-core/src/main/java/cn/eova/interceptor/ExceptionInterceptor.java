package cn.eova.interceptor;

import java.sql.Timestamp;

import cn.eova.common.base.BaseController;
import cn.eova.common.utils.util.ExceptionUtil;
import cn.eova.common.utils.web.WebUtil;
import cn.eova.model.User;
import cn.eova.tools.x;
import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInvocation;
import cn.eova.compat.jfinal.core.LegacyActionException;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.jfinal.kit.LegacyLogKit;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.interceptor.ExceptionInterceptor
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>全局异常拦截器（92 行）：把动作抛出的异常转成前端可读提示</li>
 *   <li>【已声明适配】Interceptor/Invocation/ActionException/Controller/LogKit/Db/Record 走既有接缝</li>
 *   <li>ActionException 的状态码与错误渲染语义属【对外契约】（前端据此展示）</li>
 * </ol>
 */
public class ExceptionInterceptor implements LegacyInterceptor {

    @Override
    public void intercept(LegacyInvocation inv) {
        LegacyController ctrl = inv.getController();

        try {
            inv.invoke();
        } catch (Exception e) {
            // 仅处理 HTTP 500 异常
            if (e instanceof LegacyActionException) {
                LegacyActionException ae = (LegacyActionException) e;
                if (ae.getErrorCode() != 500) {
                    ctrl.renderError(ae.getErrorCode());
                    return;
                }
            }

            LegacyLogKit.error(e.getMessage(), e);

            String uri = ctrl.getRequest().getRequestURI();
            String paras = ctrl.getRequest().getQueryString();

            String url = uri;
            if (!x.isEmpty(paras)) {
                url += "?" + paras;
            }
            if (url.length() > 250) {
                url = url.substring(0, 250);
            }

            String info = ExceptionUtil.getStackTrace(e);
            String type = e.getClass().getName();
            String msg = e.getMessage();

            // 截短消息
            if (!x.isEmpty(msg) && msg.length() > 500) {
                msg = msg.substring(0, 500);
            }

            String ip = WebUtil.getRealIp(ctrl.getRequest());
            String uid = null;

            if (ctrl instanceof BaseController) {
                BaseController base = (BaseController) ctrl;
                User user = base.getUser();
                if (user != null) {
                    uid = base.UID() + "";
                }
            }

            // 判断N分钟内是否已存在相同报错日志
            int min = x.conf.getInt("eova.exception.min", 60);
            Timestamp beforeTime = x.time.calcMin(-min);
            Long id = EovaGateways.get(x.DS_EOVA).queryLong("select max(id) from eova_exception where create_time > ? and url = ? and info = ?", beforeTime, url, info);
            if (id != null && id > 0) {
                // 叠加计数, 不重复保存
                EovaGateways.get(x.DS_EOVA).update("update eova_exception set num = num+1 where id = ?", id);
            } else {
                // 保存异常
                EovaRecord o = new EovaRecord();
                o.set("ip", ip);
                o.set("uid", uid);
                o.set("url", url);
                o.set("type", type);
                o.set("msg", msg);
                o.set("info", info);
                EovaGateways.get(x.DS_EOVA).save("eova_exception", o);
            }

            ctrl.renderError(500);
        }
    }

}
