/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.core.LegacyController;

/**
 * <p>ported from: cn.eova.mod.EovaModRoute
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>Mod 路由表（46 行）：controllerKey -> Controller 类型映射 + 拦截器列表</li>
 *   <li>【已声明适配】Controller -> LegacyController（含泛型上界 Class<? extends Controller>）；</li>
 *   <li>    Interceptor -> LegacyInterceptor（含 List<Interceptor>）</li>
 * </ol>
 */
public class EovaModRoute {

    private HashMap<String, Class<? extends LegacyController>> routeMap = new HashMap<>();
    private List<LegacyInterceptor> interceptors = new ArrayList<>();

    /**
     * 添加路由
     * @param controllerKey
     * @param controllerClass
     */
    public void add(String controllerKey, Class<? extends LegacyController> controllerClass) {
        routeMap.put(controllerKey, controllerClass);
    }

    /**
     * 添加拦截器
     * @param interceptor
     */
    public void addInterceptor(LegacyInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    public HashMap<String, Class<? extends LegacyController>> getRouteMap() {
        return routeMap;
    }

    public List<LegacyInterceptor> getInterceptors() {
        return interceptors;
    }


}
