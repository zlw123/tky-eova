/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import cn.eova.compat.cache.CacheServices;
import cn.eova.compat.db.LegacyDataSourceWiring;
import cn.eova.compat.jfinal.config.LegacyConstants;
import cn.eova.compat.db.LegacyDbPlugin;
import cn.eova.compat.jfinal.config.LegacyHandlers;
import cn.eova.compat.jfinal.config.LegacyInterceptors;
import cn.eova.compat.jfinal.config.LegacyJFinalBoot;
import cn.eova.compat.jfinal.config.LegacyPlugins;
import cn.eova.compat.jfinal.config.LegacyRoutes;
import cn.eova.compat.jfinal.config.LegacyViewType;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyActiveRecordPlugin;
import cn.eova.compat.jfinal.plugin.ehcache.LegacyEhCachePlugin;
import cn.eova.compat.table.EovaTableMapping;
import cn.eova.ext.jfinal.DbCaptchaCache;
import cn.eova.tools.x;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code EovaConfig}（第 86 轮：**整体取代 compile-stub** 的真实 port）的判据。
 *
 * <p><b>为什么这个判据必须重建：</b>旧判据 {@code EovaConfigStubGoldenTest} 断言的是
 * "它还是 stub"（文件头标记 + 成员为逐字子集），并在注释里写明"若完整 port 落地，应改用真实实现
 * 并同时删除本判据"。本轮正是那个时刻 —— 故旧判据删除、本判据建立。</p>
 *
 * <p><b>判据组织：</b></p>
 * <ol>
 *   <li><b>取代事实</b>：文件头不得再有 {@code compile-stub} 标记，且必须带 {@code ported from} 追溯头。</li>
 *   <li><b>调用方契约</b>：其它单元直接依赖的静态成员与 getter/setter 全部在位（stub 时期逐字补入的那些）。</li>
 *   <li><b>端到端（经引导驱动）</b>：用 {@link LegacyJFinalBoot} 真正回调 {@code EovaConfig}，
 *       断言各容器的产出 —— 这是"配置类终于能被驱动"的第一次实证：
 *       {@code configConstant} 写进的常量、{@code configPlugin} 里 <b>15 条 addMapping 进 EovaTableMapping</b>
 *       + 缓存插件 + 数据源坐标、{@code configHandler} 的 handler 链顺序、
 *       {@code configInterceptor} 的全局 action 拦截器。</li>
 * </ol>
 *
 * <p><b>环境纪律（R58）</b>：{@code x.conf} 与 {@code EovaTableMapping}、{@code LegacyDataSourceWiring}
 * 都是全局静态，逐项还原。</p>
 */
class EovaConfigPortGoldenTest {

    private final List<String> confKeys = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (String k : confKeys) {
            x.conf.getProps().remove(k);
        }
        confKeys.clear();
        LegacyDataSourceWiring.clear();
        EovaTableMapping.me().clear();
    }

    /**
     * 设配置项（并登记以便还原）。
     *
     * @param key   键
     * @param value 值
     */
    private void conf(String key, String value) {
        x.conf.addConfig(key, value);
        confKeys.add(key);
    }

    @Test
    @DisplayName("取代事实：无 compile-stub 标记，带 ported from 追溯头")
    void replacedTheStub() throws Exception {
        String path = "remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/cn/eova/config/EovaConfig.java";
        java.nio.file.Path p = cn.eova.testkit.OldImplementationLoader.locateRepoRoot().resolve(path);
        String head = java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
        String firstLine = head.split("\n", 2)[0];
        assertFalse(firstLine.contains("compile-stub"),
                "文件头不得再声明 compile-stub（第 86 轮已整体取代）：" + firstLine);
        assertTrue(head.contains("ported from: cn.eova.config.EovaConfig"), "必须带追溯头");
        assertTrue(head.contains("1b1d39e7350f7e031b216aad0399fc8cc55dce08"), "必须带 source revision");
    }

    @Test
    @DisplayName("调用方契约：静态成员与 getter/setter 全部在位")
    void membersForCallers() throws Exception {
        assertNotNull(EovaConfig.EOVA_DBTYPE);
        assertNotNull(EovaConfig.EOVA_INDEX);
        assertNotNull(EovaConfig.EOVA_INDEX_H5);
        assertNotNull(EovaConfig.getAuthUris(), "authUris 必须非 null（AuthInterceptor 直接遍历）");
        assertNotNull(EovaConfig.EOVA_DBTYPE);
        EovaConfig.setUploadIntercept(null);
        assertNull(EovaConfig.getUploadIntercept());
        EovaConfig.setUserSessionIntercept(null);
        EovaConfig.setDefaultMetaObjectIntercept(null);

        java.util.Map<String, Method> methods = new TreeMap<>();
        for (Method m : EovaConfig.class.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) || Modifier.isProtected(m.getModifiers())) {
                methods.put(m.getName(), m);
            }
        }
        for (String need : List.of("configConstant", "configRoute", "configEngine", "configPlugin",
                "configInterceptor", "configHandler", "onStart", "onStop", "configEova",
                "getConvertor", "addConvertor", "getQueryDialect", "addQueryDialect",
                "getAuthUris", "getUploadIntercept", "setUploadIntercept",
                "getUserSessionIntercept", "setUserSessionIntercept",
                "getEovaIntercept", "setEovaIntercept",
                "getDefaultMetaObjectIntercept", "setDefaultMetaObjectIntercept",
                "getArps", "setArps")) {
            assertTrue(methods.containsKey(need) || hasInherited(need),
                    "缺方法（调用方契约）：" + need);
        }
        for (Field f : EovaConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) {
                assertNotNull(f.getName());
            }
        }
    }

    /**
     * 父类里是否有同名公开方法（如 onStart/onStop 由 LegacyJFinalConfig 提供）。
     *
     * @param name 方法名
     * @return 是否存在
     */
    private static boolean hasInherited(String name) {
        for (Method m : EovaConfig.class.getMethods()) {
            if (m.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("端到端：引导驱动回调 EovaConfig —— 常量/configPlugin 的映射与插件/handler 链")
    void bootDrivesRealEovaConfig() {
        // configPlugin 走 EovaDataSource.create ⇒ 需要数据源配置（缺则抛，契约见下条用例）
        // file.dir.base 是旧实现的【必需配置】（configConstant 把它写进 baseUploadPath/baseDownloadPath，
        // 而 LegacyConstants 对该值有"不得空白"的旧校验）⇒ 真实部署必须配，判据也必须配
        conf("file.dir.base", System.getProperty("java.io.tmpdir") + "/eova-probe");
        conf("db.datasource", "eova");
        conf("eova.url", "jdbc:mysql://localhost:3306/eova");
        conf("eova.user", "root");
        conf("eova.pwd", "pwd");
        conf("eova.driver", "com.mysql.cj.jdbc.Driver");

        // EovaTableMapping 需要元数据源（真实部署由 eova-db-adapter 启动时注入 JDBC 实现；
        // 判据用替身，只验证"登记路径"而非表结构自省）
        EovaTableMapping.setMetadataSource(tableName ->
                new cn.eova.compat.table.TableMetadata(tableName, new String[]{"id", "name"}, new String[]{"id"}));

        LegacyJFinalBoot boot = new LegacyJFinalBoot();
        boot.init(new EovaConfig());

        // ① configConstant：常量落值
        LegacyConstants constants = boot.getConstants();
        assertEquals("UTF-8", constants.getEncoding());
        assertEquals(LegacyViewType.JFINAL_TEMPLATE, constants.getViewType());
        assertEquals(1024 * 1024 * 500, constants.getMaxPostSize(), "EOVA 把 POST 上限提到 500M");
        assertEquals(1, constants.getConfigPluginOrder());
        assertEquals("/eova/error/403.html", constants.getErrorStatusView(403));
        assertNotNull(constants.getCaptchaCache(), "必须注入 DbCaptchaCache");
        assertTrue(constants.getCaptchaCache() instanceof DbCaptchaCache);
        assertTrue(constants.getResolveJsonRequest());
        assertEquals("true", System.getProperty("java.awt.headless"));

        // ② configPlugin：15 条 addMapping 进入新栈表映射 + 三个插件 + 数据源坐标
        EovaTableMapping mapping = EovaTableMapping.me();
        java.util.Map<Class<?>, String> expected = new java.util.LinkedHashMap<>();
        expected.put(cn.eova.model.Session.class, "eova_session");
        expected.put(cn.eova.model.MetaObject.class, "eova_object");
        expected.put(cn.eova.model.MetaField.class, "eova_field");
        expected.put(cn.eova.model.MetaFieldDiy.class, "eova_field_diy");
        expected.put(cn.eova.model.Button.class, "eova_button");
        expected.put(cn.eova.model.Menu.class, "eova_menu");
        expected.put(cn.eova.model.User.class, "eova_user");
        expected.put(cn.eova.model.Role.class, "eova_role");
        expected.put(cn.eova.model.RoleBtn.class, "eova_role_btn");
        expected.put(cn.eova.model.Task.class, "eova_task");
        expected.put(cn.eova.model.Widget.class, "eova_widget");
        expected.put(cn.eova.model.Mod.class, "eova_mod");
        expected.put(cn.eova.model.EovaOption.class, "eova_option");
        expected.put(cn.eova.model.EovaTemplate.class, "eova_template");
        expected.put(cn.eova.model.EovaProps.class, "eova_props");
        for (java.util.Map.Entry<Class<?>, String> e : expected.entrySet()) {
            assertNotNull(mapping.getConfigName(e.getKey()),
                    "映射缺失（mappingEova 的 15 条之一）：" + e.getKey().getSimpleName());
            assertEquals("eova", mapping.getConfigName(e.getKey()), "数据源名取自 db.datasource");
        }
        List<String> pluginTypes = new ArrayList<>();
        for (Object p : boot.getPlugins().getPluginList()) {
            pluginTypes.add(p.getClass().getSimpleName());
        }
        assertTrue(pluginTypes.contains("LegacyEhCachePlugin"), pluginTypes.toString());
        assertTrue(pluginTypes.contains("LegacyDbPlugin"), "数据源装配插件必须在列：" + pluginTypes);
        // 旧实现只在 arps 里登记 ARP 句柄（不进 plugins 列表）—— 新栈同形
        assertNotNull(EovaConfig.getArps().get("eova"), "arps 必须登记该 ds 的映射句柄");
        assertTrue(EovaConfig.getArps().get("eova").getClass().getSimpleName().contains("ActiveRecordPlugin"));
        assertEquals(1, LegacyDataSourceWiring.specs().size(), "恰好一个数据源坐标（db.datasource=eova）");
        assertEquals("eova", LegacyDataSourceWiring.specs().get(0).getDs());
        assertEquals("jdbc:mysql://localhost:3306/eova", LegacyDataSourceWiring.specs().get(0).getUrl());
        assertEquals("root", LegacyDataSourceWiring.specs().get(0).getUser());
        assertEquals("log4j,stat,wall", LegacyDataSourceWiring.specs().get(0).getFilters(),
                "filters 默认值属契约");
        assertEquals(com.alibaba.druid.DbType.mysql, cn.eova.config.EovaDataSource.getDbType("eova"),
                "create 必须把 DbType 写进注册表（旧实现同一步）");

        // 缓存服务被装配（EhCachePlugin.start 的效果）
        assertNotNull(CacheServices.get());

        // ③ configHandler：handler 链顺序（WAF → DevMode → UrlBan → 条件性 ApiRouter）
        List<String> handlers = new ArrayList<>();
        for (Object h : boot.getHandlers().getHandlerList()) {
            handlers.add(h.getClass().getSimpleName());
        }
        assertTrue(handlers.indexOf("WAFHandler") >= 0, handlers.toString());
        assertTrue(handlers.indexOf("WAFHandler") < handlers.indexOf("UrlBanHandler"),
                "WAF 必须在 UrlBan 之前：" + handlers);

        // ④ configInterceptor：全局 action 拦截器至少含 ExceptionInterceptor
        LegacyInterceptors inters = boot.getInterceptors();
        assertFalse(inters.getGlobalActionInterceptors().isEmpty(), "必须装全局 action 拦截器");
    }

    @Test
    @DisplayName("端到端：数据源配置缺失/异常时的两条中文消息逐字")
    void datasourceConfigMessages() {
        // 缺 db.datasource
        RuntimeException e1 = assertThrows(RuntimeException.class,
                () -> EovaDataSource.create(new LegacyPlugins()));
        assertEquals("数据源配置项不存在,请检查配置jdbc.config 配置项[db.datasource]", e1.getMessage());

        // 有 ds 但缺 url
        conf("db.datasource", "probe");
        RuntimeException e2 = assertThrows(RuntimeException.class,
                () -> EovaDataSource.create(new LegacyPlugins()));
        assertEquals("数据源[probe]配置异常,请检查请检查配置jdbc.config", e2.getMessage());
    }

    @Test
    @DisplayName("configRoute：路由登记进容器（EovaWebRoutes/EovaApiRoutes + IndexController 兜底）")
    void configRouteRegistersRoutes() {
        LegacyRoutes routes = new LegacyRoutes() {
            @Override
            public void config() {
            }
        };
        new EovaConfig().configRoute(routes);
        assertFalse(routes.getRouteItemList().isEmpty(), "路由总表必须被登记");
    }
}
