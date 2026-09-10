/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.model;

import java.util.ArrayList;
import java.util.List;

import cn.eova.tools.x;
import cn.eova.common.base.BaseModel;


/**
 * <p>ported from: cn.eova.model.Role
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>纯 BaseModel 子类，无宿主依赖 —— 逐字节即语义等价</li>
 * </ol>
 */
/**
 * 用户角色
 *
 * @author Jieven
 * @date 2014-9-10
 */
public class Role extends BaseModel<Role> {

    private static final long serialVersionUID = -1794335434198017392L;

    public static final Role dao = new Role();

    /**
     * 获取下级角色
     * @return
     */
    public List<Role> findSubRole(User user) {

        List<Object> paras = new ArrayList<>();
        int lv = user.getRole().getInt("lv");
        String sql = "select * from eova_role where lv > ?";
        paras.add(lv);
        String companyField = x.conf.get("login.user.company_id", "company_id");
        // 自动按企业过滤
        if (this._getTable().getColumnNameSet().contains(companyField)) {
            Object companyValue = user.get(companyField);
            if (companyValue != null) {
                sql += String.format(" and %s = ?", companyField);
                paras.add(companyValue);
            }
        }
        return this.find(sql, paras.toArray());
    }

    @Override
    public List<Role> findAll() {
        return this.find("select * from eova_role order by lv");
    }

}