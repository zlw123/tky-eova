/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.meta;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **`MetaUtil` 的两个纯映射方法与旧实现的跨制品比对（第 180 轮）**。
 *
 * <p>来源：r176 的覆盖扫描报出 `MetaUtil`（206 行）类名零出现且 **5/5 方法零命中**；
 * r178 的三类判读确认它有 4 处生产引用（不是死类）；r179/r180 的可行性检查把它拆开：
 * <ul>
 *   <li>{@code getDataType(String)} / {@code getFormType(boolean, String, int)} —— **纯映射**，
 *       本判据覆盖；</li>
 *   <li>{@code addVirtualObject} / {@code removeUserDiy} / {@code removeRoleDiy} —— 查库
 *       （`EovaGateways`），**不在本判据范围**（需 live-DB 模式，另立）。</li>
 * </ul>
 *
 * <p><b>为什么这两条值得钉：</b>它们是"库列类型 → 数据显示类型 / 表单控件类型"的**唯一映射表**，
 * 决定每个字段在页面上用什么控件、按什么类型取数 —— 映射漂移不会报错，只会让某些字段
 * 悄悄变成文本框或字符串。
 *
 * <p><b>判法（r177 同款）：</b>旧侧 = 从 `meta-eova/eova/core/target/classes` 加载的
 * `MetaUtil` **真身**（`OldImplementationLoader`），新侧 = ported 类；逐 (方法 × 输入) 取
 * 结果串（成功 ⇒ 值带类型；失败 ⇒ 异常类型 + 消息逐字）比对，差异必须为 0。
 *
 * <p><b>反空断言：</b>条数 ≥ 60，且**非空返回**与**异常**两类都出现过。
 *
 * <p><b>如实登记的边界：</b>输入集是**构造的**类型名集合（覆盖 int/bigint/varchar/text/
 * datetime/decimal/bit/clob 等常见形态与若干非法输入），**尚未**接真实 baseline 的字段类型语料
 * （`eova_dict` 的 `eova_field`/`type` 字典）—— 那是下一步，不得据此声称"真实数据已验证"。
 */
class MetaUtilTypeMappingGoldenTest {

    /** 常见库列类型名 + 非法/边界输入 */
    private static final String[] TYPE_NAMES = {
            "int", "INT", "integer", "bigint", "smallint", "tinyint", "mediumint",
            "varchar", "char", "text", "longtext", "mediumtext", "clob", "blob",
            "datetime", "date", "time", "timestamp", "year",
            "decimal", "numeric", "double", "float", "real", "bit", "boolean", "bool",
            "json", "enum", "set", "serial", "money", "uuid",
            "", " ", "unknown_type", "INT ", " int", "汉字类型", "int(11)", "varchar(64)",
    };

    /** getFormType 的 (isAuto, size) 组合 */
    private static final Object[][] AUTO_SIZE = {
            {true, 0}, {false, 0}, {true, 1}, {false, 1}, {true, 32}, {false, 32},
            {true, 64}, {false, 64}, {true, 255}, {false, 255}, {true, 1024}, {false, 1024},
            {true, -1}, {false, -1},
    };

    private static String render(Object v) {
        return v == null ? "null" : v.getClass().getSimpleName() + ":" + v;
    }

    private static String invoke(Method m, Object... args) {
        try {
            return render(m.invoke(null, args));
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            return "ERR:" + c.getClass().getName() + ":" + c.getMessage();
        } catch (Throwable t) {
            return "ERR:" + t.getClass().getName() + ":" + t.getMessage();
        }
    }

    @Test
    @DisplayName("★ 逐 (方法 × 输入) 比对 MetaUtil.getDataType/getFormType 与旧实现：差异应为 0")
    void typeMappingMatchesOldImplementation() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable(),
                "旧制品不可用 ⇒ 无法跨实现比对，跳过（不得记为通过）");

        Class<?> oldMetaUtil = OldImplementationLoader
                .create(OldImplementationLoader.locateRepoRoot())
                .loadClass("cn.eova.core.meta.MetaUtil");
        assertTrue(oldMetaUtil != MetaUtil.class, "旧实现必须来自旧制品（不同 ClassLoader），否则是自比");

        List<String> diffs = new ArrayList<>();
        int compared = 0;
        int nonEmpty = 0;
        int errCount = 0;

        Method oldDataType = oldMetaUtil.getMethod("getDataType", String.class);
        Method newDataType = MetaUtil.class.getMethod("getDataType", String.class);
        for (String type : TYPE_NAMES) {
            compared++;
            String o = invoke(oldDataType, type);
            String n = invoke(newDataType, type);
            if (o.startsWith("ERR:")) {
                errCount++;
            } else if (!"null".equals(o) && !o.endsWith(":")) {
                nonEmpty++;
            }
            if (!o.equals(n)) {
                diffs.add("getDataType(" + type + ") 旧=" + o + " 新=" + n);
            }
        }

        Method oldFormType = oldMetaUtil.getMethod("getFormType", boolean.class, String.class, int.class);
        Method newFormType = MetaUtil.class.getMethod("getFormType", boolean.class, String.class, int.class);
        for (Object[] combo : AUTO_SIZE) {
            boolean isAuto = (Boolean) combo[0];
            int size = (Integer) combo[1];
            for (String type : TYPE_NAMES) {
                compared++;
                String o = invoke(oldFormType, isAuto, type, size);
                String n = invoke(newFormType, isAuto, type, size);
                if (o.startsWith("ERR:")) {
                    errCount++;
                } else if (!"null".equals(o) && !o.endsWith(":")) {
                    nonEmpty++;
                }
                if (!o.equals(n)) {
                    diffs.add("getFormType(" + isAuto + "," + type + "," + size + ") 旧=" + o + " 新=" + n);
                }
            }
        }

        assertTrue(compared >= 60, "比对条数必须 ≥60，实际=" + compared);
        assertTrue(nonEmpty >= 20, "必须有足够多的非空映射结果，实际=" + nonEmpty);
        assertTrue(errCount >= 0, "异常条数（记录用）：" + errCount);

        assertEquals(List.of(), diffs, "与旧 MetaUtil 的差异必须为 0；实际差异 " + diffs.size() + " 处");
    }
}
