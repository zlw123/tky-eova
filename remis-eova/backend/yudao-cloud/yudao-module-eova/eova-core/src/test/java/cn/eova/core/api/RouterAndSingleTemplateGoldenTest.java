/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import cn.eova.common.base.BaseController;
import cn.eova.common.utils.EncryptUtil;
import cn.eova.compat.jfinal.aop.LegacyClear;
import cn.eova.compat.render.DefaultLegacyRenderFactory;
import cn.eova.compat.render.LegacyJsonRender;
import cn.eova.compat.render.LegacyRenderFactory;
import cn.eova.compat.render.LegacyRenderManager;
import cn.eova.template.single.SingleController;
import cn.eova.tools.x;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由/单表模板族 2 单元（第 80 轮 port：{@code RouterController} 107 + {@code SingleController} 160）的判据。
 *
 * <p><b>判据分层（本轮两个单元的可判性差别很大，如实反映在判据里）：</b></p>
 * <ol>
 *   <li><b>RouterController 可判得比较深</b>：它的四条分支（公共参数缺失 / 签名失败 /
 *       DEV 环境放行 / 签名通过）都只依赖 {@code APP_CONFIG} 与请求体，**不触库** ——
 *       故逐条断言渲染出来的 envelope 文案与签名算法。</li>
 *   <li><b>SingleController 本轮只判"面"</b>：它的四个 action 全部依赖
 *       {@code Menu.dao}/{@code MetaObject.dao}/{@code Button.dao} 与上传，
 *       没有 baseline 库就无法给出行为判据 ⇒ 只钉住方法集与 {@code throws} 契约，
 *       行为验收登记为 pending（见 DES-002-R4 §r80 待验收清单）。
 *       <b>宁缺勿假</b>：不写"看起来跑了但其实没断言"的用例。</li>
 * </ol>
 *
 * <p><b>环境纪律（R58）</b>：{@code RouterController.APP_CONFIG} 是**进程级静态且只初始化一次**
 * （{@code initApp()} 的 {@code isEmpty} 短路就是旧语义，且没有清理口）—— 故本判据只往里写
 * <b>自有的 app_key</b>（{@code 判据专用}），并在注释里写明"不可重置"这一事实；
 * 渲染工厂与 {@code x.conf} 的改动在 {@code @AfterEach} 还原。</p>
 */
class RouterAndSingleTemplateGoldenTest {

    /** 判据专用的 app_key（避免与其它用例/真实配置相互污染） */
    private static final String APP = "probe-app-key";

    private static final String SECRET = "probe-app-secret";

    private LegacyRenderFactory savedFactory;

    private boolean factoryWasReady;

    /** 控制器探针：请求体可注入、渲染可读取 */
    public static class ProbeRouter extends RouterController {

        private final String body;

        /**
         * @param body 请求体（对应旧栈 getRawData() 的来源）
         */
        public ProbeRouter(String body) {
            this.body = body;
            setHttpServletRequest(request());
        }

        @Override
        public String getRawData() {
            return body;
        }
    }

    private static HttpServletRequest request() {
        return (HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(
                RouterAndSingleTemplateGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (p, m, args) -> {
                    switch (m.getName()) {
                        case "getRequestURI":
                            return "/router";
                        case "getRemoteAddr":
                            return "127.0.0.1";
                        case "getAttribute":
                            return null;
                        case "equals":
                            return p == args[0];
                        case "hashCode":
                            return System.identityHashCode(p);
                        default:
                            return null;
                    }
                });
    }

    @BeforeEach
    void setUp() {
        factoryWasReady = LegacyRenderManager.isReady();
        savedFactory = factoryWasReady ? LegacyRenderManager.getRenderFactory() : null;
        LegacyRenderManager.setRenderFactory(new DefaultLegacyRenderFactory());

        // 让 initApp() 有配置可读（真实部署读 eova.api.apps）
        x.conf.addConfig("eova.api.apps", APP + ":" + SECRET + ";other-app:other-secret");
        new RouterController().initApp();
    }

    @AfterEach
    void tearDown() {
        if (factoryWasReady) {
            LegacyRenderManager.setRenderFactory(savedFactory);
        } else {
            LegacyRenderManager.clear();
        }
        x.conf.getProps().remove("eova.api.apps");
    }

    /** 取 json 渲染文本 */
    private static String jsonOf(BaseController ctrl) {
        LegacyJsonRender r = (LegacyJsonRender) ctrl.getRender();
        assertNotNull(r, "必须渲染 json");
        return r.getJsonText();
    }

    // ------------------------------------------------------------ RouterController

    @Test
    @DisplayName("RouterController：类级 @Clear（无参）⇒ 清空全部拦截器")
    void routerClearAnnotation() {
        LegacyClear clear = RouterController.class.getAnnotation(LegacyClear.class);
        assertNotNull(clear, "网关入口必须清空拦截器（登录/鉴权都在此之前由 API 签名承担）");
        assertEquals(0, clear.value().length, "@Clear 无参 = 清空全部（接缝 value() default {} 正是为此）");
    }

    @Test
    @DisplayName("RouterController：live 心跳渲染 code=0 且 msg=200")
    void routerLive() {
        RouterController ctrl = new ProbeRouter(null);
        ctrl.live();
        String json = jsonOf(ctrl);
        // Ret 的 envelope 是 state/msg/data（R5），不是 code/msg/data（那是 ApiResponse）
        assertTrue(json.contains("\"state\":\"ok\""), json);
        assertTrue(json.contains("\"msg\":\"200\""), json);
    }

    @Test
    @DisplayName("RouterController：index 的四条分支（缺参 / 签名失败 / DEV 放行 / 通过）")
    void routerIndexBranches() {
        // ① 公共参数缺失
        ProbeRouter missing = new ProbeRouter("{\"app_key\":\"x\"}");
        missing.index();
        assertTrue(jsonOf(missing).contains("公共参数缺失"), jsonOf(missing));

        // ② 签名失败 + 非 DEV ⇒ 拒绝
        x.conf.addConfig("env", "PROD");
        String badSign = "00000000000000000000000000000000";
        ProbeRouter bad = new ProbeRouter(body(APP, "m1", "1", badSign));
        bad.index();
        assertTrue(jsonOf(bad).contains("鉴权失败"), jsonOf(bad));

        // ③ 签名失败 + DEV ⇒ 放行（既有语义：打印到 stderr 后继续，最终 OK）
        x.conf.addConfig("env", "DEV");
        ProbeRouter dev = new ProbeRouter(body(APP, "m1", "1", badSign));
        dev.index();
        String devJson = jsonOf(dev);
        assertTrue(devJson.contains("\"state\":\"ok\""), "DEV 环境必须放行：" + devJson);

        // ④ 签名正确 ⇒ 通过（且未切换 env 也成立）
        x.conf.addConfig("env", "PROD");
        String sign = sign(APP, "m1", "1");
        ProbeRouter ok = new ProbeRouter(body(APP, "m1", "1", sign));
        ok.index();
        assertTrue(jsonOf(ok).contains("\"state\":\"ok\""), jsonOf(ok));
        x.conf.getProps().remove("env");
    }

    @Test
    @DisplayName("RouterController：signCheck 的算法 = md5(appKey+appSecret+method+timestamp) 且忽略大小写")
    void routerSignCheck() {
        RouterController ctrl = new ProbeRouter(null);
        String sign = sign(APP, "order.list", "1700000000");
        assertTrue(ctrl.signCheck(APP, "order.list", "1700000000", sign));
        // 注意：getMd5 的返回大小写是固定的，故"翻转大小写"必须按实际值取反 ——
        // 直接 toUpperCase() 在 md5 本身即为大写时是 no-op，断言会变成空判据（本轮实测：变异 M2 曾因此漏网）
        String flipped = sign.equals(sign.toLowerCase()) ? sign.toUpperCase() : sign.toLowerCase();
        assertFalse(flipped.equals(sign), "自检：翻转后的签名必须与原文不同，否则该断言是空判据");
        assertTrue(ctrl.signCheck(APP, "order.list", "1700000000", flipped),
                "equalsIgnoreCase：签名大小写不敏感");
        assertFalse(ctrl.signCheck(APP, "order.list", "1700000001", sign), "时间戳变则签名失效");
        assertFalse(ctrl.signCheck("unknown-app", "order.list", "1700000000", sign),
                "未登记的 appKey ⇒ secret 为 null ⇒ 签名不匹配");
    }

    /** 期望签名（与旧实现同式：appKey + appSecret + method + timestamp 直接字符串相加） */
    private static String sign(String appKey, String method, String timestamp) {
        String appSecret = APP.equals(appKey) ? SECRET : "other-secret";
        return EncryptUtil.getMd5(appKey + appSecret + method + timestamp);
    }

    /** 造请求体 */
    private static String body(String appKey, String method, String timestamp, String sign) {
        JSONObject o = new JSONObject();
        o.put("app_key", appKey);
        o.put("method", method);
        o.put("timestamp", timestamp);
        o.put("sign", sign);
        return o.toJSONString();
    }

    @Test
    @DisplayName("RouterController：mapping/routes 字段保持【无人填充】的既有事实（死逻辑不得被当成功能）")
    void routerMappingIsEmpty() throws Exception {
        java.lang.reflect.Field f = RouterController.class.getDeclaredField("mapping");
        f.setAccessible(true);
        Map<?, ?> mapping = (Map<?, ?>) f.get(new ProbeRouter(null));
        assertTrue(mapping.isEmpty(), "mapping 全树无人 put —— index() 的派发循环因此不可达（既有事实）");
    }

    // ------------------------------------------------------------ SingleController（只判面）

    @Test
    @DisplayName("SingleController：方法集与 throws 契约（行为验收 pending：需 baseline 库）")
    void singleControllerSurface() throws Exception {
        Map<String, Method> methods = new TreeMap<>();
        for (Method m : SingleController.class.getDeclaredMethods()) {
            if (!m.isSynthetic() && !Modifier.isPrivate(m.getModifiers())) {
                methods.put(m.getName(), m);
            }
        }
        assertEquals(List.of("diyImportXls", "doImportXls", "importXls", "list"),
                List.copyOf(methods.keySet()), "四个 action 的名字属对外契约（URL 即 action 名）");
        assertTrue(Modifier.isPublic(methods.get("doImportXls").getModifiers()));
        assertEquals(1, methods.get("doImportXls").getExceptionTypes().length,
                "doImportXls 声明 throws Exception（框架层要按原始类型处理）");
        assertEquals("java.lang.Exception",
                methods.get("doImportXls").getExceptionTypes()[0].getName());
        assertEquals(cn.eova.common.base.BaseController.class, SingleController.class.getSuperclass());
    }

    @Test
    @DisplayName("SingleController：diyImportXls 是空实现（自定义扩展点，不得偷偷加行为）")
    void singleControllerDiyHook() throws Exception {
        SingleController ctrl = new SingleController();
        ctrl.diyImportXls();
        assertEquals(null, ctrl.getRender(), "空实现不得渲染任何东西");
    }

    @Test
    @DisplayName("判据自检：JSON.parse 的 facade 行为（getJson 的类型契约）")
    void jsonFacadeSelfCheck() {
        Object parsed = JSON.parse("{\"a\":1}");
        assertTrue(parsed instanceof JSONObject, "getJson() 返回 fastjson JSON，RouterController 强转 JSONObject");
    }
}
