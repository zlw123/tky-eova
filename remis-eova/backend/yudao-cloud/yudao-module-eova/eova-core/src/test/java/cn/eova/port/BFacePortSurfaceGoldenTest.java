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
            "cn.eova.common.render.ResourceRender",
            // 第二轮：Excel 导出链 + HTML/Office 渲染（同样是逐行对应 port）
            "cn.eova.common.render.Html2DocRender",
            "cn.eova.common.render.Html2PdfRender",
            "cn.eova.common.render.Html2XlsRender",
            "cn.eova.common.render.OfficeRender",
            "cn.eova.common.render.CsvRender",
            "cn.eova.common.render.XlsxRender",
            "cn.eova.common.utils.excel.ExceUtil",
            // 第三轮：MetaField 链（xx -> EovaExpBuilder -> MetaField）
            "cn.eova.common.utils.xx",
            "cn.eova.engine.EovaExpBuilder",
            "cn.eova.model.MetaField",
            // 第四轮：零决策、零新接缝的 6 个单元（宿主依赖全在已有能力内）
            "cn.eova.sql.dql.dialect.QueryDialect",
            "cn.eova.core.object.config.MetaObjectConfig",
            "cn.eova.mod.EovaModClassLoader",
            "cn.eova.core.meta.MetaEngine",
            "cn.eova.service.FileService",
            "cn.eova.plugin.cron4j.DemoTask",
            // 第五轮：依赖【已声明 stub】EovaConfig 的薄边单元
            "cn.eova.common.utils.io.ClassUtil",
            "cn.eova.auth.AuthUri",
            "cn.eova.mod.emi.EMILoader");

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
            "cn.eova.common.render.ResourceRender",
            "cn.eova.common.render.Html2DocRender",
            "cn.eova.common.render.Html2PdfRender",
            "cn.eova.common.render.Html2XlsRender",
            "cn.eova.common.render.OfficeRender",
            "cn.eova.common.render.CsvRender",
            "cn.eova.common.render.XlsxRender");

    /**
     * {@link #renderUnitsExtendSeam} 覆盖的渲染类集合。</p>
     *
     * <p>它与 {@link #DECLARED_SUPER_CHANGE} 当前<b>内容相同但语义不同</b>：
     * 前者问"这些类的父类是否真的换成了接缝"，后者是"允许父类差异"的白名单。
     * 二者都只应收录 render 包内的类 ——
     * {@code ExceUtil} / {@code xx} / {@code EovaExpBuilder} / {@code MetaField}
     * <b>不在其中</b>（它们父类未变，既不需要声明差异、也不该被要求继承 Render）。
     *
     * <p>本常量之所以独立存在：曾用同一集合兼任两职，结果非渲染类被卷进
     * "必须继承 LegacyRender"的断言而误报 —— 拆开后该错误不可再犯。</p>
     */
    private static final List<String> SEAM_RENDER_UNITS = List.of(
            "cn.eova.common.render.LogRender",
            "cn.eova.common.render.XmlRender",
            "cn.eova.common.render.DownloadRender",
            "cn.eova.common.render.ZipRender",
            "cn.eova.common.render.ResourceRender",
            "cn.eova.common.render.Html2DocRender",
            "cn.eova.common.render.Html2PdfRender",
            "cn.eova.common.render.Html2XlsRender",
            "cn.eova.common.render.OfficeRender",
            "cn.eova.common.render.CsvRender",
            "cn.eova.common.render.XlsxRender");

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
        int totalOldFields = 0;

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

            // ---- 声明的字段（含静态常量【取值】）----
            // 为什么必须比字段：MetaField 有 TYPE_JSON 等对外常量，EovaConfig 有 EOVA_DBTYPE 等，
            // 它们都是契约；只比方法会漏掉"常量被改值"这类漂移。
            Set<String> oldFields = fieldSet(oldCls);
            Set<String> newFields = fieldSet(newCls);
            if (!oldFields.equals(newFields)) {
                Set<String> onlyOldF = new TreeSet<>(oldFields);
                onlyOldF.removeAll(newFields);
                Set<String> onlyNewF = new TreeSet<>(newFields);
                onlyNewF.removeAll(oldFields);
                problems.add(fqcn + "：字段差异\n    仅旧有=" + onlyOldF + "\n    仅新有=" + onlyNewF);
            }
            totalOldFields += oldFields.size();

            // ---- 声明的方法 ----
            Set<String> oldMethods = methodSet(oldCls);
            Set<String> newMethods = methodSet(newCls);
            // 注意：此处【不】做逐单元"方法非空"断言 —— FileService 这类叶子类
            // （裸 extends BaseService，18 行）本身不声明任何方法，逐单元非空会误报。
            // 空洞风险改由下方的【聚合下界】统一兜住。
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
        // 实测 25 个单元合计 160+，下界取 150（随单元增加而上调，防止误判空洞）
        assertTrue(totalOldMethods >= 150,
                "旧侧声明方法合计 " + totalOldMethods + " 少于下界 150，判据可能已空洞");
        // 字段护栏用【聚合下界】而非"逐单元非空"：RequestUtil / ExceUtil 这类纯静态工具类
        // 本就没有声明字段，要求"每单元都有字段"会误报（该误报本次已被护栏自己抓到）。
        assertTrue(totalOldFields >= 15,
                "旧侧声明字段合计 " + totalOldFields + " 少于下界 15，字段判据可能已空洞");
    }

    /**
     * 渲染单元必须真的继承新接缝（决策 2 的核心断言；防止"看似 port 实则仍指 jfinal"）。
     */
    @Test
    @DisplayName("5 个 render 单元的父类是新接缝 LegacyRender/LegacyHtmlRender")
    void renderUnitsExtendSeam() throws Exception {
        Class<?> legacy = Class.forName("cn.eova.compat.render.LegacyRender");
        for (String fqcn : SEAM_RENDER_UNITS) {
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
     * 对<b>泛型类型名字符串</b>施加已声明改名（{@link #TYPE_RENAMES}）。
     *
     * <p>与 {@link #normalize(Class)} 的差别：本方法处理的是
     * {@code java.util.List<cn.eova.db.EovaRecord>} 这类含泛型参数与数组维度的文本，
     * 故用带尾部边界的正则做全名替换 —— 边界 {@code (?!\w|\$)} 保证
     * {@code com.jfinal.render.Render} <b>不会</b>误伤
     * {@code com.jfinal.render.RenderException}。</p>
     *
     * @param typeName 泛型类型名
     * @return 归一后的文本
     */
    private static String normalizeTypeName(String typeName) {
        String s = typeName;
        for (String[] r : TYPE_RENAMES) {
            // 尾部边界只在【键以词字符结尾】时才加：键是类全名（如 com.jfinal.render.Render）
            // 时必须防误伤同前缀类（RenderException）；
            // 但键是【包前缀】（如 javax.servlet.）时它本身以 '.' 结尾，
            // 再加 (?!\w) 会让 javax.servlet.http.* 永远匹配不上（实测正是这个坑）。
            char last = r[0].charAt(r[0].length() - 1);
            String tail = (Character.isLetterOrDigit(last) || last == '_' || last == '$')
                    ? "(?!\\w|\\$)" : "";
            s = s.replaceAll(java.util.regex.Pattern.quote(r[0]) + tail,
                    java.util.regex.Matcher.quoteReplacement(r[1]));
        }
        return s;
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
            // 用【泛型】签名而非擦除后的 Class：getReturnType() 会把 List<EovaRecord>
            // 与裸 List 都擦成 java.util.List，导致"泛型参数被悄悄抹掉"这类漂移静默通过。
            // 实测该漏洞由变异测试发现（把 buildFixedItem 的 List<EovaRecord> 改成裸 List 竟然为绿）。
            sb.append(normalizeTypeName(m.getGenericReturnType().getTypeName())).append(' ');
            sb.append(m.getName()).append('(');
            java.lang.reflect.Type[] ps = m.getGenericParameterTypes();
            for (int i = 0; i < ps.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(normalizeTypeName(ps[i].getTypeName()));
            }
            sb.append(')');
            out.add(sb.toString());
        }
        return out;
    }

    /**
     * 渲染声明的字段集合：{@code 修饰符 类型 名称[=常量值]}。
     *
     * <p>静态常量的<b>取值</b>参与比对 —— 常量值属对外契约，
     * "只比名字不比值"会漏掉静默改值。</p>
     *
     * @param c 类
     * @return 集合
     */
    private static Set<String> fieldSet(Class<?> c) {
        Set<String> out = new TreeSet<>();
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            if (f.isSynthetic()) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(Modifier.toString(f.getModifiers() & Modifier.fieldModifiers())).append(' ');
            sb.append(normalize(f.getType())).append(' ').append(f.getName());
            if (Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(null);
                    if (v != null && (v instanceof String || v instanceof Number
                            || v instanceof Boolean || v instanceof Character)) {
                        sb.append(" = ").append(v);
                    }
                } catch (Throwable ignored) {
                    // 取值不可达（如静态初始化依赖宿主）时只比签名，不报错
                }
            }
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
            java.lang.reflect.Type[] ps = k.getGenericParameterTypes();
            for (int i = 0; i < ps.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(normalizeTypeName(ps[i].getTypeName()));
            }
            sb.append(')');
            out.add(sb.toString());
        }
        return out;
    }

}
