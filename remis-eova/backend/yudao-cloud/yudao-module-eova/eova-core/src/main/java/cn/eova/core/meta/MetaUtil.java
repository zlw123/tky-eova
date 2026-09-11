/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.meta;

import java.util.List;

import cn.eova.tools.x;
import cn.eova.common.Ds;
import cn.eova.common.utils.xx;
import cn.eova.model.MetaField;
import cn.eova.model.MetaObject;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.core.meta.MetaUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>元数据工具（195 行）：对象/字段元数据的装配与 JSON 输出</li>
 *   <li>【已声明适配 1】com.jfinal.plugin.activerecord.Db -> EovaGateways</li>
 *   <li>【已声明适配 2】com.jfinal.plugin.activerecord.Record -> EovaRecord</li>
 * </ol>
 */
public class MetaUtil {

    /**
     * 转换数据类型
     *
     * @param typeName DB数据类型
     * @return
     */
    public static String getDataType(String typeName) {
        typeName = typeName.toLowerCase();
        if (typeName.contains("int") || typeName.contains("bit") || typeName.equals("number") || typeName.equals("double") || typeName.equals("float")) {
            return MetaField.DATATYPE_NUMBER;
        } else if (typeName.contains("time") || typeName.contains("date")) {
            return MetaField.DATATYPE_TIME;
        } else {
            return MetaField.DATATYPE_STRING;
        }
    }

    /**
     * 获取表单类型
     *
     * @param isAuto 是否自增
     * @param typeName 类型
     * @param size 长度
     * @return
     */
    public static String getFormType(boolean isAuto, String typeName, int size) {
        typeName = typeName.toLowerCase();
        if (typeName.contains("time") || typeName.contains("date")) {
            return MetaField.TYPE_TIME;
        } else if (size > 255) {
            return MetaField.TYPE_TEXTS;
        } else if (size > 500) {
            return MetaField.TYPE_EDIT;
        } else if (size == 1 && (typeName.equals("tinyint") || typeName.equals("char"))) {
            return MetaField.TYPE_BOOL;
        } else {
            // 默认都是文本框
            return MetaField.TYPE_TEXT;
        }
    }

    /**
     * 添加虚拟元对象
     * @param sql 根据 SQL select 自动构建虚拟元对象
     * @param code 菜单编码
     * @param name 菜单名称
     * @param ds SQL执行数据源
     */
    public static void addVirtualObject(String sql, String code, String name, String ds) throws Exception {
        int i1 = sql.toLowerCase().indexOf("select") + 6;
        int i2 = sql.toLowerCase().indexOf("from");

        if (i1 == -1) {
            throw new Exception("缺少select关键字, 请检查SQL语句");
        }
        if (i2 == -1) {
            throw new Exception("缺少from关键字, 请检查SQL语句");
        }

        code = "v_" + code;

        // 根据表达式手工构建Eova_Object
        MetaObject eo = MetaObject.dao.getTemplate();
        eo.remove("id");
        eo.set("code", code);
        eo.set("name", name);
        eo.set("data_source", ds);
        eo.set("table_name", "virtual");
        eo.set("is_first_load", 0);
        eo.set("view_sql", sql);
        eo.save();

        // 根据表达式手工构建Eova_Item
        String select = sql.trim().toLowerCase().substring(i1, i2);
        String[] ss = select.split(",");
//		if (ss.length < 2) {
//		    throw new Exception("自定义查询最少要有2列");
//		}
        int i = 10;
        for (String s : ss) {
            // num1, num2
            String item = s.trim();
            //  (sum1-sum2) total
            if (item.indexOf(" ") != -1) {
                // 取最后一项
                String[] items = item.split(" ");
                item = items[items.length - 1];
            }

            MetaField ei = MetaField.dao.getTemplate();
            ei.remove("id");
            ei.put("num", i);
            ei.put("en", item);
            ei.put("cn", item);
            ei.put("type", "文本框");
            ei.put("is_show", 1);
            ei.put("width", 100);
            ei.set("object_code", code);
            ei.save();

            i++;
        }

    }


    /**
     * 删除用户个性化设置
     * @param objectCode 目标元对象
     * @param fields 允许个性化的字段
     * @param diyField 当前个性化字段
     */
    public static void removeUserDiy(String objectCode, List<EovaRecord> fields, List<String> diyField, Integer uid) {
        x.log.info("删除用户个性化设置:" + objectCode);
        // 删除旧字段
        for (String s : diyField) {
            boolean flag = false;
            for (EovaRecord f : fields) {
                if (s.equals(f.getStr("en"))) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                String sql = "delete from eova_diy where object_code = ? and en = ?";
                if (uid != null) {
                    sql += " and uid = " + uid;
                }
                EovaGateways.get(Ds.EOVA).delete(sql, objectCode, s);
            }
        }
    }

    /**
     * 删除角色个性化设置
     * @param object 元对象
     * @param fields 元字段
     * @param roles 角色IDs
     * @param type 字段授权策略
     */
    public static void removeRoleDiy(String object, String fields, String roles, int type) {
        if (x.isOneEmpty(object, fields)) {
            return;
        }

        x.log.info("删除角色个性化设置:" + object);
        for (String field : fields.split(",")) {
            String sql = "delete from eova_diy where object_code = ? and en = ?";

            String userDs = x.conf.get("login.user.ds", Ds.EOVA);
            String userTable = x.conf.get("login.user.table", "eova_user");
            String userRid = x.conf.get("login.user.rid", "rid");

            List<String> uids = EovaGateways.get(userDs).query(String.format("select id from %s where %s in (%s)", userTable, userRid, roles));
            // 角色黑名单 当前角色全移除
            if (type == 2) {
                // 如果没有对应用户, 跳过当前字段
                if (x.isEmpty(uids)) {
                    return;
                }

                sql += String.format(" and uid in (%s)", xx.join(uids));
            }
            // 角色白名单 其他角色全移除
            else if (type == 1) {
                // 如果没有对应用户, 该字段全部清理(无需条件)
                if (!x.isEmpty(uids)) {
                    sql += String.format(" and uid not in (%s)", xx.join(uids));
                }
            }

            EovaGateways.get(Ds.EOVA).delete(sql, object, field);
        }
    }

}