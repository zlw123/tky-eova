/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **切片 S6 判据：表单页的元数据端点 {@code POST /api/meta/form/<object_code>?mode=<mode>}**
 * （旧 {@code MetaControler#form()}，第 295 轮）。
 *
 * <p><b>为什么 S6（前端接管表单页）需要一个后端判据</b>：S6 的三个表单页组件本身几乎只是
 * "把参数交给冻结制品 {@code <ev-form>}"——真正决定**渲染哪些字段、什么顺序、哪些必填**的，
 * 是这个端点返回的载荷（{@code <ev-form>} 内部 {@code POST K.urls.url("meta_form", props)}，
 * 见 {@code eovaui.js} 的 {@code EvForm}）。若只测前端组件，"字段顺序"这一面就**没有任何外部事实**
 * ——组件测试里的期望值只能抄自己。故把该端点钉住，作为 S6 页面的字段契约。</p>
 *
 * <p><b>期望值全部是外部事实，且有两条独立来源</b>：</p>
 * <ol>
 *   <li><b>真库实查</b>（baseline MySQL {@code 127.0.0.1:13306}，库 {@code eova_meta}）：
 *       {@code select num,en,is_required from eova_field where object_code='…' order by num}
 *       —— 实现侧的排序口径来自源码 {@code MetaField.java:343}
 *       （{@code select * from eova_field where object_code = ? order by num}）；</li>
 *   <li><b>旧栈实跑对照</b>（2026-09-12，旧 demo {@code http://127.0.0.1:9090}，账号 {@code eova}）：
 *       同一请求在旧栈返回的 {@code nums}/{@code diyNums}/{@code object.code}/{@code pk_name}
 *       与新栈**逐项相同**；未登录 401 的信封也逐字相同（{@code {"state":"fail","msg":"401 Unauthorized"}}）。</li>
 * </ol>
 *
 * <p><b>★ 本轮测出来的一条关键事实（设计文档里原先写错的）</b>：{@code DES-005 §16.3} 的
 * "字段顺序 ← {@code eova_field} 的 {@code order_num}"**不存在该列**；真实机制是**两层**：</p>
 * <ul>
 *   <li><b>载荷顺序</b> = {@code order by num}（{@code MetaField.dao.queryByObjectCode}）；</li>
 *   <li><b>渲染顺序</b> = 制品按 {@code diy.num} 重排
 *       （{@code eovaui.js} 的 {@code p} computed：{@code t.value.sort((P,M)=>P.diy.num-M.diy.num)}）
 *       ⇒ <b>mode 会改变渲染顺序</b>（同一对象 create/update/read 的 {@code diy.num} 可以完全不同），
 *       而<b>字段集合</b>与 mode 无关。</li>
 * </ul>
 * <p>这两层都在下面被钉住（含"三个 mode 的 {@code diy.num} 序列互不相同"这条判别性断言）。</p>
 *
 * <p><b>仍未执行（不得计入完成）</b>：真浏览器里"按 {@code diy.num} 顺序渲染出 14 个字段"的
 * 视觉验证（需 S5 的浏览器验收链，本轮不在范围内）。</p>
 */
// ★ 与同模块其它判据共用同一上下文配置（旧引导是"每 JVM 一次"语义，见 LegacyWebBootstrapTest 注释）
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MetaFormHttpTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 有自定义表单（`custom-apps.ts` 登记 `meta_product` → `/product/app.vue`）的对象，且 `num` 序 ≠ `id` 序 */
    private static final String OBJ_PRODUCT = "meta_product";
    /** 无自定义表单的对象（`diy.num` 与 `num` 相同 ⇒ 两个 mode 的顺序一致） */
    private static final String OBJ_OBJECT_CODE = "eova_object_code";

    /**
     * {@code meta_product} 的载荷顺序（= 真库 `order by num`，14 条，`num` 无并列）
     */
    private static final String[] PRODUCT_EN_BY_NUM = {
        "id", "name", "type", "stock", "stuff", "category", "price", "score", "cost_score",
        "test_price", "sizes", "update_time", "create_time", "img"
    };

    /**
     * ★ 同一批字段按 **`id`** 排序的顺序（真库实查）—— 用来证伪"按 id 排也能过"。
     */
    private static final String[] PRODUCT_EN_BY_ID = {
        "id", "type", "category", "stuff", "sizes", "name", "img", "test_price", "price",
        "cost_score", "score", "stock", "create_time", "update_time"
    };

    /**
     * {@code meta_product} 的必填标记（真库 `is_required`，按 `num` 序）
     */
    private static final int[] PRODUCT_REQUIRED = {1, 1, 0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 1, 0};

    /**
     * ★ **渲染顺序**的来源：三个 mode 各自的 `diy.num`（真库 `eova_field_diy`，按 `num` 序读出的字段上的值）。
     *
     * 三者互不相同 ⇒ "mode 被忽略"或"用字段 `num` 当渲染顺序"都会被抓到。
     */
    private static final int[] PRODUCT_DIY_NUM_CREATE = {1, 6, 2, 11, 4, 3, 8, 10, 9, 7, 5, 13, 12, 14};
    private static final int[] PRODUCT_DIY_NUM_UPDATE = {1, 6, 2, 12, 4, 3, 9, 11, 10, 8, 5, 13, 14, 7};
    private static final int[] PRODUCT_DIY_NUM_READ = {10, 60, 20, 120, 40, 30, 90, 110, 100, 80, 50, 140, 130, 70};

    /**
     * {@code eova_object_code} 的载荷顺序（真库 `order by num`；★ 有并列 `num`：
     * `8` 下是 `is_single`→`is_celledit`、`9` 下是 `is_show_num`→`is_first_load`）
     */
    private static final String[] OBJECT_CODE_EN_BY_NUM = {
        "id", "code", "name", "view_name", "table_name", "pk_name", "data_source", "is_single",
        "is_celledit", "is_show_num", "is_first_load", "default_order", "filter", "biz_intercept",
        "diy_html", "view_sql", "config"
    };

    /** `eova_object_code` 的 `num` 序列（含并列） */
    private static final int[] OBJECT_CODE_NUM = {
        1, 2, 3, 4, 5, 6, 7, 8, 8, 9, 9, 10, 11, 12, 13, 17, 18
    };

    @Autowired
    private TestRestTemplate rest;

    /** 登录并返回会话 Cookie（与 S2b/S4 判据同一真值账号） */
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
     * 带/不带会话 POST 元数据端点
     *
     * @param sid  会话 Cookie（null ⇒ 不带头）
     * @param url  请求路径（含查询串）
     * @return 响应
     */
    private ResponseEntity<String> postMetaForm(String sid, String url) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (sid != null) {
            h.add(HttpHeaders.COOKIE, sid);
        }
        return rest.exchange(url, org.springframework.http.HttpMethod.POST,
                new HttpEntity<>("{}", h), String.class);
    }

    /**
     * 取载荷里的 `en` 序列
     *
     * @param body 响应体
     * @return 字段名序列
     */
    @SuppressWarnings("unchecked")
    private static List<String> ensOf(String body) {
        List<Map<String, Object>> fields = fieldsOf(body);
        List<String> out = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            out.add(String.valueOf(f.get("en")));
        }
        return out;
    }

    /**
     * 取载荷里的字段列表
     *
     * @param body 响应体
     * @return 字段列表
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fieldsOf(String body) {
        try {
            Map<String, Object> root = JSON.readValue(body, Map.class);
            assertEquals("ok", root.get("state"), "信封 state 必须为 ok：" + body);
            Object fields = root.get("fields");
            assertNotNull(fields, "必须含 fields（`<ev-form>` 读的就是它）：" + body);
            return (List<Map<String, Object>>) fields;
        } catch (Exception e) {
            throw new AssertionError("响应不是预期 JSON：" + body, e);
        }
    }

    /**
     * 取每个字段的 `diy.num`
     *
     * @param fields 字段列表
     * @return `diy.num` 序列
     */
    @SuppressWarnings("unchecked")
    private static List<Integer> diyNumsOf(List<Map<String, Object>> fields) {
        List<Integer> out = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            Map<String, Object> diy = (Map<String, Object>) f.get("diy");
            assertNotNull(diy, "字段必须带 diy（制品按 diy.num 排序）：" + f.get("en"));
            out.add(((Number) diy.get("num")).intValue());
        }
        return out;
    }

    /**
     * 取每个字段的 `num`
     *
     * @param fields 字段列表
     * @return `num` 序列
     */
    private static List<Integer> numsOf(List<Map<String, Object>> fields) {
        List<Integer> out = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            out.add(((Number) f.get("num")).intValue());
        }
        return out;
    }

    /**
     * 取每个字段的 `is_required`
     *
     * @param fields 字段列表
     * @return `is_required` 序列（0/1）
     */
    private static List<Integer> requiredOf(List<Map<String, Object>> fields) {
        List<Integer> out = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            out.add(Boolean.TRUE.equals(f.get("is_required")) ? 1 : 0);
        }
        return out;
    }

    /**
     * 整型数组 → List
     *
     * @param arr 数组
     * @return 列表
     */
    private static List<Integer> list(int... arr) {
        return Arrays.stream(arr).boxed().collect(Collectors.toList());
    }

    @Test
    @DisplayName("★ S6-1：未登录 ⇒ 401，且信封与旧栈逐字相同（旧栈实测 {\"state\":\"fail\",\"msg\":\"401 Unauthorized\"}）")
    void requiresSession() {
        ResponseEntity<String> resp = postMetaForm(null, "/api/meta/form/" + OBJ_PRODUCT + "?mode=create");
        assertEquals(401, resp.getStatusCode().value(),
                "★ 未登录必须 401，实际=" + resp.getStatusCode());
        assertTrue(String.valueOf(resp.getBody()).contains("\"msg\":\"401 Unauthorized\""),
                "★ 信封必须与旧栈逐字相同，实际=" + resp.getBody());
        assertTrue(!String.valueOf(resp.getBody()).contains("\"fields\""),
                "★ 未登录不得下发字段元数据，实际=" + resp.getBody());
    }

    @Test
    @DisplayName("★ S6-2：meta_product ⇒ 14 字段、载荷顺序 = 真库 order by num、object.code/pk_name 正确")
    void productFieldOrderMatchesRealDb() {
        String sid = login();
        String body = postMetaForm(sid, "/api/meta/form/" + OBJ_PRODUCT + "?mode=create").getBody();

        List<Map<String, Object>> fields = fieldsOf(body);
        assertEquals(PRODUCT_EN_BY_NUM.length, fields.size(),
                "字段条数必须等于真库（eova_field where object_code='meta_product'）");
        assertEquals(Arrays.asList(PRODUCT_EN_BY_NUM), ensOf(body),
                "★ 载荷顺序必须等于真库 order by num");
        assertEquals(list(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14), numsOf(fields),
                "num 序列必须与真库一致（无并列）");
        assertEquals(list(PRODUCT_REQUIRED), requiredOf(fields),
                "★ is_required 必须与真库一致（页面的必填星号来源）");

        try {
            Map<?, ?> root = JSON.readValue(body, Map.class);
            Map<?, ?> object = (Map<?, ?>) root.get("object");
            assertNotNull(object, "必须含 object");
            assertEquals(OBJ_PRODUCT, object.get("code"), "★ object.code 必须与真库 eova_object 一致");
            assertEquals("id", object.get("pk_name"), "★ object.pk_name 是真库值（= 旧 `#(object.pk)`）");
        } catch (Exception e) {
            throw new AssertionError("响应不是预期 JSON：" + body, e);
        }
    }

    @Test
    @DisplayName("★ S6-3：判别性 —— 载荷顺序【不等于】按 id 排序的顺序（否则\"按 id 排\"也能绿）")
    void orderIsNotById() {
        String sid = login();
        List<String> actual = ensOf(
                postMetaForm(sid, "/api/meta/form/" + OBJ_PRODUCT + "?mode=create").getBody());
        // 这两个序列在本真库里**确实不同**（前者 num 序、后者 id 序）——先证明判据可判别
        assertNotEquals(Arrays.asList(PRODUCT_EN_BY_ID), Arrays.asList(PRODUCT_EN_BY_NUM),
                "★ 真库的 num 序与 id 序必须不同，否则本判据不可判别（前提被证伪，须换对象）");
        assertNotEquals(Arrays.asList(PRODUCT_EN_BY_ID), actual,
                "★ 载荷不得按 id 排序（真库 num 序与 id 序不同）");
    }

    @Test
    @DisplayName("★ S6-4：eova_object_code ⇒ 17 字段，含并列 num 的实际次序（is_single→is_celledit 等）")
    void objectCodeFieldOrderWithTies() {
        String sid = login();
        String body = postMetaForm(sid, "/api/meta/form/" + OBJ_OBJECT_CODE + "?mode=create").getBody();
        List<Map<String, Object>> fields = fieldsOf(body);

        assertEquals(OBJECT_CODE_EN_BY_NUM.length, fields.size(), "字段条数必须等于真库 17 条");
        assertEquals(Arrays.asList(OBJECT_CODE_EN_BY_NUM), ensOf(body),
                "★ 载荷顺序必须等于真库 order by num（含并列项的实际次序）");
        assertEquals(list(OBJECT_CODE_NUM), numsOf(fields), "num 序列含两处并列（8/9），必须原样");
        // 并列项的实际次序单独点名（"顺序变了但集合没变"这类回归只有它能抓）
        List<String> ens = ensOf(body);
        assertTrue(ens.indexOf("is_single") < ens.indexOf("is_celledit"), "num=8 的并列次序被改变");
        assertTrue(ens.indexOf("is_show_num") < ens.indexOf("is_first_load"), "num=9 的并列次序被改变");
    }

    @Test
    @DisplayName("★ S6-5：渲染顺序的来源是 diy.num（制品按它排序）—— 三个 mode 的 diy.num 互不相同")
    void diyNumDrivesRenderOrderAndVariesByMode() {
        String sid = login();
        List<Integer> create = diyNumsOf(fieldsOf(
                postMetaForm(sid, "/api/meta/form/" + OBJ_PRODUCT + "?mode=create").getBody()));
        List<Integer> update = diyNumsOf(fieldsOf(
                postMetaForm(sid, "/api/meta/form/" + OBJ_PRODUCT + "?mode=update").getBody()));
        List<Integer> read = diyNumsOf(fieldsOf(
                postMetaForm(sid, "/api/meta/form/" + OBJ_PRODUCT + "?mode=read").getBody()));

        assertEquals(list(PRODUCT_DIY_NUM_CREATE), create, "create 的 diy.num 必须与真库一致");
        assertEquals(list(PRODUCT_DIY_NUM_UPDATE), update, "update 的 diy.num 必须与真库一致");
        assertEquals(list(PRODUCT_DIY_NUM_READ), read, "read 的 diy.num 必须与真库一致");

        // ★ 判别性：三者互不相同 ⇒ "mode 被忽略"会立刻红
        assertNotEquals(create, update, "★ create/update 的渲染顺序必须不同（真库如此）");
        assertNotEquals(create, read, "★ create/read 的渲染顺序必须不同（真库如此）");
        // 且 create 的 diy.num 序**不等于**字段 num 序（否则"按 num 渲染"也能绿）
        assertNotEquals(list(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14), create,
                "★ diy.num 序不得等于字段 num 序（否则本判据不可判别）");
    }

    @Test
    @DisplayName("★ S6-6：字段【集合】与 mode 无关（mode 只改 diy，不改字段集）")
    void fieldSetIsModeIndependent() {
        String sid = login();
        List<String> expectedSet = Arrays.asList(PRODUCT_EN_BY_NUM).stream().sorted()
                .collect(Collectors.toList());
        for (String mode : new String[] {"create", "update", "read"}) {
            String body = postMetaForm(sid, "/api/meta/form/" + OBJ_PRODUCT + "?mode=" + mode).getBody();
            List<Map<String, Object>> fields = fieldsOf(body);
            List<String> ens = ensOf(body);
            assertEquals(PRODUCT_EN_BY_NUM.length, fields.size(), "mode=" + mode + " 的字段数被改变");
            // 集合比较（不是序列比较）：顺序由 diy.num 决定，集合与 mode 无关
            assertEquals(expectedSet, ens.stream().sorted().collect(Collectors.toList()),
                    "mode=" + mode + " 的字段集合被改变");
        }
    }

    @Test
    @DisplayName("★ S6-7：失败态 —— 未知元对象与缺 mode 都返回 500（与旧栈同码；旧栈实测已核）")
    void failurePathsKeepLegacyStatusCodes() {
        String sid = login();
        // 未知元对象：旧栈 2026-09-12 实测 http=500（body 是 legacy 信封）
        ResponseEntity<String> unknown = postMetaForm(sid, "/api/meta/form/no_such_object_xyz?mode=create");
        assertEquals(500, unknown.getStatusCode().value(),
                "★ 未知元对象必须 500（与旧栈同码），实际=" + unknown.getStatusCode());
        // 缺 mode：旧栈同为 500（`元字段个性化数据缺失` 分支）
        ResponseEntity<String> noMode = postMetaForm(sid, "/api/meta/form/" + OBJ_PRODUCT);
        assertEquals(500, noMode.getStatusCode().value(),
                "★ 缺 mode 必须 500（与旧栈同码），实际=" + noMode.getStatusCode());
        // ★ 两向：正常对象正常 mode 不得是 500（否则上面两条会被"一律 500"满足）
        assertEquals(200, postMetaForm(sid, "/api/meta/form/" + OBJ_PRODUCT + "?mode=create")
                .getStatusCode().value(), "正常请求必须 200");
    }
}
