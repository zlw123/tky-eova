/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.web;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import cn.eova.common.Ds;
import cn.eova.compat.db.LegacyDataSourceWiring;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **多数据源宿主接线判据**（第 298 轮，`docs/DES-008-R1-multi-datasource-host-wiring.md`）。
 *
 * <p><b>它钉的缺陷</b>：宿主 {@code LegacyWebBootstrap} 原先只注册 {@code Ds.EOVA} 一个网关
 * ⇒ 落在第二库（{@code main} → {@code demo}）的访问一律
 * {@code 未注册数据源网关（数据源=main）} 500，而**旧栈同请求是 200**。
 * 最严重的可观测后果是含**查找框**的表单页在浏览器里**永久卡死**
 * （制品 {@code EvFind} 的 {@code widget_text} 失败后走 {@code .catch(() => alert('请求异常'))}，
 * 而 {@code alert} 阻塞主线程）——见 DES-005 §16.8.3 缺口 3。</p>
 *
 * <p><b>期望值是外部事实</b>（两层来源）：</p>
 * <ol>
 *   <li><b>配置实值</b>：{@code eova-web/src/main/resources/eova/dev.txt:21,23-31}
 *       —— {@code db.datasource=eova,main}，{@code eova.url} 指向 {@code eova_meta}、
 *       {@code main.url} 指向 {@code demo}（与旧栈 {@code meta-eova/eova/demo/.../eova/dev.txt} 逐字相同）；</li>
 *   <li><b>旧栈实跑对照</b>（2026-09-12，旧 demo 9090）：同一请求返回
 *       <b>200</b> {@code {"field_val":"id","data":[],"field_txt":"name","state":"ok"}} ——
 *       本判据的期望载荷**逐字取自它**，并在修复后实测两栈响应**逐字节相同**（{@code diff} 无输出）。</li>
 * </ol>
 *
 * <p><b>★ 一条结构面断言（防"硬编码 main"）</b>：断言"网关快照的键集合 ⊇ 配置里的 ds 集合"——
 * 即"配置里写几个 ds 就得有几个网关"。若有人把实现写成 {@code if ("main".equals(ds))}，
 * 这条断言在"配置加了第三个库"时会红，而只测 HTTP 的判据不会。</p>
 */
// ★ 与同模块其它判据共用同一上下文配置（旧引导是"每 JVM 一次"语义，见 LegacyWebBootstrapTest 注释）
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultiDatasourceGatewayHttpTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 旧栈 9090 实跑的同请求响应（逐字取自 2026-09-12 的对照记录） */
    private static final String WIDGET_TEXT_EXPECTED =
            "{\"field_val\":\"id\",\"data\":[],\"field_txt\":\"name\",\"state\":\"ok\"}";

    /** 查找框用的 EovaOption 编码（`eova_field.exp`，取自真库 `meta_product.sizes` / `goods_style.supplier_id`） */
    private static final String OPTION_AREA_CITY = "area_city";

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
     * 带/不带会话 POST 组件端点
     *
     * @param sid 会话 Cookie（null ⇒ 不带头）
     * @param url 请求路径（含查询串）
     * @return 响应
     */
    private ResponseEntity<String> postWidget(String sid, String url) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (sid != null) {
            h.add(HttpHeaders.COOKIE, sid);
        }
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>("{}", h), String.class);
    }

    @Test
    @DisplayName("★ DS-1：配置里的 ds 集合 = {eova, main}，且各自指向真库基线（dev.txt 实值）")
    void specsCoverAllConfiguredDatasources() {
        List<LegacyDataSourceWiring.Spec> specs = LegacyDataSourceWiring.specs();
        Set<String> names = specs.stream().map(LegacyDataSourceWiring.Spec::getDs)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // 反空判据：解析不出任何 spec 说明配置根本没进来（那时本判据不能"因为集合为空而通过"）
        assertTrue(names.size() >= 2, "db.datasource 应解析出至少 eova+main，实际=" + names);
        assertEquals(Set.of(Ds.EOVA, Ds.MAIN), names, "ds 集合必须与 dev.txt:21 一致");

        Map<String, String> urlOf = specs.stream()
                .collect(Collectors.toMap(LegacyDataSourceWiring.Spec::getDs,
                        LegacyDataSourceWiring.Spec::getUrl));
        assertTrue(urlOf.get(Ds.EOVA).contains("/eova_meta"),
                "eova 应指向 eova_meta，实际=" + urlOf.get(Ds.EOVA));
        assertTrue(urlOf.get(Ds.MAIN).contains("/demo"),
                "★ main 应指向 demo（dev.txt:28），实际=" + urlOf.get(Ds.MAIN));
    }

    @Test
    @DisplayName("★ DS-2（结构面）：网关快照的键集合 ⊇ 配置里的 ds 集合 ⇒ 不是『只连 main』的硬编码")
    void everyConfiguredDatasourceHasAGateway() {
        Set<String> configured = LegacyDataSourceWiring.specs().stream()
                .map(LegacyDataSourceWiring.Spec::getDs).collect(Collectors.toSet());
        Map<String, ?> registered = EovaGateways.snapshot();

        assertTrue(configured.size() >= 2, "配置未解析出多个 ds，本判据无法判别：" + configured);
        Set<String> missing = configured.stream()
                .filter(ds -> !registered.containsKey(ds)).collect(Collectors.toSet());
        assertEquals(Set.of(), missing,
                "★ 以下数据源在配置里但**没有网关** ⇒ 访问会 500（旧栈可用）：" + missing
                        + "；已注册=" + registered.keySet());
    }

    @Test
    @DisplayName("★ DS-3：第二库网关真的连到 demo 库（不是只注册了个名字）")
    void mainGatewayPointsAtDemoSchema() {
        EovaRecord r = EovaGateways.get(Ds.MAIN).findFirst("select database() as db_name");
        assertNotNull(r, "第二库查询未返回结果");
        assertEquals("demo", r.getStr("db_name"),
                "★ main 网关指向的库必须是 demo（dev.txt:28），实际=" + r.getStr("db_name"));
        // 主库同样自证（两侧都钉，才能证伪"两个网关指同一个库"）
        EovaRecord e = EovaGateways.get(Ds.EOVA).findFirst("select database() as db_name");
        assertEquals("eova_meta", e.getStr("db_name"), "eova 网关必须指向 eova_meta");
    }

    @Test
    @DisplayName("★ DS-4：`POST /api/widget/text`（第二库查询）⇒ 200 且载荷与旧栈 9090 **逐字相同**")
    void widgetTextMatchesOldStack() throws Exception {
        String sid = login();
        ResponseEntity<String> resp =
                postWidget(sid, "/api/widget/text?option=" + OPTION_AREA_CITY + "&value=XXL");
        assertEquals(200, resp.getStatusCode().value(),
                "★ 修复前这里是 500（未注册数据源网关（数据源=main）），实际=" + resp.getStatusCode()
                        + " body=" + resp.getBody());
        // 用 Jackson 归一后比较（键序不承诺），字段集与值必须逐项相同
        assertEquals(JSON.readValue(WIDGET_TEXT_EXPECTED, Map.class),
                JSON.readValue(resp.getBody(), Map.class),
                "★ 载荷必须与旧栈实跑响应一致，实际=" + resp.getBody());
    }

    @Test
    @DisplayName("★ DS-5（反向）：未知 option 仍是非 200 ⇒ 证明 DS-4 不是『一律 200』")
    void unknownOptionStillFails() {
        String sid = login();
        ResponseEntity<String> resp =
                postWidget(sid, "/api/widget/text?option=no_such_option_xyz&value=X");
        assertNotNull(resp.getStatusCode());
        assertTrue(resp.getStatusCode().value() != 200,
                "★ 未知 option 不得 200（否则 DS-4 的通过没有判别力），实际=" + resp.getStatusCode());
        // 与旧栈同码（旧栈实测 500）
        assertEquals(500, resp.getStatusCode().value(),
                "旧栈同请求实测 500，实际=" + resp.getStatusCode());
    }

    @Test
    @DisplayName("★ DS-6：未登录 ⇒ 401（第二库可用**不**等于放开了鉴权）")
    void stillRequiresSession() {
        ResponseEntity<String> resp =
                postWidget(null, "/api/widget/text?option=" + OPTION_AREA_CITY + "&value=XXL");
        assertEquals(401, resp.getStatusCode().value(), "未登录必须 401，实际=" + resp.getStatusCode());
        assertTrue(!String.valueOf(resp.getBody()).contains("field_txt"),
                "★ 未登录不得下发查询结果，实际=" + resp.getBody());
    }
}
