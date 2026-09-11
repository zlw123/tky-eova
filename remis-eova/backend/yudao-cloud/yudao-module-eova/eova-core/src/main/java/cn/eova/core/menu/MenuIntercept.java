/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.menu;

import cn.eova.aop.AopContext;
import cn.eova.aop.MetaObjectIntercept;
import cn.eova.common.base.BaseCache;
import cn.eova.config.EovaConst;
import cn.eova.model.Button;
import cn.eova.model.Menu;
import cn.eova.model.RoleBtn;

/**
 * <p>ported from: cn.eova.core.menu.MenuIntercept
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>菜单业务拦截器（73 行）：菜单隐藏/新增前回调</li>
 *   <li>【零替换】本文件不 import 任何 jfinal 类型（继承已 port 的 MetaObjectIntercept）——逐字节即语义等价</li>
 * </ol>
 */
public class MenuIntercept extends MetaObjectIntercept {

    @Override
    public String hideBefore(AopContext ac) throws Exception {
        System.out.println("隐藏菜单:" + ac.record.getInt("id"));
        return null;
    }

    @Override
    public String deleteBefore(AopContext ac) throws Exception {
        int id = ac.record.getInt("id");
        Menu menu = Menu.dao.findById(id);
        String code = menu.getStr("code");

        // 有子菜单禁止删除
        //		boolean isPaent = Menu.dao.isParent(id);
        //		if (isPaent) {
        //			// 开发小常识：有子的父不能删！
        //			return error("如果爹没了，仔仔们会很伤心的！");
        //		}

        // 删除菜单按钮关联权限
        RoleBtn.dao.deleteByMenuCode(code);

        // 删除菜单关联按钮
        Button.dao.deleteByMenuCode(code);

        // 删除菜单关联对象,不能删除对象，因为对象可能被多个菜单用
        // MenuObject.dao.deleteByMenuCode(code);

        return null;
    }

    @Override
    public String addSucceed(AopContext ac) throws Exception {
        // 菜单使缓存失效
        BaseCache.delSer(EovaConst.ALL_MENU);

        return null;
    }

    @Override
    public String deleteSucceed(AopContext ac) throws Exception {
        // 菜单使缓存失效
        BaseCache.delSer(EovaConst.ALL_MENU);

        return null;
    }

    @Override
    public String updateSucceed(AopContext ac) throws Exception {
        // 菜单使缓存失效
        BaseCache.delSer(EovaConst.ALL_MENU);

        return null;
    }

}