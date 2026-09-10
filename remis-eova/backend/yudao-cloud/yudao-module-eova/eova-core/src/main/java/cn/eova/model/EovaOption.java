/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.model;

import cn.eova.common.base.BaseCache;
import cn.eova.common.base.BaseModel;
import cn.eova.tools.x;
import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import cn.eova.compat.jfinal.kit.LegacyKv;

/**
 * <p>ported from: cn.eova.model.EovaOption
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>conf 为惰性解析的 Kv；未配置时保持 null</li>
 *   <li>JsonKit.parse 实测走 fastjson（MixedJson.parse -> FastJson），故适配为 LegacyJsonKit.parse(json, LegacyKv.class) 与旧栈同构</li>
 * </ol>
 */
/**
 * Eova自定义选项
 * 主要用于自动构建:查找框,下拉框, 复选框 等选择类场景的选项来源
 *
 * @author Jieven
 */
public class EovaOption extends BaseModel<EovaOption> {

    private static final long serialVersionUID = -1592533967096109392L;

    public static final EovaOption dao = new EovaOption().dao();

    public static EovaOption create(String code, String ds, String sql, String fieldVal, String fieldTxt) {
        EovaOption option = new EovaOption();
        option.set("code", code);
        option.set("ds", ds);
        option.set("sql", sql);
        option.set("field_val", fieldVal);
        option.set("field_txt", fieldTxt);
        return option;
    }

    /*
        查找框:
        可查询字段: a,b,c (默认=txtField)
        字段宽度: a=100,b=200,b=300
        字段名称: a=姓名,b=李四,c=哈哈
        窗口宽度	0.8
        窗口高度	0.5

        下拉树
        根节点:	0

        下拉框
        首项是否为空项 : true
     */

    private LegacyKv conf = null;

    /**
     * sql exp
     * @return
     */
    public String getSql() {
        return this.getStr("sql");
    }

    public String getDs() {
        return this.getStr("ds");
    }

    public String getCache() {
        return this.getStr("cache");
    }

    public String getFieldVal() {
        return this.getStr("field_val");
    }

    public String getFieldTxt() {
        return this.getStr("field_txt");
    }

    public String getConf(String key, String defaultValue) {
        if (conf == null) {
            String json = this.getStr("config");
            if (x.isEmpty(json)) {
                json = "{}";
            }
            conf = LegacyJsonKit.parse(json, LegacyKv.class);
        }
        String s = conf.getStr(key);
        if (x.isEmpty(s)) {
            return defaultValue;
        }
        return s;
    }

    public LegacyKv getConfObj(String key) {
        String s = getConf(key, null);
        if (x.isEmpty(s)) {
            return null;
        }
        return LegacyJsonKit.parse(s, LegacyKv.class);
    }

    public void setConfig(LegacyKv kv) {
        this.set("config", kv.toJson());
    }

    public EovaOption getByCode(String code) {
        String sql = "select * from eova_option where code = ?";
        return dao.findFirstByCache(BaseCache.META, sql + code, sql, code);
    }

}