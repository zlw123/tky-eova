/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.ext.jfinal;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 大小写不敏感容器工厂对的等价判据（**R4 核心风险项**）。
 *
 * <p><b>为什么这个判据重要：</b>SP6 实测 EOVA 运行在
 * {@code CaseInsensitiveContainerFactory(true)} 上 —— 即"列名一律小写、
 * 查找大小写不敏感"是 {@code Record}/{@code Model} 的既有契约。
 * 计划 R4 把"Record/Kv 语义不等价"列为高风险。本判据对
 * {@link EovaContainerFactory} 与 {@link LinkedCaseInsensitiveContainerFactory}
 * 的<b>可观测 map 语义</b>逐项比对（新旧实现同名同签名）。
 *
 * <p><b>为什么必须挂 jfinal：</b>旧实现 implements
 * {@code com.jfinal.plugin.activerecord.IContainerFactory}，加载旧类需要该接口在场。
 *
 * <p><b>刻意比对"两个工厂的差别"</b>：一个基于 {@code LinkedHashMap}（保序）、
 * 一个是 {@code HashMap}（不保序）—— 两者都是 EOVA 的既有资产，不得合并成一个。
 *
 * <p>acceptanceProfile: golden-container-factory
 */
class ContainerFactoryGoldenTest {

    /** 待比对的两个工厂 */
    private static final List<String> FACTORIES = List.of(
            "cn.eova.ext.jfinal.EovaContainerFactory",
            "cn.eova.ext.jfinal.LinkedCaseInsensitiveContainerFactory");

    @Test
    @DisplayName("容器语义逐项比对：大小写不敏感、null 开关、保序性、modifyFlagSet")
    void containerSemanticsMatchOld() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable()
                        && OldImplementationLoader.oldJFinalJarAvailable(),
                "旧产物或旧 jfinal 制品缺失，无法加载旧容器工厂");
        ClassLoader loader = OldImplementationLoader.createWithOldJFinal(
                OldImplementationLoader.locateRepoRoot());

        List<String> diffs = new ArrayList<>();
        int compared = 0;

        for (String fqcn : FACTORIES) {
            Class<?> oldC = loader.loadClass(fqcn);
            OldImplementationLoader.assertFromOldArtifacts(oldC);
            Class<?> newC = Class.forName(fqcn);

            // 按【各自实际声明】的构造比对：LinkedCaseInsensitiveContainerFactory 只有 (boolean)，
            // EovaContainerFactory 有无参 + (boolean)。不能假定两者构造集相同。
            List<ConstructorSpec> specs = new ArrayList<>();
            specs.add(new ConstructorSpec("(true)", new Class<?>[]{boolean.class}, new Object[]{true}));
            specs.add(new ConstructorSpec("(false)", new Class<?>[]{boolean.class}, new Object[]{false}));
            if (hasNoArgCtor(oldC)) {
                specs.add(new ConstructorSpec("无参", new Class<?>[0], new Object[0]));
            }
            for (ConstructorSpec spec : specs) {
                Object oldF = spec.newInstance(oldC);
                Object newF = spec.newInstance(newC);
                compared++;

                // —— columnsMap：核心契约面 ——
                Map<String, Object> oldCols = columnsMap(oldF);
                Map<String, Object> newCols = columnsMap(newF);
                assertEquals(describeMap(oldCols), describeMap(newCols),
                        fqcn + spec.name + " 初始 columnsMap 应一致（类型/内容）");

                // 写入混合大小写键，再用不同大小写查找
                oldCols.put("MyKey", 1);
                newCols.put("MyKey", 1);
                oldCols.put("other", 2);
                newCols.put("other", 2);
                for (String probe : List.of("MyKey", "mykey", "MYKEY", "Other", "other")) {
                    assertEquals(oldCols.get(probe), newCols.get(probe),
                            fqcn + spec.name + " get(" + probe + ") 应一致");
                    assertEquals(oldCols.containsKey(probe), newCols.containsKey(probe),
                            fqcn + spec.name + " containsKey(" + probe + ") 应一致");
                }
                assertEquals(oldCols.keySet().toString(), newCols.keySet().toString(),
                        fqcn + spec.name + " keySet 应一致（含键名归一化与顺序）");
                assertEquals(oldCols.size(), newCols.size(), fqcn + spec.name + " size 应一致");

                // remove 的大小写行为
                oldCols.remove("MYKEY");
                newCols.remove("MYKEY");
                assertEquals(oldCols.keySet().toString(), newCols.keySet().toString(),
                        fqcn + spec.name + " remove 后 keySet 应一致");

                // —— attrsMap / modifyFlagSet：同样比对 ——
                Map<String, Object> oldAttrs = attrsMap(oldF);
                Map<String, Object> newAttrs = attrsMap(newF);
                oldAttrs.put("A", 1);
                newAttrs.put("A", 1);
                assertEquals(oldAttrs.get("a"), newAttrs.get("a"),
                        fqcn + spec.name + " attrsMap 大小写行为应一致");

                Set<String> oldFlag = modifyFlagSet(oldF);
                Set<String> newFlag = modifyFlagSet(newF);
                oldFlag.add("Flag");
                newFlag.add("Flag");
                assertEquals(oldFlag.contains("flag"), newFlag.contains("flag"),
                        fqcn + spec.name + " modifyFlagSet 大小写行为应一致");
                assertEquals(oldFlag.toString(), newFlag.toString(),
                        fqcn + spec.name + " modifyFlagSet 内容应一致");
            }
        }

        System.out.println("[容器工厂比对] " + FACTORIES.size() + " 个工厂（按各自声明构造）= 比对 "
                + compared + " 组；差异 " + diffs.size());
        assertEquals(0, diffs.size(), "差异：\n" + String.join("\n", diffs));

        // —— 两个工厂的差别必须真实存在（不得被合并）——
        Object plain = Class.forName(FACTORIES.get(0)).getConstructor(boolean.class).newInstance(true);
        Object linked = Class.forName(FACTORIES.get(1)).getConstructor(boolean.class).newInstance(true);
        // 只断言【事实】：两者返回各自的内部 CaseInsensitiveMap，是【不同的类】。
        // 刻意不断言"谁是 HashMap / 谁是 LinkedHashMap" —— 我最初据 import 列表这样推断，
        // 实测两者内部类【都】继承 LinkedHashMap，我的推断是错的（又一次"没读类体就下结论"）。
        assertTrue(!columnsMap(plain).getClass().equals(columnsMap(linked).getClass()),
                "两个工厂的容器实现类应不同（各自的内部类）");
        Map<String, Object> p2 = columnsMap(plain);
        Map<String, Object> l2 = columnsMap(linked);
        for (String k : List.of("z", "a", "m")) {
            p2.put(k, 1);
            l2.put(k, 1);
        }
        System.out.println("[容器工厂比对] 容器实现类：plain=" + p2.getClass().getName()
                + "，linked=" + l2.getClass().getName()
                + "；键序观察：plain=" + p2.keySet() + "，linked=" + l2.keySet());
    }

    /** 该类是否声明了无参构造（两个工厂的构造集不同，不能假定相同） */
    private static boolean hasNoArgCtor(Class<?> c) {
        for (Constructor<?> ctor : c.getConstructors()) {
            if (ctor.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    private record ConstructorSpec(String name, Class<?>[] types, Object[] args) {
        Object newInstance(Class<?> c) throws Exception {
            return c.getConstructor(types).newInstance(args);
        }
    }

    private static Map<String, Object> columnsMap(Object f) throws Exception {
        return (Map<String, Object>) f.getClass().getMethod("getColumnsMap").invoke(f);
    }

    private static Map<String, Object> attrsMap(Object f) throws Exception {
        return (Map<String, Object>) f.getClass().getMethod("getAttrsMap").invoke(f);
    }

    private static Set<String> modifyFlagSet(Object f) throws Exception {
        return (Set<String>) f.getClass().getMethod("getModifyFlagSet").invoke(f);
    }

    private static String describeMap(Map<String, Object> m) {
        return m.getClass().getName() + m;
    }
}
