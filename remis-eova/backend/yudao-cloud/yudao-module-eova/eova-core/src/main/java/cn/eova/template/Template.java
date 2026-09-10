/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.template;

import java.util.List;
import java.util.Map;

import cn.eova.model.Button;

/**
 * <p>ported from: cn.eova.template.Template
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>模板基类；模板类型枚举与取值属契约</li>
 * </ol>
 */
/**
 * Eova 业务模版接口
 *
 * @author Jieven
 *
 */
public interface Template {
    /**
     * 模版名称
     *
     * @return
     */
    String name();

    /**
     * 模版编码
     *
     * @return
     */
    String code();

    /**
     * 模版按钮组
     * @return
     */
    Map<Integer, List<Button>> getBtnMap();

}