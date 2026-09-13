/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

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
 * **U2 判据：旧模板渲染面的「实跑事实表」**（= 退役 {@code com.jfinal:enjoy} 的真实前置清单）。
 *
 * <p><b>为什么需要这张表</b>：U1 退役了 15 处页面入口后，"还剩下哪些 URL 真的会渲染旧 Enjoy 模板"
 * 只能**实测**得出 —— 读代码会得出错误结论。实测反例（本轮真实踩到）：</p>
 * <ul>
 *   <li>19 个 {@code render("…html")} 渲染点里，**17 个的模板文件在全仓根本不存在**
 *       （{@code code.html}、{@code menu/add.html}、{@code widget/find/find.html} …）⇒ 它们在
 *       **旧栈和新栈都是 500**，是**死入口**（不是"待迁移的页面"）；</li>
 *   <li>3 个渲染点所在控制器（{@code WidgetCtrl}/{@code FormController}/{@code SingleController}）
 *       在**两侧都没有注册路由** ⇒ 那些动作本身就不可达；</li>
 *   <li>真正仍活着的旧模板页**只有两个**：{@code /auth[/rid]}（角色授权）与
 *       {@code /excel/imports/<objectCode>}（Excel 导入）。</li>
 * </ul>
 *
 * <p><b>本判据钉什么</b>：把上表的**分类**逐一钉死在 HTTP 层，使"退役了多少、还剩多少"无法悄悄漂移 ——
 * 页面被接管成壳、死入口被补上模板、或 urlPara 规则被放宽，都会让对应断言变红（那时必须**显式改表**）。</p>
 *
 * <p><b>旧栈真值来源</b>：带会话直连 {@code 127.0.0.1:9090}（旧 demo）逐 URL 实测，记在各断言注释里。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegacyTemplateFaceHttpTest {

    @Autowired
    private TestRestTemplate rest;

    /** 登录（旧 demo 实测真值），返回带会话的请求头 */
    private HttpHeaders session() {
        HttpHeaders form = new HttpHeaders();
        form.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> resp = rest.postForEntity("/user/doLogin",
                new HttpEntity<>("login_id=eova&login_pwd=000000", form), String.class);
        assertEquals(200, resp.getStatusCode().value(), "登录必须 200（本判据依赖真库 baseline）");
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, String.valueOf(resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE)));
        return h;
    }

    private ResponseEntity<String> get(HttpHeaders h, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    // ---------------------------------------------------------------- ① 仍活着的旧模板页

    @Test
    @DisplayName("★ U2-1：仍渲染旧 Enjoy 模板的页面（退役前置清单的**存活项**）")
    void legacyLiveTemplatePagesStillRender() {
        HttpHeaders h = session();

        // `/excel/imports/<objectCode>`：旧栈带会话实测 200 + <title>导入酒店数据</title>；
        //    模板 `<视图根>/excel/import/app.html`（**不以 /eova/ 开头 ⇒ 不经 _view 重写**）。
        //    urlPara = objectCode（`ExcelController#imports()` 的 `get(0)`）。
        //    ★ U2 结束后这是**唯一**仍渲染旧模板的页面 URL。
        ResponseEntity<String> excel = get(h, "/excel/imports/sys_hotel");
        assertEquals(200, excel.getStatusCode().value(), "/excel/imports/<code> 必须 200（旧栈同）");
        String excelBody = String.valueOf(excel.getBody());
        assertTrue(excelBody.contains("导入酒店数据"), "正文必须来自旧 Excel 导入模板，实际前 80 字="
                + excelBody.substring(0, Math.min(80, excelBody.length())));
        assertFalse(excelBody.contains("eova-assets/"), "尚未迁移 ⇒ 不得被 SPA 壳接管");
    }

    @Test
    @DisplayName("★ U2-9：`/auth` 页面入口**已迁移**（旧栈 200 旧模板页 ⇒ 新栈壳，SPA 走旧原路径 /auth/:rid）")
    void roleAuthPageEntryWasMigrated() {
        HttpHeaders h = session();
        // 取证（旧栈带会话实测）：`/auth`、`/auth/1248` 都是 200「功能权限分配」；
        //   `/eova/role/auth/1` 与 `/role/auth/1` 都是 **404** ⇒ 旧页面 URL 是 `/auth/<rid>`。
        // SPA 侧 `views/role/RoleAuth.vue` 早已按 `AuthController#index/data/doAuth` 逐条迁移，
        //   但注册在 `/eova/role/auth/:rid`（把**模板路径**当 URL）⇒ 生产/开发都拿不到它。
        // 处置：SPA 路由改回 `/auth/:rid` + `AuthController#index()` 退役为壳（本判据钉住"已迁移"）。
        for (String path : new String[]{"/auth", "/auth/1248"}) {
            ResponseEntity<String> resp = get(h, path);
            assertEquals(200, resp.getStatusCode().value(), path + " 必须 200（壳）");
            String body = String.valueOf(resp.getBody());
            assertTrue(body.contains("eova-assets/"), path + " 必须返回 SPA 壳（页面入口已退役）");
            assertFalse(body.contains("功能权限分配"),
                    path + " 不得再返回旧模板页正文（否则说明退役被回退）");
        }
        // ★ 反向断言：`/auth` 前缀下的**动作**必须仍然可达（页面退役不得连带动作被吞）。
        //   按**真实调用形态**发（旧页 `app.js` 就是 `axios.post('/auth/data', {rid})`）：
        //   实测旧栈与新栈都是 200 + application/json，**字节数相同（24828）** ⇒ 动作契约未动。
        ResponseEntity<String> action = rest.exchange("/auth/data", HttpMethod.POST,
                new HttpEntity<>("{\"rid\":1248}", jsonHeaders(h)), String.class);
        assertEquals(200, action.getStatusCode().value(),
                "★ /auth/data 动作必须仍被 AuthController 处理（旧栈 200 JSON）");
        String actionBody = String.valueOf(action.getBody());
        assertTrue(String.valueOf(action.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/json"),
                "★ 动作必须仍是 JSON（不得变成壳 HTML）");
        assertTrue(actionBody.contains("\"btns\""), "★ 动作响应形状不变（旧栈含 btns/auths/roles）");
        assertFalse(actionBody.contains("eova-assets/"),
                "★ 动作不得返回 SPA 壳（那说明页面退役把同前缀动作一起吞了）");
    }

    /** JSON 请求头 + 会话 */
    private HttpHeaders jsonHeaders(HttpHeaders session) {
        HttpHeaders h = new HttpHeaders(session);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // ---------------------------------------------------------------- ② 死入口（模板缺失）

    @Test
    @DisplayName("★ U2-2：模板文件全仓缺失的入口 ⇒ **两侧同款 500**（死入口，不是待迁页面）")
    void brokenTemplateEntriesAreDeadInBothStacks() {
        HttpHeaders h = session();
        // 旧栈带会话实测（9090）与下列逐条一致：**全部 500**。
        // 新栈 500 的日志根因逐条为
        //   `File not found : ".../src/legacy/eova/_view/code.html"`（同理 upgrade/menu/flow/icon/…）。
        //
        // 为什么这条判据重要：若把"模板不存在"误判成"页面待迁移"，退役 enjoy 时就会漏掉真正的调用面；
        // 反过来，若哪天把缺的模板补上，本判据会红 ⇒ 强制把该面从"死入口"改成"活页面"并补判据。
        String[] dead = {
                "/code",                                  // IndexController#code() → /eova/code.html
                "/eova/admin/upgrade",                    // AdminController#upgrade() → /eova/admin/upgrade.html
                "/menu/icon",                             // MenuController#icon() → /eova/icon.html
                "/menu/flow",                             // MenuController#flow() → /eova/menu/flow.html
                "/button/quick/meta_product",             // ButtonController#quick() → /eova/button/quick.html（urlPara=menuCode）
                "/menu/toMenuFun/1",                      // MenuController#toMenuFun() → /eova/menu/menuFun.html（urlPara=id）
                "/menu/toUpdate/1",                       // MenuController#toUpdate() → /eova/menu/add.html
                "/meta/find/main-table",                  // MetaController#find() → /eova/widget/find/find.html（urlPara 按 `-` 切分）
        };
        // ★★ 判据**禁止调用**的 URL：**有写副作用**。
        //   `/meta/diy/<objectCode>` 的 `MetaController#diy()` 在渲染前会 `save("eova_diy", r)`
        //   **补写缺失字段**，然后才因为模板缺失而 500 ⇒ **"死入口"不等于"无副作用"**。
        //   本轮真实事故：首版把它列进本判据 ⇒ 每次调用写 14 行（meta_product 的 is_show=1 字段数）
        //   ⇒ `check-baseline-integrity.py` 第 7 步红（`eova_diy 实际=14 期望=0`）。
        //   处置：从 HTTP 判据里剔除（**只在静态清单里登记**），并在此处留下**防回归**断言。
        for (String forbidden : NEVER_CALL_OVER_HTTP) {
            for (String p : dead) {
                assertFalse(p.startsWith(forbidden),
                        "★ " + p + " 有写副作用，**不得**放进 HTTP 判据（会污染 baseline 库）");
            }
        }
        for (String path : dead) {
            assertEquals(500, get(h, path).getStatusCode().value(),
                    path + " 必须是 500（模板缺失；旧栈同）—— 若变绿说明模板被补上了，本表须显式更新");
        }
    }

    /**
     * ★ 判据**禁止调用**的 URL 前缀（有写副作用，调用即污染 baseline）。
     *
     * <p>{@code /meta/diy/<objectCode>?type=N}：`MetaController#diy()` 先按 `eova_field` 补写 `eova_diy`
     * （`save("eova_diy", r)`），**再**渲染缺失的模板 ⇒ 两侧 500 但两侧都写库。
     * 该 URL 的**写语义仍然活着**（冻结的 `eova.table.js` 会以 `'/meta/diy/' + object.code + '?type=1'`
     * 打开它）⇒ 退役 enjoy 时它属于"**写端点 + 死页面**"这一类，须单独裁定，不能当死入口直接删。</p>
     */
    private static final java.util.List<String> NEVER_CALL_OVER_HTTP = java.util.List.of("/meta/diy");

    // ---------------------------------------------------------------- ③ 未注册控制器的动作

    @Test
    @DisplayName("★ U2-3：控制器**两侧都未注册路由**的渲染点 ⇒ 404（那些动作本来不可达）")
    void unregisteredControllerEntriesAreNotFound() {
        HttpHeaders h = session();
        // `cn.eova.widget.form.FormController`（→ /eova/widget/form/*.html）、
        // `cn.eova.template.single.SingleController`（→ /eova/template/common/import.html）
        // 在**旧栈的 EovaWebRoutes 与新栈的 EovaWebRoutes 里都没有注册**（两侧路由表同为那 18 条 + demo 的 /test）
        // ⇒ 这些 render 调用是**死代码**。旧栈带会话实测同样 404。
        for (String path : new String[]{"/form/add", "/form/update", "/form/detail", "/form/diy", "/single/importXls"}) {
            assertEquals(404, get(h, path).getStatusCode().value(),
                    path + " 必须 404（对应控制器未注册路由；旧栈同）");
        }
        // `WidgetCtrl#find`（→ select/select_callbak/find 三个**片段**）同样未注册：
        // 旧栈 `/widget/find` 实测 200 但内容是**首页**（`/` 兜底路由的产物），**不是**那个片段；
        // 新栈 U1 后由 SPA 壳接管 `/widget`（登记在案）⇒ 两侧都不是"片段"语义。
        // 这里只钉"不得是旧 find 片段"，避免把兜底产物误当功能面。
        ResponseEntity<String> wf = get(h, "/widget/find");
        assertEquals(200, wf.getStatusCode().value(), "/widget/find 走兜底（旧栈落首页、新栈落壳）");
        assertFalse(String.valueOf(wf.getBody()).contains("eova-find"),
                "/widget/find 不得意外命中 WidgetCtrl#find 的片段");
    }

    // ---------------------------------------------------------------- ④ urlPara 只有一段

    @Test
    @DisplayName("★ U2-4：urlPara **只有一段**（多段 ⇒ 无对应动作键 ⇒ 404；多值用 `-` 分隔）")
    void urlParaIsSingleSegment() {
        HttpHeaders h = session();
        // ★ 这条是 U2 实跑抓出的**第二个移植缺口**：此前把 `main-table/1` 整段当 urlPara ⇒
        //   真的去渲染模板 ⇒ 500；而旧栈 `ActionMapping#getAction` 只把**一个**尾段当 urlPara
        //   ⇒ 「方法名 + 2 段」没有对应动作键 ⇒ **404**。
        assertEquals(404, get(h, "/meta/find/main-table/1").getStatusCode().value(),
                "★ 方法名之后 2 段 ⇒ 404（旧栈同）—— 不得当成多段 urlPara 去渲染");
        // 合法形态对照：单段 urlPara 必须仍然可用（防止"一刀切禁 urlPara"把合法的也禁掉）
        assertEquals(200, get(h, "/app/add/meta_product").getStatusCode().value(),
                "★ 单段 urlPara（/app/add/<menuCode>）必须仍然可达 —— 旧栈 200（渲染新增页，U1 后由壳接管）");
        assertEquals(200, get(h, "/excel/imports/sys_hotel").getStatusCode().value(),
                "★ 单段 urlPara 的活页面同样必须可达");
    }

    // ---------------------------------------------------------------- ⑤ demo 族（U1 已接管，旧真值登记）

    @Test
    @DisplayName("★ U2-5：demo 族 URL 现状（U1 壳接管）与**旧栈真值**的差异登记")
    void demoFamilyFacesAreShellsWithRegisteredDifferences() {
        HttpHeaders h = session();
        // 旧栈真值（带会话实测）：`/main` 200「EovaUI主题风格」(=_view/theme/index.html, 591 行)、
        //   `/theme` 200「Eova Meta 2026」(**首页** —— 旧栈没有独立 theme 页，是 `/` 兜底的产物)、
        //   `/widget` 200「EovaMeta 组件」、`/test` 200 **纯文本** `test index...`、
        //   `/test/sse` 200「SSE Demo」、`/ip` 200 **纯文本** IP、`/sso` **500**（模板 _view/login/login.html 缺失）。
        // 现状（U1 后）：这些 URL 一律返回 SPA 壳（`eova-assets/`）—— 属**已声明的口径④变更**。
        // 登记在案的差异（不在本轮修）：
        //   · `/main`：旧=主题页（模板在新栈 legacy 视图根**存在**，但**新后端无任何方法渲染它**）
        //     ⇒ 归属未定：SPA 实现该页（迁 591 行模板）或后端补渲染入口；SPA 侧目前只是 Placeholder。
        //   · `/theme`：SPA 路由**前提有误**（旧栈无此页）—— 需撤销或明确为 SPA 新增页。
        //   · `/ip`、`/test`：旧栈是**纯文本端点**，现被壳接管 ⇒ 语义已变（登记）。
        //   · `/sso`：旧栈本就 500（死页）⇒ 无损失。
        for (String path : new String[]{"/main", "/theme", "/widget", "/test", "/test/sse", "/ip"}) {
            ResponseEntity<String> resp = get(h, path);
            assertEquals(200, resp.getStatusCode().value(), path + " U1 后应为壳（200）");
            assertTrue(String.valueOf(resp.getBody()).contains("eova-assets/"),
                    path + " 必须是 SPA 壳（口径④：demo 工程 URL 归 SPA）");
        }
    }
}
