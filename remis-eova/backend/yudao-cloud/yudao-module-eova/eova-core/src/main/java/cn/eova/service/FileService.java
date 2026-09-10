/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.service;

import cn.eova.common.base.BaseService;

/**
 * <p>ported from: cn.eova.service.FileService
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>文件服务（18 行）：BaseService 子类</li>
 *   <li>宿主依赖仅 cn.eova.common.base.BaseService —— 【无需任何替换】，天然逐字节 port</li>
 * </ol>
 */
/**
 * 文件服务
 *
 * @author Jieven
 * @date 2013-1-3
 */
public class FileService extends BaseService {

}