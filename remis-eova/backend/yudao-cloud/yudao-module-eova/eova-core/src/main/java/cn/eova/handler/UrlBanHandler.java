/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.regex.Pattern;

import cn.eova.compat.jfinal.handler.LegacyHandler;
import cn.eova.compat.jfinal.kit.LegacyHandlerKit;
import com.jfinal.kit.StrKit;

/**
 * <p>ported from: cn.eova.handler.UrlBanHandler
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>URL 封禁处理器（42 行）：extends Handler，命中封禁规则则渲染 404</li>
 *   <li>【已声明适配】Handler -> LegacyHandler；HandlerKit -> LegacyHandlerKit</li>
 *   <li>jakarta.servlet 的 HttpServletRequest/Response（决策 1）</li>
 * </ol>
 */
/**
 * Ban掉禁止访问的资源
 *
 *
 * @创建者：Jieven
 * @创建时间：2017-2-20 下午3:14:06
 */
public class UrlBanHandler extends LegacyHandler {

    private Pattern skipedUrlPattern;

    public UrlBanHandler(String skipedUrlRegx, boolean isCaseSensitive) {
        if (StrKit.isBlank(skipedUrlRegx))
            throw new IllegalArgumentException("The para excludedUrlRegx can not be blank.");
        skipedUrlPattern = isCaseSensitive ? Pattern.compile(skipedUrlRegx) : Pattern.compile(skipedUrlRegx, Pattern.CASE_INSENSITIVE);
    }

    public void handle(String target, HttpServletRequest request, HttpServletResponse response, boolean[] isHandled) {
        if (skipedUrlPattern.matcher(target).matches()) {
            System.err.println(skipedUrlPattern + " 文件禁止直接访问, 如果想直接访问静态网页,请改成 .htm 格式!");
            LegacyHandlerKit.renderError404(request, response, isHandled);
            return;
        } else {
            next.handle(target, request, response, isHandled);
        }
    }
}