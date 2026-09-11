/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import cn.eova.compat.jfinal.core.LegacyConst;
import cn.eova.compat.jfinal.handler.LegacyHandler;
import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 决策 3 地基波「声明性原语」的<b>跨实现</b>判据。
 *
 * <p>覆盖 {@link LegacyConst} 与 {@link LegacyHandler} —— 两者都是"只有声明、没有算法"的
 * 原语（常量接口 / 抽象类），故判据的形态是<b>声明面逐项比对旧 jfinal 制品</b>：
 * 常量取值、字段（含类型与修饰符）、方法签名与抽象性。</p>
 *
 * <p><b>为什么不用行为判据：</b>抽象类与常量接口没有可执行的行为 ——
 * 但"读一遍源码确认长得一样"不构成证据（R45）。反射逐项比对才是。
 * {@code LegacyHandler} 的行为由继承它的 3 个 EOVA handler 单元（239 行）承担，
 * 那批单元的判据在 port 时随单元一起建。</p>
 *
 * <p><b>已声明适配（归一表）：</b>{@code com.jfinal.handler.Handler} →
 * {@code cn.eova.compat.jfinal.handler.LegacyHandler}、
 * {@code javax.servlet.http.*} → {@code jakarta.servlet.http.*}。</p>
 *
 * <p>acceptanceProfile: golden-mvc-primitive-surface</p>
 */
class MvcPrimitiveSurfaceGoldenTest {

    /** 类型改名归一表（旧全名 → 新全名） */
    private static final String[][] RENAMES = {
            {"com.jfinal.handler.Handler", "cn.eova.compat.jfinal.handler.LegacyHandler"},
            {"javax.servlet.", "jakarta.servlet."},
    };

    /**
     * {@code LegacyConst} 的两个常量取值逐字对照旧 {@code com.jfinal.core.Const}。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("LegacyConst 两常量取值逐字对照旧 jfinal Const")
    void legacyConstValuesMatchOld() throws Exception {
        Class<?> oldCls = oldClass("com.jfinal.core.Const");

        // JFINAL_VERSION 会经 AppController 写入前端可见的 kv，属对外输出 —— 必须逐字
        assertEquals(oldCls.getField("JFINAL_VERSION").get(null), LegacyConst.JFINAL_VERSION,
                "JFINAL_VERSION 必须逐字一致（该值会出现在前端可见 JSON 里）");
        assertEquals(oldCls.getField("DEFAULT_ENCODING").get(null), LegacyConst.DEFAULT_ENCODING,
                "DEFAULT_ENCODING 必须逐字一致（SseKit 用它设置响应编码）");

        // 非空洞性自检：确认取到的是真实取值而非 null
        assertTrue(!LegacyConst.JFINAL_VERSION.isEmpty(), "常量不得为空");
    }

    /**
     * {@code LegacyConst} <b>不得</b>引入 EOVA 未使用的 jfinal 常量。
     *
     * <p>本用例把"方法/常量集口径"钉死：jfinal {@code Const} 有 20+ 常量，
     * 而 EOVA 只用 2 个；多引入会让接缝无谓膨胀并掩盖真实使用面。
     * 若将来确有新用法，应连同其语义一并加，而不是先放一批壳常量。</p>
     */
    @Test
    @DisplayName("LegacyConst 只声明被实际使用的 2 个常量（不引入未用常量）")
    void legacyConstHasOnlyUsedConstants() {
        Field[] declared = LegacyConst.class.getDeclaredFields();
        List<String> names = new ArrayList<>();
        for (Field f : declared) {
            names.add(f.getName());
        }
        assertEquals(List.of("JFINAL_VERSION", "DEFAULT_ENCODING"), names,
                "只允许这两个常量（全树普查：jfinal Const 在 EOVA 中仅此 2 处被读）");
    }

    /**
     * {@code LegacyHandler} 的字段与抽象方法逐项对照旧 {@code Handler}。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("LegacyHandler 字段/抽象方法逐项对照旧 jfinal Handler")
    void legacyHandlerSurfaceMatchesOld() throws Exception {
        Class<?> oldCls = oldClass("com.jfinal.handler.Handler");

        // 父类
        assertEquals("java.lang.Object", LegacyHandler.class.getSuperclass().getName(),
                "两者都应直接继承 Object");
        assertEquals(oldCls.getSuperclass().getName(), LegacyHandler.class.getSuperclass().getName());

        // 字段：next 与 nextHandler（后者是旧实现的历史别名，并存不得合并）
        for (String fname : new String[]{"next", "nextHandler"}) {
            Field oldF = oldCls.getDeclaredField(fname);
            Field newF = LegacyHandler.class.getDeclaredField(fname);
            assertEquals(Modifier.toString(oldF.getModifiers()), Modifier.toString(newF.getModifiers()),
                    fname + " 的修饰符必须一致");
            assertEquals(normalize(oldF.getType().getName()), normalize(newF.getType().getName()),
                    fname + " 的类型必须一致（归一已声明适配后）");
            assertEquals(oldF.getType().getName(), oldF.getType().getName());
        }

        // 抽象方法 handle(String, req, resp, boolean[])
        Method oldM = null;
        for (Method m : oldCls.getDeclaredMethods()) {
            if ("handle".equals(m.getName())) {
                oldM = m;
            }
        }
        assertTrue(oldM != null, "旧 Handler 必须有 handle 方法");
        Method newM = LegacyHandler.class.getMethod("handle", String.class,
                jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class, boolean[].class);

        assertEquals(oldM.getReturnType(), newM.getReturnType(), "返回类型必须一致");
        assertTrue(Modifier.isAbstract(newM.getModifiers()), "新 handle 必须是抽象方法（旧实现亦然）");
        assertEquals(oldM.getParameterCount(), newM.getParameterCount(), "参数个数必须一致");

        // 参数类型逐项比对（归一 javax→jakarta 与 Handler 自指）
        Class<?>[] op = oldM.getParameterTypes();
        Class<?>[] np = newM.getParameterTypes();
        for (int i = 0; i < op.length; i++) {
            assertEquals(normalize(op[i].getName()), normalize(np[i].getName()),
                    "第 " + i + " 个参数类型必须一致");
        }
    }

    /**
     * 取旧 jfinal 类并做非空洞性自检。
     *
     * @param fqcn 全限定名
     * @return 旧类
     * @throws Exception 反射/IO 失败
     */
    private static Class<?> oldClass(String fqcn) throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> c = Class.forName(fqcn, true, jf);
        OldImplementationLoader.assertFromJar(c, OldImplementationLoader.oldJFinalJar());
        assertTrue(c != LegacyConst.class && c != LegacyHandler.class,
                "旧侧不得就是新接缝本身");
        return c;
    }

    /**
     * 类型名归一（已声明适配）：jfinal Handler → LegacyHandler；javax.servlet → jakarta.servlet。
     *
     * @param name 类型全名
     * @return 归一后的名字
     */
    private static String normalize(String name) {
        String s = name;
        for (String[] r : RENAMES) {
            char last = r[0].charAt(r[0].length() - 1);
            String tail = (Character.isLetterOrDigit(last) || last == '_' || last == '$')
                    ? "(?!\\w|\\$)" : "";
            s = s.replaceAll(java.util.regex.Pattern.quote(r[0]) + tail,
                    java.util.regex.Matcher.quoteReplacement(r[1]));
        }
        return s;
    }

}
