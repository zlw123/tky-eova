/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.model;

import cn.eova.common.Ds;
import cn.eova.common.base.BaseModel;

/**
 * <p>ported from: cn.eova.model.MetaFieldDiy
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>自定义字段；SQL 显式指定 eova 数据源</li>
 * </ol>
 */
/**
 * 元字段个性化
 *
 * @author Jieven
 *
 */
public class MetaFieldDiy extends BaseModel<MetaFieldDiy> {

    private static final long serialVersionUID = -7381270435240459528L;

    public static final MetaFieldDiy dao = new MetaFieldDiy();

    public void deleteByObjectCode(String code) {
        String sql = "delete from eova_field_diy where object_code = ?";
        gw(Ds.EOVA).update(sql, code);
    }
}