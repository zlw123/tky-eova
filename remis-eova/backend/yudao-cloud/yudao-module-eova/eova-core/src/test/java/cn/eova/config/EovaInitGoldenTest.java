/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.config;

import cn.eova.core.api.ApiRouterHandler;
import cn.eova.tools.x;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code EovaInit.initEovaApiAppCofing}（第 64 轮 port）的判据。
 *
 * <p><b>为什么要判这一支：</b>它把配置串 {@code eova.api.apps} 解析成
 * appKey→secret 注册表，是 API 签名的<b>配置入口</b>；并且有两条"提前返回"分支
 * （注册表非空 / 配置为空），其中<b>前者会让配置被静默忽略</b> ——
 * 一旦被改成"总是解析覆盖"，运行期行为与配置优先级都会变。</p>
 */
class EovaInitGoldenTest {

    @Test
    @DisplayName("initEovaApiAppCofing：解析 k:v 并用 ';' 分隔；注册表非空时【提前返回】不覆盖")
    void parseAndSkipWhenNotEmpty() {
        java.util.Map<String, String> before = new java.util.LinkedHashMap<>(
                ApiRouterHandler.getAppConfig());
        // ConfigTool 无 set()，只有 addConfig(String,String)；快照整张 props 以便还原
        java.util.Map<String, String> confSnapshot =
                new java.util.LinkedHashMap<>(x.conf.getProps());
        try {
            // ① 正常解析
            ApiRouterHandler.getAppConfig().clear();
            x.conf.addConfig("eova.api.apps", "app1:sec1;app2:sec2");
            EovaInit.initEovaApiAppCofing();
            assertEquals(2, ApiRouterHandler.getAppConfig().size(), "应解析出两条");
            assertEquals("sec1", ApiRouterHandler.getAppConfig().get("app1"));
            assertEquals("sec2", ApiRouterHandler.getAppConfig().get("app2"));

            // ② 注册表非空 → 提前返回，配置【不被重新解析/覆盖】
            x.conf.addConfig("eova.api.apps", "other:secret");
            EovaInit.initEovaApiAppCofing();
            assertEquals(2, ApiRouterHandler.getAppConfig().size(),
                    "注册表非空时必须提前返回（旧语义）");
            assertTrue(!ApiRouterHandler.getAppConfig().containsKey("other"),
                    "非空时不得把新配置合并进来");

            // ③ 配置为空串 → 只记日志、不注册任何东西
            ApiRouterHandler.getAppConfig().clear();
            x.conf.addConfig("eova.api.apps", "");
            EovaInit.initEovaApiAppCofing();
            assertEquals(0, ApiRouterHandler.getAppConfig().size(),
                    "配置为空时不得注册任何 app");
        } finally {
            ApiRouterHandler.getAppConfig().clear();
            ApiRouterHandler.getAppConfig().putAll(before);
            x.conf.getProps().clear();
            x.conf.getProps().putAll(confSnapshot);
        }
    }
}
