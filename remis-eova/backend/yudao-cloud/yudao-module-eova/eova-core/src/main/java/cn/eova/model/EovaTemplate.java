/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.model;

import java.util.List;

import cn.eova.common.base.BaseCache;
import cn.eova.common.base.BaseModel;

/**
 * <p>ported from: cn.eova.model.EovaTemplate
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 */
/**
 * Eova设置
 *
 * @author Jieven
 */
public class EovaTemplate extends BaseModel<EovaTemplate> {

    private static final long serialVersionUID = -1592533967096109392L;

    public static final EovaTemplate dao = new EovaTemplate().dao();

    /**
     * 获取业务模版设置
     * @param bizCode 业务编码
     * @return
     */
    public List<EovaTemplate> findTemplateByCode(String bizCode) {
        String sql = "select * from eova_props where type = 1 and biz = ?";
        return dao.findByCache(BaseCache.META, sql + bizCode, sql, bizCode);
    }
}