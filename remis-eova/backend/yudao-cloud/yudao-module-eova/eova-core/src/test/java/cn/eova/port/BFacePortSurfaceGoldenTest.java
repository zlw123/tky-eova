/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.port;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>B 面决策 1 / 2 已 port 单元的声明面等价判据</b>。
 *
 * <p>覆盖 8 个单元：{@code cn.eova.common.utils.HttpUtils}、
 * {@code cn.eova.common.utils.web.RequestUtil}（决策 1：javax→jakarta），
 * {@code cn.eova.common.render.{RenderUtil,LogRender,XmlRender,DownloadRender,ZipRender,ResourceRender}}
 * （决策 2：Render 家族）。</p>
 *
 * <p><b>为什么"逐字节一致"还不够：</b>这 8 个单元都是<b>逐行对应 port</b>
 * （应用了已声明替换），而<b>替换点恰恰是最容易出错的地方</b>
 * （本轮实测：只替换 import 未替换裸名 {@code extends Render}，导致 4 个 render 编译失败）。
 * 逐字节复核只能证明"文件体 = 旧源 + 已应用替换"，不能证明"替换后的签名仍与旧实现等价"。
 * 故此处用反射逐项比对<b>声明面</b>，并把允许的差异<b>显式声明</b>出来。</p>
 *
 * <p><b>已声明的适配（非静默放宽）：</b>见 {@link #normalize} —— 仅做
 * ① 类型改名归一（{@code javax.servlet.*}→{@code jakarta.servlet.*}、
 * {@code com.jfinal.kit.Kv}→{@code LegacyKv} 等）与 ② 简单名化。
 * 未声明的差异一律失败。</p>
 *
 * <p>acceptanceProfile: golden-bface-surface</p>
 */
class BFacePortSurfaceGoldenTest {

    /** 本批覆盖的单元（新旧同 FQCN） */
    private static final List<String> UNITS = List.of(
            "cn.eova.common.utils.HttpUtils",
            "cn.eova.common.utils.web.RequestUtil",
            "cn.eova.common.render.RenderUtil",
            "cn.eova.common.render.LogRender",
            "cn.eova.common.render.XmlRender",
            "cn.eova.common.render.DownloadRender",
            "cn.eova.common.render.ZipRender",
            "cn.eova.common.render.ResourceRender");

    /**
     * 已声明适配：允许在【新实现侧】出现的差异。
     *
     * <p>键为单元 FQCN，值为 {@code "super"} 或 {@code "interfaces"}，
     * 对应"父类改变"与"实现接口集合改变"。这两项本轮必须声明，
     * 因为基类由 jfinal 的 {@code Render}/{@code HtmlRender} 换成了
     * {@code cn.eova.compat.render.LegacyRender}/{@code LegacyHtmlRender} ——
     * 这正是决策 2 的全部内容。</p>
     */
    private static final Set<String> DECLARED_SUPER_CHANGE = Set.of(
            "cn.eova.common.render.LogRender",
            "cn.eova.common.render.XmlRender",
            "cn.eova.common.render.DownloadRender",
            "cn.eova.common.render.ZipRender",
            "cn.eova.common.render.ResourceRender");

    /**
     * 类型改名归一表：旧类型全名 → 新类型全名。
     *
     * <p>口径：只归一"已声明的底座替换"，其余差异照旧报错。</p>
     */
    private static final List<String[]> TYPE_RENAMES = List.of(
            // 决策 1：Servlet 命名空间迁移（Spring Boot 3 强制 jakarta）
            new String[]{"javax.servlet.", "jakarta.servlet."},
            // 决策 2 / R37：宿主类等价物
            new String[]{"com.jfinal.render.RenderException", "cn.eova.compat.render.LegacyRenderException"},
            new String[]{"com.jfinal.render.HtmlRender", "cn.eova.compat.render.LegacyHtmlRender"},
            new String[]{"com.jfinal.render.Render", "cn.eova.compat.render.LegacyRender"},
            new String[]{"com.jfinal.kit.Kv", "cn.eova.compat.jfinal.kit.LegacyKv"},
            new String[]{"com.jfinal.kit.LogKit", "cn.eova.compat.jfinal.kit.LegacyLogKit"},
            new String[]{"com.jfinal.plugin.activerecord.Record", "cn.eova.db.EovaRecord"},
            new String[]{"com.jfinal.plugin.activerecord.Model", "cn.eova.db.EovaModel"});

    /**
     * 声明面逐项比对：8 个单元的方法/构造器签名集合差异应为 0。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("B 面 8 个单元的声明面与旧实现逐项一致（仅已声明适配）")
    void surfacesMatchOld() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable(),
                "旧 EOVA 产物不可用，跳过（golden: skipped）");

        ClassLoader oldLoader = OldImplementationLoader.createWithOldJFinal(
                OldImplementationLoader.locateRepoRoot());

        List<String> problems = new ArrayList<>();
        int totalOldMethods = 0;

        for (String fqcn : UNITS) {
            Class<?> oldCls;
            try {
                oldCls = Class.forName(fqcn, false, oldLoader);
            } catch (ClassNotFoundException e) {
                problems.add(fqcn + "：旧侧无法加载 —— " + e.getMessage());
                continue;
            }
            // 非空洞性自检（R33）：旧侧必须真的来自旧产物
            OldImplementationLoader.assertFromOldArtifacts(oldCls);

            Class<?> newCls = Class.forName(fqcn, false,
                    BFacePortSurfaceGoldenTest.class.getClassLoader());
            assertEquals(fqcn, newCls.getName());

            // ---- 父类 ----
            String oldSuper = normalize(oldCls.getSuperclass());
            String newSuper = normalize(newCls.getSuperclass());
            if (!oldSuper.equals(newSuper)) {
                if (DECLARED_SUPER_CHANGE.contains(fqcn)) {
                    assertTrue(true);
                } else {
                    problems.add(fqcn + "：父类差异未声明 " + oldSuper + " -> " + newSuper);
                }
            }

            // ---- 声明的构造器 ----
            Set<String> oldCtors = ctorSet(oldCls);
            Set<String> newCtors = ctorSet(newCls);
            if (!oldCtors.equals(newCtors)) {
                problems.add(fqcn + "：构造器差异\n    旧=" + oldCtors + "\n    新=" + newCtors);
            }

            // ---- 声明的方法 ----
            Set<String> oldMethods = methodSet(oldCls);
            Set<String> newMethods = methodSet(newCls);
            // 非空洞性护栏：两侧都空会让"集合相等"退化为恒真
            assertTrue(!oldMethods.isEmpty(), fqcn + "：旧侧声明方法为空，判据会退化为空洞");
            assertTrue(!newMethods.isEmpty(), fqcn + "：新侧声明方法为空，判据会退化为空洞");
            totalOldMethods += oldMethods.size();
            if (!oldMethods.equals(newMethods)) {
                Set<String> onlyOld = new TreeSet<>(oldMethods);
                onlyOld.removeAll(newMethods);
                Set<String> onlyNew = new TreeSet<>(newMethods);
                onlyNew.removeAll(oldMethods);
                problems.add(fqcn + "：方法差异\n    仅旧有=" + onlyOld + "\n    仅新有=" + onlyNew);
            }
        }

        assertTrue(problems.isEmpty(), "声明面差异必须为 0，实际：\n" + String.join("\n", problems));
        // 非空洞性护栏：8 个单元合计声明方法数必须达到已知下界（实测 40+，取下界 30）。
        // 若旧侧装载失败被误判成"两侧都空"，或 UNITS 被改小，这里会先报错。
        assertTrue(totalOldMethods >= 30,
                "旧侧声明方法合计 " + totalOldMethods + " 少于下界 30，判据可能已空洞");
    }

    /**
     * 渲染单元必须真的继承新接缝（决策 2 的核心断言；防止"看似 port 实则仍指 jfinal"）。
     */
    @Test
    @DisplayName("5 个 render 单元的父类是新接缝 LegacyRender/LegacyHtmlRender")
    void renderUnitsExtendSeam() throws Exception {
        Class<?> legacy = Class.forName("cn.eova.compat.render.LegacyRender");
        for (String fqcn : DECLARED_SUPER_CHANGE) {
            Class<?> c = Class.forName(fqcn, false,
                    BFacePortSurfaceGoldenTest.class.getClassLoader());
            assertTrue(legacy.isAssignableFrom(c),
                    fqcn + " 必须继承 cn.eova.compat.render.LegacyRender（实际父类 "
                            + c.getSuperclass().getName() + "）");
        }
    }

    /**
     * 决策 1 的非空洞断言：两个 servlet 工具单元不得残留 {@code javax.servlet} 引用。
     *
     * <p>做法：扫描新类<b>方法签名里的类型全名</b>，若出现 {@code javax.servlet}
     * 即失败。这是"import 替换漏改"的直接探测器。</p>
     */
    @Test
    @DisplayName("HttpUtils/RequestUtil 的签名中不得残留 javax.servlet")
    void noJavaxServletLeft() throws Exception {
        for (String fqcn : List.of("cn.eova.common.utils.HttpUtils",
                "cn.eova.common.utils.web.RequestUtil")) {
            Class<?> c = Class.forName(fqcn, false,
                    BFacePortSurfaceGoldenTest.class.getClassLoader());
            for (Method m : c.getDeclaredMethods()) {
                String sig = m.toString();
                assertTrue(!sig.contains("javax.servlet"),
                        fqcn + " 的签名残留 javax.servlet：" + sig);
            }
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                assertTrue(!k.toString().contains("javax.servlet"),
                        fqcn + " 的构造器残留 javax.servlet：" + k);
            }
        }
    }

    /**
     * 类型全名归一：先做已声明改名，再取简单名。
     *
     * @param t 类型
     * @return 归一后的文本
     */
    private static String normalize(Class<?> t) {
        if (t == null) {
            return "<none>";
        }
        String n = t.getName();
        for (String[] r : TYPE_RENAMES) {
            if (n.startsWith(r[0])) {
                n = r[1] + n.substring(r[0].length());
                break;
            }
        }
        // 数组类型保留维度
        int dim = 0;
        while (n.endsWith("[]")) {
            dim++;
            n = n.substring(0, n.length() - 2);
        }
        String simple = n.contains(".") ? n.substring(n.lastIndexOf('.') + 1) : n;
        return simple + "[]".repeat(dim);
    }

    /**
     * 渲染声明的方法集合：{@code 修饰符 返回类型 名称(参数类型)}。
     *
     * @param c 类
     * @return 集合
     */
    private static Set<String> methodSet(Class<?> c) {
        Set<String> out = new TreeSet<>();
        for (Method m : c.getDeclaredMethods()) {
            if (m.isSynthetic() || m.isBridge()) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(Modifier.toString(m.getModifiers() & Modifier.methodModifiers())).append(' ');
            sb.append(normalize(m.getReturnType())).append(' ');
            sb.append(m.getName()).append('(');
            Class<?>[] ps = m.getParameterTypes();
            for (int i = 0; i < ps.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(normalize(ps[i]));
            }
            sb.append(')');
            out.add(sb.toString());
        }
        return out;
    }

    /**
     * 渲染声明的构造器集合：{@code 修饰符 <init>(参数类型)}。
     *
     * @param c 类
     * @return 集合
     */
    private static Set<String> ctorSet(Class<?> c) {
        Set<String> out = new TreeSet<>();
        for (Constructor<?> k : c.getDeclaredConstructors()) {
            if (k.isSynthetic()) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(Modifier.toString(k.getModifiers() & Modifier.constructorModifiers())).append(' ');
            sb.append("<init>(");
            Class<?>[] ps = k.getParameterTypes();
            for (int i = 0; i < ps.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(normalize(ps[i]));
            }
            sb.append(')');
            out.add(sb.toString());
        }
        return out;
    }

}
