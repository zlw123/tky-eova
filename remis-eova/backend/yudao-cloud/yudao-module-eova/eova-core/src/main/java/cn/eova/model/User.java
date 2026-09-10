/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.model;

import java.util.HashSet;
import java.util.Set;

import cn.eova.common.base.BaseModel;
import cn.eova.config.EovaConst;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.model.User
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>getRid 优先取临时切换角色 su_rid，缺失时回落 rid —— 顺序即语义</li>
 *   <li>getIsAdmin 对 rid 为 null 会 NPE（this.get("rid").toString()）—— 属既有行为</li>
 *   <li>getCompanyId 两次读取同列（判空 + 取值）—— 原样保留</li>
 * </ol>
 */
public class User extends BaseModel<User> {

    private static final long serialVersionUID = 1064291771401662738L;

    /**
     * 用户禁用字段
     */
    private Set<String> disableFields = new HashSet<>();

    public static final User dao = new User().dao();

    public Role role;
    public EovaRecord data;// 登录源用户数据

    public Object getId() {
        return this.get("id");
    }

    public int getRid() {
        // 优先获取临时切换角色
        Integer suRid = this.getInt("su_rid");
        if (suRid != null) {
            return suRid;
        }
        // TODO 多角色支持，变成字符串
        return this.getInt("rid");
    }

    /**
     * 是否超级管理员
     * @return
     */
    public boolean isAdmin() {
        return getIsAdmin();
    }

    // 为兼容模版取值
    public boolean getIsAdmin() {
        // 兼容多角色
        if (this.get("rid").toString().equals(EovaConst.ADMIN_RID + "")) {
            return true;
        }
        return false;
    }

    public void initRole() {
        this.role = Role.dao.findById(getRid());
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getName() {
        return this.getStr("name");
    }

    public int getOrgId() {
        return this.getInt("org_id");
    }

    public int getCompanyId() {
        if (this.getInt("company_id") == null) {
            return 0;
        }
        return this.getInt("company_id");
    }

    /**
     * 获取登录源用户数据
     * @return
     */
    public EovaRecord getData() {
        return data;
    }

    public void setData(EovaRecord data) {
        this.data = data;
    }

    public Set<String> getDisableFields() {
        return disableFields;
    }

    public void setDisableFields(Set<String> disableFields) {
        this.disableFields = disableFields;
    }
}