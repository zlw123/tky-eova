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
 * **切片 S4 判据：页面引导端点 {@code POST /api/page/bootstrap} 的 HTTP 契约**（DES-004 §3.1）。
 *
 * <p><b>期望值是外部事实</b>（真库 baseline，MySQL 13306），全部来自 SQL 实查而非"看代码推"：</p>
 * <ul>
 *   <li>菜单 {@code eova_meta}（元数据管理）⇒ {@code config.object_code = eova_object_code}，
 *       该元对象在 {@code eova_field} 里有 <b>5</b> 个 {@code is_query=1} 字段 ⇒ {@code isQuery=true}；</li>
 *   <li>菜单 {@code meta_product} ⇒ {@code object_code = meta_product}，{@code is_query=1} 字段 <b>0</b> 个
 *       ⇒ {@code isQuery=false}（★ 两向都钉，才能证伪"恒为 true/false"）；</li>
 *   <li>{@code eova_meta} 有 <b>7</b> 个按钮，角色 {@code rid=1} 全部受权 ⇒ 超管应收到 <b>7</b> 条；</li>
 *   <li>驾驶账号 {@code eova}：{@code rid=1}，而 {@code EovaConst.ADMIN_RID=1} ⇒ {@code loginUser.isAdmin=true}。</li>
 * </ul>
 *
 * <p><b>断言（7 条互为独立面）</b>：未登录 ⇒ 401 且不下发部分数据 · 正常装配全字段 · 按角色的按钮集合 ·
 * isQuery 两向 · path 末段可当菜单编码 + {@code Cache-Control: no-store} · 菜单不存在 ⇒ 200+state=no ·
 * 缺菜单编码 ⇒ 200+state=no。</p>
 */
// ★ 与同模块其它判据共用同一上下文配置（旧引导是"每 JVM 一次"语义，见 LegacyWebBootstrapTest 注释）
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PageBootstrapHttpTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 有查询字段的菜单 ⇒ isQuery=true */
    private static final String MENU_QUERYABLE = "eova_meta";
    /** 无查询字段的菜单 ⇒ isQuery=false */
    private static final String MENU_NOT_QUERYABLE = "meta_product";
    /** eova_meta 的按钮总数（真库 eova_button.menu_code='eova_meta'） */
    private static final int BTN_COUNT = 7;

    @Autowired
    private TestRestTemplate rest;

    /** 登录并返回会话 Cookie（与 S2b 判据同一真值账号） */
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
    private ResponseEntity<String> postBootstrap(String sid, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (sid != null) {
            h.add(HttpHeaders.COOKIE, sid);
        }
        return rest.exchange("/api/page/bootstrap", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    @Test
    @DisplayName("★ S4-1：未登录 ⇒ 401，且不返回任何引导数据（含权限面，不得部分下发）")
    void requiresSession() {
        ResponseEntity<String> resp = postBootstrap(null, "{\"menu\":\"" + MENU_QUERYABLE + "\"}");
        assertEquals(401, resp.getStatusCode().value(),
                "★ 未登录必须 401（DES-004 §3.1），实际=" + resp.getStatusCode());
        String body = String.valueOf(resp.getBody());
        assertTrue(!body.contains("\"state\":\"ok\"") && !body.contains("eova_object_code"),
                "★ 未登录不得下发部分引导数据，实际正文=" + body);
    }

    @Test
    @DisplayName("★ S4-2：登录后按 menu 装配 ⇒ state=ok + object/menu/menuCode/btnList/loginUser/isQuery 全字段")
    void assemblesBootstrapPayload() throws Exception {
        String sid = login();
        ResponseEntity<String> resp = postBootstrap(sid, "{\"menu\":\"" + MENU_QUERYABLE + "\"}");
        assertEquals(200, resp.getStatusCode().value(), "带会话必须 200，实际=" + resp.getStatusCode());

        Map<?, ?> body = JSON.readValue(resp.getBody(), Map.class);
        assertEquals("ok", body.get("state"), "信封 state 必须为 ok：" + resp.getBody());
        assertEquals(MENU_QUERYABLE, body.get("menuCode"), "menuCode 必须回显请求的菜单编码");

        Map<?, ?> object = (Map<?, ?>) body.get("object");
        assertNotNull(object, "必须含 object（旧 setAttr(\"object\", object)）");
        assertEquals("eova_object_code", object.get("code"), "★ object.code 必须与真库 eova_object 一致");
        assertNotNull(object.get("name"), "object.name 必须有值（真库 eova_object.name）");
        assertNotNull(object.get("table"), "object.table 必须有值");

        Map<?, ?> menu = (Map<?, ?>) body.get("menu");
        assertNotNull(menu, "必须含 menu");
        assertEquals(MENU_QUERYABLE, menu.get("code"), "menu.code 必须是该菜单编码");

        Map<?, ?> loginUser = (Map<?, ?>) body.get("loginUser");
        assertNotNull(loginUser, "必须含 loginUser（权限上下文，随会话变化）");
        assertEquals(Boolean.TRUE, loginUser.get("isAdmin"),
                "★ eova(rid=1) 是超管（EovaConst.ADMIN_RID=1）⇒ isAdmin 必须 true");
    }

    @Test
    @DisplayName("★ S4-3：btnList 必须按会话角色查（eova_meta 7 个按钮、rid=1 全受权 ⇒ 7 条，且 ui 是字符串）")
    void btnListComesFromRole() throws Exception {
        String sid = login();
        ResponseEntity<String> resp = postBootstrap(sid, "{\"menu\":\"" + MENU_QUERYABLE + "\"}");
        Map<?, ?> body = JSON.readValue(resp.getBody(), Map.class);
        List<?> btnList = (List<?>) body.get("btnList");
        assertNotNull(btnList, "必须含 btnList（旧 setAttr(\"btnList\", btnList)）");
        assertEquals(BTN_COUNT, btnList.size(),
                "★ 按钮数必须等于该菜单的按钮数（真库 eova_button.menu_code='eova_meta' ⇒ 7）");
        for (Object b : btnList) {
            Map<?, ?> btn = (Map<?, ?>) b;
            // 模型必须被序列化成"属性集合"，而不是对象内部结构（旧栈由 jfinal JsonKit 处理）
            assertTrue(btn.containsKey("ui"), "★ btnList 每项必须是模型属性（含 ui），实际键=" + btn.keySet());
        }
    }

    @Test
    @DisplayName("★ S4-4：isQuery 两向都钉 —— eova_meta(5 个查询字段)=true、meta_product(0 个)=false")
    void isQueryFollowsMetaObjectFields() throws Exception {
        String sid = login();

        Map<?, ?> q = JSON.readValue(postBootstrap(sid, "{\"menu\":\"" + MENU_QUERYABLE + "\"}").getBody(), Map.class);
        assertEquals(Boolean.TRUE, q.get("isQuery"),
                "★ eova_meta 的元对象有 5 个 is_query=1 字段 ⇒ isQuery 必须 true（旧 SingleController 同口径）");

        Map<?, ?> nq = JSON.readValue(
                postBootstrap(sid, "{\"menu\":\"" + MENU_NOT_QUERYABLE + "\"}").getBody(), Map.class);
        assertEquals("ok", nq.get("state"), "meta_product 也必须装配成功：" + nq);
        assertEquals(Boolean.FALSE, nq.get("isQuery"),
                "★ meta_product 的元对象 0 个查询字段 ⇒ isQuery 必须 false（否则说明该字段是写死的）");
    }

    @Test
    @DisplayName("★ S4-5：Cache-Control: no-store（含权限面与逐次导航结果，不得缓存）+ path 末段可当菜单编码")
    void noStoreAndPathFallback() throws Exception {
        String sid = login();
        ResponseEntity<String> resp = postBootstrap(sid, "{\"menu\":\"" + MENU_QUERYABLE + "\"}");
        assertEquals("no-store", resp.getHeaders().getCacheControl(),
                "★ DES-004 §3.1：含权限面 ⇒ Cache-Control 必须是 no-store，实际="
                        + resp.getHeaders().getCacheControl());

        // 旧栈页面 URL 形如 /app/<menuCode>（AuthUri 硬编码 "/app/#(menu.code)"）⇒ path 末段即菜单编码
        Map<?, ?> viaPath = JSON.readValue(
                postBootstrap(sid, "{\"path\":\"/app/" + MENU_QUERYABLE + "\"}").getBody(), Map.class);
        assertEquals("ok", viaPath.get("state"), "path 末段应可当菜单编码：" + viaPath);
        assertEquals(MENU_QUERYABLE, viaPath.get("menuCode"), "menuCode 应等于 path 末段");
    }

    @Test
    @DisplayName("★ S4-7：body 里没有菜单编码 ⇒ 200 + state=no + 可读文案（不得 500，也不得装配半个页面）")
    void missingMenuCodeFailsSoftly() throws Exception {
        String sid = login();
        ResponseEntity<String> resp = postBootstrap(sid, "{}");
        assertEquals(200, resp.getStatusCode().value(),
                "参数问题也是业务失败（200 + state=no），实际=" + resp.getStatusCode());
        Map<?, ?> body = JSON.readValue(resp.getBody(), Map.class);
        assertEquals("no", body.get("state"), "必须 state=no：" + resp.getBody());
        assertTrue(String.valueOf(body.get("msg")).contains("缺少菜单编码"),
                "★ 文案必须指明是参数问题（便于与'菜单不存在'区分），实际=" + body.get("msg"));
    }

    @Test
    @DisplayName("★ S4-6：菜单不存在 ⇒ 200 + state=no + 可读文案（前端据此降级，而不是 500）")
    void unknownMenuFailsSoftly() throws Exception {
        String sid = login();
        ResponseEntity<String> resp = postBootstrap(sid, "{\"menu\":\"no_such_menu_code\"}");
        assertEquals(200, resp.getStatusCode().value(),
                "业务失败也返回 200（前端按 state 判定），实际=" + resp.getStatusCode());
        Map<?, ?> body = JSON.readValue(resp.getBody(), Map.class);
        assertEquals("no", body.get("state"), "必须 state=no：" + resp.getBody());
        assertTrue(String.valueOf(body.get("msg")).contains("菜单不存在"),
                "★ 文案必须可读（旧契约 renderMsg/Ret.fail 风格），实际=" + body.get("msg"));
    }

    /**
     * **页面自有引导数据**：`/meta/reorder` 的排序项（第 305 轮 · 真缺陷 P-REORDER 回归锁）。
     *
     * <p>旧栈该页的数据是**服务端注入**的（`MetaController#reorder()` 的 `set(data, tps)`；
     * 模板 `#json(data)` 塞进 `uzoo.app.data`）⇒ 分离后必须由引导端点给 `pageParams.data`。
     * 缺它时前端 `MetaReorder.vue` **显式回退 `[]`**（诚实降级）⇒ 页面全空：
     * 实测修前新栈正文 0 字符，旧栈 104（`ID #1 名称 #2 产品类型 #3 …`）。</p>
     *
     * <p>★ 为什么钉在 **HTTP 层**：本修复踩过一个只有端到端才能暴露的坑 ——
     * 该页请求同时带 `object`（元对象编码），若把本分支排在"带 object 就走动作页装配"之后，
     * 它会**永不可达**（实测：响应变成动作页的 object 载荷，`pageParams` 为空）。
     * 装配器自身的判据看不见"分支顺序"，只有打这条 URL 才看得见。</p>
     */
    @Test
    @DisplayName("★ S4-8：/meta/reorder ⇒ pageParams.data 为 {id,name,num} 列表（旧 set(data) 的等价物）")
    void assemblesReorderPageParams() throws Exception {
        String sid = login();
        ResponseEntity<String> resp = postBootstrap(sid,
                "{\"path\":\"/meta/reorder\",\"object\":\"meta_product\",\"biz\":\"field\"}");
        assertEquals(200, resp.getStatusCode().value(), "带会话必须 200，实际=" + resp.getStatusCode());
        Map<?, ?> body = JSON.readValue(resp.getBody(), Map.class);
        assertEquals("ok", body.get("state"), "信封 state 必须为 ok：" + resp.getBody());

        Map<?, ?> params = (Map<?, ?>) body.get("pageParams");
        assertNotNull(params, "必须含 pageParams（否则前端回退成空列表 ⇒ 页面全空）");
        assertEquals("field", params.get("biz"), "biz 必须回带（旧 set(biz, biz)）");
        java.util.List<?> rows = (java.util.List<?>) params.get("data");
        assertNotNull(rows, "pageParams.data 必须在");
        assertFalse(rows.isEmpty(), "meta_product 的元字段列表不得为空（基线库 14 个字段）");
        Map<?, ?> first = (Map<?, ?>) rows.get(0);
        assertEquals(java.util.Set.of("id", "name", "num"), new java.util.HashSet<>(first.keySet()),
                "每项必须是 {id, name, num}（旧实现就是这三个键）");
    }

    @Test
    @DisplayName("★ S4-8b：/meta/reorder 的 biz 缺省为 field（旧 get(biz, field)），且不得落到动作页装配分支")
    void reorderPageParamsDefaultBiz() throws Exception {
        String sid = login();
        ResponseEntity<String> resp = postBootstrap(sid, "{\"path\":\"/meta/reorder\",\"object\":\"meta_product\"}");
        Map<?, ?> body = JSON.readValue(resp.getBody(), Map.class);
        Map<?, ?> params = (Map<?, ?>) body.get("pageParams");
        assertNotNull(params, "缺 biz 时也必须走页面自有引导数据分支（不得落到动作页装配）");
        assertEquals("field", params.get("biz"), "缺省场景必须是 field");
    }

    @Test
    @DisplayName("★ r311：登录页配置在**匿名**下可取（旧栈由服务端渲染注入，SPA 必须经本端点拿到）")
    void loginPageConfIsAnonymousAndComesFromConfig() throws Exception {
        // ★ 本端点的默认形态是"未登录 ⇒ 401"（旧 LoginInterceptor）。登录页天然未登录，
        //   故 `/user/login` 是**唯一**豁免路径 —— 本判据同时钉住"豁免存在"与"豁免不外溢"。
        ResponseEntity<String> resp = postBootstrap(null, "{\"path\":\"/user/login\"}");
        assertEquals(200, resp.getStatusCode().value(), "登录页配置必须匿名可取（否则前端只剩硬编码默认值）");
        Map<?, ?> body = JSON.readValue(resp.getBody(), Map.class);

        assertEquals("ok", body.get("state"));
        // 取值与配置同源（`x.conf`）：本环境 `isCaptcha=false` ⇒ 与旧栈登录页"不显示验证码"一致
        Object isCaptcha = body.get("isCaptcha");
        assertNotNull(isCaptcha, "必须下发 isCaptcha（旧模板 `#(isCaptcha??false)`）");
        assertEquals(cn.eova.tools.x.conf.getBool("isCaptcha", true), isCaptcha,
                "isCaptcha 必须与配置同源（不得另发明默认值）");
        // 版权：`app.copyright` 为空时是 `© 2015-<今年> EOVA.CN`（旧 login() 的兜底文案）
        Object cp = body.get("copyright");
        assertNotNull(cp, "必须下发 copyright（旧栈页脚 `.eova-footer` 靠它）");
        assertTrue(String.valueOf(cp).startsWith("© 2015-"),
                "copyright 兜底形态必须与旧实现一致，实际=" + cp);
        // 开发免输（旧 set("login_id"/"login_pwd")）
        assertTrue(body.containsKey("login_id") && body.containsKey("login_pwd"),
                "必须含 dev.login_id/dev.login_pwd 两个键（旧栈渲染进输入框）");
        // 豁免不外溢：非登录页仍必须 401
        ResponseEntity<String> other = postBootstrap(null, "{\"path\":\"/app/meta_product\"}");
        assertEquals(401, other.getStatusCode().value(),
                "★ 匿名豁免只允许 `/user/login`：其它页面必须仍然 401");
    }
}
