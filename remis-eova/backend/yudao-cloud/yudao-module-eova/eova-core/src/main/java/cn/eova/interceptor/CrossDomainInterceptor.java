package cn.eova.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.aop.LegacyInvocation;

/**
 * <p>ported from: cn.eova.interceptor.CrossDomainInterceptor
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>跨域拦截器（27 行）：implements Interceptor，写 CORS 响应头</li>
 *   <li>【已声明适配】Interceptor -> LegacyInterceptor；Invocation -> LegacyInvocation</li>
 *   <li>jakarta.servlet 的 HttpServletRequest/Response（决策 1）</li>
 * </ol>
 */
public class CrossDomainInterceptor implements LegacyInterceptor {

    @Override
    public void intercept(LegacyInvocation inv) {
        HttpServletRequest request = inv.getController().getRequest();
        HttpServletResponse response = inv.getController().getResponse();
 
        // 允许跨域的域名，*代表允许任何域名
        response.setHeader("Access-Control-Allow-Origin", "*");
        // 允许的方法
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
        // 允许的头信息字段
        response.setHeader("Access-Control-Allow-Headers", "x-requested-with, content-type");
        // 允许
        response.setHeader("Access-Control-Allow-Credentials", "true");

        inv.invoke();
    }
}