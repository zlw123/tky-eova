/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.widget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.eova.db.EovaModel;
import cn.eova.db.EovaRecord;
import cn.eova.widget.tree.TreeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code WidgetUtil} / {@code TreeUtil}（第 63 轮 port）的判据。
 *
 * <p><b>期望值来源（必须说明）：</b>两个单元的文件体已由
 * {@code port-units.py --verify} 证明与旧源<b>逐字节一致</b>（仅 import/类型名按已声明适配替换），
 * 故语义等价由文件级等价承载；本判据的职责是<b>把易错的行为钉死</b>，
 * 尤其是 {@link TreeUtil#buildTree} 里"合成根"与"丢弃同 id 真实记录"这类
 * 反直觉但对前端契约有影响的点。</p>
 *
 * <p><b>为什么不跨实现：</b>旧 {@code WidgetUtil}/{@code TreeUtil} 的类加载会牵出
 * jfinal 的 {@code Model}/{@code Record}，属 R38/R44 明令隔离的情形。</p>
 */
class WidgetTreeUtilGoldenTest {

    /** 供 modelsToRecords 使用的模型 */
    public static class DemoModel extends EovaModel<DemoModel> {
    }

    /**
     * 造一条记录。
     *
     * @param id   主键
     * @param pid  父键
     * @return 记录
     */
    private static EovaRecord rec(String id, String pid) {
        EovaRecord r = new EovaRecord();
        r.set("id", id);
        r.set("pid", pid);
        return r;
    }

    @Test
    @DisplayName("TreeUtil.listToTree：合成根 / 丢弃同 id 真实记录 / 跳过孤立节点 / 保序")
    void listToTreeSemantics() {
        EovaRecord root = rec("0", "-1");
        EovaRecord a = rec("1", "0");
        EovaRecord b = rec("2", "1");
        EovaRecord orphan = rec("9", "999");
        List<EovaRecord> list = new ArrayList<>(List.of(root, a, b, orphan));

        EovaRecord tree = TreeUtil.listToTree(list, "0", "id", "pid", "children");

        // ① 【反直觉但属契约】返回的是【合成根】：id 为 null，且不是那条 id=="0" 的真实记录
        assertNotNull(tree, "必须返回根节点");
        assertNull(tree.get("id"), "返回的是新建的空根，不是 id==0 的那条记录");
        assertTrue(tree != root, "不得直接复用同 id 的真实记录（旧实现会覆盖它）");

        // ② 子节点挂载：根 -> a -> b
        List<EovaRecord> level1 = tree.get("children");
        assertNotNull(level1, "根下应有 children 列表");
        assertEquals(1, level1.size(), "根下只有 a（id=1）");
        assertSame(a, level1.get(0), "children 里应是【原记录实例】，不是副本");
        List<EovaRecord> level2 = a.get("children");
        assertNotNull(level2, "a 下应有 children");
        assertEquals(1, level2.size());
        assertSame(b, level2.get(0));

        // ③ 孤立节点（父不在集合里）被跳过：不出现在任何 children 中
        assertNull(orphan.get("children"), "孤立节点不得被挂到任何父下");
        assertTrue(!level1.contains(orphan), "孤立节点不得出现在根的子集中");

        // ④ 同一集合里的兄弟节点按【插入顺序】挂载
        EovaRecord a2 = rec("3", "0");
        List<EovaRecord> list2 = new ArrayList<>(List.of(rec("0", "-1"), rec("1", "0"), a2));
        EovaRecord tree2 = TreeUtil.listToTree(list2, "0", "id", "pid", "children");
        List<EovaRecord> lv = tree2.get("children");
        assertEquals(2, lv.size());
        assertEquals("1", lv.get(0).get("id"), "先插入的排前（LinkedHashMap 保序）");
        assertEquals("3", lv.get(1).get("id"));
    }

    @Test
    @DisplayName("TreeUtil.buildTree：根值不在集合中时返回空根；父字段为 null 时 NPE（既有语义）")
    void buildTreeEdgeCases() {
        // ① 根值不存在 → 仍然返回一个空根（不是 null）
        Map<String, EovaRecord> temp = new LinkedHashMap<>();
        temp.put("1", rec("1", "0"));
        EovaRecord t = TreeUtil.buildTree("999", "pid", "children", temp);
        assertNotNull(t, "根值不存在时旧实现仍返回合成根");
        assertNull(t.get("children"), "没有任何节点能挂上去");

        // ② 父字段为 null → 旧实现在 e.get(parentField).toString() 处 NPE，属既有语义
        Map<String, EovaRecord> temp2 = new LinkedHashMap<>();
        EovaRecord noPid = new EovaRecord();
        noPid.set("id", "5");
        temp2.put("5", noPid);
        assertThrows(NullPointerException.class,
                () -> TreeUtil.buildTree("0", "pid", "children", temp2),
                "父字段缺失时旧实现 NPE —— 不得'顺手'改成跳过");
    }

    @Test
    @DisplayName("WidgetUtil.modelsToRecords：经 setColumns(Model) 逐列复制，且为独立副本")
    void modelsToRecordsUsesSetColumnsModel() {
        DemoModel m1 = new DemoModel();
        m1.set("id", 1);
        m1.set("name", "甲");
        DemoModel m2 = new DemoModel();
        m2.set("id", 2);
        m2.set("name", "乙");

        List<EovaRecord> records = WidgetUtil.modelsToRecords(List.of(m1, m2));
        assertEquals(2, records.size(), "每个模型一条记录");
        assertEquals(1, ((Number) records.get(0).get("id")).intValue());
        assertEquals("甲", records.get(0).get("name"));
        assertEquals("乙", records.get(1).get("name"));

        // 独立副本：改记录不应回写模型（setColumns 是复制语义）
        records.get(0).set("name", "改过");
        assertEquals("甲", m1.get("name"), "记录与模型不得共享列容器");
    }
}
