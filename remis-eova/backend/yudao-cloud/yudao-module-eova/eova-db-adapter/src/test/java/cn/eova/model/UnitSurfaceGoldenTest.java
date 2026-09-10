/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.model;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>声明面</b>等价判据：覆盖模型层与 i18n/menu-config 等待审单元的类。
 *
 * <p><b>为什么这些类用"声明面"判据：</b>它们的内容主要是<b>字段、访问器与静态工具方法</b>：
 * 契约就是"有哪些字段/方法、签名如何、常量取值多少"。对这类单元，
 * "读一遍源码确认长得一样"不构成证据，逐项反射比对才是；
 * 而其<b>行为</b>大多落在已单独验证过的底座上
 * （{@code EovaModel}/{@code BaseModel}/{@code EovaRecord}）。
 *
 * <p><b>为什么必须挂 jfinal 才能加载旧类：</b>旧模型类的继承链是
 * {@code Widget → BaseModel → com.jfinal.plugin.activerecord.Model}，
 * 故加载旧类需要 jfinal 制品在场 —— 用 {@code createWithOldJFinal}
 * （子优先前缀含 {@code com.jfinal.}），并做来源自检，避免出现 R44 那类
 * "旧侧被换成混合体"的污染。
 *
 * <p><b>允许的替换（显式声明，非静默放宽）：</b>仅类型简单名归一化 ——
 * {@code Page} → {@code EovaPage}、{@code Model} → {@code EovaModel}
 * （基类由 jfinal {@code Model} 换为 {@code EovaModel} 导致泛型擦除上界变化）。
 *
 * <p>acceptanceProfile: golden-model-surface
 */
class UnitSurfaceGoldenTest {

    /** 本批覆盖的模型类（新旧同 FQCN） */
    private static final List<String> UNITS = List.of(
            "cn.eova.model.Widget",
            "cn.eova.model.EovaProps",
            "cn.eova.model.EovaTemplate",
            "cn.eova.model.Mod",
            "cn.eova.model.Session",
            "cn.eova.model.MetaFieldConfig",
            // 第二批：Role（逐字节）；User / MenuObject（含已声明的底座替换）
            "cn.eova.model.Role",
            "cn.eova.model.User",
            "cn.eova.model.MenuObject",
            // i18n 簇（解锁 Button）与菜单配置（解锁 MenuConfig）
            "cn.eova.i18n.I18N",
            "cn.eova.i18n.I18NBuilder",
            "cn.eova.core.menu.config.ChartConfig",
            "cn.eova.core.menu.config.TreeConfig");

    @Test
    @DisplayName("模型层声明面：字段/方法签名/常量值/父类与旧实现逐项一致")
    void surfaceMatchesOldModelClasses() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable()
                        && OldImplementationLoader.oldJFinalJarAvailable(),
                "旧产物或旧 jfinal 制品缺失，无法加载旧模型类");

        ClassLoader loader = OldImplementationLoader.createWithOldJFinal(
                OldImplementationLoader.locateRepoRoot());
        // 来源自检：旧侧必须来自旧产物目录（否则比对退化为"新 vs 新"）
        Class<?> firstOld = loader.loadClass(UNITS.get(0));
        OldImplementationLoader.assertFromOldArtifacts(firstOld);

        List<String> diffs = new ArrayList<>();
        int compared = 0;
        int constFields = 0;
        int daoFields = 0;

        for (String fqcn : UNITS) {
            Class<?> oldC = loader.loadClass(fqcn);
            Class<?> newC = Class.forName(fqcn);
            compared++;

            diff(diffs, fqcn, "kind", kind(oldC), kind(newC));
            diff(diffs, fqcn, "modifiers", Modifier.toString(oldC.getModifiers()),
                    Modifier.toString(newC.getModifiers()));
            // 父类按【简单名】比对：新旧都是 BaseModel（FQCN 相同），
            // 但其上层由 jfinal Model 换为 EovaModel，故只比本类的直接父类名
            diff(diffs, fqcn, "superclass", simple(oldC.getSuperclass()), simple(newC.getSuperclass()));
            diff(diffs, fqcn, "interfaces", ifaces(oldC), ifaces(newC));
            diff(diffs, fqcn, "fields", fields(oldC), fields(newC));
            diff(diffs, fqcn, "methods", methods(oldC), methods(newC));

            for (Field f : oldC.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())) {
                    constFields++;
                }
                if ("dao".equals(f.getName())) {
                    daoFields++;
                }
            }
        }

        System.out.println("[模型层比对] 类 " + compared + " 个（常量字段 " + constFields
                + " / dao 字段 " + daoFields + "）；差异 " + diffs.size());
        // 非空转证据：矩阵必须真的压到"常量值"与"dao 单例字段"两类
        assertTrue(constFields > 0, "比对矩阵退化：没有任何静态常量字段被比对");
        assertTrue(daoFields > 0, "比对矩阵退化：没有比对 dao 单例字段");
        assertTrue(diffs.isEmpty(),
                "模型层声明面差异 " + diffs.size() + " 条：\n" + String.join("\n", diffs));
    }

    private static void diff(List<String> diffs, String fqcn, String what, Object o, Object n) {
        if (!String.valueOf(o).equals(String.valueOf(n))) {
            diffs.add(fqcn + " | " + what + ":\n    旧=" + o + "\n    新=" + n);
        }
    }

    private static String kind(Class<?> c) {
        if (c.isAnnotation()) {
            return "annotation";
        }
        if (c.isEnum()) {
            return "enum";
        }
        if (c.isInterface()) {
            return "interface";
        }
        return "class";
    }

    private static String simple(Class<?> c) {
        return c == null ? "-" : normalize(c.getSimpleName());
    }

    /** 已声明的类型简单名归一化 */
    private static String normalize(String simpleName) {
        if ("Page".equals(simpleName)) {
            return "EovaPage";
        }
        if ("Model".equals(simpleName)) {
            return "EovaModel";
        }
        // 已声明的底座替换：jfinal Record -> EovaRecord（见 User 的追溯头）
        if ("Record".equals(simpleName)) {
            return "EovaRecord";
        }
        return simpleName;
    }

    private static String ifaces(Class<?> c) {
        return Arrays.stream(c.getInterfaces()).map(Class::getSimpleName).sorted()
                .collect(Collectors.joining(","));
    }

    /** 字段签名 + 静态常量值（常量值属对外契约） */
    private static Set<String> fields(Class<?> c) {
        List<String> out = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            String v = "";
            if (Modifier.isStatic(f.getModifiers())) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val instanceof String || val instanceof Number
                            || val instanceof Character || val instanceof Boolean) {
                        v = "=" + val;
                    }
                } catch (Throwable ignored) {
                    // 不可读的静态字段：只比签名
                }
            }
            out.add(Modifier.toString(f.getModifiers()) + " " + normalize(f.getType().getSimpleName())
                    + " " + f.getName() + v);
        }
        out.sort(null);
        return new TreeSet<>(out);
    }

    /** 方法签名 */
    private static Set<String> methods(Class<?> c) {
        List<String> out = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            if (m.isSynthetic()) {
                continue;
            }
            String params = Arrays.stream(m.getParameterTypes())
                    .map(t -> normalize(t.getSimpleName()))
                    .collect(Collectors.joining(","));
            out.add(Modifier.toString(m.getModifiers()) + " " + normalize(m.getReturnType().getSimpleName())
                    + " " + m.getName() + "(" + params + ")");
        }
        out.sort(null);
        return new TreeSet<>(out);
    }
}
