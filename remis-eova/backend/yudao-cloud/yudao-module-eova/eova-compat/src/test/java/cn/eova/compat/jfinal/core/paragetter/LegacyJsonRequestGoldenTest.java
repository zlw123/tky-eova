/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.core.paragetter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

import cn.eova.testkit.OldImplementationLoader;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LegacyJsonRequest} 的<b>跨实现</b>判据（对照旧 jfinal 制品）。
 *
 * <p>旧 {@code JsonRequest} 形参是 javax 版请求、新接缝是 jakarta 版，
 * 故两侧各用一个单接口动态代理作为被包装请求（决策 2 已实测：javax/jakarta
 * 双接口代理不可行，因 {@code getOutputStream} 返回类型不兼容）。</p>
 *
 * <p><b>比对项（都是旧字节码里读出、且容易写错的）：</b>
 * <ul>
 *   <li>{@code getParameter}：JSON 命中优先、其值为 {@code JSON} 时取
 *       {@code toJSONString()}、值为 null 时返回 null、未命中<b>回落</b>被包装请求；</li>
 *   <li>{@code getParameterNames}：<b>不</b>回落（jsonObject 为 null 时给空枚举）——
 *       与 {@code getParameter} 的回落行为<b>不对称</b>，属既有语义；</li>
 *   <li>{@code getParameterMap}：先并入被包装请求的参数，再叠加 JSON 键；</li>
 *   <li>{@code isJSONObject}/{@code isJSONArray}；非对象非数组的体（如 {@code "123"}）
 *       两者都 false 且不报错。</li>
 * </ul>
 *
 * <p>acceptanceProfile: golden-jsonrequest-seam</p>
 */
class LegacyJsonRequestGoldenTest {

    /**
     * 逐项跨实现比对。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("LegacyJsonRequest：getParameter/Map/Names/isJSON* 逐项对照旧制品")
    void matchesOld() throws Exception {
        String body = "{\"a\":\"1\",\"b\":{\"x\":2},\"n\":null,\"c\":3}";
        Map<String, String> reqParams = new LinkedHashMap<>();
        reqParams.put("a", "req-a");
        reqParams.put("only", "req-only");
        reqParams.put("empty", "");

        // ---- 旧侧（javax） ----
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.core.paragetter.JsonRequest", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());
        Class<?> oldReqType = Class.forName("javax.servlet.http.HttpServletRequest", true, jf);
        Object oldReq = spy(oldReqType, reqParams);
        Object oldJr = oldCls.getConstructor(String.class, oldReqType).newInstance(body, oldReq);

        // ---- 新侧（jakarta） ----
        LegacyJsonRequest newJr = new LegacyJsonRequest(body, jakartaSpy(reqParams));

        // ① isJSONObject / isJSONArray
        assertEquals(oldCls.getMethod("isJSONObject").invoke(oldJr), newJr.isJSONObject());
        assertEquals(oldCls.getMethod("isJSONArray").invoke(oldJr), newJr.isJSONArray());
        assertTrue(newJr.isJSONObject());

        // ② getParameter：JSON 优先 + JSON 值 toJSONString + null 值 + 回落
        for (String name : new String[]{"a", "b", "n", "c", "only", "empty", "absent"}) {
            Object oldV = oldCls.getMethod("getParameter", String.class).invoke(oldJr, name);
            assertEquals(oldV, newJr.getParameter(name),
                    "getParameter(\"" + name + "\") 必须一致");
        }
        assertEquals("1", newJr.getParameter("a"), "JSON 命中优先于请求参数（req 里是 req-a）");
        assertEquals("{\"x\":2}", newJr.getParameter("b"), "JSON 值应取 toJSONString()");
        assertNull(newJr.getParameter("n"), "JSON 值为 null 时应返回 null（不得回落）");
        assertEquals("req-only", newJr.getParameter("only"), "JSON 未命中应回落被包装请求");

        // ③ getParameterMap：键集合一致
        Map<String, String[]> oldMap = asMap(oldCls.getMethod("getParameterMap").invoke(oldJr));
        Map<String, String[]> newMap = newJr.getParameterMap();
        assertEquals(new TreeSet<>(oldMap.keySet()), new TreeSet<>(newMap.keySet()),
                "getParameterMap 的键集必须一致");
        for (String k : oldMap.keySet()) {
            assertTrue(Arrays.equals(oldMap.get(k), newMap.get(k)), "键 " + k + " 的值必须一致");
        }

        // ④ getParameterValues
        for (String name : new String[]{"a", "b", "only"}) {
            Object oldV = oldCls.getMethod("getParameterValues", String.class).invoke(oldJr, name);
            assertTrue(Arrays.equals((String[]) oldV, newJr.getParameterValues(name)),
                    "getParameterValues(\"" + name + "\") 必须一致");
        }

        // ⑤ getParameterNames：不回落（jsonObject 非 null 时给其 keySet）
        java.util.List<String> oldNames = java.util.Collections.list(
                (java.util.Enumeration<String>) oldCls.getMethod("getParameterNames").invoke(oldJr));
        java.util.List<String> newNames = java.util.Collections.list(newJr.getParameterNames());
        assertEquals(new TreeSet<>(oldNames), new TreeSet<>(newNames));
        assertTrue(!newNames.contains("only"), "getParameterNames 不回落 —— only 只存在于被包装请求");
    }

    /**
     * 非对象非数组的请求体：两者都 false、不报错。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("非对象/非数组体：isJSONObject 与 isJSONArray 均为 false，且不报错")
    void scalarBodyIsNeither() throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("only", "v");

        LegacyJsonRequest newJr = new LegacyJsonRequest("123", jakartaSpy(p));
        assertTrue(!newJr.isJSONObject());
        assertTrue(!newJr.isJSONArray());
        assertEquals("v", newJr.getParameter("only"), "jsonObject 为 null 时回落被包装请求");
        assertTrue(!newJr.getParameterNames().hasMoreElements(),
                "jsonObject 为 null 时 getParameterNames 应为空枚举（不回落）");

        // 数组体：isJSONArray 为 true
        LegacyJsonRequest arr = new LegacyJsonRequest("[1,2]", jakartaSpy(p));
        assertTrue(arr.isJSONArray());
        assertTrue(!arr.isJSONObject());
    }

    /**
     * 取 map（旧侧返回的是 javax 无关的 Map，可直接用）。
     *
     * @param o 对象
     * @return map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String[]> asMap(Object o) {
        return (Map<String, String[]>) o;
    }

    /**
     * jetty 侧替身（jakarta）。
     *
     * @param params 参数
     * @return 替身
     */
    private static HttpServletRequest jakartaSpy(Map<String, String> params) {
        return (HttpServletRequest) spy(HttpServletRequest.class, params);
    }

    /**
     * 建只服务 getParameter/getParameterMap 的请求替身。
     *
     * @param reqType 请求接口（javax 或 jakarta）
     * @param params  参数
     * @return 替身
     */
    private static Object spy(Class<?> reqType, Map<String, String> params) {
        Map<String, String[]> multi = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            multi.put(e.getKey(), new String[]{e.getValue()});
        }
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "getParameter": {
                    String[] v = multi.get(args[0]);
                    return (v != null && v.length > 0) ? v[0] : null;
                }
                case "getParameterMap":
                    return new LinkedHashMap<>(multi);
                case "getParameterValues":
                    return multi.get(args[0]);
                case "getParameterNames":
                    return java.util.Collections.enumeration(multi.keySet());
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                case "toString":
                    return "ReqSpy";
                default:
                    return null;
            }
        };
        return Proxy.newProxyInstance(LegacyJsonRequestGoldenTest.class.getClassLoader(),
                new Class<?>[]{reqType}, h);
    }

}
