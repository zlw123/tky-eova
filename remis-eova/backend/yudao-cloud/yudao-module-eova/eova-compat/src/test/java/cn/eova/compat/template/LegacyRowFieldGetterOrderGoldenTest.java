/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.template;

import java.util.HashMap;
import java.util.Map;

import cn.eova.db.EovaModel;
import com.jfinal.template.Engine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LegacyRowFieldGetter} 的**注册顺序**判据（第 303 轮 · 真缺陷 D5 回归锁）。
 *
 * <p><b>缺陷现场</b>：该读取器原用 {@code Engine.addFieldGetterToFirst(...)} 装在**链首**，
 * 于是抢在"方法 getter / 公有字段"之前接管 —— {@code #(user.role.lv)} 里的 {@code user.role}
 * 被当成"读名为 role 的列"（该列不存在 ⇒ {@code null}）⇒ {@code Field.eval} 抛
 * {@code TemplateException: Can not accessed by "lv" field from null target}。
 * 实测后果：{@code eova_object.filter} 含 {@code #(user.role.lv)} 的 {@code eova_role} /
 * {@code eova_user_code} 在 {@code /api/table/query/*} 一律回「查询条件错误」
 * ⇒ **角色列表页整页无数据**（旧栈 9 行 / 新栈 0 行），而 {@code #(user.id)} 的 {@code eova_diy} 正常。</p>
 *
 * <p><b>旧实现的顺序（字节码实证，不是推断）</b>：{@code javap -c com.jfinal.template.expr.ast.FieldKit}
 * 显示 jfinal 5.2.6 的 {@code init()} 按 {@code addLast} 装入
 * {@code GetterMethodFieldGetter → RealFieldGetter → ModelFieldGetter → RecordFieldGetter →
 * MapFieldGetter → ArrayLengthGetter} ⇒ <b>同名 getter/公有字段先胜出，都没有才按列名读</b>。
 * 本判据即钉这条相对顺序。</p>
 *
 * <p>acceptanceProfile: golden-template-field-getter</p>
 */
class LegacyRowFieldGetterOrderGoldenTest {

    /** 带**公有字段** {@code role} 的模型（模拟 {@code cn.eova.model.User#role}，且没有 role 列） */
    public static class FieldModel extends EovaModel<FieldModel> {
        /** 公有字段：旧栈由 jfinal 的 RealFieldGetter 接管 */
        public Object role;
    }

    /**
     * 反证专用的**全新**模型类。
     *
     * <p>★ 为什么不能复用 {@link FieldModel}：enjoy 的 {@code FieldKit} 有
     * {@code fieldGetterCache}（按 目标类+字段名 缓存解析结果）—— 复用会让"装到链首"这一步被缓存绕过，
     * 反证变成**假绿**（实测：第一次就这么绿了）。换一个从未解析过的类，缓存才会真的走链。</p>
     */
    public static class FieldModelForReverse extends EovaModel<FieldModelForReverse> {
        /** 公有字段：旧栈由 jfinal 的 RealFieldGetter 接管 */
        public Object role;
    }

    /** 只有列、没有同名 getter/字段的模型（模拟旧 {@code cn.eova.model.Menu} 的 {@code #(menu.code)}） */
    public static class ColumnModel extends EovaModel<ColumnModel> {
    }

    /**
     * 公有字段优先于列读取（旧 jfinal 顺序）——本用例在"装到链首"的缺陷态下会抛 TemplateException。
     */
    @Test
    @DisplayName("注册顺序：公有字段 role 先胜出 ⇒ #(probe.role.lv) 取字段值，不再抛『null target』")
    void publicFieldWinsOverColumnLookup() {
        LegacyRowFieldGetter.install();

        Map<String, Object> role = new HashMap<>();
        role.put("lv", 7);
        FieldModel probe = new FieldModel();
        probe.role = role; // 公有字段；模型里【没有】role 列

        Map<String, Object> root = new HashMap<>();
        root.put("probe", probe);

        Engine engine = new Engine();
        String out = engine.getTemplateByString("#(probe.role.lv)").renderToString(root);
        assertEquals("7", out,
                "公有字段必须先于『按列名读』胜出（jfinal FieldKit 里 ModelFieldGetter 排在 RealFieldGetter 之后）");
    }

    /**
     * 反空断言：把**同逻辑的影子读取器**装到链首即复现缺陷 —— 证明缺陷确实由"顺序"造成。
     *
     * <p>为什么用影子类而不是复用生产类：enjoy 的 {@code FieldKit} **禁止同一个 getter 类重复注册**
     * （实测抛 {@code FieldGetter already exists : cn.eova.compat.template.LegacyRowFieldGetter}），
     * 故反证只能另起一个类；它的 {@code takeOver}/{@code get} 直接转发给生产单例
     * ⇒ 差异**只有位置**，正是要证明的自变量。</p>
     */
    @Test
    @DisplayName("反证：同逻辑读取器装到链首即复现『null target』（缺陷由位置造成，不是读法）")
    void installToFirstReproducesDefect() {
        LegacyRowFieldGetter.install();
        Engine.addFieldGetterToFirst(new ShadowAtFirst());
        try {
            Map<String, Object> role = new HashMap<>();
            role.put("lv", 7);
            FieldModelForReverse probe = new FieldModelForReverse();
            probe.role = role;
            Map<String, Object> root = new HashMap<>();
            root.put("probe", probe);
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> new Engine().getTemplateByString("#(probe.role.lv)").renderToString(root),
                    "装到链首时应当复现缺陷（此断言失败说明判据没钉住顺序）");
            assertTrue(String.valueOf(ex.getMessage()).contains("null target"),
                    "异常应为『Can not accessed ... from null target』，实测：" + ex.getMessage());
        } finally {
            Engine.removeFieldGetter(ShadowAtFirst.class);
        }
    }

    /** 与生产读取器**同逻辑、仅位置不同**的影子类（判据专用） */
    public static class ShadowAtFirst extends com.jfinal.template.expr.ast.FieldGetter {
        @Override
        public com.jfinal.template.expr.ast.FieldGetter takeOver(Class<?> clazz, String fieldName) {
            return LegacyRowFieldGetter.me().takeOver(clazz, fieldName);
        }

        @Override
        public Object get(Object target, String fieldName) throws Exception {
            return LegacyRowFieldGetter.me().get(target, fieldName);
        }
    }

    /**
     * 原有语义不得被破坏：没有同名 getter/字段时仍按**列名**读（旧 {@code #(menu.code)} 那条路）。
     */
    @Test
    @DisplayName("列名读取不被破坏：无同名 getter/字段时 #(m.code) 仍读列值")
    void columnLookupStillWorks() {
        LegacyRowFieldGetter.install();
        ColumnModel m = new ColumnModel();
        m.set("code", "demo_menu");
        Map<String, Object> root = new HashMap<>();
        root.put("m", m);
        assertEquals("demo_menu", new Engine().getTemplateByString("#(m.code)").renderToString(root),
                "旧实现靠 ModelFieldGetter 读列名（jfinal 里它排在方法/字段读取器之后）");
    }
}
