/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.api.page;

import cn.eova.compat.jfinal.kit.LegacyKv;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 装配的**不查库分支**判据（第 140 轮）—— 查库路径之外的第一个分支。
 *
 * <p>`assemble(menuCode, user, isQuery)` 的第一句就是未登录判定
 * （旧 `AppController#index()`：`if (user == null) { renderMsg("请先登录"); return; }`），
 * 该分支**在触库之前**返回 ⇒ 可以在**不起数据库**的情况下判。
 *
 * <p>为什么值得单独钉：这是 DES-004 §3.1「未登录不得返回部分引导数据」在**装配层**的落点。
 * 若有人把用户检查挪到查库之后（或干脆删掉），前端会拿到一份**没有 loginUser 的引导数据**，
 * 并据此渲染出"看起来正常"的页面 —— 后端编译通过、既有判据（只判 `fail(...)` 的返回值）全绿。
 */
class PageBootstrapAssemblerNoDbTest {

    @Test
    @DisplayName("未登录：state='no' + 逐字沿用旧 renderMsg 文案，且**不得**带任何引导数据字段")
    void notLoginBeforeAnyQuery() {
        LegacyKv kv = new PageBootstrapAssembler().assemble("meta_hotel", null, true);

        assertEquals(PageBootstrapAssembler.STATE_NO, kv.get("state"));
        assertEquals("请先登录", kv.get("msg"));
        // ★ 不得返回"部分引导数据"（否则前端会当正常数据用）
        for (String forbidden : new String[] {"object", "menu", "menuCode", "btnList", "loginUser", "isQuery"}) {
            assertFalse(kv.containsKey(forbidden), "未登录载荷不得包含字段: " + forbidden);
        }
        assertEquals(2, kv.size(), "未登录载荷只应有 state 与 msg");
    }
}
