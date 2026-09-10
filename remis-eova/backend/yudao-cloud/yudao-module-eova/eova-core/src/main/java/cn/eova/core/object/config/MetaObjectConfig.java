/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.object.config;

import java.util.LinkedHashMap;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import cn.eova.common.utils.xx;

/**
 * <p>ported from: cn.eova.core.object.config.MetaObjectConfig
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>元对象配置（78 行）：JSON <-> LinkedHashMap，TypeReference 反序列化</li>
 *   <li>宿主依赖仅 fastjson —— 【无需任何替换】，天然逐字节 port</li>
 * </ol>
 */
/**
 * 元对象配置
 *
 * @author Jieven
 *
 */
public class MetaObjectConfig {

    // JSON配置
    private JSONObject json;
    // 视图配置
    private LinkedHashMap<String, TableConfig> view;

    public MetaObjectConfig() {
    }

    public MetaObjectConfig(String s) {
        this.json = JSON.parseObject(s);
        this.view = JSON.parseObject(json.getString("view"), new TypeReference<LinkedHashMap<String, TableConfig>>() {
        });
    }


    public LinkedHashMap<String, TableConfig> getView() {
        return view;
    }

    public void setView(LinkedHashMap<String, TableConfig> view) {
        this.view = view;
    }

    public JSONObject getJson() {
        return json;
    }

    public static void main(String[] args) {
        MetaObjectConfig o = new MetaObjectConfig();
        // {"viewMap":{"users":{"key":"id","value":"id"},"users_exp":{"key":"users_id","value":"id"}}}
        LinkedHashMap<String, TableConfig> v = new LinkedHashMap<>();
        {
            TableConfig tc = new TableConfig();
            tc.setWhereField("id");
            tc.setParamField("id");
            v.put("users", tc);
        }
        {
            TableConfig tc = new TableConfig();
            tc.setWhereField("users_id");
            tc.setParamField("id");
            v.put("users_exp", tc);
        }
        o.setView(v);

        String s = JSONObject.toJSONString(o);
        System.out.println(xx.formatJson(s));

        MetaObjectConfig metaObjectConfig = new MetaObjectConfig(s);
        String s1 = JSONObject.toJSONString(metaObjectConfig);
        System.out.println(xx.formatJson(s1));

    }


}