/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import cn.eova.common.base.BaseCache;
import cn.eova.common.utils.web.RequestUtil;
import cn.eova.tools.x;
import cn.eova.compat.jfinal.handler.LegacyHandler;
import cn.eova.compat.jfinal.kit.LegacyHandlerKit;
import cn.eova.compat.jfinal.kit.LegacyLogKit;
import cn.eova.compat.jfinal.kit.LegacyStrKit;
import cn.eova.compat.cache.LegacyCacheKit;

/**
 * <p>ported from: cn.eova.handler.WAFHandler
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>WAF 处理器（71 行）：extends Handler，按缓存计数拦截异常请求</li>
 *   <li>【已声明适配】Handler -> LegacyHandler；HandlerKit -> LegacyHandlerKit；</li>
 *   <li>    CacheKit -> LegacyCacheKit；StrKit -> LegacyStrKit；LogKit -> LegacyLogKit</li>
 *   <li>缓存名 BaseCache.WAF_404/WAF_BAN 与计数阈值属契约</li>
 * </ol>
 */
/**
 * WAF 安全防护
 * 1.按IP统计404，超阈值后临时封禁10分钟。
 * 2.软封后若持续刷，每 LOG_EVERY 次输出一次日志，便于决定是否网关封 IP。
 */
public class WAFHandler extends LegacyHandler {

    /** 404 次数达到该值即软封 */
    public static final int LIMIT = x.conf.getInt("waf.404.limit", 10);
    /** 软封后持续拦截，每隔多少次打一条日志 */
    public static final int LOG_EVERY = x.conf.getInt("waf.404.log", 1000);

    @Override
    public void handle(String target, HttpServletRequest request, HttpServletResponse response, boolean[] isHandled) {
        String ip = RequestUtil.getIp(request);
        if (LegacyStrKit.notBlank(ip) && LegacyCacheKit.get(BaseCache.WAF_BAN, ip) != null) {
            onBlocked(ip, target);
            // 与正常 404 一致，不暴露限流/封禁信号
            LegacyHandlerKit.renderError404(request, response, isHandled);
            return;
        }

        next.handle(target, request, response, isHandled);

        if (response.getStatus() == HttpServletResponse.SC_NOT_FOUND && LegacyStrKit.notBlank(ip)) {
            onNotFound(ip, target);
        }
    }

    private void onNotFound(String ip, String target) {
        Integer count = LegacyCacheKit.get(BaseCache.WAF_404, ip);
        int next = (count == null ? 0 : count) + 1;
        LegacyCacheKit.put(BaseCache.WAF_404, ip, next);

        if (next >= LIMIT) {
            LegacyCacheKit.put(BaseCache.WAF_BAN, ip, Boolean.TRUE);
            LegacyCacheKit.remove(BaseCache.WAF_404, ip);
            LegacyLogKit.warn("WAF softban ip=" + ip);
        }
    }

    /** 已软封：累计拦截次数，每 LOG_EVERY 次提醒一次 */
    private void onBlocked(String ip, String target) {
        Integer count = LegacyCacheKit.get(BaseCache.WAF_404, ip);
        int next = (count == null ? 0 : count) + 1;
        LegacyCacheKit.put(BaseCache.WAF_404, ip, next);

        if (next % LOG_EVERY == 0) {
            LegacyLogKit.warn("WAF 404 scan ip=" + ip + ", scan num=" + next);
        }
    }
}
