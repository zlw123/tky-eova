/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **动作页引导数据面判据**（第 299 轮，`docs/DES-004-R2-action-page-bootstrap.md`）。
 *
 * <p><b>钉的两件事</b>：</p>
 * <ol>
 *   <li><b>对象驱动分支</b>：{@code POST /api/page/bootstrap} 的 body 里 {@code object} 非空
 *       （且 {@code menu} 为空）时，按**元对象**装配 —— 动作页
 *       {@code /app/{add,update,detail}/<object_code>} 的 URL 末段是**元对象编码**，
 *       而既有的 `path` 末段口径会把它当**菜单编码**（实测：返回 {@code menu:{code:'meta_product'}} + btnList）⇒ 误解析。
 *       参见 {@code docs/DES-004-R2-…} §1.1；兑现的是 `DES-004 §3.1` **已声明**的 `object?` 键。</li>
 *   <li>★ <b>{@code object.id} 补口</b>：旧栈模板 {@code _page/form.html} 写的是
 *       {@code object_id: '#(object.id)'}（旧载荷本来就有），而 ported 的 {@code objectKv}
 *       只下发 5 键、**没有 {@code id}** ⇒ 新栈前端三张模板页读到的 {@code object['id']} 恒为
 *       undefined，冻结脚本据此拼出 {@code ?id=undefined}（超管面板"元对象编辑"入口一直是坏的）。
 *       见 §1.2。</li>
 * </ol>
 *
 * <p><b>期望值是外部事实</b>：真库 {@code eova_meta.eova_object} 实查
 * （{@code eova_object_code}: id=3 / pk_name=id / table_name=eova_object / data_source=eova；
 * {@code meta_product}: id=1223 / pk_name=id / table_name=product / data_source=main）
 * ＋ 旧栈渲染期插值的字段名（{@code #(object.id)} / {@code #(object.pk)}）。
 * 会话账号 {@code eova} 的 {@code rid=1} 且 {@code EovaConst.ADMIN_RID=1} ⇒ {@code isAdmin=true}。</p>
 */
// ★ 与同模块其它判据共用同一上下文配置（旧引导是"每 JVM 一次"语义，见 LegacyWebBootstrapTest 注释）
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PageBootstrapObjectHttpTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 有自定义表单、且 `data_source=main`（第二库）的元对象 */
    private static final String OBJ_PRODUCT = "meta_product";
    /** 主库元对象（菜单 `eova_meta` 指向它） */
    private static final String OBJ_OBJECT_CODE = "eova_object_code";
    /** 菜单（菜单驱动对照用） */
    private static final String MENU = "eova_meta";

    @Autowired
    private TestRestTemplate rest;

    /** 登录并返回会话 Cookie（与同模块其它判据同一真值账号） */
    private String login() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> resp = rest.postForEntity("/user/doLogin",
                new HttpEntity<>("login_id=eova&login_pwd=000000", h), String.class);
        assertEquals(200, resp.getStatusCode().value(), "登录必须 200，实际=" + resp.getStatusCode());
        String sid = resp.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("eovasid=")).findFirst().orElseThrow();
        return sid.substring(0, sid.indexOf(';') > 0 ? sid.indexOf(';') : sid.length());
    }

    /**
     * 带/不带会话 POST 引导端点
     *
     * @param sid  会话 Cookie（null ⇒ 不带头）
     * @param body 请求体 JSON
     * @return 响应
     */
    private ResponseEntity<String> post(String sid, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (sid != null) {
            h.add(HttpHeaders.COOKIE, sid);
        }
        return rest.exchange("/api/page/bootstrap", HttpMethod.POST, new HttpEntity<>(body, h),
                String.class);
    }

    /**
     * 解析响应体
     *
     * @param resp 响应
     * @return 载荷
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> bodyOf(ResponseEntity<String> resp) {
        try {
            return JSON.readValue(resp.getBody(), Map.class);
        } catch (Exception e) {
            throw new AssertionError("响应不是 JSON：" + resp.getBody(), e);
        }
    }

    @Test
    @DisplayName("★ BO-1：对象驱动 ⇒ 只含 state/object/loginUser（动作页**没有**菜单面）")
    void objectDrivenPayloadHasNoMenuSurface() {
        Map<String, Object> body = bodyOf(post(login(), "{\"object\":\"" + OBJ_PRODUCT + "\"}"));
        assertEquals("ok", body.get("state"), "必须 state=ok：" + body);
        assertEquals(java.util.Set.of("state", "object", "loginUser"), body.keySet(),
                "★ 动作页载荷只能有这三个键（旧 AppController#add/update/detail 就没有 menu/btnList）");

        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) body.get("object");
        assertNotNull(object, "必须含 object");
        assertEquals(OBJ_PRODUCT, object.get("code"));
        assertEquals("Meta产品", object.get("name"), "object.name 必须是真库值");
        assertEquals("id", object.get("pk_name"), "object.pk_name ← getPk()");
        assertEquals("main", object.get("data_source"), "object.data_source ← getDs()（meta_product 在第二库）");

        @SuppressWarnings("unchecked")
        Map<String, Object> loginUser = (Map<String, Object>) body.get("loginUser");
        assertNotNull(loginUser, "必须含 loginUser（旧栈由 LoginInterceptor:123 提供）");
        assertEquals(Boolean.TRUE, loginUser.get("isAdmin"), "eova 是 rid=1 ⇒ isAdmin=true");
    }

    @Test
    @DisplayName("★ BO-2（反向）：菜单驱动仍下 menu/btnList/isQuery —— 两种形状**不同**，不是一条路")
    void menuDrivenStillHasMenuSurface() {
        Map<String, Object> body = bodyOf(post(login(), "{\"menu\":\"" + MENU + "\"}"));
        assertEquals("ok", body.get("state"), "必须 state=ok：" + body);
        for (String key : new String[] {"menu", "menuCode", "btnList", "isQuery"}) {
            assertTrue(body.containsKey(key), "菜单驱动必须含 " + key + "：" + body.keySet());
        }
        // ★ 两种驱动的键集合必须**不同**（否则"对象驱动"只是把菜单分支复制了一遍）
        Map<String, Object> objectBody = bodyOf(post(login(), "{\"object\":\"" + OBJ_PRODUCT + "\"}"));
        assertFalse(objectBody.keySet().equals(body.keySet()),
                "★ 对象驱动与菜单驱动的键集合不得相同");
    }

    @Test
    @DisplayName("★ BO-3：object.id == 真库 eova_object.id —— 两个不同对象**两向**钉住（防恒返回同一值）")
    void objectIdComesFromRealDb() {
        String sid = login();
        // 对象驱动
        @SuppressWarnings("unchecked")
        Map<String, Object> product = (Map<String, Object>) bodyOf(
                post(sid, "{\"object\":\"" + OBJ_PRODUCT + "\"}")).get("object");
        assertEquals(1223, ((Number) product.get("id")).intValue(),
                "★ meta_product 的 id 必须 = 真库 1223（缺它前端 uzoo.page.object_id 恒 undefined）");
        // 菜单驱动（既有路径也必须带上 id —— 三张模板页读的就是它）
        @SuppressWarnings("unchecked")
        Map<String, Object> viaMenu = (Map<String, Object>) bodyOf(
                post(sid, "{\"menu\":\"" + MENU + "\"}")).get("object");
        assertEquals(OBJ_OBJECT_CODE, viaMenu.get("code"));
        assertEquals(3, ((Number) viaMenu.get("id")).intValue(),
                "★ 菜单载荷的 object.id 必须 = 真库 3（TemplateTable/Tree/TreeTable 三处读它）");
    }

    @Test
    @DisplayName("★ BO-4：`menu` 与 `object` 同时给 ⇒ 按**菜单**（既有优先序不变）")
    void menuWinsWhenBothGiven() {
        Map<String, Object> body = bodyOf(
                post(login(), "{\"menu\":\"" + MENU + "\",\"object\":\"" + OBJ_PRODUCT + "\"}"));
        assertEquals("ok", body.get("state"), body.toString());
        assertTrue(body.containsKey("menu"), "★ 两者同时给必须按菜单（D4 优先序）");
        @SuppressWarnings("unchecked")
        Map<String, Object> menu = (Map<String, Object>) body.get("menu");
        assertEquals(MENU, menu.get("code"), "菜单编码必须是请求里的 menu，而不是 object");
    }

    @Test
    @DisplayName("★ BO-5：未登录 ⇒ 401（对象驱动分支**不得**绕过鉴权）")
    void objectDrivenStillRequiresSession() {
        ResponseEntity<String> resp = post(null, "{\"object\":\"" + OBJ_PRODUCT + "\"}");
        assertEquals(401, resp.getStatusCode().value(), "未登录必须 401，实际=" + resp.getStatusCode());
        assertTrue(!String.valueOf(resp.getBody()).contains("\"object\""),
                "★ 未登录不得下发引导数据，实际=" + resp.getBody());
    }

    @Test
    @DisplayName("★ BO-6：未知元对象 ⇒ 500（与 `MetaService#getMeta` 的既有 NPE 形态同族，非可读降级）")
    void unknownObjectKeepsLegacyFailureShape() {
        ResponseEntity<String> resp = post(login(), "{\"object\":\"no_such_object_xyz\"}");
        assertEquals(500, resp.getStatusCode().value(),
                "★ 未知元对象必须 500（既有形态；`MetaFormHttpTest` 同码），实际=" + resp.getStatusCode());
        // 反向：正常对象不得 500（否则上一条会被"一律 500"满足）
        assertEquals(200, post(login(), "{\"object\":\"" + OBJ_PRODUCT + "\"}").getStatusCode().value(),
                "正常对象必须 200");
    }

    @Test
    @DisplayName("★ BO-7：只给 `path`（不带 object）仍按**菜单**解析 —— 记录 D5 的歧义面")
    void pathOnlyStillResolvesAsMenu() {
        // ★ 这条**有意钉住既有行为**（DES-004-R2 D5）：动作页的 path 末段是元对象编码，
        //   而该口径把它当菜单编码 ⇒ 会返回菜单载荷。前端据此必须显式带 `object`
        //   （由前端判据 `form-page` 一侧钉住）。单方面改这里会与 S4-5 的既有口径冲突。
        Map<String, Object> body = bodyOf(post(login(), "{\"path\":\"/app/add/" + OBJ_PRODUCT + "\"}"));
        assertEquals("ok", body.get("state"), body.toString());
        assertTrue(body.containsKey("menu"), "既有行为：只给 path ⇒ 菜单驱动");
        @SuppressWarnings("unchecked")
        Map<String, Object> menu = (Map<String, Object>) body.get("menu");
        assertEquals(OBJ_PRODUCT, menu.get("code"),
                "既有行为：取 path 末段当菜单编码（这正是前端必须显式带 object 的原因）");
        List<?> btnList = (List<?>) body.get("btnList");
        assertNotNull(btnList, "既有行为：菜单驱动会带 btnList");
    }
}
