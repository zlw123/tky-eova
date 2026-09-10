/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.template;

import java.util.Map;

/**
 * 模板渲染接缝（阶段 1 预留，阶段 2 替换实现）。
 *
 * <p>存在理由：旧系统用 Enjoy 做服务端渲染，而 Enjoy 是 JFinal 生态的宿主能力。
 * 把渲染收敛到本接口之后，业务代码不再直接依赖 {@code com.jfinal.template.*}；
 * 阶段 2（前后端分离，DES-002-R4 §6bis T04）退役服务端模板时，
 * <b>只需替换本接口的实现，无需回头改动业务代码</b>。
 *
 * <p>约束（DES-002-R4 §2.1 / §2.5）：
 * <ul>
 *   <li>阶段 1 期间渲染必须与旧系统<b>逐字节等价</b>，本接缝是纯 pass-through；</li>
 *   <li>不得在此处做任何业务判断或结构调整。</li>
 * </ul>
 *
 * <p>ported from: 无（新增适配层，非 port 单元）
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 */
public interface TemplateRenderService {

    /**
     * 渲染指定模板并返回 HTML 字符串。
     *
     * @param templatePath 模板路径，须以 {@code /} 开头，相对模板根解析（如 {@code /eova/_view/index/login.html}）
     * @param scope        模板作用域变量；调用方负责传入与旧系统一致的取值
     * @return 渲染结果；与旧系统同输入时需逐字节一致
     */
    String render(String templatePath, Map<String, Object> scope);
}
