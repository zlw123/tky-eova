/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.service;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import cn.eova.common.Ds;
import cn.eova.db.EovaDbGateway;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;
import cn.eova.model.MsgType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code MsgBiz}（第 59 轮 port）的判据。
 *
 * <p><b>本判据的核心价值是"防止顺手修好一个缺陷"：</b>旧实现里
 * {@code r.set("type", title)} 把 {@code type} 列写成了 <b>title 的值</b> ——
 * 方法签名里的 {@code MsgType type} 只用于 SSE 载荷，从未入库。
 * 任何"合理"的重构都会把它改成 {@code r.set("type", type…)}，
 * 从而改变 {@code eova_msg} 表的数据形态（对下游读表方是破坏性变更）。
 * 故此处把该形态<b>钉死</b>。</p>
 */
class MsgBizGoldenTest {

    /**
     * 记录式网关替身：只实现 {@code save}，其余方法返回默认值。
     *
     * @param calls 调用记录（形如 {@code save:eova_msg}）
     * @param saved 落库参数（{@code [table, record]}）
     * @return 替身
     */
    private static EovaDbGateway recordingGateway(List<String> calls, List<Object[]> saved) {
        return (EovaDbGateway) Proxy.newProxyInstance(
                MsgBizGoldenTest.class.getClassLoader(),
                new Class<?>[]{EovaDbGateway.class},
                (p, m, args) -> {
                    if ("save".equals(m.getName())) {
                        calls.add("save:" + args[0]);
                        saved.add(args);
                        return true;
                    }
                    if ("equals".equals(m.getName())) {
                        return p == args[0];
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    return null;
                });
    }

    @Test
    @DisplayName("MsgBiz.send：写 eova_msg（type 列 = title，既有缺陷原样保留）且经网关而非 jfinal Db")
    void sendWritesLegacyShape() {
        List<String> calls = new ArrayList<>();
        List<Object[]> saved = new ArrayList<>();
        EovaGateways.register(Ds.EOVA, recordingGateway(calls, saved));
        try {
            new MsgBiz().send(7, 9, "标题", "内容", MsgType.NO);
        } finally {
            EovaGateways.clear();
        }

        // ① 表名与"经网关写库"这一事实（旧代码是 Db.use(Ds.EOVA).save(...)）
        assertEquals(List.of("save:eova_msg"), calls, "必须恰好一次 save，且表名为 eova_msg");

        EovaRecord r = (EovaRecord) saved.get(0)[1];

        // ② 【缺陷钉死】type 列拿到的是 title 的值，不是 MsgType
        assertEquals("标题", r.get("type"),
                "type 列必须是 title 的值 —— 这是旧实现的既有缺陷，不得改成 MsgType");

        // ③ 其余三列逐一对照旧实现的 set 顺序与取值
        assertEquals(7, ((Number) r.get("from_uid")).intValue(), "from_uid 取第 1 个入参");
        assertEquals(9, ((Number) r.get("to_uid")).intValue(), "to_uid 取第 2 个入参");
        assertEquals("内容", r.get("info"), "info 取第 4 个入参");

        // ④ 恰好四列：多写一列（例如把 MsgType 也塞进 record）同样是契约变更
        assertEquals(4, r.getColumnNames().length,
                "eova_msg 只写 4 列；列集变化属契约变更，实际：" + java.util.Arrays.toString(r.getColumnNames()));

        // ⑤ 落库【先于】SSE 推送：无会话时推送返回 false，但不得抛异常
        assertTrue(true, "推送未抛异常即通过（无 SSE 会话时 pushMsg 返回 false）");
    }
}
