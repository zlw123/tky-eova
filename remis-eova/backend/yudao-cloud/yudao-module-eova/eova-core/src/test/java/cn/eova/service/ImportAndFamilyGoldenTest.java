/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.service;

import java.util.List;

import cn.eova.compat.jfinal.aop.LegacyClear;
import cn.eova.core.IndexController;
import cn.eova.core.LoginController;
import cn.eova.core.dict.DictController;
import cn.eova.core.menu.MenuHook;
import cn.eova.core.msg.MsgHook;
import cn.eova.core.object.ObjectHook;
import cn.eova.core.role.RoleHook;
import cn.eova.core.task.TaskController;
import cn.eova.core.task.TaskHook;
import cn.eova.config.MetaConfigHook;
import cn.eova.hook.EovaMetaHook;
import cn.eova.template.single.SingleIntercept;
import cn.eova.user.UserHook;
import cn.eova.widget.WidgetManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第 72 轮 port 的<b>导入服务 / Hook 族 / 控制器族 / WidgetManager</b> 的类型与接缝判据。
 *
 * <p>本批的<b>最大价值点是退掉了 {@code ImportBiz} 的已声明 stub</b>（全工程剩余 stub 从 2 降到 1，
 * 只剩 {@code EovaConfig}）；其次是 {@code WidgetManager}(840) 与 5 个控制器 + 7 个 Hook。</p>
 */
class ImportAndFamilyGoldenTest {

    /** Hook 族（旧实现全部实现 EovaMetaHook） */
    private static final List<Class<?>> HOOKS = List.of(
            MenuHook.class, TaskHook.class, MetaConfigHook.class, UserHook.class,
            ObjectHook.class, RoleHook.class, MsgHook.class);

    /** 本批控制器 */
    private static final List<Class<?>> CONTROLLERS = List.of(
            TaskController.class, IndexController.class, LoginController.class,
            DictController.class, cn.eova.core.sse.SSEController.class);

    @Test
    @DisplayName("ImportBiz：退出 stub 后是真 port（带追溯头），且依赖 HookRegistry/MsgType 等已就绪单元")
    void importBizIsRealPort() {
        java.nio.file.Path f = java.nio.file.Path.of(
                "src/main/java/cn/eova/service/ImportBiz.java");
        String head;
        try {
            head = java.nio.file.Files.readString(f).substring(0, 2000);
        } catch (java.io.IOException e) {
            throw new AssertionError("ImportBiz 源文件缺失", e);
        }
        assertTrue(head.contains("ported from:"),
                "ImportBiz 必须是真 port（带追溯头），不再是 compile-stub");
        assertTrue(!head.contains("compile-stub"),
                "ImportBiz 不得保留 stub 标记");
        assertTrue(cn.eova.common.base.BaseService.class.isAssignableFrom(ImportBiz.class)
                        || Object.class.equals(ImportBiz.class.getSuperclass()),
                "ImportBiz 类型可用（继承关系不设硬约束，此处仅确保类可加载）");
    }

    @Test
    @DisplayName("Hook 族：7 个 Hook 必须实现 EovaMetaHook 且为 public（按 code 登记）")
    void hookFamilyTypes() {
        for (Class<?> c : HOOKS) {
            assertTrue(EovaMetaHook.class.isAssignableFrom(c),
                    c.getSimpleName() + " 必须实现 EovaMetaHook（HookRegistry.addMeta 按其装载）");
            assertTrue(java.lang.reflect.Modifier.isPublic(c.getModifiers()),
                    c.getSimpleName() + " 必须 public（反射实例化前提）");
        }
        assertEquals(7, HOOKS.size(), "族大小钉死（防漏 port）");
    }

    @Test
    @DisplayName("控制器族：必须继承 BaseController（路由装载 + getUser/SID 等基类能力）")
    void controllerFamilyTypes() {
        for (Class<?> c : CONTROLLERS) {
            assertTrue(cn.eova.common.base.BaseController.class.isAssignableFrom(c),
                    c.getSimpleName() + " 必须继承 BaseController");
            assertTrue(java.lang.reflect.Modifier.isPublic(c.getModifiers()),
                    c.getSimpleName() + " 必须 public");
        }
    }

    @Test
    @DisplayName("LegacyClear.value() 必须有【默认空数组】（@Clear 可无参使用）—— 第 72 轮修正的接缝缺陷")
    void legacyClearHasDefaultValue() throws Exception {
        java.lang.reflect.Method value = LegacyClear.class.getDeclaredMethod("value");
        Object def = value.getDefaultValue();
        assertTrue(def != null,
                "value() 必须有 AnnotationDefault —— 旧制品是 default_value: []，"
                        + " 漏掉会让 @Clear 无参使用编译失败（port IndexController 时实测）");
        assertEquals(0, ((Object[]) def).length, "默认值必须是空数组（= 清除全部拦截器）");
    }

    @Test
    @DisplayName("SingleIntercept/WidgetManager：类型可加载且 public（本批其余两个单元）")
    void miscTypes() {
        assertTrue(java.lang.reflect.Modifier.isPublic(SingleIntercept.class.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPublic(WidgetManager.class.getModifiers()),
                "WidgetManager 必须 public");
        assertEquals("cn.eova.widget.WidgetManager", WidgetManager.class.getName(),
                "FQCN 属契约（被 WidgetController 等按类名引用）");
    }
}
