/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.hook;

import cn.eova.compat.jfinal.kit.LegacyKv;

/**
 * <p>ported from: cn.eova.hook.BizHook
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>业务钩子接口；方法签名属契约</li>
 * </ol>
 */
/**
 * 通用钩子
 *
 * @author Jieven
 */
public interface BizHook extends Hook {

    void invoke(LegacyKv kv) throws Exception;

}
