/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.model;

import java.util.ArrayList;
import java.util.List;

import cn.eova.common.Ds;
import cn.eova.config.EovaConfig;
import cn.eova.core.type.Convertor;
import cn.eova.core.type.MysqlConvertor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code MetaObject}（第 67 轮真 port，168 行；<b>退掉了 LC-011 起就存在的 stub</b>）的判据。
 *
 * <p><b>本判据同时覆盖一处宿主装配缺口：</b>{@code buildFieldValue} 走
 * {@code EovaConfig.getConvertor(ds).convert(f, val)}。旧栈由 {@code EovaDataSource}
 * 的业务方言初始化路径把转换器注册进 {@code convertorMap}；新栈该路径尚未 port，
 * 故<b>本判据显式注册</b>（即扮演启动期行为），并断言转换按字段类型发生。</p>
 */
class MetaObjectGoldenTest {

    /**
     * 造一个字段。
     *
     * @param en       英文名
     * @param dataType 数据库类型
     * @param size     长度
     * @return 字段
     */
    private static MetaField field(String en, String dataType, Integer size) {
        MetaField f = new MetaField();
        f.set("en", en);
        f.set("data_type_name", dataType);
        if (size != null) {
            f.set("data_size", size);
        }
        return f;
    }

    @Test
    @DisplayName("字段读取：code/name/ds/table/pk/view 直接映射列；isView 由 table 是否为空决定")
    void gettersMapColumns() {
        MetaObject o = new MetaObject();
        o.set("code", "eova_menu");
        o.set("name", "菜单");
        o.set("data_source", "eova");
        o.set("table_name", "eova_menu");
        o.set("pk_name", "id");
        o.set("view_name", null);

        assertEquals("eova_menu", o.getCode());
        assertEquals("菜单", o.getName());
        assertEquals("eova", o.getDs());
        assertEquals("eova_menu", o.getTable());
        assertEquals("id", o.getPk());
        assertFalse(o.isView(), "table 非空 ⇒ 不是视图");

        // table 为空 ⇒ 视为视图（旧实现：isEmpty(getTable()) ? true : false）
        MetaObject v = new MetaObject();
        v.set("table_name", null);
        assertTrue(v.isView(), "table 为空 ⇒ isView() 为 true");

        // getType：view_name 为空返回 TABLE，否则 VIEW
        assertEquals(cn.eova.common.utils.db.DsUtil.TABLE, o.getType(), "无 view_name ⇒ TABLE");
        o.set("view_name", "v_menu");
        assertEquals(cn.eova.common.utils.db.DsUtil.VIEW, o.getType(), "有 view_name ⇒ VIEW");
    }

    @Test
    @DisplayName("buildFieldValue：按字段类型经 EovaConfig.getConvertor(ds) 转换（含 TINYINT(1)->Boolean）")
    void buildFieldValueUsesConvertor() {
        Convertor before = EovaConfig.getConvertor(Ds.EOVA);
        try {
            // 扮演旧栈启动期的注册（EovaDataSource 的业务方言初始化路径）
            EovaConfig.addConvertor(Ds.EOVA, new MysqlConvertor());

            MetaObject o = new MetaObject();
            o.set("data_source", Ds.EOVA);
            List<MetaField> fields = new ArrayList<>();
            fields.add(field("flag", "TINYINT", 1));
            fields.add(field("num", "INT", null));
            fields.add(field("title", "VARCHAR", 64));
            o.setFields(fields);

            assertEquals(Boolean.TRUE, o.buildFieldValue(Ds.EOVA, "flag", "true"),
                    "TINYINT(1) 必须转 Boolean（Eova 定制规则）");
            assertEquals(7, o.buildFieldValue(Ds.EOVA, "num", "7"), "INT 必须转 Integer");
            assertEquals("abc", o.buildFieldValue(Ds.EOVA, "title", "abc"));

            // 字段名不在列表中 ⇒ 原样返回（旧实现直接 return val）
            Object raw = new Object();
            assertSame(raw, o.buildFieldValue(Ds.EOVA, "not_exists", raw),
                    "未命中的字段名必须原样返回值");

            // 未注册转换器的数据源 ⇒ getConvertor 返回 null ⇒ 调 convert 会 NPE（旧栈同样如此）
            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                    () -> o.buildFieldValue("no_such_ds", "num", "1"),
                    "未注册数据源的转换器为 null —— 属既有语义（调用方须先完成注册）");
        } finally {
            EovaConfig.addConvertor(Ds.EOVA, before);
        }
    }

    @Test
    @DisplayName("buildPkValue：用对象的 ds 与 pk 组合出转换（兼容 PGSQL 强类型）")
    void buildPkValueUsesOwnDsAndPk() {
        Convertor before = EovaConfig.getConvertor(Ds.EOVA);
        try {
            EovaConfig.addConvertor(Ds.EOVA, new MysqlConvertor());
            MetaObject o = new MetaObject();
            o.set("data_source", Ds.EOVA);
            o.set("pk_name", "id");
            List<MetaField> fields = new ArrayList<>();
            fields.add(field("id", "BIGINT", null));
            o.setFields(fields);

            assertEquals(9L, o.buildPkValue("9"), "主键按 BIGINT -> Long 转换");
        } finally {
            EovaConfig.addConvertor(Ds.EOVA, before);
        }
    }

    @Test
    @DisplayName("config/conf：MetaObjectConfig 与 JSONObject 两条读取路径")
    void configAccessors() {
        MetaObject o = new MetaObject();
        assertNull(o.getConfig(), "config 列为空 ⇒ getConfig() 返回 null");
        assertNotNull(o.getConf(), "config 列为空 ⇒ getConf() 返回空 JSONObject（不是 null）");
        assertEquals(0, o.getConf().size());

        o.set("config", "{\"a\":1}");
        assertNotNull(o.getConfig(), "有 config 时必须构造出 MetaObjectConfig");
        assertEquals(1, o.getConf().getIntValue("a"));
    }

    @Test
    @DisplayName("fields：getFields/setFields 是普通存取（AopContext 的构造器依赖它）")
    void fieldsAccessors() {
        MetaObject o = new MetaObject();
        assertNull(o.getFields(), "未设置时为 null（旧实现无默认值）");
        List<MetaField> fs = new ArrayList<>();
        fs.add(field("x", "INT", null));
        o.setFields(fs);
        assertSame(fs, o.getFields(), "必须原样存取同一个列表实例");
    }
}
