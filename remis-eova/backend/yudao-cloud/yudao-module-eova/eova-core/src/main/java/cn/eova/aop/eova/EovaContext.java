/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.aop.eova;

import cn.eova.aop.EovaAopContext;
import cn.eova.engine.EovaExp;
import cn.eova.model.Menu;
import cn.eova.model.MetaObject;
import cn.eova.compat.jfinal.core.LegacyController;

/**
 * <p>ported from: cn.eova.aop.eova.EovaContext
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>EOVA AOP 上下文（43 行）</li>
 * </ol>
 */
/**
 * Eova全局业务拦截器上下文
 *
 * @author Jieven
 * @date 2014-8-29
 */
public class EovaContext extends EovaAopContext {

    /**
     * 当前菜单
     */
    public Menu menu;

    /**
     * 当前元对象
     * 元字段=object.fields
     *
     */
    public MetaObject object;

    /**
     * 当前操作表达式
     */
    public EovaExp exp;

    public EovaContext(LegacyController ctrl) {
        super(ctrl);
    }

}