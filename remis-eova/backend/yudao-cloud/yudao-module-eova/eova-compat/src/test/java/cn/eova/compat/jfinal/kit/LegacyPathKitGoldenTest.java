/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **T04 第二段判据：{@code LegacyPathKit} 与旧 {@code com.jfinal.kit.PathKit} 行为等价**。
 *
 * <p>判据口径：**不写死期望值**，而是在**同一环境**里同时调用新旧两边比结果 ——
 * 这样"默认 web 根怎么推导"这类环境相关语义也能被钉住（写死字符串在别的机器/别的构建方式下就会假红或假绿）。</p>
 *
 * <p>覆盖旧 {@code PathKit} 里**生产代码实际用到**的四个成员（其余如 {@code getPath(Class)} 对 JDK 类会
 * NPE，属易碎面、生产代码未用 ⇒ 不 port 也不判）。</p>
 */
class LegacyPathKitGoldenTest {

    @Test
    @DisplayName("★ T04-4：默认值等价（web 根、classpath 根）—— 标为易碎面的 getPath(Class) 不 port")
    void defaultsMatchOldPathKit() {
        assertEquals(com.jfinal.kit.PathKit.getWebRootPath(), LegacyPathKit.getWebRootPath(),
                "★ 默认 web 根必须与旧实现一致（旧口径：classpath 根的祖父目录）");
        assertEquals(com.jfinal.kit.PathKit.getRootClassPath(), LegacyPathKit.getRootClassPath(),
                "★ 默认 classpath 根必须与旧实现一致");
        // 非空断言：两边都为 null 时上一行会"假绿"
        assertFalse(LegacyPathKit.getWebRootPath() == null || LegacyPathKit.getWebRootPath().isEmpty(),
                "默认 web 根不得为空");
        assertTrue(new File(LegacyPathKit.getRootClassPath()).isAbsolute(), "classpath 根应为绝对路径");
    }

    @Test
    @DisplayName("★ T04-5：setter 语义等价（旧实现是可变静态值）")
    void settersMatchOldPathKit() {
        String oldWeb = com.jfinal.kit.PathKit.getWebRootPath();
        String oldRoot = com.jfinal.kit.PathKit.getRootClassPath();
        String newWeb = LegacyPathKit.getWebRootPath();
        String newRoot = LegacyPathKit.getRootClassPath();
        try {
            com.jfinal.kit.PathKit.setWebRootPath("/tmp/probe-webroot");
            LegacyPathKit.setWebRootPath("/tmp/probe-webroot");
            assertEquals(com.jfinal.kit.PathKit.getWebRootPath(), LegacyPathKit.getWebRootPath(),
                    "★ setWebRootPath 之后两边必须一致");

            com.jfinal.kit.PathKit.setRootClassPath("/tmp/probe-classpath");
            LegacyPathKit.setRootClassPath("/tmp/probe-classpath");
            assertEquals(com.jfinal.kit.PathKit.getRootClassPath(), LegacyPathKit.getRootClassPath(),
                    "★ setRootClassPath 之后两边必须一致");
        } finally {
            // 静态值必须复原：同批其它判据（含真浏览器/HTTP 面）依赖默认 web 根
            com.jfinal.kit.PathKit.setWebRootPath(oldWeb);
            LegacyPathKit.setWebRootPath(newWeb);
            com.jfinal.kit.PathKit.setRootClassPath(oldRoot);
            LegacyPathKit.setRootClassPath(newRoot);
        }
        assertEquals(oldWeb, LegacyPathKit.getWebRootPath(), "复原后应与原值相同");
    }

    @Test
    @DisplayName("★ T04-6：getPackagePath 等价（已删除的 ResourceRender 曾靠它拼 resources/ 路径）")
    void packagePathMatchesOldPathKit() {
        for (Object o : new Object[]{this, "x", new Object()}) {
            assertEquals(com.jfinal.kit.PathKit.getPackagePath(o), LegacyPathKit.getPackagePath(o),
                    "★ getPackagePath 必须与旧实现一致（对象=" + o.getClass().getName() + "）");
        }
        // 契约要点：**无首尾斜杠**（调用方自己拼 "/resources/"）。
        // ⚠️ 必须传**实例**：传 `X.class` 时 `getClass()` 是 `Class` ⇒ 两边都会给 `java/lang`
        //    （判据首版就踩了这个：写死断言与传参不匹配 ⇒ 假红）。
        assertEquals("cn/eova/compat/jfinal/kit", LegacyPathKit.getPackagePath(this));
        assertFalse(LegacyPathKit.getPackagePath(this).startsWith("/"), "不得以 / 开头");
        assertFalse(LegacyPathKit.getPackagePath(this).endsWith("/"), "不得以 / 结尾");
    }

    @Test
    @DisplayName("★ T04-7：isAbsolutePath 等价（含 `C:` 这种边界；防「只测 / 开头」的假等价）")
    void isAbsolutePathMatchesOldPathKit() {
        // `"C:"` 这条是本判据当场抓出来的：旧实现的规则是"以 / 开头 或 下标 1 是冒号"，
        // 首版实现写成"须有 X:\ 三段式" ⇒ 判据立刻报不等价。
        for (String p : new String[]{"/a/b", "a/b", "C:\\a", "C:/a", "C:", "", "//server/share", "~/"}) {
            assertEquals(com.jfinal.kit.PathKit.isAbsolutePath(p), LegacyPathKit.isAbsolutePath(p),
                    "★ isAbsolutePath(" + p + ") 必须与旧实现一致");
        }
        assertTrue(LegacyPathKit.isAbsolutePath("C:"), "`C:` 在旧实现里是绝对路径（下标 1 是冒号）");
        // ★ 已声明的差异：null 入参旧实现 NPE，本 port 返回 false（生产无调用点 ⇒ 取更稳的语义）
        assertFalse(LegacyPathKit.isAbsolutePath(null), "null ⇒ false（旧实现会 NPE，属已声明差异）");
    }

    @Test
    @DisplayName("★ T04-8：**web 根必须与 Enjoy 侧保持同步**（否则本仓读默认值、Enjoy 读宿主值）")
    void webRootStaysInSyncWithEnjoyPathKit() throws Exception {
        // 旧栈里 PathKit.setWebRootPath 同时喂两边：本仓代码 + **Enjoy 内部**（FileSource/Engine 也读它）
        // ⇒ 在 enjoy 退役前，设置点必须两边同时设；本判据钉住"设置之后两边一致"。
        // ★ r309 说明：生产代码里做这件事的那处（`EnjoyTemplateRenderService` 构造器）已删除，
        //   自此**没有任何生产设置点**（本判据因此只剩"port 本身不失同步"的运行时探针作用）。
        com.jfinal.kit.PathKit.setWebRootPath("/tmp/sync-probe");
        LegacyPathKit.setWebRootPath("/tmp/sync-probe");
        assertEquals(com.jfinal.kit.PathKit.getWebRootPath(), LegacyPathKit.getWebRootPath(),
                "★ 设置点必须两边同时设 —— 否则 Enjoy 与本仓会读不同的 web 根（静默分歧）");
        // 复原
        com.jfinal.kit.PathKit.setWebRootPath(null);
        LegacyPathKit.setWebRootPath(null);
    }
}
