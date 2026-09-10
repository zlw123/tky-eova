/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.model;

import java.util.List;

import cn.eova.tools.x;
import com.alibaba.fastjson.JSON;
import cn.eova.common.Ds;
import cn.eova.common.base.BaseModel;

/**
 * <p>ported from: cn.eova.model.MenuObject
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>getConfig 用 fastjson 反序列化为 MetaFieldConfig bean；解析失败会抛（不吞异常）</li>
 *   <li>setConfig 用 fastjson 序列化 —— 与 envelope 用的 JFinalJsonKit 是两条不同路径，不可互换（R42）</li>
 *   <li>deleteByMenuCode 显式指定 eova 数据源（不依赖模型自身映射）—— 故适配为 gw(Ds.EOVA) 而非 gw()</li>
 * </ol>
 */
/**
 * 菜单关联对象
 *
 * @author Jieven
 * @date 2014-9-18
 */
public class MenuObject extends BaseModel<MenuObject> {

    private static final long serialVersionUID = 9176734392973431592L;

    public static final MenuObject dao = new MenuObject();

    public MetaFieldConfig getConfig() {
        String json = this.getStr("config");
        if (x.isEmpty(json)) {
            return null;
        }
        return JSON.parseObject(json, MetaFieldConfig.class);
    }

    public void setConfig(MetaFieldConfig config) {
        this.set("config", JSON.toJSONString(config));
    }

    /**
     * 获取菜单关联对象
     * @param menuCode
     * @return
     */
    public List<MenuObject> queryByMenuCode(String menuCode) {
        return MenuObject.dao.queryByCache("select object_code from eova_menu_object where menu_code = ?", menuCode);
    }

    /**
     * 删除菜单关联数据对象
     * @param menuCode
     */
    public void deleteByMenuCode(String menuCode) {
        gw(Ds.EOVA).update("delete from eova_menu_object where menu_code = ?", menuCode);
    }


}