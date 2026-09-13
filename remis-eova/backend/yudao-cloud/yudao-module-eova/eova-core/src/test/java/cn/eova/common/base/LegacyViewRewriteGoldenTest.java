/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.base;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import cn.eova.compat.render.DefaultLegacyRenderFactory;
import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyRenderManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * **U2 判据：{@code BaseController.render(String)} 的 `_view` 重写映射**（旧契约，逐字保留）。
 *
 * <p><b>为什么这条要单独存在</b>：旧 EOVA 的视图落点规则是
 * {@code render("/eova/x.html") ⇒ <视图根>/eova/_view/x.html}（多了 `_view` 一层），
 * 而 {@code render("/excel/x.html")} 这类**不以 `/eova/` 开头**的路径**原样解析**。</p>
 *
 * <p>r305（U1）与 r306（U2）把页面入口逐个退役为 SPA 壳之后，这条重写**已经没有活页面在走**
 * （仅剩的 18 个渲染点要么模板缺失、要么控制器未注册路由）⇒ 原先在 HTTP 层盯它的判据
 * （`LegacyHttpContractTest#stillLegacyPageRendersFromLegacyViewRoot`）锚点被换到了
 * `/excel/imports/<code>`（那条**不**走重写）。若不补本判据，这条重写就会在"没人盯"的状态下
 * 被悄悄简化（例如去掉 `_view` 一层）—— 而它仍是 18 个渲染点的契约基础。</p>
 *
 * <p><b>判据口径</b>：直接调 {@code render(String)} 后读 {@code getRender().getView()}
 * —— 这就是旧 {@code Controller#render(String)} 的可观测结果（与 port 前的 `setView` 同口径）。</p>
 */
class LegacyViewRewriteGoldenTest {

    /** 渲染工厂必须先装配（否则 render(String) 直接抛「未装配渲染工厂」）—— 与既有 golden 判据同口径 */
    @BeforeEach
    void setUp() {
        LegacyRenderManager.setRenderFactory(new DefaultLegacyRenderFactory());
    }

    /** 最小探针控制器：只暴露 render(String) 这一个被观测面 */
    private static class Probe extends BaseController {
    }

    private static Probe probe() {
        Probe p = new Probe();
        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(
                LegacyViewRewriteGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (InvocationHandler) (proxy, method, args) -> null);
        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                LegacyViewRewriteGoldenTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (InvocationHandler) (proxy, method, args) -> null);
        p.setHttpServletRequest(req);
        p.setHttpServletResponse(resp);
        return p;
    }

    @Test
    @DisplayName("★ U2-10：`/eova/x.html` ⇒ `/eova/_view/x.html`；非 `/eova/` 前缀原样解析")
    void viewRewriteMappingIsFrozen() {
        Probe a = probe();
        a.render("/eova/role/auth/app.html");
        LegacyRender ra = a.getRender();
        assertNotNull(ra, "render(String) 必须产出渲染对象");
        assertEquals("/eova/_view/role/auth/app.html", ra.getView(),
                "★ `/eova/` 开头必须插入 `_view` 一层（旧契约）");

        Probe b = probe();
        b.render("/excel/import/app.html");
        assertEquals("/excel/import/app.html", b.getRender().getView(),
                "★ 非 `/eova/` 前缀**不得**插入 `_view`（Excel 导入页就靠这条原样解析）");

        Probe c = probe();
        c.render("/eova/index/login.html");
        assertEquals("/eova/_view/index/login.html", c.getRender().getView(),
                "★ 登录页形态同样走重写（U1 退役前的旧落点）");
    }
}
