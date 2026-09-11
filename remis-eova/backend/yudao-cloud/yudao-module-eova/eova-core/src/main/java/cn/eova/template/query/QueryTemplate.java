/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.template.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.eova.model.Button;
import cn.eova.template.Template;
import cn.eova.template.common.config.TemplateConfig;
import cn.eova.template.common.util.TemplateUtil;

/**
 * <p>ported from: cn.eova.template.query.QueryTemplate
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>通用查询模板（43 行）</li>
 * </ol>
 */
public class QueryTemplate implements Template {

    @Override
    public String name() {
        return "自定义查询";
    }

    @Override
    public String code() {
        return TemplateConfig.QUERY;
    }

    @Override
    public Map<Integer, List<Button>> getBtnMap() {
        Map<Integer, List<Button>> btnMap = new HashMap<>();
        {
            List<Button> btns = new ArrayList<>();

            btns.add(TemplateUtil.getQueryButton());
            btnMap.put(0, btns);
        }

        return btnMap;
    }

}