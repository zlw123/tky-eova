/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.button;

import cn.eova.aop.AopContext;
import cn.eova.aop.MetaObjectIntercept;
import cn.eova.model.RoleBtn;

/**
 * <p>ported from: cn.eova.core.button.ButtonIntercept
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>按钮业务拦截器（24 行）</li>
 * </ol>
 */
public class ButtonIntercept extends MetaObjectIntercept {

    @Override
    public String deleteBefore(AopContext ac) throws Exception {
        int id = ac.record.getInt("id");

        // 删除菜单按钮关联权限
        RoleBtn.dao.deleteByBid(id);

        return null;
    }

}