/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.dict;

import cn.eova.tools.x;
import cn.eova.aop.AopContext;
import cn.eova.aop.MetaObjectIntercept;
import cn.eova.model.MetaObject;

/**
 * <p>ported from: cn.eova.core.dict.DictIntercept
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>字典业务拦截器（28 行）</li>
 * </ol>
 */
public class DictIntercept extends MetaObjectIntercept {

    @Override
    public void queryBefore(AopContext ac) throws Exception {
        String objectCode = ac.ctrl.get("query_v_object_code");
        if (!x.isEmpty(objectCode)) {
            MetaObject o = MetaObject.dao.getByCode(objectCode);
            ac.condition = " and object = ?";
            ac.params.add(o.getTable());
        }

        super.queryBefore(ac);
    }


}