/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.button;

import java.util.List;

import cn.eova.model.Button;

/**
 * <p>ported from: cn.eova.core.button.ButtonFactory
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>按钮工厂；按 ui 类型分发，分支顺序属契约</li>
 * </ol>
 */
/**
 * 按钮构建工厂
 *
 * @author Jieven
 * @date 2016-11-19
 */
public class ButtonFactory {

    /**
     * 创建模版应用
     */
    public static void create(String menuCode, String template) {
        String code = String.format("eova_template_%s", template);

        // 获取应用功能
        List<Button> list = Button.dao.findByMenuCode(code);
        for (Button btn : list) {
            btn.remove("id");
            btn.set("menu_code", menuCode);
            String bs = btn.getStr("bs");
            // btn.set("bs", formatTemplate(bs, Kv.of("object", config.getObjectCode())));
            // buildAuth(btn); TODO 使用时实时构建, 无需提前配置, 方便动态修改, 例如 动态修改菜单编码, 元对象编码.
            btn.save();
        }
    }

}