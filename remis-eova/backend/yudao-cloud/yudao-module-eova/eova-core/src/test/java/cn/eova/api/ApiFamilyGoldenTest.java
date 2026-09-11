/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.api;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.eova.EovaApiRoutes;
import cn.eova.api.sys.DemoApi;
import cn.eova.api.sys.RoleApi;
import cn.eova.api.sys.UserApi;
import cn.eova.common.Ds;
import cn.eova.compat.jfinal.aop.LegacyBefore;
import cn.eova.compat.jfinal.config.LegacyRoutes;
import cn.eova.compat.jfinal.core.LegacyNotAction;
import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyTx;
import cn.eova.core.api.ApiInterceptor;
import cn.eova.core.api.ApiResponse;
import cn.eova.core.api.BaseApi;
import cn.eova.db.EovaDbGateway;
import cn.eova.db.EovaGateways;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API 族 5 单元（第 75 轮 port：BaseApi 64 + DemoApi 69 + UserApi 82 + RoleApi 140 + EovaApiRoutes 28）的判据。
 *
 * <p><b>为什么判据落在这四支：</b></p>
 * <ol>
 *   <li><b>路由暴露面</b>：{@code BaseApi} 的 8 个入口若丢掉 {@code @NotAction}，
 *       旧栈会把 {@code OK/NO/getKv} 当 action 暴露成可直连 URL（越权面）。</li>
 *   <li><b>入参解析链</b>：{@code getKv()/getKvs()} 必须走
 *       {@code LegacyJsonKit.parse(json, LegacyKv.class)}（旧 {@code Json.getJson()} 是
 *       JFinalJson 工厂）—— 换成 fastjson 会得到不同的类型处理。</li>
 *   <li><b>既有缺陷锁定</b>：{@code RoleApi.update()} 用 {@code delete(...)} 入口执行
 *       UPDATE SQL（旧 {@code DbPro.delete} 不校验 SQL 类型），必须被记录式网关钉住，
 *       避免被"顺手修正"成 {@code update} 入口。</li>
 *   <li><b>URL 契约</b>：{@code EovaApiRoutes} 三条路径、顺序与父类拦截器。</li>
 * </ol>
 *
 * <p><b>R59 已修复（第 76 轮）：</b>{@code LegacyJsonKit} 补齐了 jfinal 的
 * {@code buildBeanToJson} 分支（POJO 反射 getter）+ 嵌套深度上限 + {@code Double/Float} 的
 * NaN/Infinity→{@code null} + 基本类型数组展开 + 枚举走 {@code toString()}。
 * 本类因此恢复断言 {@code OK/NO} 的应答体为 {@code ApiResponse} 的结构 JSON
 * （企业侧 {@code /router/eova/*} 契约）。对照台账在 {@code eova-compat} 的
 * {@code LegacyJsonKitBeanCrossGoldenTest}（对 jfinal 5.2.6 真制品逐样本比对）。
 */
class ApiFamilyGoldenTest {

    /** BaseApi 探针：只替换 JSON 入参入口，其余（解析、类型映射）走真实实现 */
    public static class ProbeApi extends BaseApi {

        private final JSONObject json;
        private final JSONArray array;

        /**
         * @param json  模拟 getJson() 的结果
         * @param array 模拟 getJsonArray() 的结果
         */
        public ProbeApi(JSONObject json, JSONArray array) {
            this.json = json;
            this.array = array;
        }

        @Override
        public JSONObject getJson() {
            return json;
        }

        @Override
        public JSONArray getJsonArray() {
            return array;
        }
    }

    /**
     * RoleApi 探针：把 {@code getKv()} 固定成给定参数，并把 {@code renderJson(Object)} 换成捕获器。
     *
     * <p><b>为什么连渲染也替换（第 75 轮实测教训，属 R58 的"环境不洁"族）：</b>
     * 若沿用真实渲染出口，{@code OK()} 会走 {@code LegacyRenderManager.getRenderFactory()}，
     * 而该工厂是**由宿主/别的判据类在静态里装配**的 —— 于是本判据在**全量跑**时绿、
     * 在**单跑本类**时报 {@code 未装配渲染工厂}。这种"靠同 JVM 里别的用例顺手装配全局状态"
     * 的判据不是判据。此处直接在探针里截获 envelope 对象，既去掉全局依赖，
     * 又顺带把 {@code OK()} 的载荷（code/msg/data）钉住。</p>
     */
    public static class ProbeRoleApi extends RoleApi {

        private final LegacyKv kv;

        /** 截获到的应答 envelope（每次 OK/NO 一条） */
        final List<ApiResponse> rendered = new ArrayList<>();

        /**
         * @param kv 入参
         */
        public ProbeRoleApi(LegacyKv kv) {
            this.kv = kv;
        }

        @Override
        public LegacyKv getKv() {
            return kv;
        }

        @Override
        public void renderJson(Object object) {
            rendered.add((ApiResponse) object);
        }
    }

    /** 记录网关调用的替身 */
    static final class Rec {
        final List<String> methods = new ArrayList<>();
        final List<String> sqls = new ArrayList<>();
        final List<Object[]> paras = new ArrayList<>();

        /**
         * 清空记录。
         */
        void clear() {
            methods.clear();
            sqls.clear();
            paras.clear();
        }
    }

    /**
     * 建记录式网关。
     *
     * @param rec 记录器
     * @return 替身
     */
    private static EovaDbGateway gateway(Rec rec) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "delete":
                case "update":
                    rec.methods.add(m.getName());
                    rec.sqls.add((String) args[0]);
                    rec.paras.add(args.length > 1 && args[1] instanceof Object[]
                            ? (Object[]) args[1] : new Object[0]);
                    return 1;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (EovaDbGateway) Proxy.newProxyInstance(
                ApiFamilyGoldenTest.class.getClassLoader(),
                new Class<?>[]{EovaDbGateway.class}, h);
    }

    /**
     * 方法签名（名 + 形参类型全名），用于稳定断言（{@code getDeclaredMethods()} 顺序不属契约）。
     *
     * @param m 方法
     * @return 签名串
     */
    private static String sig(Method m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        Class<?>[] ps = m.getParameterTypes();
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ps[i].getName());
        }
        return sb.append(')').toString();
    }

    @Test
    @DisplayName("BaseApi：8 个入口全部带 @LegacyNotAction（否则会被当 action 暴露）")
    void baseApiEntryPointsAreNotActions() {
        Map<String, Method> methods = new LinkedHashMap<>();
        for (Method m : BaseApi.class.getDeclaredMethods()) {
            if (m.isSynthetic()) {
                continue;  // 合成方法（如 bridge）不是旧实现的成员
            }
            methods.put(sig(m), m);
        }
        assertEquals(8, methods.size(), "BaseApi 入口数属契约：" + methods.keySet());
        for (Map.Entry<String, Method> e : methods.entrySet()) {
            assertNotNull(e.getValue().getAnnotation(LegacyNotAction.class),
                    e.getKey() + " 必须带 @LegacyNotAction");
        }
        // 两条重载并存属契约：OK() 显式传 null，与 OK(new JSONObject()) 的 data 形态不同
        assertTrue(methods.containsKey("OK(java.lang.Object)"));
        assertTrue(methods.containsKey("OK()"));
        assertTrue(methods.containsKey("NO(java.lang.String)"));
        assertTrue(methods.containsKey("NO(java.lang.String,int)"), "NO(msg, code) 的重载不得丢");
        assertTrue(methods.containsKey("getKv()"));
        assertTrue(methods.containsKey("getKvs()"));
    }

    @Test
    @DisplayName("UserApi：只有 updateRole 标注事务（@LegacyBefore(LegacyTx.class)）")
    void userApiTxOnlyOnUpdateRole() throws Exception {
        Method ur = UserApi.class.getMethod("updateRole");
        LegacyBefore before = ur.getAnnotation(LegacyBefore.class);
        assertNotNull(before, "updateRole 必须带事务注解");
        assertEquals(1, before.value().length, "只挂一个拦截器");
        assertEquals(LegacyTx.class, before.value()[0], "事务拦截器必须是 LegacyTx");

        for (String name : new String[]{"sync", "init", "logout"}) {
            Method m = UserApi.class.getMethod(name);
            assertNull(m.getAnnotation(LegacyBefore.class),
                    name + " 不得加事务（旧实现只有 updateRole 是 @Before(Tx.class)）");
        }
        assertEquals(BaseApi.class, UserApi.class.getSuperclass(), "UserApi 必须直接继承 BaseApi");
    }

    @Test
    @DisplayName("BaseApi：getKv/getKvs 走 LegacyJsonKit 解析（不是 fastjson 直解）")
    void baseApiParsesParamsThroughLegacyJsonKit() {
        JSONObject first = new JSONObject();
        first.put("id", 7);
        first.put("name", "张三");
        JSONArray array = new JSONArray();
        array.add(first);
        JSONObject second = new JSONObject();
        second.put("id", 8);
        array.add(second);

        ProbeApi api = new ProbeApi(first, array);

        LegacyKv kv = api.getKv();
        assertEquals(Integer.valueOf(7), kv.getInt("id"), "数值必须解析成 Integer（Kv.getInt 语义）");
        assertEquals("张三", kv.getStr("name"), "中文不得被转义/丢失");

        List<LegacyKv> kvs = api.getKvs();
        assertEquals(2, kvs.size(), "getKvs() 逐个 JSONObject 解析");
        assertEquals(Integer.valueOf(7), kvs.get(0).getInt("id"));
        assertEquals(Integer.valueOf(8), kvs.get(1).getInt("id"));

        assertEquals(2, api.getJsonArray().size(), "getJsonArray() 直接返回解析结果");
        assertEquals("张三", api.getJson().getString("name"));
    }

    @Test
    @DisplayName("RoleApi：update/delete 的原样 SQL 与入口名（含『update 走 delete 入口』既有缺陷）")
    void roleApiKeepsLegacySqlAndEntryNames() {
        Rec rec = new Rec();
        EovaGateways.register(Ds.EOVA, gateway(rec));
        try {
            LegacyKv kv = new LegacyKv();
            kv.set("role_id", 7);
            kv.set("name", "新名");
            ProbeRoleApi api = new ProbeRoleApi(kv);

            // ① update()：既有缺陷 —— 用 delete 入口执行 UPDATE SQL（DbPro.delete 不校验 SQL 类型）
            api.update();
            assertEquals(List.of("delete"), rec.methods,
                    "update() 必须仍走 delete 入口（既有缺陷，原样保留）");
            assertEquals("update eova_role set name = ? where id = ?", rec.sqls.get(0),
                    "SQL 文本与占位符顺序属契约");
            assertArrayEquals(new Object[]{"新名", 7}, rec.paras.get(0),
                    "参数顺序 name, role_id（role_id 由 Kv 反序列化为 Integer）");
            assertEquals(1, api.rendered.size(), "update() 恰好应答一次");
            assertEquals(0, api.rendered.get(0).getCode(), "OK() 的 code 必须是 0");
            assertEquals("ok", api.rendered.get(0).getMsg(), "OK() 的 msg 必须是 ok");
            assertNull(api.rendered.get(0).getData(), "OK() 的 data 必须是 null（旧实现显式传 null）");

            // ② delete()：正常删除
            rec.clear();
            api.rendered.clear();
            api.delete();
            assertEquals(List.of("delete"), rec.methods);
            assertEquals("delete from eova_role where id = ?", rec.sqls.get(0));
            assertArrayEquals(new Object[]{7}, rec.paras.get(0), "删除只传 role_id");
            assertEquals(1, api.rendered.size(), "delete() 恰好应答一次");
            assertEquals(0, api.rendered.get(0).getCode());
        } finally {
            EovaGateways.clear();
        }
    }

    @Test
    @DisplayName("BaseApi：OK/NO 的应答体必须是 ApiResponse 的【结构】JSON（R59 契约回归）")
    void baseApiRendersApiEnvelope() {
        // 第 75 轮此处曾红：接缝缺 Bean 分支，渲染成 "cn.eova.core.api.ApiResponse@hash"。
        // 第 76 轮补齐 LegacyJsonKit 后，POJO 按 getter 结构展开 ⇒ 企业侧 /router/eova/* 契约恢复。
        String ok = LegacyJsonKit.toJson(ApiResponse.OK(new JSONObject().fluentPut("id", 7)));
        assertTrue(ok.startsWith("{\""), "必须是 JSON 对象：" + ok);
        assertTrue(ok.contains("\"code\":0"), ok);
        assertTrue(ok.contains("\"msg\":\"ok\""), ok);
        assertTrue(ok.contains("\"data\":{\"id\":7}"), "data 逐层展开，不得倒成字符串：" + ok);
        assertTrue(!ok.contains("ApiResponse@"), "不得退化为 toString()：" + ok);

        // 注意：ApiResponse.NO 的重载顺序是 NO(int code, String msg)，与 BaseApi.NO(String, int) 相反
        String no = LegacyJsonKit.toJson(ApiResponse.NO(10040, "业务异常"));
        assertTrue(no.contains("\"code\":10040"), no);
        assertTrue(no.contains("\"msg\":\"业务异常\""), "中文不得被转义：" + no);
        assertTrue(no.contains("\"data\":null"), "NO 的 data 为 null：" + no);

        String noDefault = LegacyJsonKit.toJson(ApiResponse.NO("仅消息"));
        assertTrue(noDefault.contains("\"code\":500"), "NO(msg) 默认码 500：" + noDefault);

        // 键序非契约（= getMethods() 返回序），这里只断言键集合的完整性
        assertTrue(ok.contains("\"code\"") && ok.contains("\"msg\"") && ok.contains("\"data\""),
                "code/msg/data 三个键必须齐备：" + ok);
    }

    @Test
    @DisplayName("BaseApi：OK()/NO() 的 envelope 对象形态（不依赖渲染出口）")
    void baseApiEnvelopeObjects() {
        ApiResponse ok = ApiResponse.OK(null);
        assertEquals(0, ok.getCode());
        assertEquals("ok", ok.getMsg());
        assertNull(ok.getData());
        assertEquals(500, ApiResponse.NO("x").getCode(), "NO(msg) 固定 500");
        assertEquals("x", ApiResponse.NO("x").getMsg());
    }

    @Test
    @DisplayName("EovaApiRoutes：三条路由的顺序/路径/控制器 + 继承 ApiInterceptor")
    void eovaApiRoutesRegisterThreePathsInOrder() {
        EovaApiRoutes routes = new EovaApiRoutes();
        routes.config();

        assertEquals(1, routes.getInterceptors().length, "父类 config() 只挂一个拦截器");
        assertTrue(routes.getInterceptors()[0] instanceof ApiInterceptor,
                "必须以 ApiInterceptor 守护（签名校验 + 403）");

        List<LegacyRoutes.Route> items = routes.getRouteItemList();
        assertEquals(3, items.size(), "只登记 demo/role/user 三条：" + items.size());
        assertEquals("/router/eova/demo", items.get(0).getControllerPath(), "路径含 /router 前缀（父类强制）");
        assertEquals("/router/eova/role", items.get(1).getControllerPath());
        assertEquals("/router/eova/user", items.get(2).getControllerPath());
        assertEquals(DemoApi.class, items.get(0).getControllerClass());
        assertEquals(RoleApi.class, items.get(1).getControllerClass());
        assertEquals(UserApi.class, items.get(2).getControllerClass());
    }
}
