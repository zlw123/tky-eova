/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.service;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import cn.eova.aop.MetaObjectIntercept;
import cn.eova.common.Ds;
import cn.eova.config.EovaConfig;
import cn.eova.db.EovaDbGateway;
import cn.eova.db.EovaGateways;
import cn.eova.template.common.util.TemplateUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code MetaService}(259，<b>本轮退掉其 stub</b>) 与 {@code TemplateUtil}(93)（第 68 轮 port）的判据。
 *
 * <p>两者都通过记录式网关替身驱动，不依赖数据库；重点钉住<b>既有 SQL 形态</b>与
 * <b>默认拦截器的回落</b>（后者依赖第 67 轮为 {@code EovaConfig} stub 按旧源码逐字补入的
 * {@code getDefaultMetaObjectIntercept()}）。</p>
 */
class MetaServiceAndTemplateUtilGoldenTest {

    /** 记录器：SQL 与参数 */
    static final class Probe {
        final List<String> sqls = new ArrayList<>();
        final List<Object[]> paras = new ArrayList<>();
        final List<String> methods = new ArrayList<>();
    }

    /**
     * 记录式网关替身。
     *
     * @param probe 记录器
     * @return 替身
     */
    private static EovaDbGateway gateway(Probe probe) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "delete":
                    probe.methods.add("delete");
                    probe.sqls.add((String) args[0]);
                    probe.paras.add((Object[]) args[1]);
                    return 1;
                case "update":
                    probe.methods.add("update");
                    probe.sqls.add((String) args[0]);
                    probe.paras.add((Object[]) args[1]);
                    return 1;
                case "findFirst":
                    probe.sqls.add((String) args[0]);
                    return null;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (EovaDbGateway) Proxy.newProxyInstance(
                MetaServiceAndTemplateUtilGoldenTest.class.getClassLoader(),
                new Class<?>[]{EovaDbGateway.class}, h);
    }

    @Test
    @DisplayName("deleteMetaField(object)：三条删除语句与字典 like 模式逐字一致")
    void deleteMetaFieldIssuesThreeDeletes() {
        Probe probe = new Probe();
        EovaGateways.register(Ds.EOVA, gateway(probe));
        try {
            new MetaService().deleteMetaField("eova_menu");

            assertEquals(3, probe.sqls.size(), "必须恰好三条删除");
            assertEquals("delete from eova_field where object_code = ?", probe.sqls.get(0));
            assertEquals("delete from eova_field_diy where object_code = ?", probe.sqls.get(1));
            assertEquals("delete from eova_option where code like ?", probe.sqls.get(2));
            assertEquals("dict_eova_menu_%", probe.paras.get(2)[0],
                    "字典 code 的 like 模式必须是 dict_<object>_%（注意 object 在后、% 在尾）");
        } finally {
            EovaGateways.clear();
        }
    }

    @Test
    @DisplayName("deleteMetaField(object, field)：单字段删除的 SQL 逐字一致")
    void deleteSingleMetaField() {
        Probe probe = new Probe();
        EovaGateways.register(Ds.EOVA, gateway(probe));
        try {
            new MetaService().deleteMetaField("eova_menu", "name");
            // 实测：单字段删除也是三条（字段本体 + 个性化 + 该字段的字典表达式）
            assertEquals(3, probe.sqls.size(), "实测三条，实际：" + probe.sqls);
            assertEquals("delete from eova_field where object_code = ? and en = ?", probe.sqls.get(0));
            assertEquals("delete from eova_field_diy where object_code = ? and en = ?", probe.sqls.get(1));
            assertEquals("delete from eova_option where code like ?", probe.sqls.get(2));
            assertEquals("eova_menu", probe.paras.get(0)[0]);
            assertEquals("name", probe.paras.get(0)[1]);
            assertEquals("dict_eova_menu_name%", probe.paras.get(2)[0],
                    "单字段字典模式为 dict_<object>_<field>%（无下划线分隔符）");
        } finally {
            EovaGateways.clear();
        }
    }

    @Test
    @DisplayName("upodateFieldWdith：方法名与 SQL 的既有瑕疵原样保留（含尾随空格、经 delete 执行 UPDATE）")
    void updateFieldWidthKeepsExistingQuirks() {
        Probe probe = new Probe();
        EovaGateways.register(Ds.EOVA, gateway(probe));
        try {
            // ① 方法名本身拼写错误（upodateFieldWdith）—— 属对外契约，不得"顺手"改名
            assertTrue(java.util.Arrays.stream(MetaService.class.getMethods())
                            .anyMatch(m -> m.getName().equals("upodateFieldWdith")),
                    "拼写错误的公开方法名必须保留");

            new MetaService().upodateFieldWdith("eova_menu", "name", 120);
            assertEquals(1, probe.sqls.size());
            // ② 走的是 delete(...) 通道执行 UPDATE —— 旧实现如此（不是 update 通道）
            assertEquals("delete", probe.methods.get(0),
                    "旧实现用 Db.delete 执行该 UPDATE —— 通道本身属既有语义");
            // ③ SQL 末尾【有一个空格】（旧源码如此），此处逐字比对
            assertEquals("update eova_field set width = ? where object_code = ? and en = ? ",
                    probe.sqls.get(0), "SQL 尾随空格属既有实现细节，逐字保留");
            assertEquals(120, probe.paras.get(0)[0], "width 在第一个参数位");
        } finally {
            EovaGateways.clear();
        }
    }

    @Test
    @DisplayName("TemplateUtil.initMetaObjectIntercept：空类名回落默认拦截器；指定类名则实例化；错名抛错")
    void initMetaObjectInterceptFallback() throws Exception {
        MetaObjectIntercept before = EovaConfig.getDefaultMetaObjectIntercept();
        try {
            // ① 未配置默认拦截器且类名为空 ⇒ 返回 null
            EovaConfig.setDefaultMetaObjectIntercept(null);
            assertNull(TemplateUtil.initMetaObjectIntercept(null), "无默认拦截器时返回 null");
            assertNull(TemplateUtil.initMetaObjectIntercept(""), "空串同样回落");

            // ② 配置了默认拦截器 ⇒ 回落它（同一实例）
            MetaObjectIntercept def = new MetaObjectIntercept() {
            };
            EovaConfig.setDefaultMetaObjectIntercept(def);
            assertSame(def, TemplateUtil.initMetaObjectIntercept(null),
                    "必须回落到 EovaConfig.getDefaultMetaObjectIntercept() 的同一实例");
            // 实测：只有 null/空串才回落；【空白串不回落】，会走 ClassUtil.newClass 并抛错
            // （既有语义，不得"顺手"改成 trim 后回落）
            assertThrows(Exception.class, () -> TemplateUtil.initMetaObjectIntercept("  "),
                    "空白类名不会回落 —— 旧实现不做 trim");

            // ③ 指定类名 ⇒ 反射实例化（用 JDK 类作样本：证明"非空类名即实例化"这条分支；
            //    待 intercept 族 port 后可换成真实的 MetaObjectIntercept 子类）
            Object o = TemplateUtil.initMetaObjectIntercept("java.util.ArrayList");
            assertTrue(o instanceof java.util.ArrayList,
                    "非空类名必须按 FQCN 反射实例化，实际：" + o);

            // ④ 找不到类 ⇒ 抛异常（不静默回落）
            assertThrows(Exception.class,
                    () -> TemplateUtil.initMetaObjectIntercept("no.such.Intercept"));
        } finally {
            EovaConfig.setDefaultMetaObjectIntercept(before);
        }
    }

    @Test
    @DisplayName("TemplateUtil.buildException：异常转可读文本（含 cause 链与类名）")
    void buildExceptionText() {
        Exception e = new IllegalStateException("boom");
        String s = TemplateUtil.buildException(e);
        assertTrue(s.contains("boom"), "必须含异常消息，实际：" + s);
        assertTrue(s.contains("IllegalStateException"), "必须含异常类名，实际：" + s);

        // 换行语义：多行文本（旧实现用换行拼栈信息）
        assertTrue(s.contains("\n"), "必须是多行文本，实际：" + s);
    }
}
