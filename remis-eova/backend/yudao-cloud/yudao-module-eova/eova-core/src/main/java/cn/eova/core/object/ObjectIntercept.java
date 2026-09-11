/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.object;

import cn.eova.aop.AopContext;
import cn.eova.aop.MetaObjectIntercept;
import cn.eova.model.MetaField;
import cn.eova.model.MetaFieldDiy;

/**
 * <p>ported from: cn.eova.core.object.ObjectIntercept
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>对象业务拦截器（31 行）</li>
 * </ol>
 */
public class ObjectIntercept extends MetaObjectIntercept {

    @Override
    public String deleteBefore(AopContext ac) throws Exception {
        Integer id = ac.record.getInt("id");
        String code = ac.record.getStr("code");
        // 删除对象关联元字段属性
        MetaField.dao.deleteByObjectId(id);
        MetaFieldDiy.dao.deleteByObjectCode(code);
        // 删除对象关联的所有字典 慎重，会导致误删同表字段
        // String ds = ac.record.getStr("data_source");
        // String table = ac.record.getStr("table_name");
        // String dict = EovaConfig.getProps().get("main_dict_table");
        // Db.use(ds).update("delete from " + dict + " where object = ?", table);

        return null;
    }

}