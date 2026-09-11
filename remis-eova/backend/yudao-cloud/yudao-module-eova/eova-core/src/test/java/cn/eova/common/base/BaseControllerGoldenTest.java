/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.base;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.lang.reflect.InvocationHandler;
import java.util.List;

import cn.eova.compat.jfinal.core.LegacyNotAction;
import cn.eova.compat.render.DefaultLegacyRenderFactory;
import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyRenderManager;
import cn.eova.service.LoginService;
import cn.eova.tools.x;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code BaseController}（第 67 轮真 port，363 行）的判据。
 *
 * <p><b>为什么本轮才 port 它：</b>它是所有 EOVA 控制器的基类（W4 的前置），第 59 轮因依赖闭包
 * 太大（73 单元 / 11,381 行）而回退；第 66 轮 `biz`/`sm` 落地后其依赖全部就绪，
 * 本轮随"60 单元核心块"一起 port。</p>
 *
 * <p><b>判据聚焦三处对外契约：</b>① {@code SID()} 的<b>四级取值优先级</b>；
 * ② {@code render(String)} 的 <b>{@code /eova/ → /eova/_view/} 路径改写</b>（属页面契约）；
 * ③ {@code @NotAction} 标注的辅助方法<b>不得</b>被当成 action 暴露。</p>
 */
class BaseControllerGoldenTest {

    /** 渲染工厂探针：继承默认工厂，只记录"被请求了哪个渲染入口 + 入参" */
    static final class SpyFactory extends DefaultLegacyRenderFactory {

        /** 记录：形如 {@code jsonObject:...} / {@code tmpl:/eova/_view/menu/add.html} */
        static final List<String> CALLS = new ArrayList<>();

        @Override
        public LegacyRender getJsonRender() {
            CALLS.add("json");
            return super.getJsonRender();
        }

        @Override
        public LegacyRender getJsonRender(String jsonText) {
            CALLS.add("jsonText:" + jsonText);
            return super.getJsonRender(jsonText);
        }

        @Override
        public LegacyRender getJsonRender(Object object) {
            CALLS.add("jsonObject:" + object);
            return super.getJsonRender(object);
        }

        @Override
        public LegacyRender getTemplateRender(String view) {
            CALLS.add("tmpl:" + view);
            return super.getTemplateRender(view);
        }

        @Override
        public LegacyRender getHtmlRender(String htmlText) {
            CALLS.add("html:" + htmlText);
            return super.getHtmlRender(htmlText);
        }
    }

    /** 探针控制器 */
    static class Probe extends BaseController {
    }

    /** 装上探针工厂 */
    @BeforeAll
    static void installFactory() {
        LegacyRenderManager.setRenderFactory(new SpyFactory());
        SpyFactory.CALLS.clear();
    }

    /**
     * 造请求替身（同时支持属性/ Cookie / 参数 / URL）。
     *
     * @param attr   请求属性
     * @param cookie Cookie 值（{@code eovasid}）
     * @param param  请求参数（{@code sid}）
     * @param url    请求 URL
     * @return 替身
     */
    private static HttpServletRequest request(String attr, String cookie, String param, String url) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getAttribute":
                    return attr;
                case "getCookies":
                    return cookie == null ? null : new Cookie[]{new Cookie(LoginService.CKSID, cookie)};
                case "getParameter":
                    return param;
                case "getRequestURL":
                    return new StringBuffer(url);
                case "getCharacterEncoding":
                    return null;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(BaseControllerGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, h);
    }

    @Test
    @DisplayName("SID()：四级优先级 —— 请求属性 > Cookie > 参数 > dev 兜底")
    void sidPriorityOrder() {
        String beforeSid = x.conf.get("dev.sid");
        String beforeDomain = x.conf.get("dev.domain");
        try {
            x.conf.addConfig("dev.sid", "DEV_SID");
            x.conf.addConfig("dev.domain", "http://never-match.example");
            Probe c = new Probe();

            // ① 属性优先于 Cookie 与参数
            c.setHttpServletRequest(request("A", "C", "P", "http://localhost/x"));
            assertEquals("A", c.SID(), "请求属性最高优先");

            // ② 无属性时取 Cookie
            c.setHttpServletRequest(request(null, "C", "P", "http://localhost/x"));
            assertEquals("C", c.SID(), "Cookie 次之（eovasid）");

            // ③ 无属性/ Cookie 时取参数
            c.setHttpServletRequest(request(null, null, "P", "http://localhost/x"));
            assertEquals("P", c.SID(), "参数再次之（sid）");

            // ④ 三者都无：dev.domain 不匹配 ⇒ 返回 null
            c.setHttpServletRequest(request(null, null, null, "http://localhost/x"));
            assertNull(c.SID(), "dev.domain 不匹配时不得回落到 dev.sid");

            // ⑤ dev.domain 为空串 ⇒ 任意 URL 都 startsWith("") ⇒ 命中 dev.sid
            x.conf.addConfig("dev.domain", "");
            c.setHttpServletRequest(request(null, null, null, "http://anything/x"));
            assertEquals("DEV_SID", c.SID(),
                    "dev.domain 为空时 startsWith(\"\") 恒真 —— 属既有语义，不得'顺手'改成非空判断");
        } finally {
            x.conf.addConfig("dev.sid", beforeSid == null ? "" : beforeSid);
            x.conf.addConfig("dev.domain", beforeDomain == null ? "" : beforeDomain);
        }
    }

    @Test
    @DisplayName("render(String)：/eova/ 前缀必须改写成 /eova/_view/（页面契约）")
    void renderRewritesEovaPrefix() {
        Probe c = new Probe();
        SpyFactory.CALLS.clear();
        c.render("/eova/menu/add.html");
        assertEquals(List.of("tmpl:/eova/_view/menu/add.html"), SpyFactory.CALLS,
                "路径改写属页面契约：/eova/xxx.html -> /eova/_view/xxx.html");

        SpyFactory.CALLS.clear();
        c.render("/custom/page.html");
        assertEquals(List.of("tmpl:/custom/page.html"), SpyFactory.CALLS,
                "非 /eova/ 前缀不得改写");
    }

    @Test
    @DisplayName("OK()/NO(msg)/renderMsg(msg)/uploadCallback：各自的渲染出口与内容")
    void renderHelpersUseDistinctOutlets() {
        Probe c = new Probe();

        SpyFactory.CALLS.clear();
        c.OK();
        assertEquals(1, SpyFactory.CALLS.size(), "OK() 只产生一次渲染请求");
        assertTrue(SpyFactory.CALLS.get(0).startsWith("jsonObject:"),
                "OK() 必须走 renderJson(Ret.ok())，实际：" + SpyFactory.CALLS);

        SpyFactory.CALLS.clear();
        c.NO("失败啦");
        assertTrue(SpyFactory.CALLS.get(0).contains("失败啦"),
                "NO(msg) 必须把消息带进渲染对象，实际：" + SpyFactory.CALLS);

        SpyFactory.CALLS.clear();
        c.renderMsg("错误消息");
        assertTrue(SpyFactory.CALLS.get(0).startsWith("html:"), "renderMsg 走 HTML 渲染");
        assertTrue(SpyFactory.CALLS.get(0).contains("eova-msg-error"),
                "renderMsg 必须包 eova-msg-error 容器（样式契约）");
        assertTrue(SpyFactory.CALLS.get(0).contains("eova.render.css"),
                "renderMsg 必须引 eova.render.css（前端样式依赖）");

        SpyFactory.CALLS.clear();
        c.uploadCallback(true, "ok-msg");
        assertTrue(SpyFactory.CALLS.get(0).contains("parent.callback"),
                "uploadCallback 必须产出 JSONP 回调脚本，实际：" + SpyFactory.CALLS);
        assertTrue(SpyFactory.CALLS.get(0).contains("ok-msg"));
    }

    @Test
    @DisplayName("@NotAction：辅助方法不得被当成 action 暴露（NotAction 接缝的用途）")
    void helperMethodsAreNotActions() throws Exception {
        for (String name : new String[]{"UID", "RID", "CID", "SID", "getUser", "updateUser",
                "getJson", "getJsonToRecord", "renderMsg", "OK", "NO", "renderEnjoy",
                "render", "uploadCallback"}) {
            List<Method> ms = new ArrayList<>();
            for (Method m : BaseController.class.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    ms.add(m);
                }
            }
            assertTrue(!ms.isEmpty(), "BaseController 必须声明 " + name);
            for (Method m : ms) {
                assertTrue(m.isAnnotationPresent(LegacyNotAction.class),
                        name + " 必须带 @NotAction（否则会被映射成 URL 动作）");
            }
        }
        // 反例：接口里其余 public 方法（如 getSelectValue）原本就【没有】@NotAction，
        // 但它们是 protected，不构成 action —— 这里只钉住上面这组 public 辅助方法。
        assertTrue(BaseController.class.getMethod("render", String.class)
                .isAnnotationPresent(LegacyNotAction.class));
    }

    @Test
    @DisplayName("renderEnjoy：走模板渲染；getJsonToRet 解析请求体 JSON")
    void renderEnjoyAndJsonBody() {
        Probe c = new Probe();
        // renderEnjoy(view) = render(new TemplateRender(view)) -> 直接赋值 render 字段，
        // 【不经工厂】（工厂只在渲染执行时才被调用）—— 故断言 getRender() 本身
        c.renderEnjoy("/x/y.html");
        assertTrue(c.getRender() instanceof cn.eova.compat.render.LegacyTemplateRender,
                "renderEnjoy 必须构造模板渲染，实际：" + c.getRender());
        assertEquals("/x/y.html", c.getRender().toString(),
                "LegacyTemplateRender.toString 返回 view 本身（不做 /eova/ 改写）");

        // 请求体 JSON → Ret（经 LegacyJsonKit，与 LoginService 的适配同构）
        c.setHttpServletRequest(jsonRequest("{\"state\":\"ok\",\"msg\":\"hi\"}"));
        Object ret = c.getJsonToRet();
        assertTrue(ret != null, "必须解析出非 null 的 Ret");
        assertTrue(ret.toString().contains("ok") || ret.toString().contains("hi"),
                "解析结果应包含 JSON 内容，实际：" + ret);
    }

    /**
     * 造一个只提供请求体的请求替身。
     *
     * @param body JSON 文本
     * @return 替身
     */
    private static HttpServletRequest jsonRequest(String body) {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getCharacterEncoding":
                    return "UTF-8";
                case "getReader":
                    // LegacyController.readData 走 getReader()（BufferedReader）
                    return new java.io.BufferedReader(new java.io.StringReader(body));
                case "getInputStream":
                    return new jakarta.servlet.ServletInputStream() {
                        private final java.io.ByteArrayInputStream in =
                                new java.io.ByteArrayInputStream(bytes);

                        @Override
                        public int read() {
                            return in.read();
                        }

                        @Override
                        public boolean isFinished() {
                            return in.available() == 0;
                        }

                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setReadListener(jakarta.servlet.ReadListener l) {
                        }
                    };
                case "getAttribute":
                    return null;
                case "getParameter":
                    return null;
                case "getRequestURL":
                    return new StringBuffer("http://localhost/x");
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(BaseControllerGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, h);
    }

}
