/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.role;

import cn.eova.aop.AopContext;
import cn.eova.hook.EovaMetaHook;

/**
 * <p>ported from: cn.eova.core.role.RoleHook
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>角色 Hook（37 行）</li>
 * </ol>
 */
public class RoleHook implements EovaMetaHook {

    @Override
    public String invoke(EovaMetaHook.Action action, AopContext ac) throws Exception {
        switch (action) {
            case ADD_BEFORE:
                return addBefore(ac);
            case UPDATE_BEFORE:
                return updateBefore(ac);
        }
        return null;
    }

    public String addBefore(AopContext ac) throws Exception {
        Integer lv = ac.record.getInt("lv");
        Integer roleLv = ac.user.getRole().getInt("lv");
        if (lv <= roleLv) {
            return "权限级别必须大于：" + roleLv;
        }
        return null;
    }

    public String updateBefore(AopContext ac) throws Exception {
        return addBefore(ac);
    }

}