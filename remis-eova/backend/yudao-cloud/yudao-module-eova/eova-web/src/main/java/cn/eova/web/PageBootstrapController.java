/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.io.IOException;
import java.util.Map;

import cn.eova.api.page.PageBootstrapAssembler;
import cn.eova.common.utils.web.RequestUtil;
import cn.eova.common.utils.web.WebUtil;
import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.compat.render.LegacyRenderManager;
import cn.eova.model.Menu;
import cn.eova.model.MetaObject;
import cn.eova.model.User;
import cn.eova.service.LoginService;
import cn.eova.service.biz;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * **切片 S4：页面引导数据端点 {@code POST /api/page/bootstrap}**（DES-004 §3.1 的落点）。
 *
 * <p><b>为什么落在这一层</b>：该端点的消费端（{@code remis-eova-ui/src/compat/page-bootstrap-fetcher.ts}）
 * 早在第 124 轮就做完了，但它一直**落不了地** —— 因为 ported 的 eova-core 是纯 JFinal→Spring 的
 * 代码级 port（无 Spring、无 MVC、无 {@code @Controller}），端点需要 Web 层存在。S2b 收口后
 * 本层已可用，故按 DES-005 §9 的口径**先落在 eova-web**（自有临时实现），阶段 3 按平台规范平移。</p>
 *
 * <p><b>契约（逐条来自 DES-004 §3.1，不自行发明）</b>：</p>
 * <ul>
 *   <li>方法 POST；body {@code {path, object?, menu?, biz?, mode?, id?}}；</li>
 *   <li>响应字段名**沿用旧 {@code setAttr} 名**：{@code state/object/menu/menuCode/btnList/loginUser/isQuery}；</li>
 *   <li>未登录 ⇒ **401**，且**不得**下发"部分引导数据"；</li>
 *   <li>含权限面（{@code btnList} 按会话角色查、{@code loginUser} 随会话变化）⇒
 *       {@code Cache-Control: no-store}；</li>
 *   <li>失败也返回 200 + {@code {"state":"no","msg":…}}（前端把 {@code state !== 'ok'} 当失败降级）。</li>
 * </ul>
 *
 * <p><b>两处口径的取证（不是推断）</b>：</p>
 * <ol>
 *   <li><b>菜单编码</b>：优先 {@code body.menu}；缺失时取 {@code body.path} 的**末段** ——
 *       旧栈页面 URL 形如 {@code /app/<menuCode>}（{@code AuthUri} 里硬编码的
 *       {@code "/app/#(menu.code)"}，r246 实测该模板确实被渲染），故末段即菜单编码。</li>
 *   <li><b>isQuery</b>：取 {@code MetaObject.dao.isExistQuery(objectCode)}（objectCode 由菜单配置推导），
 *       与旧 {@code SingleController#index} 的 {@code setAttr("isQuery", …)} **同源**；
 *       它**不是**请求参数（DES-004 §3.1 的 {@code mode} 不承担该语义）。</li>
 * </ol>
 *
 * <p><b>序列化走移植版 {@link LegacyJsonKit}（jfinal {@code JFinalJson} 等价）</b>：不引第二套
 * JSON 语义 —— {@code btnList} 里是 {@code Button} 模型（{@code EovaModel}），Jackson 直接序列化
 * 会得到一堆内部字段而非"模型属性"（旧栈由 jfinal 的 JsonKit 处理）。</p>
 */
@RestController
public class PageBootstrapController {

    private static final Logger log = LoggerFactory.getLogger(PageBootstrapController.class);

    /** 缺菜单编码时的文案（前端据此降级，且判据据此分辨"参数问题"与"菜单不存在"） */
    static final String MSG_NO_MENU_CODE = "缺少菜单编码: body.menu 或 body.path 末段";

    private final PageBootstrapAssembler assembler = new PageBootstrapAssembler();

    /**
     * 页面引导数据端点。
     *
     * @param body     请求体（DES-004 §3.1 的 6 个键；允许为空）
     * @param request  请求
     * @param response 响应
     * @throws IOException 写响应失败
     */
    @PostMapping("/api/page/bootstrap")
    public void bootstrap(@RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        // ① 会话：未登录 ⇒ 401（形态与旧栈 LoginInterceptor 的 ctrl.renderError(401) 完全一致）
        User user = sessionUser(request);
        if (user == null) {
            LegacyRenderManager.getRenderFactory().getErrorRender(401)
                    .setContext(request, response).render();
            return;
        }

        // ② 菜单编码（口径见类注释）
        String menuCode = menuCode(body);
        if (menuCode == null) {
            writeJson(response, PageBootstrapAssembler.fail(MSG_NO_MENU_CODE));
            return;
        }

        // ③ 装配（查库在 assembler 内：菜单/元对象/按角色的按钮）
        boolean isQuery = isQuery(menuCode);
        LegacyKv payload = assembler.assemble(menuCode, user, isQuery);
        if (!PageBootstrapAssembler.STATE_OK.equals(payload.get("state"))) {
            log.info("页面引导失败：menu={} user={} msg={}", menuCode, user.get("id"), payload.get("msg"));
        }
        writeJson(response, payload);
    }

    /**
     * 取会话用户：Cookie {@code eovasid} → {@link LoginService#loginBySid}（与 LoginInterceptor 同源）。
     *
     * @param request 请求
     * @return 会话用户；未登录返回 null
     */
    private static User sessionUser(HttpServletRequest request) {
        String sid = RequestUtil.getCookieStr(request, LoginService.CKSID, null);
        if (sid == null || sid.isEmpty()) {
            return null;
        }
        return biz.login.loginBySid(sid, WebUtil.getRealIp(request));
    }

    /**
     * 解析菜单编码：{@code body.menu} 优先，其次 {@code body.path} 的末段（旧栈页面 URL {@code /app/<menuCode>}）。
     *
     * @param body 请求体
     * @return 菜单编码；解析不出返回 null
     */
    static String menuCode(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        String menu = str(body.get("menu"));
        if (menu != null) {
            return menu;
        }
        String path = str(body.get("path"));
        if (path == null) {
            return null;
        }
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        String last = path.substring(path.lastIndexOf('/', end - 1) + 1, end);
        return last.isEmpty() ? null : last;
    }

    /**
     * 取字符串（空白视为缺失）
     *
     * @param value 原值
     * @return 去空白后的字符串；空白/非字符串返回 null
     */
    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * isQuery 口径：{@code MetaObject.dao.isExistQuery(objectCode)}（与旧 SingleController 同源）。
     *
     * @param menuCode 菜单编码
     * @return 是否可查询；菜单不存在或元对象编码缺失时 false（失败载荷由 assembler 给出）
     */
    private static boolean isQuery(String menuCode) {
        Menu menu = Menu.dao.findByCode(menuCode);
        if (menu == null) {
            return false;
        }
        String objectCode = menu.getMenuConfig().getStr("object_code");
        return objectCode != null && MetaObject.dao.isExistQuery(objectCode);
    }

    /**
     * 写 JSON 响应：200 + {@code application/json;charset=UTF-8} + {@code Cache-Control: no-store}。
     *
     * @param response 响应
     * @param payload  载荷（{@link LegacyJsonKit} 序列化）
     * @throws IOException 写失败
     */
    private static void writeJson(HttpServletResponse response, Object payload) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        // DES-004 §3.1：载荷含权限上下文与逐次导航结果 ⇒ 不得缓存
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(LegacyJsonKit.toJson(payload));
    }
}
