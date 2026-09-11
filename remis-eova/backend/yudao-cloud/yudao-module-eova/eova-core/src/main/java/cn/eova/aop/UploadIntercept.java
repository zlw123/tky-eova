/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.aop;

import java.util.List;

import cn.eova.model.MetaFieldConfig;
import cn.eova.compat.jfinal.kit.LegacyRet;
import cn.eova.db.EovaRecord;
import cn.eova.compat.jfinal.upload.LegacyUploadFile;

/**
 * <p>ported from: cn.eova.aop.UploadIntercept
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>上传拦截接口（40 行）：上传前/后的扩展点</li>
 *   <li>【已声明适配】Ret -> LegacyRet；Record -> EovaRecord；UploadFile -> LegacyUploadFile</li>
 *   <li>接口方法签名属契约，不得增减</li>
 * </ol>
 */
/**
 * 上传拦截器
 * @author Jieven
 *
 */
public interface UploadIntercept {

    /**
     * 上传文件
     * @param code 元对象编码
     * @param en 元字段
     * @param config 元字段配置
     * @param newFileName 上传文件名
     * @param uploadDir 上传目录
     * @param file 文件
     * @return
     */
    public LegacyRet upload(String code, String en, MetaFieldConfig config, String newFileName, String uploadDir, LegacyUploadFile file);

    /**
     * 查询文件
     * @param list 文件记录
     * @return
     */
    public LegacyRet query(List<EovaRecord> list);
}