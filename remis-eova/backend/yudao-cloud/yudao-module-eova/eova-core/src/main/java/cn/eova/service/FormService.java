/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import cn.eova.tools.x;
import cn.eova.common.Ds;
import cn.eova.common.base.BaseService;
import cn.eova.common.utils.jfinal.RecordUtil;
import cn.eova.common.utils.xx;
import cn.eova.model.MetaField;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.service.FormService
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>表单服务（167 行）：extends BaseService，表单数据装配</li>
 *   <li>【已声明适配】Controller -> LegacyController；Db.use -> EovaGateways.get；Record -> EovaRecord；JsonKit -> LegacyJsonKit</li>
 * </ol>
 */
/**
 * 动态表单服务
 *
 * @author Jieven
 *
 */
public class FormService extends BaseService {

    /**
     * 获取表单字段
     * @param formCode 表单编码
     * @return
     */
    public List<EovaRecord> getFormField(String formCode) {
        List<EovaRecord> list = EovaGateways.get(Ds.EOVA).find(
                "select fw.id widget_id,fw.type,fw.exp widget_exp,fw.validator,fw.config,fw.path,ff.* from eova_form_field ff left join eova_flow_widget fw on ff.widget_type = fw.name where form_code = ? order by ff.fieldnum,ff.num",
                formCode);
        list.forEach(e -> {

            // 表达式的特别处理(如果存在表达式,自动从原表查找!)
            String exp = e.getStr("exp");
            String widgetExp = e.getStr("widget_exp");
            // 字段表达式优先, 没有就用控件预设的
            if (x.isEmpty(exp)) {
                exp = widgetExp;
            }
            if (!x.isEmpty(exp)) {
                exp = exp.trim();
                // 如果是SQL表达式, 就注明和标记SQL取值关系.
                if (exp.toLowerCase().startsWith("select")) {
                    e.set("exp", "EOVA_FLOW_WIDGET," + e.getInt("widget_id"));
                }
                // 反之说明是固定值表达式,进行值的数据JSON预处理
                else {
                    if (!x.isEmpty(exp)) {
                        // 按空格分割
                        String[] ss = xx.splitBlank(exp);
                        List<EovaRecord> arrs = new ArrayList<>();
                        for (int j = 0; j < ss.length; j++) {
                            // TODO 有值和传统玩法的兼容!!
                            arrs.add(new EovaRecord().set("id", ss[j]).set("cn", ss[j]));
                        }
                        e.set("items", arrs);// 构建字段
                        e.set("items_json", LegacyJsonKit.toJson(arrs));// 构建条件
                    }
                }
            }

            // 配置预处理
            String config = e.getStr("config");
            if (!x.isEmpty(config)) {
                e.set("config", MetaField.parseConfig(config));
                e.set("conf", RecordUtil.parseObject(config));
            }
        });
        return list;
    }

    public HashMap<String, List<EovaRecord>> getFormSet(String formCode) {
        // 获取当前节点表单
        List<EovaRecord> fields = EovaGateways.get(Ds.EOVA).find("select * from eova_form_field where form_code = ? order by fieldnum, num", formCode);
        LinkedHashMap<String, List<EovaRecord>> fieldMap = new LinkedHashMap<>();
        fields.forEach(f -> {
            String key = f.getStr("fieldset");
            List<EovaRecord> fieldset = fieldMap.get(key);
            if (fieldset == null) {
                fieldset = new ArrayList<>();
            }
            fieldset.add(f);
            fieldMap.put(key, fieldset);
        });
        return fieldMap;
    }

    /**
     * 更改排序
     *
     * @param code 表单编码
     * @param sid 原字段
     * @param tid 目标字段
     */
    public void updateOrderNum(String code, String sid, String tid) {
        EovaRecord e = EovaGateways.get(Ds.EOVA).findFirst("select * from eova_form_field where form_code = ? and id = ?", code, tid);
        EovaGateways.get(Ds.EOVA).update("update eova_form_field set num = ? where form_code = ? and id = ?", e.getInt("num") + 1, code, sid);
    }

    /**
     * 获取表单数据并格式化
     * @param c
     * @param fields
     * @return
     */
    public static EovaRecord buildData(LegacyController c, List<EovaRecord> fields) {
        EovaRecord data = new EovaRecord();

        for (EovaRecord e : fields) {
            // 字段名
            String en = e.getStr("en");
            //			String cn = e.getStr("cn");
            //			String type = e.getStr("type");
            //			String wid = e.getStr("widget_id");// 控件业务ID

            String value = c.get(en);
            // 控制跳过
            if (x.isEmpty(value)) {
                continue;
            }

            // 值格式化
            //			if (!x.isEmpty(value)) {
            //				String IMG = x.conf.get("domain_img");// 图片域名配置
            //				MetaFieldConfig config = e.get("config");// 多图框业务配置
            //				if (type.equals("多图框")) {
            //					String[] vs = value.split(",");
            //					StringBuilder sb = new StringBuilder("<ul class='eova-img-list'>");
            //					for (String v : vs) {
            //						sb.append(String.format("<li><img src='%s/%s/%s'></li>", IMG, config.getFiledir(), v));
            //					}
            //					value = sb.append("</ul>").toString();
            //				}
            //				else if (type.equals("附件框")) {
            //					String[] vs = value.split(",");
            //					StringBuilder sb = new StringBuilder("<ul class='eova-file-list'>");
            //					for (String v : vs) {
            //						sb.append(String.format("<li><a target='_blank' href='%s/%s/%s'>%s</a></li>", IMG, config.getFiledir(), v, v));
            //					}
            //					value = sb.append("</ul>").toString();
            //				}
            //			}

            //			EovaRecord data = new EovaRecord();
            //			e.set("type", type);
            //			e.set("widget_id", wid);
            //			e.set("en", en);
            //			e.set("cn", cn);
            //			e.set("value", value);
            data.set(en, value);

            //			list.add(data);
        }

        return data;
    }
}