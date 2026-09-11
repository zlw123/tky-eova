/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code biz}(40) 与 {@code sm}(15)（第 66 轮 port）的判据。
 *
 * <p><b>这是"60 单元核心块"的第一步。</b>该块（60 单元 / 10,256 行）每个成员都传递依赖
 * 其余成员，无法逐单元落地，故本轮 port 块内可独立成立的一小部分
 * （{@code biz}/{@code sm}），其余块内依赖以<b>已声明 stub</b>支撑（{@code ImportBiz}/
 * {@code MetaService}）—— stub 清单由工具在每次 {@code --verify} 打印且不计入进度。</p>
 */
class BizServiceRegistryGoldenTest {

    @Test
    @DisplayName("biz.init()：6 个静态服务字段全部被填充，且类型与字段契约一致")
    void initPopulatesAllServiceFields() {
        biz.init();

        assertTrue(biz.login instanceof LoginService, "login 必须是 LoginService");
        assertTrue(biz.auth instanceof AuthService, "auth 必须是 AuthService");
        assertTrue(biz.meta instanceof MetaService, "meta 必须是 MetaService（当前为已声明 stub）");
        assertTrue(biz.form instanceof FormService, "form 必须是 FormService");
        assertTrue(biz.imports instanceof ImportBiz, "imports 必须是 ImportBiz（当前为已声明 stub）");
        assertTrue(biz.msg instanceof MsgBiz, "msg 必须是 MsgBiz");

        for (String name : new String[]{"login", "auth", "meta", "form", "imports", "msg"}) {
            assertNotNull(readStatic(name), "字段 " + name + " 不得为 null");
        }
        // 字段是 public static（外部直接赋值），不是 getter —— 反射取 public 字段即可证明
        assertEquals(6, publicServiceFields().size(), "公开静态服务字段应为 6 个（旧契约）");
    }

    @Test
    @DisplayName("biz 的字段与 init 顺序：FileService 被注释掉（旧注释明写），不得恢复")
    void fileServiceIsDeliberatelyAbsent() {
        biz.init();
        Map<String, Class<?>> fields = publicServiceFields();
        assertTrue(!fields.containsKey("file"),
                "旧实现把 file 字段与 file = new FileService() 都注释掉了 —— 不得'顺手'恢复");
        assertTrue(!fields.containsKey("imports_"),
                "不得新增字段（契约是 6 个）");
    }

    @Test
    @DisplayName("sm：@Deprecated 的 biz 别名，实例本身可当 biz 用")
    void smIsDeprecatedAliasOfBiz() {
        assertTrue(biz.class.isAssignableFrom(sm.class), "sm 必须继承 biz");
        assertNotNull(sm.class.getAnnotation(Deprecated.class),
                "sm 必须带 @Deprecated（旧实现如此，编译器据此告警）");
        sm instance = new sm();
        biz.init();
        // sm 不覆写任何成员：经 sm 看到的静态字段就是 biz 的
        assertTrue(instance instanceof biz);
        assertNotNull(biz.login);
    }

    /**
     * 取 biz 的公开静态服务字段。
     *
     * @return 字段名 → 类型
     */
    private static Map<String, Class<?>> publicServiceFields() {
        Map<String, Class<?>> out = new LinkedHashMap<>();
        for (java.lang.reflect.Field f : biz.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isPublic(f.getModifiers())
                    && java.lang.reflect.Modifier.isStatic(f.getModifiers())
                    && !f.isSynthetic()) {
                out.put(f.getName(), f.getType());
            }
        }
        return out;
    }

    /**
     * 反射读一个静态字段。
     *
     * @param name 字段名
     * @return 值
     */
    private static Object readStatic(String name) {
        try {
            return biz.class.getField(name).get(null);
        } catch (Exception e) {
            throw new AssertionError("字段读取失败：" + name, e);
        }
    }
}
