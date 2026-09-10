/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.menu.config;

import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * <p>ported from: cn.eova.core.menu.config.MenuConfig
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>菜单配置聚合：ChartConfig / TreeConfig 为嵌套结构，缺失时保持 null（不造默认值）</li>
 *   <li>fastjson 反序列化 bean —— 与 envelope 的 JFinalJsonKit 是两条不同路径（R42）</li>
 * </ol>
 */
/**
 * 菜单配置
 *
 * @author Jieven
 *
 */
public class MenuConfig {
    // 主对象Code
    private String objectCode;
    // 主对象外键字段
    private String objectField;

    // 子对象Code列表
    private List<String> objects;
    // 子对象关联字段列表
    private List<String> fields;

    // 树属性
    private TreeConfig tree;

    // 图属性
    private ChartConfig chart;

    // 个性化参数
    private JSONObject params = new JSONObject();

    public MenuConfig() {
    }

    public MenuConfig(String str) {
        JSONObject json = JSON.parseObject(str);
        String a = json.getString("object_code");
        String b = json.getString("objectCode");

        if (a != null) {
            this.objectCode = a;
        } else if (b != null) {
            this.objectCode = b;
        }

        // System.out.println("EovaMeta兼容:重新获取objectCode:" + this.objectCode);
        this.objectField = json.getString("objectField");
        this.objects = JSON.parseArray(json.getString("objects"), String.class);
        this.fields = JSON.parseArray(json.getString("fields"), String.class);
        this.tree = JSON.parseObject(json.getString("tree"), TreeConfig.class);
        this.chart = JSON.parseObject(json.getString("chart"), ChartConfig.class);

        this.params = JSON.parseObject(json.getString("params"));
    }

    public String getObjectCode() {
        return objectCode;
    }

    public void setObjectCode(String objectCode) {
        this.objectCode = objectCode;
    }

    public String getObjectField() {
        return objectField;
    }

    public void setObjectField(String objectField) {
        this.objectField = objectField;
    }

    public List<String> getObjects() {
        return objects;
    }

    public void setObjects(List<String> objects) {
        this.objects = objects;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public TreeConfig getTree() {
        return tree;
    }

    public void setTree(TreeConfig tree) {
        this.tree = tree;
    }

    public ChartConfig getChart() {
        return chart;
    }

    public void setChart(ChartConfig chart) {
        this.chart = chart;
    }

    public JSONObject getParams() {
        return params;
    }

    /**
     * 获取自定义参数
     * @param key
     * @return
     */
    public String get(String key) {
        if (params == null) {
            return null;
        }
        return params.getString(key);
    }

    public void setParams(JSONObject params) {
        this.params = params;
    }

    public static void main(String[] args) {
        MenuConfig mc = new MenuConfig();
        TreeConfig tc = new TreeConfig();
        tc.setObjectCode("eova_menu_code");
        tc.setObjectField("code");

        tc.setIdField("id");
        tc.setParentField("parent_id");
        tc.setTreeField("name");
        tc.setIconField("icon");
        mc.setTree(tc);

        mc.setObjectCode("eova_button_code");
        mc.setObjectField("menu_code");

        JSONObject params = new JSONObject();
        params.put("slave_height", 500);
        mc.setParams(params);

        String s = JSONObject.toJSONString(mc);
        System.out.println(s);
    }

}