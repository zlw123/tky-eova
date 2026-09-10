/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.port;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>声明式单元</b>的通用跨实现等价判据。
 *
 * <p><b>为什么单独做一类判据：</b>枚举、常量接口、注解、值对象这类单元没有"行为"可跑，
 * 它们的契约就是<b>结构本身</b> —— 常量名与值、枚举常量及其顺序、注解属性与默认值、
 * 字段与方法的签名和修饰符。对这些单元，
 * 「读一遍源码确认长得一样」不构成证据；反射面逐项比对才是。
 *
 * <p>判据口径：让<b>旧实现上场</b>（{@link OldImplementationLoader} 加载旧产物），
 * 与本次 port 的新类逐项比对：
 * <ol>
 *   <li>类型种类（class / interface / enum / annotation）与修饰符</li>
 *   <li>父类、实现的接口集合</li>
 *   <li>枚举常量<b>名称与顺序</b>（顺序即 {@code values()} 契约）</li>
 *   <li>声明字段：名称、类型、修饰符；静态常量<b>值</b>也要相等</li>
 *   <li>声明方法：名称、参数类型、返回类型、修饰符；注解属性的默认值</li>
 * </ol>
 *
 * <p>acceptanceProfile: golden-declarative-surface
 */
class DeclarativeUnitGoldenTest {

    /**
     * 本判据覆盖的单元（新旧同名，因为 port 保持原 FQCN）。
     * 新增声明式单元时在此登记即可复用同一判据。
     */
    private static final List<String> UNITS = List.of(
            "cn.eova.common.Ds",
            "cn.eova.hook.Hook",
            "cn.eova.hook.EovaHookCode",
            "cn.eova.hook.EovaHookType",
            "cn.eova.common.utils.check.ValidaUtil",
            "cn.eova.plugin.automodel.TableBind",
            "cn.eova.model.MetaObjectConfig",
            "cn.eova.sql.dql.QueryParam",
            "cn.eova.ops.OpsConst",
            "cn.eova.common.utils.db.JdbcUtil",
            "cn.eova.model.MsgType",
            "cn.eova.template.common.config.TemplateConfig",
            "cn.eova.core.menu.config.TreeGridConfig",
            "cn.eova.core.api.ApiResponse",
            "cn.eova.common.vo.KeyVal",
            // 缓存常量：旧类只在方法体内引用 CacheKit，类初始化不触及它，
            // 故可在不挂 ehcache/jfinal 的情况下由旧实现直接作证常量取值
            "cn.eova.common.base.BaseCache");

    /**
     * <b>已声明的适配</b>：单元 FQCN → 允许在【新实现侧】额外出现在的成员。
     *
     * <p>口径：迁移只允许"新技术底座必需的适配"，且必须<b>显式声明</b>
     * （见 §5 的 {@code allowedAdaptations}）。本表就是该声明的机器可审计形式：
     * 列在这里的成员差异被接受，未列出的差异一律失败 ——
     * 这样既不放行静默漂移，也避免把"必需的适配"误报成缺陷。
     *
     * <p>成员串的格式与 {@link #fields}/{@link #methods} 的渲染一致，
     * 必须逐字匹配；若声明与实际不符（声明了但不存在）同样会报错，防止声明过期。
     */
    private static final Map<String, Set<String>> DECLARED_ADDITIONS = Map.of(
            // 宿主替换：com.jfinal.plugin.ehcache.CacheKit -> CacheService 接缝
            // 注：原声明的字段 cacheService 已上移到 cn.eova.compat.cache.CacheServices
            //（单一事实源，因为 compat 层的 EovaGateways.findByCache 也需要取缓存），
            // 故此处不再声明该字段 —— 该声明过期由本判据的"声明过期"检测报出过。
            "cn.eova.common.base.BaseCache|methods", Set.of(
                    "private static cn.eova.compat.cache.CacheService service()",
                    "public static void setCacheService(cn.eova.compat.cache.CacheService)"));

    @Test
    @DisplayName("声明式单元反射面逐项比对：枚举顺序/常量值/字段/方法签名差异应为 0")
    void surfacesMatchOld() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable(),
                "旧产物缺失：" + OldImplementationLoader.oldClassesDir());

        ClassLoader oldLoader =
                OldImplementationLoader.create(OldImplementationLoader.locateRepoRoot());

        List<String> diffs = new ArrayList<>();
        int compared = 0;
        int enums = 0;
        int annotations = 0;
        int constFields = 0;

        for (String fqcn : UNITS) {
            Class<?> oldC = oldLoader.loadClass(fqcn);
            // 自校验：确保比对的是旧产物，而非本次 port 的新类（否则比对退化为"新 vs 新"）
            OldImplementationLoader.assertFromOldArtifacts(oldC);
            Class<?> newC = Class.forName(fqcn);
            compared++;

            if (oldC.isEnum()) {
                enums++;
            }
            if (oldC.isAnnotation()) {
                annotations++;
            }

            diff(diffs, fqcn, "kind", kind(oldC), kind(newC));
            diff(diffs, fqcn, "modifiers", Modifier.toString(oldC.getModifiers()),
                    Modifier.toString(newC.getModifiers()));
            diff(diffs, fqcn, "superclass", name(oldC.getSuperclass()), name(newC.getSuperclass()));
            diff(diffs, fqcn, "interfaces", ifaces(oldC), ifaces(newC));

            if (oldC.isEnum()) {
                diff(diffs, fqcn, "enumConstants", enumNames(oldC), enumNames(newC));
            }

            memberDiff(diffs, fqcn, "fields", fields(oldC), fields(newC));
            memberDiff(diffs, fqcn, "methods", methods(oldC), methods(newC));
            diff(diffs, fqcn, "annotations", annotations(oldC), annotations(newC));

            constFields += countConstFields(oldC);
        }

        // 非空转证据：矩阵必须覆盖到枚举顺序、注解属性、常量值三类，否则判据不具判别力
        System.out.println("[声明式比对] 单元 " + compared + " 个（含枚举 " + enums
                + " / 注解 " + annotations + " / 静态常量字段 " + constFields
                + "）；差异 " + diffs.size());
        assertTrue(enums > 0 && annotations > 0 && constFields > 0,
                "比对矩阵退化：枚举 " + enums + " / 注解 " + annotations
                        + " / 常量字段 " + constFields + "，三类都必须覆盖");
        assertTrue(diffs.isEmpty(),
                "声明式单元反射面差异 " + diffs.size() + " 条：\n" + String.join("\n", diffs));
    }

    private static void diff(List<String> diffs, String fqcn, String what, Object o, Object n) {
        if (!String.valueOf(o).equals(String.valueOf(n))) {
            diffs.add(fqcn + " | " + what + ":\n    旧=" + o + "\n    新=" + n);
        }
    }

    private static String name(Class<?> c) {
        return c == null ? "-" : c.getName();
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

    private static String ifaces(Class<?> c) {
        return Arrays.stream(c.getInterfaces()).map(Class::getName).sorted()
                .collect(Collectors.joining(","));
    }

    private static String enumNames(Class<?> c) {
        Object[] cs = c.getEnumConstants();
        return cs == null ? "-"
                : Arrays.stream(cs).map(Object::toString).collect(Collectors.joining(","));
    }

    /**
     * 成员差异比对：允许【已声明适配】带来的新增成员，其余任何缺失或新增都算差异。
     * 声明了但实际不存在的成员也会报错（防止声明过期变成"空白豁免"）。
     */
    private static void memberDiff(List<String> diffs, String fqcn, String what,
                                   Set<String> oldM, Set<String> newM) {
        Set<String> declared = DECLARED_ADDITIONS.getOrDefault(fqcn + "|" + what, Set.of());
        Set<String> expected = new TreeSet<>(oldM);
        expected.addAll(declared);

        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(newM);
        Set<String> extra = new TreeSet<>(newM);
        extra.removeAll(expected);
        Set<String> staleDecl = new TreeSet<>(declared);
        staleDecl.removeAll(newM);

        if (!missing.isEmpty()) {
            diffs.add(fqcn + " | " + what + " 缺失（旧有新无）: " + missing);
        }
        if (!extra.isEmpty()) {
            diffs.add(fqcn + " | " + what + " 未声明的新增: " + extra
                    + "\n    提示：确属必需适配时，请登记到 DECLARED_ADDITIONS");
        }
        if (!staleDecl.isEmpty()) {
            diffs.add(fqcn + " | " + what + " 声明过期（DECLARED_ADDITIONS 中已不存在）: " + staleDecl);
        }
    }

    /** 字段签名 + 静态常量值（值属对外契约，必须比对） */
    private static Set<String> fields(Class<?> c) {
        List<String> out = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            String v;
            try {
                f.setAccessible(true);
                Object val = f.get(null);
                v = (val instanceof String || val instanceof Number || val instanceof Character
                        || val instanceof Boolean) ? "=" + val : "";
            } catch (Throwable t) {
                // 非静态字段或不可访问：只比签名
                v = "";
            }
            out.add(Modifier.toString(f.getModifiers()) + " " + f.getType().getName()
                    + " " + f.getName() + v);
        }
        out.sort(null);
        return new TreeSet<>(out);
    }

    /** 方法签名；注解属性附默认值 */
    private static Set<String> methods(Class<?> c) {
        List<String> out = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            if (m.isSynthetic()) {
                continue;
            }
            String params = Arrays.stream(m.getParameterTypes())
                    .map(Class::getName).collect(Collectors.joining(","));
            String def = "";
            if (c.isAnnotation() && m.getDefaultValue() != null) {
                def = " default=" + m.getDefaultValue();
            }
            out.add(Modifier.toString(m.getModifiers()) + " " + m.getReturnType().getName()
                    + " " + m.getName() + "(" + params + ")" + def);
        }
        out.sort(null);
        return new TreeSet<>(out);
    }

    private static String annotations(Class<?> c) {
        List<String> out = new ArrayList<>();
        for (Annotation a : c.getAnnotations()) {
            out.add(a.toString());
        }
        out.sort(null);
        return String.join("; ", out);
    }

    private static int countConstFields(Class<?> c) {
        int n = 0;
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())) {
                n++;
            }
        }
        return n;
    }
}
