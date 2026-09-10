/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.model;

import java.util.List;

import cn.eova.common.base.BaseModel;

/**
 * <p>ported from: cn.eova.model.Widget
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>BaseModel 子类；模板取值方法名属对外契约（前端经 Enjoy 模板调用）</li>
 * </ol>
 */
/**
 * 控件
 *
 * @author Jieven
 * @date 2014-9-10
 */
public class Widget extends BaseModel<Widget> {

    private static final long serialVersionUID = 4254060861819273244L;

    public static final Widget dao = new Widget();

    /** EOVA控件 **/
    public static final int TYPE_EOVA = 1;
    /** DIY控件 **/
    public static final int TYPE_DIY = 2;

    public List<Widget> findByType(int type) {
        return this.queryByCache("select * from eova_widget where type = ?", type);
    }
}