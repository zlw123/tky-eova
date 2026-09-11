/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.core.paragetter;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.core.paragetter.JsonRequest} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.core.paragetter.JsonRequest}（jfinal 5.2.6）。</p>
 *
 * <p><b>EOVA 侧用法（全树普查）：只有一处 —— {@code WebUtil.isAjax} 的
 * {@code request instanceof JsonRequest}</b>。但本接缝仍按<b>完整语义</b>实现，
 * 因为 {@code LegacyController.getKv()} 也会判该类型（jfinal 的 {@code getKv}
 * 有 {@code instanceof JsonRequest} 分支，见该方法）。</p>
 *
 * <p><b>⚠️ 已声明的适配：用 {@link HttpServletRequestWrapper} 代替"实现全部 60 个方法"。</b>
 * 旧实现是 {@code implements HttpServletRequest} 并<b>手写 60 个委托方法</b>
 * （逐个转发给内部 {@code req}）。新接缝改为继承
 * {@code HttpServletRequestWrapper} —— 它在语义上正是"把所有方法委托给被包装请求"
 * 的标准装饰器，故<b>委托行为按构造等价</b>，且免去 60 段容易抄错的样板。
 * 这是一个<b>底座替换</b>（形如 {@code LegacyController} 之于 jfinal 的宿主依赖），
 * 不是行为改写：本类只覆写与 JSON 语义相关的那几个方法。</p>
 *
 * <p><b>逐条取自旧字节码：</b>
 * <ol>
 *   <li>构造：{@code JSON.parse(body)}；{@code instanceof JSONObject} → 存 jsonObject；
 *       {@code instanceof JSONArray} → 存 jsonArray；两者都不是则都留 null（<b>不报错</b>）。</li>
 *   <li>{@code getParameter(name)}：<b>先查 jsonObject</b>（含该键时，值是 {@code JSON}
 *       则返回其 {@code toJSONString()}，否则 {@code toString()}，null 则返回 null）；
 *       <b>否则回落</b>到被包装请求的 {@code getParameter}。</li>
 *   <li>{@code createParaMap}：先 {@code putAll(被包装请求的 getParameterMap())}
 *       （仅当其非 null 且 size &gt; 0）；再遍历 jsonObject —— 值为 {@code JSON} 存
 *       {@code toJSONString()}，值非 null 存 {@code toString()}，
 *       值为 null 时放入 <b>{@code null} 值</b>（键【存在】，值不是数组而是 null）。</li>
 *   <li>私有的 {@code getParaMap()} 惰性构建：{@code jsonObject != null} 时用
 *       {@code createParaMap}，否则用空 {@code HashMap}。</li>
 *   <li>{@code getParameterMap()} / {@code getParameterValues(name)} 都走该惰性 map。</li>
 *   <li>{@code getParameterNames()}：{@code jsonObject != null} 时返回其
 *       {@code keySet()} 的枚举，否则<b>空枚举</b>（<b>不</b>回落被包装请求 ——
 *       与 {@code getParameter} 的回落行为不同，属既有不对称，照抄）。</li>
 *   <li>{@code isJSONObject()} / {@code isJSONArray()}：对应字段非 null。</li>
 * </ol>
 */
public class LegacyJsonRequest extends HttpServletRequestWrapper {

    private JSONObject jsonObject;

    private JSONArray jsonArray;

    private HashMap<String, String[]> paraMap;

    /**
     * 构造。
     *
     * @param body 请求体
     * @param req  被包装请求
     */
    public LegacyJsonRequest(String body, HttpServletRequest req) {
        super(req);
        Object o = JSON.parse(body);
        if (o instanceof JSONObject) {
            this.jsonObject = (JSONObject) o;
        } else if (o instanceof JSONArray) {
            this.jsonArray = (JSONArray) o;
        }
    }

    /**
     * 取 JSON 对象（非对象体时为 null）。
     *
     * @return JSONObject
     */
    public JSONObject getJSONObject() {
        return jsonObject;
    }

    /**
     * 取 JSON 数组（非数组体时为 null）。
     *
     * @return JSONArray
     */
    public JSONArray getJSONArray() {
        return jsonArray;
    }

    /**
     * 取被包装的原始请求（旧实现为 {@code getInnerRequest}）。
     *
     * @return 原始请求
     */
    public HttpServletRequest getInnerRequest() {
        return (HttpServletRequest) getRequest();
    }

    /**
     * 是否 JSON 对象体。
     *
     * @return jsonObject != null
     */
    public boolean isJSONObject() {
        return jsonObject != null;
    }

    /**
     * 是否 JSON 数组体。
     *
     * @return jsonArray != null
     */
    public boolean isJSONArray() {
        return jsonArray != null;
    }

    /**
     * 取参数（先查 JSON 对象，再回落被包装请求）。
     *
     * @param name 参数名
     * @return 值
     */
    @Override
    public String getParameter(String name) {
        if (jsonObject != null && jsonObject.containsKey(name)) {
            Object o = jsonObject.get(name);
            if (o instanceof JSON) {
                return ((JSON) o).toJSONString();
            }
            return o != null ? o.toString() : null;
        }
        return getInnerRequest().getParameter(name);
    }

    /**
     * 惰性构建的参数表（规则见类注释）。
     *
     * @return 参数表
     */
    private HashMap<String, String[]> getParaMap() {
        if (paraMap == null) {
            paraMap = (jsonObject != null) ? createParaMap(jsonObject) : new HashMap<>();
        }
        return paraMap;
    }

    /**
     * 由 JSON 对象与被包装请求的参数表合成。
     *
     * @param jsonObject JSON 对象
     * @return 合成后的参数表
     */
    private HashMap<String, String[]> createParaMap(JSONObject jsonObject) {
        HashMap<String, String[]> map = new HashMap<>();
        Map<String, String[]> pm = getInnerRequest().getParameterMap();
        if (pm != null && pm.size() > 0) {
            map.putAll(pm);
        }
        for (Map.Entry<String, Object> e : jsonObject.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (value instanceof JSON) {
                map.put(key, new String[]{((JSON) value).toJSONString()});
            } else if (value != null) {
                map.put(key, new String[]{value.toString()});
            } else {
                // 【易错点】值为 null 时旧实现是 map.put(key, null) —— 键【会】放进 map，
                // 只是值为 null（不是 new String[]{null}）。
                // 我最初读漏了这一支、并在注释里写"无 else 分支"，
                // 由 LegacyJsonRequestGoldenTest 的键集比对当场抓出（旧有键 n，我没有）。
                map.put(key, null);
            }
        }
        return map;
    }

    /**
     * 取参数表。
     *
     * @return 参数表
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        return getParaMap();
    }

    /**
     * 取参数值的多值形态。
     *
     * @param name 参数名
     * @return 值数组
     */
    @Override
    public String[] getParameterValues(String name) {
        return getParaMap().get(name);
    }

    /**
     * 取参数名枚举（<b>不</b>回落被包装请求，属既有不对称）。
     *
     * @return 枚举
     */
    @Override
    public Enumeration<String> getParameterNames() {
        if (jsonObject != null) {
            return Collections.enumeration(jsonObject.keySet());
        }
        return Collections.emptyEnumeration();
    }

}
