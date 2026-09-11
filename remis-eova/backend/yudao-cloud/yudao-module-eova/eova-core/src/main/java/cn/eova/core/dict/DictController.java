/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.dict;


import cn.eova.tools.x;
import cn.eova.common.Ds;
import cn.eova.common.Easy;
import cn.eova.common.base.BaseController;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;


/**
 * <p>ported from: cn.eova.core.dict.DictController
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>字典控制器（36 行）</li>
 * </ol>
 */
/**
 * 字典管理
 * @author Jieven
 *
 */
public class DictController extends BaseController {

    // 复制字典
    public void copy() {
        String id = getSelectValue("id");

        String dictTable = x.conf.get("main_dict_table");

        EovaRecord r = EovaGateways.get(Ds.MAIN).findById(dictTable, id);
        r.remove("id");

        EovaGateways.get(Ds.MAIN).save(dictTable, r);
        renderJson(new Easy());
    }
}