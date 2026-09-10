/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.jfinal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cn.eova.tools.x;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import cn.eova.db.EovaModel;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.common.utils.jfinal.RecordUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>Record/Model 与 JSON 的互转工具（peel / parseObject / parseArray）</li>
 *   <li>parseArray 依赖 fastjson 的 JSONObject 行为</li>
 * </ol>
 */
public class RecordUtil {

    /**
     * 从指定Record中剥离出指定字段
     *
     * @param data 原数据集
     * @param columnNames 要剥离的字段名组
     * @return 剥离出的数据集
     */
    public static EovaRecord peel(EovaRecord data, String... columnNames) {
        EovaRecord o = new EovaRecord();
        for (String name : columnNames) {
            if (name.contains("->")) {
                String[] ss = name.split("->");
                String source = ss[0].trim();
                String target = ss[1].trim();
                o.set(target, data.get(source));
                // 虚拟字段: Table模式不参与持久化无需移除, View模式也不应使用虚拟字段
                // 存在关系的字段不移除，后续其它表可能还需使用
            } else {
                o.set(name, data.get(name));
                data.remove(name);
            }
        }
        return o;
    }

    /**
     * 从指定Record中剥离出指定字段
     * @param modelClass 类型
     * @param data 原数据集
     * @param columnNames 要剥离的字段名组(默认使用全部数据)
     * @return
     */
    public static <T extends EovaModel> T peelModel(Class<? extends EovaModel> modelClass, EovaRecord data, String... columnNames) {
        EovaModel<?> m = null;
        try {
            m = modelClass.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (x.isEmpty(columnNames)) {
            m._setAttrs(data.getColumns());
        } else {
            EovaRecord r = peel(data, columnNames);
            m._setAttrs(r.getColumns());
        }
        return (T) m;
    }

    /**
     * JSON String -> List&lt;EovaRecord>
     * @param json
     * @return
     */
    public static List<EovaRecord> parseArray(String json) {
        List<EovaRecord> records = new ArrayList<EovaRecord>();

        List<JSONObject> list = JSON.parseArray(json, JSONObject.class);
        for (JSONObject o : list) {
            Map<String, Object> map = JSON.parseObject(o + "", new TypeReference<Map<String, Object>>() {
            });
            EovaRecord e = new EovaRecord();
            e.setColumns(map);
            records.add(e);
        }

        return records;
    }

    /**
     * JSON String -> EovaRecord
     * @param json
     * @return
     */
    public static EovaRecord parseObject(String json) {
        Map<String, Object> map = JSON.parseObject(json, new TypeReference<Map<String, Object>>() {
        });
        EovaRecord e = new EovaRecord();
        e.setColumns(map);
        return e;
    }
}