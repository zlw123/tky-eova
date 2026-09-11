/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.model;

import java.util.List;

import cn.eova.tools.x;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import cn.eova.common.Ds;
import cn.eova.common.base.BaseModel;
import cn.eova.common.utils.db.DsUtil;
import cn.eova.config.EovaConfig;
import cn.eova.core.object.config.MetaObjectConfig;
import cn.eova.i18n.I18NBuilder;
import cn.eova.db.EovaGateways;

/**
 * <p>ported from: cn.eova.model.MetaObject
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>元对象（168 行）：对象/字段元数据的模型层，含 getFields/getTemplate/缓存查询</li>
 *   <li>【第 67 轮退掉 stub】原为 LC-011 起的已声明 compile-stub；本轮真 port 后从 DECLARED_STUBS 移除</li>
 *   <li>【已声明适配 1】com.jfinal.plugin.activerecord.Db -> cn.eova.db.EovaGateways（Db.use(Ds.EOVA).update/delete -> EovaGateways.get(Ds.EOVA).…）</li>
 *   <li>依赖 EovaConfig.getConvertor(ds)（该静态方法已按旧源码逐字补入 EovaConfig stub）</li>
 *   <li>⚠️ 关联缺口：convertorMap 在旧栈由 EovaDataSource 的业务方言初始化路径填充，新栈该路径尚未 port ⇒ 启动后本表为空（已记入风险项）</li>
 * </ol>
 */
public class MetaObject extends BaseModel<MetaObject> {

    private static final long serialVersionUID = 1635722346260249629L;

    public static final MetaObject dao = new MetaObject();

    private List<MetaField> fields;

    public Object buildPkValue(Object val) {
        return buildFieldValue(this.getDs(), getPk(), val);
    }

    /**
     * 构建主键精准类型值(用于兼容PGSQL强类型问题)
     *
     * @param ds
     * @param name
     * @param val
     * @return
     */
    public Object buildFieldValue(String ds, String name, Object val) {
        for (MetaField f : fields) {
            if (f.getEn().equals(name)) {
                return EovaConfig.getConvertor(ds).convert(f, val);
            }
        }
        return val;
    }

    public MetaObjectConfig getConfig() {
        String json = this.getStr("config");
        if (x.isEmpty(json)) {
            return null;
        }
        return new MetaObjectConfig(json);
    }

    /**
     * 获取元对象JSON配置
     * @return
     */
    public JSONObject getConf() {
        String config = this.getStr("config");
        if (x.isEmpty(config)) {
            return new JSONObject();
        }
        return JSON.parseObject(config);
    }

    public void setConfig(MetaObjectConfig config) {
        this.set("config", JSON.toJSONString(config));
    }

    public String getCode() {
        return this.getStr("code");
    }

    public String getName() {
        return this.getStr("name");
    }

    public String getDs() {
        return this.getStr("data_source");
    }

    public String getTable() {
        return this.getStr("table_name");
    }

    public String getPk() {
        return this.getStr("pk_name");
    }

    public String getView() {
        // 获取View(没有视图用Table)
        String view = this.getStr("view_name");
        if (x.isEmpty(view)) {
            view = getTable();
        }
        return view;
    }

    public boolean isView() {
        return x.isEmpty(this.getTable()) ? true : false;
    }

    public String getType() {
        String view = this.getStr("view_name");
        if (x.isEmpty(view)) {
            return DsUtil.TABLE;
        }
        return DsUtil.VIEW;
    }

    public String getBizIntercept() {
        return this.getStr("biz_intercept");
    }


    public List<MetaField> getFields() {
        return fields;
    }

    public void setFields(List<MetaField> fields) {
        this.fields = fields;
    }

    /**
     * 获取元对象数据模版(用于模拟元数据)
     * @return
     */
    public MetaObject getTemplate() {
        return this.queryFisrtByCache("select * from eova_object where code = 'eova_meta_template'");
    }

    /**
     * 根据Code获得对象
     * @param code 对象Code
     * @return 对象
     */
    public MetaObject getByCode(String code) {
        MetaObject o = MetaObject.dao.queryFisrtByCache("select * from eova_object where code = ?", code);
        I18NBuilder.model(o, "name");
        return o;
    }

    /**
     * 是否存在查询条件
     * @param objectCode
     * @return
     */
    public boolean isExistQuery(String objectCode) {
        return MetaObject.dao.isExist("select count(*) from eova_field where is_query = 1 and object_code = ?", objectCode);
    }

    /**
     * 删除元对象和元字段和对应字典
     * @param objectCode
     */
    public void deleteByObjectCode(String objectCode) {
        String sql = "delete from eova_object where code = ?";
        EovaGateways.get(Ds.EOVA).update(sql, objectCode);

        MetaField.dao.deleteByObjectCode(objectCode);

        // 删除字典
        EovaGateways.get(Ds.EOVA).delete("delete from eova_dict where object = ?", objectCode);
    }
}