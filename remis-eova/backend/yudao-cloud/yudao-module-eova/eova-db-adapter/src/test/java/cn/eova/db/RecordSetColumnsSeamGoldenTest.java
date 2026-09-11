/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code EovaRecord.setColumns} 三个重载的接缝判据（第 63 轮）。
 *
 * <p><b>为什么有这条判据：</b>port {@code WidgetUtil} 时
 * {@code new Record().setColumns(model)} 编译失败，才暴露出本类当时只有
 * {@code setColumns(Map)} —— 少了 jfinal 的 {@code (Record)} 与 {@code (Model)} 两个重载。
 * 这类"接缝面比旧类窄"的缺口<b>不会在既有判据里暴露</b>（没人调用就不报错），
 * 只会在 port 更上层单元时以编译错误的形式冒出来。</p>
 *
 * <p><b>跨实现说明：</b>本判据住在 {@code eova-db-adapter}（该模块已声明 jfinal test 作用域），
 * 被比对的是 <b>jfinal 制品自身</b>的 {@code Record}，故不受 R38/R44 的禁令约束，
 * 但仍用 {@code assertFromJar} 自校验来源。</p>
 */
class RecordSetColumnsSeamGoldenTest {

    /** jfinal Model 的最小可用子类（仅用于 setColumns(Model) 这一跳） */
    public static class OldModel extends com.jfinal.plugin.activerecord.Model<OldModel> {
    }

    /** 本实现的 Model 子类 */
    public static class NewModel extends EovaModel<NewModel> {
    }

    @Test
    @DisplayName("setColumns 重载面：与 jfinal Record 的三条重载逐一对应（含 (Record) 与 (Model)）")
    void setColumnsOverloadSurfaceMatches() throws Exception {
        Class<?> old = com.jfinal.plugin.activerecord.Record.class;
        OldImplementationLoader.assertFromJar(old, OldImplementationLoader.oldJFinalJar());

        // 旧侧三条重载的参数类型名
        java.util.Set<String> oldShapes = new java.util.TreeSet<>();
        for (Method m : old.getMethods()) {
            if (!m.getName().equals("setColumns")) {
                continue;
            }
            Class<?>[] ps = m.getParameterTypes();
            if (ps.length == 2) {
                continue; // Collection,Collection（子类新增，不属旧面）
            }
            oldShapes.add(ps[0].getSimpleName());
        }
        assertEquals(java.util.Set.of("Map", "Record", "Model"), oldShapes,
                "旧 jfinal Record 恰有 Map/Record/Model 三条 setColumns 重载");

        // 本侧必须一一对应（类型名按已声明适配改名）
        java.util.Set<String> newShapes = new java.util.TreeSet<>();
        for (Method m : EovaRecord.class.getMethods()) {
            if (!m.getName().equals("setColumns")) {
                continue;
            }
            Class<?>[] ps = m.getParameterTypes();
            if (ps.length == 2) {
                continue;
            }
            newShapes.add(ps[0].getSimpleName());
        }
        assertEquals(java.util.Set.of("Map", "EovaRecord", "EovaModel"), newShapes,
                "本实现的 setColumns 重载面必须与旧面一一对应（Record->EovaRecord、Model->EovaModel）");
    }

    @Test
    @DisplayName("setColumns(Map)/(Record)/(Model)：委托链与内容跨实现一致")
    void setColumnsDelegationMatchesOld() {
        // ① Map 重载
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("ID", 7);
        attrs.put("Name", "eova");

        com.jfinal.plugin.activerecord.Record oldByMap =
                new com.jfinal.plugin.activerecord.Record().setColumns(attrs);
        EovaRecord newByMap = new EovaRecord().setColumns(attrs);
        assertEquals(oldByMap.getColumns().size(), newByMap.getColumns().size(),
                "列数必须一致");
        assertEquals(7, ((Number) newByMap.get("id")).intValue(), "键归一化后仍能按小写取到");

        // ② (Record) 重载：旧实现是 setColumns(record.getColumns()) —— 一条委托
        com.jfinal.plugin.activerecord.Record oldSrc = new com.jfinal.plugin.activerecord.Record();
        oldSrc.set("a", 1).set("b", "x");
        com.jfinal.plugin.activerecord.Record oldByRecord =
                new com.jfinal.plugin.activerecord.Record().setColumns(oldSrc);

        EovaRecord newSrc = new EovaRecord();
        newSrc.set("a", 1).set("b", "x");
        EovaRecord newByRecord = new EovaRecord().setColumns(newSrc);
        assertEquals(oldByRecord.getColumns().size(), newByRecord.getColumns().size());
        assertEquals(1, ((Number) newByRecord.get("a")).intValue());
        assertEquals("x", newByRecord.get("b"));
        // 委托而非共享：改副本不应影响来源
        newByRecord.set("a", 99);
        assertEquals(1, ((Number) newSrc.get("a")).intValue(),
                "副本与来源不得共享同一份列容器");

        // ③ (Model) 重载：旧实现是 setColumns(model._getAttrs())
        OldModel oldModel = new OldModel();
        oldModel.set("m1", "v1");
        com.jfinal.plugin.activerecord.Record oldByModel =
                new com.jfinal.plugin.activerecord.Record().setColumns(oldModel);
        assertEquals(1, oldByModel.getColumns().size(), "旧侧：Model 的一个属性进 Record");

        NewModel newModel = new NewModel();
        newModel.set("m1", "v1");
        EovaRecord newByModel = new EovaRecord().setColumns(newModel);
        assertEquals(oldByModel.getColumns().size(), newByModel.getColumns().size(),
                "(Model) 重载的列数必须与旧侧一致");
        assertEquals("v1", newByModel.get("m1"));

        // ④ 三个重载都返回 this（可链式）
        EovaRecord chained = new EovaRecord();
        assertSame(chained, chained.setColumns(attrs));
        assertSame(chained, chained.setColumns(newSrc));
        assertSame(chained, chained.setColumns(newModel));
        assertNotNull(chained.getColumns());
        assertTrue(chained.getColumns().containsKey("m1"));
    }
}
