package cn.eova.core.api;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.eova.tools.x;
import com.alibaba.fastjson.JSONObject;
import cn.eova.common.base.BaseController;
import cn.eova.common.utils.EncryptUtil;
import cn.eova.common.utils.web.WebUtil;
import cn.eova.compat.jfinal.aop.LegacyClear;
import cn.eova.compat.jfinal.core.LegacyAction;
import cn.eova.compat.jfinal.kit.LegacyLogKit;

/**
 * <p>ported from: cn.eova.core.api.RouterController
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>API 网关入口（107 行）：live 心跳 / initApp 读 eova.api.apps / index 签名校验并派发 / signCheck</li>
 *   <li>【已声明适配 1】com.jfinal.aop.Clear -> cn.eova.compat.jfinal.aop.LegacyClear（类级 @Clear 无参 = 清空全部拦截器；接缝 value() 有 default {} 正是为此）</li>
 *   <li>【已声明适配 2】com.jfinal.core.Action -> cn.eova.compat.jfinal.core.LegacyAction（仅 mapping 字段与 index 内的 action.getMethod()）</li>
 *   <li>【已声明适配 3】com.jfinal.kit.LogKit -> cn.eova.compat.jfinal.kit.LegacyLogKit</li>
 *   <li>getJson() 不是 jfinal Controller 的方法（jfinal 5.2.6 只有 getRawData/getBean）：它来自 EOVA 的 BaseController.getJson() -> com.alibaba.fastjson.JSON，故 (JSONObject) getJson() 这行在旧栈同样由 BaseController 提供，不需新接缝</li>
 *   <li>【既有缺陷，原样保留 1】index() 的派发循环是【不可达的死逻辑】：Object target = null 之后直接 action.getMethod().invoke(target, args) —— 对实例方法传 null target 必 NPE；且 mapping 全树无人 put（routes 字段同样无人用），循环体从不执行。不得顺手修好</li>
 *   <li>【既有缺陷，原样保留 2】args 是 new Object[100]（100 个 null 实参）—— 同上属死逻辑的一部分</li>
 *   <li>【既有缺陷，原样保留 3】env.equalsIgnoreCase(DEV)：x.conf.get 对缺失键返回空串（ConfigTool.get 实测），故不会 NPE；DEV 环境下签名校验失败会打印到 System.err 并【放行】—— 有意的开发便利，属对外行为</li>
 *   <li>initApp 的 APP_CONFIG 是静态且只初始化一次（isEmpty 短路），签名用 EncryptUtil.getMd5(appKey+appSecret+method+timestamp) 且 equalsIgnoreCase 比对 —— 算法与拼接顺序属对外契约</li>
 * </ol>
 */
@LegacyClear
public class RouterController extends BaseController {

    private static final HashMap<String, String> APP_CONFIG = new HashMap<>();
    private static HashMap<String, BaseApi> routes = new HashMap<>();

    protected Map<String, LegacyAction> mapping = new LinkedHashMap<String, LegacyAction>(2048, 0.5F);

    // 心跳检查
    public void live() {
        OK("200");
    }

    public void initApp() {
        if (!APP_CONFIG.isEmpty()) {
            return;
        }

        String appsConfig = x.conf.get("eova.api.apps");
        if (x.isEmpty(appsConfig)) {
            LegacyLogKit.debug("eova.api.apps 为空, 可能无法使用API");
            return;
        }
        String[] apps = appsConfig.split(";");
        for (String app : apps) {
            String[] ss = app.split(":");
            APP_CONFIG.put(ss[0], ss[1]);
        }

    }

    // 网关入口
    public void index() {
        JSONObject json = (JSONObject) getJson();
        String appKey = json.getString("app_key");
        String method = json.getString("method");
        String timestamp = json.getString("timestamp");
        String sign = json.getString("sign");

        if (x.isOneEmpty(appKey, method, timestamp, sign)) {
            NO("公共参数缺失, 请检查!");
            return;
        }

        if (!signCheck(appKey, method, timestamp, sign)) {
            String ip = WebUtil.getRealIp(getRequest());
            String env = x.conf.get("env");
            if (env.equalsIgnoreCase("DEV")) {
                System.err.println("鉴权跳过, 开发环境免鉴权");
            } else {
                NO("鉴权失败, 非法请求");
                return;
            }
        }

        // 后期加入 ver 参数, API可以实现分版本.

        for (String ak : mapping.keySet()) {
            LegacyAction action = mapping.get(ak);
            try {
                Object target = null;
                Object[] args = new Object[100];
                action.getMethod().invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable t = e.getTargetException();
                if (t == null) {
                    t = e;
                }
                throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }

        }

        OK();
    }

    /**
     * 签名校验
     */
    public boolean signCheck(String appKey, String method, String timestamp, String sign) {
        String appSecret = APP_CONFIG.get(appKey);
        // 简单签名, 未来校验所有业务参数
        String md5 = EncryptUtil.getMd5(appKey + appSecret + method + timestamp);
        return md5.equalsIgnoreCase(sign);
    }

}
