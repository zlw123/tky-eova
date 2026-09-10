/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.ext.jfinal.directive;

import java.io.IOException;

import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import com.jfinal.template.Directive;
import com.jfinal.template.Env;
import com.jfinal.template.io.Writer;
import com.jfinal.template.stat.Scope;

/**
 * <p>ported from: cn.eova.ext.jfinal.directive.JsonDirective
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>Enjoy 自定义指令 #json；指令名属【模板契约】（view 模板按名调用）</li>
 *   <li>JsonKit->LegacyJsonKit</li>
 * </ol>
 */
/**
 * 将目标对象转为JSON字符串
 * #json(xxxx)
 *
 * @author Jieven
 */
public class JsonDirective extends Directive {

    @Override
    public void exec(Env env, Scope scope, Writer writer) {
        Object value = exprList.eval(scope);
        if (value == null) {
            throw new IllegalArgumentException("Data object not found in scope.");
        }

        try {
            // toString 自动转了JSON
            writer.write(LegacyJsonKit.toJson(value));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}