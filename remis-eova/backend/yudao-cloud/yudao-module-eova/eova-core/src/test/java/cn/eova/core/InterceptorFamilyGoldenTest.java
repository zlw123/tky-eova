/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core;

import java.util.List;

import cn.eova.aop.MetaObjectIntercept;
import cn.eova.aop.eova.EovaContext;
import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.config.EovaConfigIntercept;
import cn.eova.core.admin.AdminInterceptor;
import cn.eova.core.auth.EovaFieldAuthIntercept;
import cn.eova.core.button.ButtonIntercept;
import cn.eova.core.dict.DictIntercept;
import cn.eova.core.object.ObjectIntercept;
import cn.eova.core.role.RoleIntercept;
import cn.eova.model.EovaLog;
import cn.eova.ops.OpsInterceptor;
import cn.eova.template.single.SingleIntercept;
import cn.eova.user.UserIntercept;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第 71 轮 port 的<b>拦截器/Hook 族（13 个单元）</b>的类型契约判据。
 *
 * <p><b>为什么先钉类型而不先钉行为：</b>这一族分两类底座 ——
 * ① 元对象业务拦截器（继承 {@link MetaObjectIntercept}，由 {@code @TxConfig}/{@code biz_intercept}
 * 数据驱动装载）；② jfinal 拦截器（实现 {@link LegacyInterceptor}，由 {@code @Before} 装载）。
 * <b>类型归属决定它们被谁装载</b>：归属错了，装载路径就断了（例如把业务拦截器写成
 * jfinal 拦截器，元数据配置里的 {@code com.eova.user.UserIntercept} 就再也装不上）。
 * 而这一族的行为体大多要数据库与请求上下文，其实跑属 acceptanceProfile 范畴。</p>
 */
class InterceptorFamilyGoldenTest {

    /** 元对象业务拦截器族（旧实现全部继承 MetaObjectIntercept） */
    private static final List<Class<?>> BIZ_FAMILY = List.of(
            EovaFieldAuthIntercept.class, EovaConfigIntercept.class, UserIntercept.class,
            RoleIntercept.class, DictIntercept.class, ButtonIntercept.class,
            ObjectIntercept.class, cn.eova.aop.impl.SqlQueryIntercept.class);

    /** jfinal 拦截器族（旧实现实现 com.jfinal.aop.Interceptor） */
    private static final List<Class<?>> JFINAL_FAMILY = List.of(
            OpsInterceptor.class, AdminInterceptor.class);

    @Test
    @DisplayName("元对象业务拦截器族：必须继承 MetaObjectIntercept（数据驱动装载路径）")
    void bizFamilyExtendsMetaObjectIntercept() {
        for (Class<?> c : BIZ_FAMILY) {
            assertTrue(MetaObjectIntercept.class.isAssignableFrom(c),
                    c.getSimpleName() + " 必须继承 MetaObjectIntercept —— "
                            + "元数据 biz_intercept 配置按该类装载它");
            assertTrue(c.getSimpleName().endsWith("Intercept"),
                    c.getSimpleName() + " 命名以 Intercept 结尾（与 jfinal 拦截器的 Interceptor 区分）");
        }
        assertEquals(8, BIZ_FAMILY.size(), "族大小钉死（防漏 port）");
    }

    @Test
    @DisplayName("jfinal 拦截器族：必须实现 LegacyInterceptor（@Before 装载路径）")
    void jfinalFamilyImplementsInterceptor() {
        for (Class<?> c : JFINAL_FAMILY) {
            assertTrue(LegacyInterceptor.class.isAssignableFrom(c),
                    c.getSimpleName() + " 必须实现 LegacyInterceptor —— @Before 按该接口装载它");
            assertTrue(c.getSimpleName().endsWith("Interceptor"),
                    c.getSimpleName() + " 命名以 Interceptor 结尾");
        }
    }

    @Test
    @DisplayName("EovaContext：继承 EovaAopContext（持有 Controller 与 User）；EovaLog：模型族")
    void contextAndLogTypes() {
        assertTrue(cn.eova.aop.EovaAopContext.class.isAssignableFrom(EovaContext.class),
                "EovaContext 必继承 EovaAopContext");
        assertTrue(cn.eova.db.EovaModel.class.isAssignableFrom(EovaLog.class),
                "EovaLog 必须是模型（EovaModel 子类）");
        assertTrue(cn.eova.model.EovaLog.class.getSuperclass().getSimpleName().contains("BaseModel"),
                "EovaLog 的直接父类是 BaseModel（旧实现如此）");
    }

    @Test
    @DisplayName("SingleIntercept：单表模板拦截器不是 MetaObjectIntercept 族（独立类型）")
    void singleInterceptIsStandalone() {
        assertTrue(!MetaObjectIntercept.class.isAssignableFrom(SingleIntercept.class),
                "SingleIntercept 在旧实现里是独立类型，不得归入元对象拦截器族");
        assertTrue(!LegacyInterceptor.class.isAssignableFrom(SingleIntercept.class),
                "SingleIntercept 也不是 jfinal 拦截器");
    }

    @Test
    @DisplayName("族内所有类都是 public（数据驱动反射装载的前提）")
    void familyIsPublic() {
        List<Class<?>> all = new java.util.ArrayList<>(BIZ_FAMILY);
        all.addAll(JFINAL_FAMILY);
        all.add(SingleIntercept.class);
        all.add(EovaContext.class);
        all.add(EovaLog.class);
        for (Class<?> c : all) {
            assertTrue(java.lang.reflect.Modifier.isPublic(c.getModifiers()),
                    c.getSimpleName() + " 必须是 public —— biz_intercept 配置按全限定名反射实例化它");
        }
    }
}
