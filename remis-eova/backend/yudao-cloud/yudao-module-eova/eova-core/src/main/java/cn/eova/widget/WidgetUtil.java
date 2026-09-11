/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.eova.tools.x;
import com.alibaba.fastjson.JSONObject;
import cn.eova.core.menu.config.TreeConfig;
import cn.eova.engine.EovaExp;
import cn.eova.model.Menu;
import cn.eova.model.MetaField;
import cn.eova.widget.tree.TreeUtil;
import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import cn.eova.db.EovaModel;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.widget.WidgetUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>通用控件数据组装（209 行）：树/下拉/分组的 JSON 组装，被 AuthService 与多个控制器使用</li>
 *   <li>【已声明适配 1】com.jfinal.kit.JsonKit -> LegacyJsonKit</li>
 *   <li>【已声明适配 2】com.jfinal.plugin.activerecord.Record -> cn.eova.db.EovaRecord</li>
 *   <li>【已声明适配 3】com.jfinal.plugin.activerecord.Model -> cn.eova.db.EovaModel</li>
 *   <li>与 TreeUtil 互为依赖（两文件同批 port，否则单侧编译不过）</li>
 * </ol>
 */
public class WidgetUtil {

    /**
     * 原值被自动转成了中文显示，所以转换前先备份值为"字段_val"
     *
     * @param reList Grid数据集
     * @param pkName 当前Eova_Object的主键名
     */
    public static void copyValueColumn(List<EovaRecord> reList, String pkName, List<MetaField> eis) {
        // 复制主键列
        for (EovaRecord re : reList) {
            for (MetaField f : eis) {
                // 如果有表达式，说明会被翻译，所以需要备份列
                if (!EovaExp.isFormNull(f.getExp())) {
                    String en = f.getEn();
                    // 备份被转换的列的值
                    re.set(en + "_val", re.get(en, ""));
                }
            }
            // 复制主键值列
            if (!x.isEmpty(pkName)) {
                re.set("pk_val", re.get(pkName));
            }
        }
    }


    public static List<EovaRecord> modelsToRecords(List<? extends EovaModel> models) {
        List<EovaRecord> es = new ArrayList<>();
        for (EovaModel m : models) {
            es.add(new EovaRecord().setColumns(m));
        }
        return es;
    }

    /**
     * Tree数据准备 List转有序Map
     * @param records
     * @return
     */
    public static Map<String, EovaRecord> listToLinkedMap(List<EovaRecord> records, TreeConfig treeConfig) {
        LinkedHashMap<String, EovaRecord> temp = new LinkedHashMap<>();
        for (EovaRecord r : records) {
            // 获取EasyUI所需ICON字段名
            //			String icon = x.get("icon");
            //			if (x.isEmpty(icon)) {
            //				icon = "icon-application";
            //			}
            //			x.set("icon", icon);

            temp.put(r.get(treeConfig.getIdField()).toString(), r);
        }
        return temp;
    }

    /**
     * Tree数据准备 List转有序Map
     * @param records
     * @return
     */
    public static LinkedHashMap<Integer, Menu> menusToLinkedMap(List<Menu> records) {
        LinkedHashMap<Integer, Menu> temp = new LinkedHashMap<Integer, Menu>();
        for (Menu r : records) {
            // 获取EasyUI所需ICON字段名
            String icon = r.get("icon");
            if (x.isEmpty(icon)) {
                icon = "icon-application";
            }
            r.put("icon", icon);

            temp.put(r.getInt("id"), r);
        }
        return temp;
    }

    /**
     * 将Menu转为Tree Json
     *
     * @param temp
     * @return
     */
    public static String menu2TreeJson(Map<Integer, Menu> temp, Integer rootId) {
        LinkedHashMap<String, EovaRecord> recordMap = new LinkedHashMap<String, EovaRecord>();
        // model to record
        for (Map.Entry<Integer, Menu> map : temp.entrySet()) {
            recordMap.put(map.getKey().toString(), new EovaRecord().setColumns(map.getValue()));
        }
        return map2TreeJson(recordMap, rootId);
    }

    /**
     * 菜单转化为Tree所需JSON结构
     *
     * @param temp
     * @return
     */
    public static String map2TreeJson(Map<String, EovaRecord> temp, Integer rootId) {
        // EasyUI 公共数据格式处理
        for (Map.Entry<String, EovaRecord> map : temp.entrySet()) {
            EovaRecord r = map.getValue();

            // 是否默认折叠
            String state = "open";
            Boolean isOpen = r.getBoolean("open");
            if (!x.isEmpty(isOpen) && !isOpen) {
                state = "closed";
            }
            r.set("state", state);
            r.set("iconCls", r.getStr("icon"));
            // 移除无用属性
            r.remove("open");
            r.remove("icon");
            r.remove("url");
            r.remove("diy_js");
            r.remove("config");
            r.remove("biz_intercept");
        }


        EovaRecord tree = TreeUtil.buildTree(rootId + "", "parent_id", "children", temp);

        // 组装Tree Json
        StringBuilder sb = new StringBuilder("[");
        // 获取根节点
        List<EovaRecord> childList = tree.get("children");
        for (EovaRecord r : childList) {
            sb.append(LegacyJsonKit.toJson(r));
            sb.append(",");
        }
        sb.delete(sb.length() - 1, sb.length());
        sb.append("]");
        //System.out.println(sb.toString());

        // 大小写与EasyUI兼容问题：
        String json = sb.toString();
        json = json.replaceAll("iconcls", "iconCls");

        return json;
    }

    /**
     * 格式化EasyUI Tree
     *
     * @param maps Tree Map
     */
    public static void formatEasyTree(Map<Integer, Menu> maps) {
        for (Map.Entry<Integer, Menu> map : maps.entrySet()) {
            Menu x = map.getValue();

            // 目录无URL，为功能构建URL
            JSONObject attrs = new JSONObject();
            if (!x.getStr("type").equals(Menu.TYPE_DIR)) {
                attrs.put("url", x.getUrl());
            }

            x.put("id", x.getInt("id"));
            x.put("text", x.getStr("name"));
            x.put("attributes", attrs);
            // menu.put("checked", false);
            x.remove("name");
        }
    }

    /**
     * 递归获得上级节点
     *
     * @param allMenu 所有菜单
     * @param authParent 需要获取上级的节点Map(找到的父节点也会放进来)
     * @param menu 当前节点
     */
    public static void getParent(HashMap<Integer, Menu> allMenu, HashMap<Integer, Menu> authParent, Menu menu) {
        // 获取上级父节点
        Integer pid = menu.getInt("parent_id");
        // 上级不存在 || =0，说明到了Root
        if (x.isEmpty(pid) || pid == 0) {
            return;
        }
        Menu pm = allMenu.get(pid);
        authParent.put(pid, pm);
        // 递归上上级
        getParent(allMenu, authParent, pm);
    }


}