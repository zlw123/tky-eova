/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LegacyRoutes} 的跨实现判据（第 61 轮）。
 *
 * <p><b>为什么本判据可以做"活体比对"：</b>被比对的是 <b>jfinal 制品自身</b>
 * （{@code com.jfinal.config.Routes}），测试 classpath 上就是真实的 5.2.6 jar
 * —— 不涉及旧 EOVA 类，故不受 R38/R44 的<h>禁令</h>（那条禁令针对的是"挂载
 * jfinal 后旧 EOVA 类会静默返回错值"）。仍用 {@code assertFromJar} 显式断言
 * 来源，避免 classpath 顺序变了而比对退化成"新 vs 新"。</p>
 *
 * <p><b>为什么本判据住在 eova-db-adapter 而不是 eova-compat：</b>
 * {@code eova-compat} 的测试 classpath <b>不得</b>引入 jfinal 制品 ——
 * 它的 {@code OldImplementationLoader.create()}（子优先加载器）以测试 classpath 为父，
 * 一旦父上有 jfinal，旧 EOVA 类就会从"缺依赖而<b>响亮</b>失败"变成"可解析后
 * <b>静默返回错值</b>"（R38 实测，{@code IoUtilsGoldenTest.txtUtilBoundary} 正是为此而设）。
 * 而本判据需要<b>编译期</b>继承 {@code com.jfinal.config.Routes} 才能忠实驱动旧实现
 * （它是抽象类，无法实例化，反射也造不出子类）。
 * {@code eova-db-adapter} 已经因 {@code EovaModel} 的比对而声明了 jfinal（test 作用域、
 * <b>排在最前</b>）并接受了该模块的 classpath 口径，故此处是唯一不引入新风险的位置。</p>
 *
 * <p><b>比对方式：</b>两侧各跑同一串操作，把结果压成等长的字符串列表后逐条比对
 * （而不是逐字段 assert）—— 这样一旦有差异，失败信息里就能直接看到是哪一格的
 * 哪一侧不同，而不是"某字段 expected/actual 不符"。</p>
 */
class LegacyRoutesGoldenTest {

    /** 旧侧控制器（jfinal Controller 的子类，仅作路由登记用） */
    public static class OldCtrl extends com.jfinal.core.Controller {
    }

    /** 新侧控制器（LegacyController 的子类） */
    public static class NewCtrl extends cn.eova.compat.jfinal.core.LegacyController {
    }

    /** 旧侧 Routes 探针 */
    static class OldProbe extends com.jfinal.config.Routes {
        @Override
        public void config() {
            add("/inner", OldCtrl.class, "/innerView");
        }
    }

    /** 新侧 Routes 探针 */
    static class NewProbe extends LegacyRoutes {
        @Override
        public void config() {
            add("/inner", NewCtrl.class, "/innerView");
        }
    }

    /**
     * 旧侧一整轮操作的结果快照。
     *
     * @return 快照（每格一项）
     */
    private static List<String> oldSnapshot() {
        com.jfinal.config.Routes r = new OldProbe();
        List<String> out = new ArrayList<>();

        // ① add(path, cls)：viewPath 默认取 path —— 旧字节码是 add(path, cls, path)
        r.add("/widget", OldCtrl.class);
        out.add("item.viewPath=" + r.getRouteItemList().get(0).getFinalViewPath(""));
        out.add("item.controllerPath=" + r.getRouteItemList().get(0).getControllerPath());
        out.add("item.controllerKey=" + r.getRouteItemList().get(0).getControllerKey());

        // ② add(path, cls, viewPath)：viewPath 归一为 "/index/"
        r.add("/mod", OldCtrl.class, "index");
        out.add("item2.finalViewPath=" + r.getRouteItemList().get(1).getFinalViewPath("/x"));
        out.add("item2.finalViewPath.null=" + r.getRouteItemList().get(1).getFinalViewPath(null));

        // ③ 归一与校验
        r.add("  /api/meta  ", OldCtrl.class, "  meta  ");
        out.add("item3.controllerPath=" + r.getRouteItemList().get(2).getControllerPath());
        out.add("item3.finalViewPath=" + r.getRouteItemList().get(2).getFinalViewPath(""));
        out.add("blank=" + messageOf(() -> r.add("", OldCtrl.class, "/")));
        out.add("nullClass=" + messageOf(() -> r.add("/x", null, "/")));
        out.add("blankView=" + r.getRouteItemList().size());

        // ④ 拦截器：空数组 -> 有内容 -> 顺序
        out.add("inters.empty=" + r.getInterceptors().length);
        r.addInterceptor((inv) -> {
        });
        r.addInterceptor((inv) -> {
        });
        out.add("inters.size=" + r.getInterceptors().length);

        // ⑤ baseViewPath 归一（去尾部斜杠）与校验
        r.setBaseViewPath("_mod/abc");
        out.add("baseViewPath1=" + r.getBaseViewPath());
        r.setBaseViewPath("/_mod/abc/");
        out.add("baseViewPath2=" + r.getBaseViewPath());
        out.add("baseViewBlank=" + messageOf(() -> r.setBaseViewPath("  ")));

        // ⑥ mappingSuperClass 三态
        out.add("msc.default=" + r.getMappingSuperClass());
        r.setMappingSuperClass(true);
        out.add("msc.true=" + r.getMappingSuperClass());

        // ⑦ add(Routes)：调用其 config()、继承 mappingSuperClass、登记静态列表
        com.jfinal.config.Routes child = new OldProbe();
        out.add("before.add=" + r.getRoutesList().size());
        r.add(child);
        out.add("after.add=" + r.getRoutesList().size());
        out.add("child.msc=" + child.getMappingSuperClass());
        out.add("child.items=" + child.getRouteItemList().size());
        out.add("static.lastIsChild=" + (r.getRoutesList().get(r.getRoutesList().size() - 1) == child));

        // ⑧ getRouteItemList 返回内部可变实例
        out.add("list.same=" + (r.getRouteItemList() == r.getRouteItemList()));
        int size = r.getRouteItemList().size();
        r.getRouteItemList().add(new com.jfinal.config.Routes.Route("/direct", OldCtrl.class, "/"));
        out.add("list.mutable=" + (r.getRouteItemList().size() == size + 1));

        // ⑨ clear()：clearAfterMapping=false 时必须什么都不做
        r.clear();
        out.add("clear.noop=" + (r.getRouteItemList() != null) + "," + (r.getRoutesList() != null));

        // ⑩ clear()：开关为真时静态列表被置 null（既有语义）
        r.setClearAfterMapping(true);
        r.clear();
        out.add("clear.after.routeItemList=" + (r.getRouteItemList() == null));
        out.add("clear.after.baseViewPath=" + (r.getBaseViewPath() == null));
        out.add("clear.after.routesList=" + (com.jfinal.config.Routes.getRoutesList() == null));
        restoreOldRoutesList();
        return out;
    }

    /**
     * 新侧一整轮操作的结果快照（步骤与 {@link #oldSnapshot()} 逐条对应）。
     *
     * @return 快照（每格一项）
     */
    private static List<String> newSnapshot() {
        LegacyRoutes r = new NewProbe();
        List<String> out = new ArrayList<>();

        r.add("/widget", NewCtrl.class);
        out.add("item.viewPath=" + r.getRouteItemList().get(0).getFinalViewPath(""));
        out.add("item.controllerPath=" + r.getRouteItemList().get(0).getControllerPath());
        out.add("item.controllerKey=" + r.getRouteItemList().get(0).getControllerKey());

        r.add("/mod", NewCtrl.class, "index");
        out.add("item2.finalViewPath=" + r.getRouteItemList().get(1).getFinalViewPath("/x"));
        out.add("item2.finalViewPath.null=" + r.getRouteItemList().get(1).getFinalViewPath(null));

        r.add("  /api/meta  ", NewCtrl.class, "  meta  ");
        out.add("item3.controllerPath=" + r.getRouteItemList().get(2).getControllerPath());
        out.add("item3.finalViewPath=" + r.getRouteItemList().get(2).getFinalViewPath(""));
        out.add("blank=" + messageOf(() -> r.add("", NewCtrl.class, "/")));
        out.add("nullClass=" + messageOf(() -> r.add("/x", null, "/")));
        out.add("blankView=" + r.getRouteItemList().size());

        out.add("inters.empty=" + r.getInterceptors().length);
        r.addInterceptor((inv) -> {
        });
        r.addInterceptor((inv) -> {
        });
        out.add("inters.size=" + r.getInterceptors().length);

        r.setBaseViewPath("_mod/abc");
        out.add("baseViewPath1=" + r.getBaseViewPath());
        r.setBaseViewPath("/_mod/abc/");
        out.add("baseViewPath2=" + r.getBaseViewPath());
        out.add("baseViewBlank=" + messageOf(() -> r.setBaseViewPath("  ")));

        out.add("msc.default=" + r.getMappingSuperClass());
        r.setMappingSuperClass(true);
        out.add("msc.true=" + r.getMappingSuperClass());

        LegacyRoutes child = new NewProbe();
        out.add("before.add=" + r.getRoutesList().size());
        r.add(child);
        out.add("after.add=" + r.getRoutesList().size());
        out.add("child.msc=" + child.getMappingSuperClass());
        out.add("child.items=" + child.getRouteItemList().size());
        out.add("static.lastIsChild=" + (r.getRoutesList().get(r.getRoutesList().size() - 1) == child));

        out.add("list.same=" + (r.getRouteItemList() == r.getRouteItemList()));
        int size = r.getRouteItemList().size();
        r.getRouteItemList().add(new LegacyRoutes.Route("/direct", NewCtrl.class, "/"));
        out.add("list.mutable=" + (r.getRouteItemList().size() == size + 1));

        r.clear();
        out.add("clear.noop=" + (r.getRouteItemList() != null) + "," + (r.getRoutesList() != null));

        r.setClearAfterMapping(true);
        r.clear();
        out.add("clear.after.routeItemList=" + (r.getRouteItemList() == null));
        out.add("clear.after.baseViewPath=" + (r.getBaseViewPath() == null));
        out.add("clear.after.routesList=" + (LegacyRoutes.getRoutesList() == null));
        LegacyRoutes.restoreRoutesListForTest();
        return out;
    }

    /**
     * 取抛错消息（不抛则给 {@code <none>}）。
     *
     * @param body 待执行体
     * @return 消息文本
     */
    private static String messageOf(Runnable body) {
        try {
            body.run();
            return "<none>";
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + ":" + e.getMessage();
        }
    }

    /**
     * 复原旧侧静态 routesList（避免本用例的 clear() 影响同 JVM 的其他用例）。
     */
    private static void restoreOldRoutesList() {
        try {
            Field f = com.jfinal.config.Routes.class.getDeclaredField("routesList");
            f.setAccessible(true);
            f.set(null, new ArrayList<>());
        } catch (Exception e) {
            throw new IllegalStateException("无法复原旧侧 routesList", e);
        }
    }

    @Test
    @DisplayName("LegacyRoutes：与 jfinal 5.2.6 真制品逐格对照（28 格）")
    void matchesOldJFinalRoutes() throws Exception {
        // 自校验：确认比对的确实是 jfinal 5.2.6 制品，而不是被别处的同名类顶替
        OldImplementationLoader.assertFromJar(com.jfinal.config.Routes.class,
                OldImplementationLoader.oldJFinalJar());

        List<String> old = oldSnapshot();
        List<String> now = newSnapshot();
        assertEquals(old.size(), now.size(), "两侧格数必须一致（防某一侧少跑步骤）");
        assertTrue(old.size() >= 28, "格数下限（防判据空洞）—— 实测 28 格，改小须说明理由，实际 " + old.size());
        for (int i = 0; i < old.size(); i++) {
            assertEquals(old.get(i), now.get(i),
                    "第 " + i + " 格不一致（左=旧 jfinal，右=LegacyRoutes）");
        }
    }

    @Test
    @DisplayName("LegacyRoutes：空拦截器返回共享常量 NULL_INTERS；非空返回新数组")
    void interceptorsFollowOldContract() {
        LegacyRoutes r = new NewProbe();
        assertSame(LegacyRoutes.NULL_INTERS, r.getInterceptors(),
                "空时必须返回共享常量（旧为 InterceptorManager.NULL_INTERS）");
        assertEquals(0, r.getInterceptors().length);

        LegacyInterceptor a = (inv) -> {
        };
        r.addInterceptor(a);
        LegacyInterceptor[] arr = r.getInterceptors();
        assertSame(a, arr[0], "顺序必须与加入顺序一致");
        assertNotNull(arr);
        // 非空时是【新数组】：改它不应影响内部列表
        arr[0] = null;
        assertSame(a, r.getInterceptors()[0], "getInterceptors 非空时必须返回副本");
    }

    /**
     * <b>覆盖断言</b>：本接缝实现的方法集必须覆盖 EOVA 全树对 {@code Routes} 的实际调用。
     *
     * <p>普查方式是"<b>有意取超集</b>"：凡文件中出现 Routes 系方法名 + {@code (} 即计入，
     * 宁可多实现一个方法，也不能少 —— 少了会在 port 业务单元时才发现，代价更高。</p>
     *
     * @throws Exception 读文件失败
     */
    @Test
    @DisplayName("覆盖断言：实现集必须覆盖全树对 Routes 的实际调用（scan 登记为已声明待办）")
    void routesMethodCoverage() throws Exception {
        java.nio.file.Path base = OldImplementationLoader.locateRepoRoot()
                .resolve("meta-eova/eova/core/src/main/java/cn/eova");
        java.nio.file.Path demo = OldImplementationLoader.locateRepoRoot()
                .resolve("meta-eova/eova/demo/src/main/java/cn/eova");
        assertTrue(java.nio.file.Files.isDirectory(base), "旧源码目录缺失：" + base);

        // 旧 Routes 的方法名（含 protected）
        java.util.Set<String> all = new java.util.TreeSet<>();
        for (java.lang.reflect.Method m : com.jfinal.config.Routes.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPrivate(m.getModifiers())) {
                all.add(m.getName());
            }
        }
        // 16 个非私有方法，按【名字】去重后 13 个（add 有 3 个重载、scan 有 2 个）
        assertTrue(all.size() >= 13, "应取到 ≥13 个 Routes 方法名，实际 " + all.size());

        // 只扫"与 Routes 相关的文件"：继承 Routes/WebRoutes 的、或 EovaConfig 这类装配点
        java.util.Set<String> used = new java.util.TreeSet<>();
        int files = 0;
        for (java.nio.file.Path root : List.of(base, demo)) {
            try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root)) {
                for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) walk
                        .filter(x -> x.toString().endsWith(".java"))::iterator) {
                    String src = java.nio.file.Files.readString(p);
                    // 过滤口径：只要源文件提到 "Routes"（含 EovaModRoutes/EovaWebRoutes 这类
                    // 间接使用 —— EovaModPlugin 就是这样被漏掉过一次），就纳入普查。
                    if (!src.contains("Routes")) {
                        continue;
                    }
                    files++;
                    for (String n : all) {
                        if (src.contains(n + "(")) {
                            used.add(n);
                        }
                    }
                }
            }
        }
        assertTrue(files >= 4, "应扫描到 ≥4 个与 Routes 相关的文件，实际 " + files);

        // 我方实现集
        java.util.Set<String> impl = new java.util.TreeSet<>();
        for (java.lang.reflect.Method m : LegacyRoutes.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPrivate(m.getModifiers()) && !m.isSynthetic()
                    && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                impl.add(m.getName());
            }
        }
        impl.add("config"); // 抽象方法：实现落在子类

        java.util.Set<String> missing = new java.util.TreeSet<>(used);
        missing.removeAll(impl);
        missing.removeAll(DECLARED_PENDING.keySet());
        assertTrue(missing.isEmpty(),
                "以下 Routes 方法在全树被调用但本接缝未实现，且未登记为已声明待办：\n  " + missing
                        + "\n（若依赖未 port 的底座，请登记到 DECLARED_PENDING 并写明阻塞它的接缝）");
        // 实测普查集恰为 7 个：add / addInterceptor / config / getInterceptors /
        // getRouteItemList / setBaseViewPath / setMappingSuperClass（与手工读码一致，互为交叉验证）。
        // 另有 getMappingSuperClass / getBaseViewPath / setClearAfterMapping / clear /
        // getRoutesList / scan 属"接缝保真但全树未调用" —— 保真是为将来 mod 与集成期不掉坑。
        assertTrue(used.size() >= 7,
                "普查集应 ≥7 个方法（防判据空洞），实际 " + used.size() + "：" + used);
    }

    /**
     * <b>已声明待办</b>的 Routes 方法：全树被调用、但本接缝有意不实现。
     *
     * <p>每项必须写明理由，防止它变成"永远不做的借口"。</p>
     */
    private static final java.util.Map<String, String> DECLARED_PENDING = java.util.Map.of(
            "scan", "类路径扫描：需复刻 jfinal 的目录/jar 遍历与包名匹配；EOVA 全树调用数为 0，"
                    + "故登记为独立单元而非在本接缝留空壳");
}
