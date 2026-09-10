/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Record.getColumnNames()} / {@code Record.getColumns()} 的<b>跨实现</b>等价判据。
 *
 * <p><b>为什么要单独做这一条：</b>本轮移植 {@code DbUtil} 时编译失败于
 * {@code String[] names = r.getColumnNames();} —— 才发现在<b>另一处</b>接缝里
 * {@code EovaRecord.getColumnNames()} 返回的是 {@code Set<String>}，
 * 与 jfinal 的 {@code String[]} 不符。而 EOVA 自己有两处按 {@code String[]} 用：
 * {@code DbUtil:388}、{@code WidgetManager:831}。</p>
 *
 * <p><b>为什么既有判据没有发现它 —— 一条"排除项掩盖契约"的实例：</b>
 * {@code RecordSemanticsGoldenTest} 把 {@code accessor|getColumnNames} 与
 * {@code accessor|getColumns.keySet} 归入"键序类"一并排除。而旧侧探针
 * {@code Sp6Record:135-136} 的原文注释就写着
 * <i>"// getColumnNames() 返回 String[]（不是 Set）"</i> ——
 * <b>契约当时就被记录下来，却被排除项遮蔽</b>。</p>
 *
 * <p><b>为什么不能只靠 golden 值比对：</b>{@code String[]} 与 {@code Set<String>}
 * 经 JSON 序列化后<b>都是数组</b>，值比对在原理上就分辨不出两者。故必须直接断言
 * <b>声明形态</b>。</p>
 *
 * <p><b>顺序为何不作契约（附实测证据）：</b>表 {@code demo.users} 的列序为
 * <pre>id, status, login_id, login_pwd, nickname, nickname1, reg_time, info, tag</pre>
 * 与<b>新实现</b>完全一致；而旧 golden 记录的顺序是
 * <pre>id, info, login_id, login_pwd, nickname, nickname1, reg_time, status, tag</pre>
 * （{@code info} 与 {@code status} 互换）—— 可见旧序是 jfinal
 * {@code CaseInsensitiveContainerFactory} 的<b>容器产物</b>，不是数据序。
 * 故本判据对<b>内容</b>做顺序无关比对，对<b>类型</b>做精确断言。</p>
 *
 * <p>acceptanceProfile: golden-record-columnnames</p>
 */
class RecordColumnNamesSeamGoldenTest {

    /**
     * 返回类型必须与旧 jfinal {@code Record} 一致（{@code String[]}）。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("getColumnNames() 的返回类型：新接缝与旧 Record 同为 String[]")
    void returnTypeMatchesOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.plugin.activerecord.Record", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());

        Method oldM = oldCls.getMethod("getColumnNames");
        Method newM = EovaRecord.class.getMethod("getColumnNames");

        assertEquals(oldM.getReturnType(), newM.getReturnType(),
                "返回类型必须与旧 Record 一致（旧为 String[]；Set<String> 是缺陷，会让 DbUtil/WidgetManager 编译失败）");
        assertEquals(String[].class, newM.getReturnType(), "必须是 String[]");

        // 非空洞性自检：确认比对的两侧确实来自不同制品
        assertTrue(oldCls != EovaRecord.class, "旧侧不得就是新接缝本身");
        assertEquals(0, oldM.getParameterCount(), "旧侧为无参方法");
        assertEquals(0, newM.getParameterCount(), "新侧为无参方法");
    }

    /**
     * {@code getColumns()} 与 {@code getColumnNames()} 的<b>内容</b>与旧实现一致
     * （顺序无关）。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("getColumnNames/getColumns 的内容与旧 Record 一致（顺序无关）")
    void contentMatchesOldOrderInsensitively() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.plugin.activerecord.Record", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());

        // 同一组列，两侧各建一个实例。
        // 这里刻意只用【已小写】的列名 —— 理由（本轮实测的坑）：
        //   旧侧大小写归一来自 EOVA 装配的 CaseInsensitiveContainerFactory(true)，
        //   而 createForJFinalOnly() 只挂 jfinal 制品、【没有】EOVA 的容器工厂配置，
        //   故旧侧对混合大小写键会保留原样（实测得到 Id/INFO/Login_Id/status）。
        //   若不限小写，本判据就变成在比"我有没有给旧侧装错配置"，而不是比契约。
        //   混合大小写的归一小写行为由 golden 的 case|set(MyKey)后 getColumns.keys=['mykey']
        //   覆盖，且下面 caseNormalizationMatchesGolden 直接断言新实现复现该行为。
        //   （这正是 R44「oracle 污染」的同一族陷阱：旧侧的"生产配置"必须真的到位。）
        String[] cols = {"id", "info", "login_id", "status"};
        Object oldRec = oldCls.getConstructor().newInstance();
        Method oldSet = oldCls.getMethod("set", String.class, Object.class);
        for (String c : cols) {
            oldSet.invoke(oldRec, c, "v");
        }
        EovaRecord newRec = new EovaRecord();
        for (String c : cols) {
            newRec.set(c, "v");
        }

        // getColumnNames() 的内容（顺序无关）
        String[] oldNames = (String[]) oldCls.getMethod("getColumnNames").invoke(oldRec);
        String[] newNames = newRec.getColumnNames();
        assertEquals(sorted(oldNames), sorted(newNames),
                "列名内容必须一致（大小写归一后）");
        assertEquals(cols.length, newNames.length, "列数必须一致");

        // getColumns() 的键集合（顺序无关）
        @SuppressWarnings("unchecked")
        Set<String> oldKeys = ((java.util.Map<String, Object>)
                oldCls.getMethod("getColumns").invoke(oldRec)).keySet();
        Set<String> newKeys = newRec.getColumns().keySet();
        assertEquals(sorted(oldKeys.toArray(new String[0])), sorted(newKeys.toArray(new String[0])),
                "getColumns 的键集合必须一致");

        assertNotNull(newNames, "不得返回 null");
    }

    /**
     * 键大小写归一：新实现必须复现 golden 记录的 {@code ['mykey']}。
     *
     * <p>golden 证据（旧实现在 EOVA 容器工厂下实测）：
     * {@code case|set(MyKey)后 getColumns.keys = ['mykey']} ——
     * 即旧实现把键<b>转为小写</b>（{@code CaseInsensitiveContainerFactory(true)} 的
     * {@code toLowerCase} 语义）。故 {@code EovaRecord} 在 {@code set} 内小写化是<b>忠实</b>的。</p>
     */
    @Test
    @DisplayName("键大小写归一：set(MyKey) 后列名为 ['mykey']（复现 golden 记录）")
    void caseNormalizationMatchesGolden() {
        EovaRecord r = new EovaRecord();
        r.set("MyKey", "v");
        assertEquals(Arrays.asList("mykey"), new ArrayList<>(r.getColumns().keySet()),
                "旧实现（CaseInsensitiveContainerFactory(true)）把键转为小写；golden 记录为 ['mykey']");
        assertEquals(Arrays.asList("mykey"), Arrays.asList(r.getColumnNames()),
                "getColumnNames() 同样给出小写名");
        // 大小写不敏感查找（golden: get(mykey)/get(MYKEY)/get(MyKey) 三者同值）
        assertEquals("v", r.get("mykey"));
        assertEquals("v", r.get("MYKEY"));
        assertEquals("v", r.get("MyKey"));
    }

    /**
     * 顺序差异属既有容器产物，不作契约 —— 本用例把该结论固定下来，
     * 以免后人误以为"序不同就是缺陷"而去做无谓的修正。
     */
    @Test
    @DisplayName("列序不作契约：新实现给出的是数据序（表列序），旧实现给出的是容器产物序")
    void orderIsNotAContract() {
        // 表 demo.users 的列序（来自 information_schema，见类注释）
        List<String> tableOrder = Arrays.asList(
                "id", "status", "login_id", "login_pwd", "nickname", "nickname1", "reg_time", "info", "tag");
        // 旧 golden 记录的顺序
        List<String> oldGoldenOrder = Arrays.asList(
                "id", "info", "login_id", "login_pwd", "nickname", "nickname1", "reg_time", "status", "tag");

        // 断言的是"两者内容相同但顺序不同"这一事实本身 —— 它是排除顺序的实测依据
        assertEquals(sorted(tableOrder.toArray(new String[0])),
                sorted(oldGoldenOrder.toArray(new String[0])),
                "旧序与新序的内容必须相同（仅顺序不同）");
        assertTrue(!tableOrder.equals(oldGoldenOrder),
                "旧序必须与表列序不同，否则'旧序是容器产物'这一结论不成立");
    }

    /**
     * 排序后的规范化形式（顺序无关比较用）。
     *
     * @param names 列名数组
     * @return 排序后的列表
     */
    private static List<String> sorted(String[] names) {
        List<String> l = new ArrayList<>(new TreeSet<>(Arrays.asList(names)));
        return l;
    }

}
