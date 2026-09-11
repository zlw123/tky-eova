/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.aop;

import cn.eova.common.base.BaseController;
import cn.eova.model.User;
import cn.eova.compat.jfinal.core.LegacyController;


/**
 * <p>ported from: cn.eova.aop.EovaAopContext
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>EOVA AOP 上下文基类（39 行）：持有当前 Controller 与 User，提供 UID()</li>
 *   <li>【已声明适配 1】com.jfinal.core.Controller -> cn.eova.compat.jfinal.core.LegacyController</li>
 *   <li>构造器把 ctrl 强转成 BaseController 取 user —— 该强转属【既有契约】，不得改成接口判定</li>
 * </ol>
 */
/**
 * EOVA AOP上下文
 *
 * @author Jieven
 */
public class EovaAopContext {

    /**
     * 当前控制器
     */
    public LegacyController ctrl;

    /**
     * 当前用户对象
     */
    public User user;

    public EovaAopContext(LegacyController ctrl) {
        this.ctrl = ctrl;
        this.user = ((BaseController) ctrl).getUser();
    }

    public int UID() {
        return this.user.get("id");
    }

}