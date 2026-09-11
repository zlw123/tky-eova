/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code EovaDbGateway.batch(List,int)} 接缝的判据（第 64 轮为 port {@code EovaModUtil} 而补）。
 *
 * <p><b>本判据只覆盖守卫分支</b>（空列表 / batchSize&lt;1）—— 它们不触碰 DataSource，
 * 故可无库运行。真正的分块提交语义（非事务时每块 commit、结果压平）需要真实库，
 * 属 acceptanceProfile 实跑项，当前状态 <b>not executed</b>，不得据此判 verified。</p>
 */
class BatchSeamGoldenTest {

    @Test
    @DisplayName("batch 守卫：null/空列表返回空数组；batchSize<1 抛错且消息逐字一致")
    void batchGuardsMatchOldDbPro() {
        // 守卫分支不触碰 DataSource，故传 null 即可
        JdbcEovaDbGateway gw = new JdbcEovaDbGateway(null);
        assertEquals(0, gw.batch(null, 10).length, "null 列表 ⇒ 长度 0 的数组");
        assertEquals(0, gw.batch(new ArrayList<>(), 10).length, "空列表 ⇒ 长度 0 的数组");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> gw.batch(List.of("select 1"), 0));
        assertEquals("The batchSize must more than 0.", e.getMessage(),
                "消息必须与旧 DbPro 逐字节一致（含句点）");
        assertThrows(IllegalArgumentException.class, () -> gw.batch(List.of("select 1"), -3));
    }

    @Test
    @DisplayName("batch 已进入网关接口面（EovaModUtil 的 Db.use(ds).batch(sqls, size) 依赖它）")
    void batchIsOnGatewaySurface() {
        List<String> names = new ArrayList<>();
        for (Method m : EovaDbGateway.class.getDeclaredMethods()) {
            if (m.getName().equals("batch")) {
                names.add(m.getParameterTypes()[0].getSimpleName() + "/" + m.getParameterCount());
            }
        }
        // 第 70 轮 MetaController port 时新增了 batch(String,String,List,int)（jfinal 的
        // "按列名批量执行"重载），故此处钉【实测全集】而不是单元素 —— 原有断言过于严格，
        // 隐含假设"接口面永远只有一个 batch 重载"，属不必要约束。
        assertEquals(List.of("List/2", "String/4"), names,
                "网关的 batch 重载集合必须与实测一致（List,int + String,String,List,int）");
    }
}
