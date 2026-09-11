/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.aop;

import java.util.ArrayList;
import java.util.List;

import cn.eova.common.base.BaseController;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.db.EovaRecord;
import cn.eova.model.MetaField;
import cn.eova.model.MetaObject;
import cn.eova.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AOP 三件套（第 67 轮 port）：{@code EovaAopContext}(39) / {@code AopContext}(162) /
 * {@code MetaObjectIntercept}(351)。
 *
 * <p><b>为什么要一起判：</b>三者是一条链 —— 拦截器（{@code MetaObjectIntercept}）拿到的
 * 参数类型是 {@code AopContext}，而后者继承 {@code EovaAopContext}（持有 Controller 与 User）。
 * 任何一环的字段/构造契约变了，用户 mod 覆写的回调就会拿到不一致的上下文。</p>
 */
class AopContextGoldenTest {

    /** 探针控制器：让 getUser 可被替换 */
    static class ProbeCtrl extends BaseController {

        /** 固定返回的用户 */
        User user;

        @Override
        public User getUser() {
            return user;
        }
    }

    /** 探针拦截器：记录被回调的方法名 */
    static class ProbeIntercept extends MetaObjectIntercept {

        /** 回调轨迹 */
        final List<String> calls = new ArrayList<>();

        @Override
        public void queryBefore(AopContext ac) {
            calls.add("queryBefore");
        }

        @Override
        public String addBefore(AopContext ac) {
            calls.add("addBefore");
            return "拦截提示";
        }

        @Override
        public LegacyKv queryFooter(AopContext ac) {
            calls.add("queryFooter");
            return LegacyKv.of("k", "v");
        }
    }

    /**
     * 造一个带 id 的用户。
     *
     * @param id 用户 id
     * @return 用户
     */
    private static User user(int id) {
        User u = new User();
        u.set("id", id);
        return u;
    }

    @Test
    @DisplayName("EovaAopContext：构造时从 BaseController 取 user；UID() 读用户 id")
    void eovaAopContextCarriesUser() {
        ProbeCtrl ctrl = new ProbeCtrl();
        ctrl.user = user(42);

        EovaAopContext ctx = new EovaAopContext(ctrl);
        assertSame(ctrl, ctx.ctrl, "必须保留控制器引用");
        assertSame(ctrl.user, ctx.user, "必须取到 BaseController.getUser() 的同一个对象");
        assertEquals(42, ctx.UID(), "UID() 返回用户 id");
    }

    @Test
    @DisplayName("AopContext：六个构造器各自填充哪些字段（上下文契约）")
    void aopContextConstructors() {
        ProbeCtrl ctrl = new ProbeCtrl();
        ctrl.user = user(7);

        MetaObject object = new MetaObject();
        object.set("code", "eova_menu");
        List<MetaField> fields = new ArrayList<>();
        MetaField f = new MetaField();
        f.set("en", "id");
        fields.add(f);
        object.setFields(fields);

        EovaRecord record = new EovaRecord();
        record.set("id", 1);
        List<EovaRecord> records = new ArrayList<>();
        records.add(record);

        // ① 仅控制器
        AopContext c1 = new AopContext(ctrl);
        assertEquals("", c1.condition, "condition 初值为空串（不是 null）—— 既有契约");
        assertNotNull(c1.params, "params 必须初始化为空列表");

        // ② 控制器 + records
        AopContext c2 = new AopContext(ctrl, records);
        assertSame(records, c2.records);
        assertEquals(7, c2.UID());

        // ③ 控制器 + record
        AopContext c3 = new AopContext(ctrl, record);
        assertSame(record, c3.record);

        // ④ 控制器 + object：必须同时填 fields（从 object.getFields() 取）
        AopContext c4 = new AopContext(ctrl, object);
        assertSame(object, c4.object);
        assertSame(fields, c4.fields, "构造器必须把 object.getFields() 填进 fields");

        // ⑤ 控制器 + object + record
        AopContext c5 = new AopContext(ctrl, object, record);
        assertSame(record, c5.record);
        assertSame(object, c5.object);

        // ⑥ 控制器 + object + records
        AopContext c6 = new AopContext(ctrl, object, records);
        assertSame(records, c6.records);
        assertSame(object, c6.object);

        // 注：getStart/getEnd 会读请求参数（经 Controller.get），需要请求上下文，
        // 不属本判据的关注点，故此处不做断言。
    }

    @Test
    @DisplayName("MetaObjectIntercept：回调面完整（用户 mod 覆写的契约），默认返回值为默认值")
    void interceptCallbackSurface() {
        // ① 默认实现不得抛错（旧实现是空实现 / 返回 null / 返回 Kv.create?）
        MetaObjectIntercept base = new MetaObjectIntercept();
        AopContext ac = new AopContext(new ProbeCtrl());
        assertDoesNotThrow(() -> base.queryBefore(ac));
        assertDoesNotThrow(() -> base.addInit(ac));
        assertDoesNotThrow(() -> base.deleteBefore(ac));
        assertDoesNotThrow(() -> base.updateBefore(ac));
        assertDoesNotThrow(() -> base.metadata(ac));

        // ② 子类覆写后按覆写生效
        ProbeIntercept probe = new ProbeIntercept();
        probe.queryBefore(ac);
        assertEquals("拦截提示", probe.addBefore(ac));
        assertEquals("v", probe.queryFooter(ac).get("k"));
        assertEquals(List.of("queryBefore", "addBefore", "queryFooter"), probe.calls);

        // ③ 回调面钉死：这些方法名与参数类型是 mod 的覆写契约
        for (String name : new String[]{"queryBefore", "queryAfter", "queryFooter", "addInit",
                "addBefore", "addAfter", "addSucceed", "deleteBefore", "deleteAfter",
                "deleteSucceed", "hideBefore", "hideAfter", "hideSucceed", "updateInit",
                "updateBefore", "updateAfter", "updateSucceed", "updateCellBefore", "updateCell",
                "updateCellAfter", "detailInit", "metadata"}) {
            boolean found = false;
            for (java.lang.reflect.Method m : MetaObjectIntercept.class.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    found = true;
                    assertEquals(1, m.getParameterCount(), name + " 必须只有一个 AopContext 形参");
                    assertEquals(AopContext.class, m.getParameterTypes()[0],
                            name + " 形参类型必须是 AopContext（mod 覆写契约）");
                }
            }
            assertTrue(found, "MetaObjectIntercept 必须声明回调 " + name);
        }
    }

    /**
     * 断言体不抛异常（JUnit 的 assertDoesNotThrow 包装）。
     *
     * @param body 待执行体
     */
    private static void assertDoesNotThrow(ThrowingRunnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            throw new AssertionError("不得抛出异常，实际抛出 " + t, t);
        }
    }

    /** 可抛异常的执行体 */
    @FunctionalInterface
    private interface ThrowingRunnable {
        /** 执行 */
        void run() throws Throwable;
    }
}
