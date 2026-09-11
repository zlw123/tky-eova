/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;
import java.util.TreeSet;

import cn.eova.compat.jfinal.plugin.activerecord.LegacyIAtom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第 74 轮接缝判据：<b>`IAtom` 族（布尔驱动事务）</b> + `ModelSqlBuilder.replaceOrderBy` +
 * 网关的两个新重载（`save(table, pk, record)` / `deleteById(table, pk, id)`）。
 *
 * <p><b>本轮的接缝是本工程第一次明确"两套事务语义并存"：</b>
 * {@code tx(Atom)} 是<b>异常驱动</b>（正常返回即提交），{@code tx(LegacyIAtom)} 是
 * <b>布尔驱动</b>（true 提交 / false 回滚）。二者不可合并，故判据把"接口面同时存在两者"钉住。</p>
 */
class IAtomSeamGoldenTest {

    /** 网关（不触碰 DataSource） */
    private final JdbcEovaDbGateway gateway = new JdbcEovaDbGateway(null);

    @Test
    @DisplayName("LegacyIAtom：函数接口，run() 返回 boolean 且声明 throws SQLException（旧签名）")
    void legacyIAtomShape() throws Exception {
        assertTrue(LegacyIAtom.class.isAnnotationPresent(FunctionalInterface.class),
                "LegacyIAtom 必须是函数接口（旧 IAtom 是 @FunctionalInterface）");
        Method run = LegacyIAtom.class.getDeclaredMethod("run");
        assertEquals(boolean.class, run.getReturnType(), "run() 必须返回 boolean（决定提交/回滚）");
        assertEquals(1, run.getExceptionTypes().length);
        assertEquals(SQLException.class, run.getExceptionTypes()[0],
                "run() 必须声明 throws SQLException（旧签名）");
    }

    @Test
    @DisplayName("网关必须【同时】声明两套事务入口（布尔驱动 + 异常驱动，不可合并）")
    void gatewayHasBothTxFlavors() {
        TreeSet<String> shapes = new TreeSet<>();
        for (Method m : EovaDbGateway.class.getDeclaredMethods()) {
            if (m.getName().equals("tx")) {
                shapes.add(m.getParameterTypes()[0].getSimpleName() + "->" + m.getReturnType().getSimpleName());
            }
        }
        assertEquals(new TreeSet<>(List.of("Atom->Object", "LegacyIAtom->boolean")), shapes,
                "必须同时有 tx(Atom)（异常驱动，返回 T）与 tx(LegacyIAtom)（布尔驱动，返回 boolean）");
    }

    @Test
    @DisplayName("网关新增重载：save(table,pk,record) 与 deleteById(table,pk,id)")
    void gatewayHasNewOverloads() throws Exception {
        assertTrue(EovaDbGateway.class.getMethod("save", String.class, String.class, EovaRecord.class) != null);
        assertTrue(EovaDbGateway.class.getMethod("deleteById", String.class, String.class, Object.class) != null);
    }

    @Test
    @DisplayName("deleteById 的 SQL 形态：表名/主键 trim、反引号、多主键用 ' and ' 连接（旧制品实测）")
    void deleteByIdSqlShape() {
        assertEquals("delete from `eova_menu` where `id` = ?",
                JdbcEovaDbGateway.deleteByIdSql("eova_menu", "id"));
        assertEquals("delete from `t2` where `id` = ? and `code` = ?",
                JdbcEovaDbGateway.deleteByIdSql("  t2  ", " id , code "),
                "表名与各主键必须 trim（实测：旧制品输出 delete from `t2` where `id` = ? and `code` = ?）");
    }

    @Test
    @DisplayName("findById 的 SQL 形态：where 后有【空格】（javap 注释会吃掉尾随空格，须以实测为准）")
    void findByIdSqlShape() {
        assertEquals("select * from `eova_menu` where `id` = ?",
                JdbcEovaDbGateway.findByIdSql("eova_menu", "id"),
                "实测：旧制品产出 where `id` = ?（where 与反引号之间【有空格】）");
        assertEquals("select * from `t2` where `id` = ? and `code` = ?",
                JdbcEovaDbGateway.findByIdSql("  t2  ", "id, code"));
    }

    @Test
    @DisplayName("replaceOrderBy：四个实测格（含保留前导空格、吞掉紧跟的 ')'）")
    void replaceOrderByMatchesOldArtifact() {
        // 实测：去掉 order by 子句；【保留 order 之前的空格】（模式不含前导空格）
        assertEquals("select * from t ",
                ModelSqlBuilder.replaceOrderBy("select * from t order by id desc"));
        // 大小写不敏感
        assertEquals("select * from t ",
                ModelSqlBuilder.replaceOrderBy("select * from t ORDER BY id"));
        // 无 order by ⇒ 原样返回
        assertEquals("select * from t",
                ModelSqlBuilder.replaceOrderBy("select * from t"));
        // 子查询：列名模式 [^,\s]+ 会吞掉紧跟的 ')'（旧实现在此的表现，原样保留）
        assertEquals("select * from (select * from t  x ",
                ModelSqlBuilder.replaceOrderBy(
                        "select * from (select * from t order by id) x order by y"));
    }

    @Test
    @DisplayName("本批 5 个单元的类型契约（控制器继承 BaseController、SingleAtom 实现 LegacyIAtom）")
    void batchTypeContracts() {
        for (Class<?> c : List.of(cn.eova.widget.grid.GridController.class,
                cn.eova.meta.api.TableController.class,
                cn.eova.widget.form.FormController.class,
                cn.eova.meta.api.FormControler.class)) {
            assertTrue(cn.eova.common.base.BaseController.class.isAssignableFrom(c),
                    c.getName() + " 必须继承 BaseController");
            assertTrue(java.lang.reflect.Modifier.isPublic(c.getModifiers()));
        }
        assertTrue(LegacyIAtom.class.isAssignableFrom(cn.eova.template.single.SingleAtom.class),
                "SingleAtom 必须实现 LegacyIAtom（旧实现 implements IAtom）");
    }
}
