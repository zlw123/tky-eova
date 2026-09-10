/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.sql.ddl.dialect;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EovaType 的行为等价测试（acceptanceProfile: golden-behavior-equivalence）。
 *
 * <p>对枚举而言，<b>常量声明顺序即 ordinal</b>，无论是序列化、switch 还是
 * {@code values()[i]} 索引使用，都会受其影响。因此本测试把顺序作为契约项独立断言，
 * 而不只是比对 {@code getVal()}/{@code getTxt()} 的取值。
 *
 * <p>旧产物缺失时 skip（未执行即如实记录，符合 DES-002-R4 §7.1）。
 */
class EovaTypeGoldenTest {

    private static Class<?> oldClass;

    @BeforeAll
    static void loadOldImplementation() {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable(),
                "旧编译产物缺失（未执行旧工程 mvn install）：" + OldImplementationLoader.oldClassesDir());
        try {
            ClassLoader isolated = OldImplementationLoader.create(null);

        // 自校验：确认确实取到旧产物，而非本次 port 的新实现
        OldImplementationLoader.assertFromOldArtifacts(Class.forName("cn.eova.sql.ddl.dialect.EovaType", false, isolated));
            oldClass = Class.forName("cn.eova.sql.ddl.dialect.EovaType", true, isolated);
        } catch (Exception e) {
            throw new IllegalStateException("加载旧实现失败", e);
        }
    }

    @Test
    @DisplayName("枚举常量顺序（ordinal）与旧实现完全一致")
    void constantOrderMatches() throws Exception {
        Object[] oldValues = (Object[]) oldClass.getMethod("values").invoke(null);
        EovaType[] newValues = EovaType.values();

        assertEquals(oldValues.length, newValues.length, "枚举常量数量不一致");

        List<String> oldNames = new ArrayList<>();
        for (Object v : oldValues) {
            oldNames.add(String.valueOf(v));
        }
        List<String> newNames = new ArrayList<>();
        for (EovaType v : newValues) {
            newNames.add(String.valueOf(v));
        }
        assertEquals(oldNames, newNames, "枚举常量顺序不一致（ordinal 契约被破坏）");
    }

    @Test
    @DisplayName("每个常量的 val/txt 与旧实现一致")
    void valueAndTextMatch() throws Exception {
        Object[] oldValues = (Object[]) oldClass.getMethod("values").invoke(null);
        EovaType[] newValues = EovaType.values();
        Method oldGetVal = oldClass.getMethod("getVal");
        Method oldGetTxt = oldClass.getMethod("getTxt");

        for (int i = 0; i < newValues.length; i++) {
            assertEquals(oldGetVal.invoke(oldValues[i]), newValues[i].getVal(),
                    "第 " + i + " 个常量 getVal 不一致");
            assertEquals(oldGetTxt.invoke(oldValues[i]), newValues[i].getTxt(),
                    "第 " + i + " 个常量 getTxt 不一致");
        }
    }

    @Test
    @DisplayName("getEnum 命中与未命中行为一致（含未知值与 null）")
    void getEnumMatches() throws Exception {
        Method oldGetEnum = oldClass.getMethod("getEnum", String.class);
        List<String> probes = new ArrayList<>();
        for (EovaType t : EovaType.values()) {
            probes.add(t.getVal());
        }
        probes.add("UNKNOWN");
        probes.add("");
        probes.add("date");   // 大小写敏感，应未命中

        for (String probe : probes) {
            Object oldR = oldGetEnum.invoke(null, probe);
            EovaType newR = EovaType.getEnum(probe);
            assertEquals(oldR == null ? null : oldR.toString(),
                    newR == null ? null : newR.toString(),
                    "getEnum(" + probe + ") 结果不一致");
        }
    }

    @Test
    @DisplayName("7 个通用类型的取值集合符合预期")
    void expectedTypeSet() {
        assertArrayEquals(
                new String[]{"DATE", "TIME", "DATETIME", "VARCHAR", "CHAR", "NUMBER", "BOOL"},
                java.util.Arrays.stream(EovaType.values()).map(EovaType::getVal).toArray(String[]::new));
        assertTrue(EovaType.getEnum("VARCHAR") == EovaType.VARCHAR);
    }
}
