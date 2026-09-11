/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

import java.util.ArrayList;
import java.util.List;

import cn.eova.compat.jfinal.aop.LegacyInterceptor;
import cn.eova.compat.jfinal.handler.LegacyHandler;
import cn.eova.compat.jfinal.plugin.LegacyPlugin;
import cn.eova.compat.jfinal.upload.LegacyUploadConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W3 接缝族（第 2 批：容器 + 引导驱动）的判据。
 *
 * <p><b>为什么判"驱动顺序"而不是只判容器：</b>容器的 add/get 是 trivial 的，
 * 真正的语义在<b>调用顺序</b>—— jfinal 的 {@code JFinal.init()} 按固定序列回调配置类，
 * 而 EOVA 的配置方法之间存在隐含依赖（例如 {@code configRoute} 里读 {@code constants}、
 * {@code onStart} 依赖前面所有步骤已完成）。故本判据用一个**记录型配置类**，
 * 把"被回调的顺序"与"每步能看到的容器状态"一并钉住。</p>
 */
class W3BootDriverGoldenTest {

    /** 记录调用顺序的配置类 */
    static final class RecConfig extends LegacyJFinalConfig {

        final List<String> calls = new ArrayList<>();

        /** 每步记录的"此时常量已就绪"标志 */
        final List<String> seen = new ArrayList<>();

        @Override
        public void configConstant(LegacyConstants me) {
            calls.add("configConstant");
            me.setEncoding("UTF-8");
            me.setBaseUploadPath("/data/eova");
            me.setMaxPostSize(123L);
        }

        @Override
        public void configRoute(LegacyRoutes me) {
            calls.add("configRoute");
            seen.add("encoding=" + constants().getEncoding());
            me.setMappingSuperClass(true);
            me.addInterceptor(new LegacyInterceptor() {
                @Override
                public void intercept(cn.eova.compat.jfinal.aop.LegacyInvocation inv) {
                }
            });
        }

        @Override
        public void configEngine(LegacyEngine me) {
            calls.add("configEngine");
            me.addDirective("json", com.jfinal.template.Directive.class);
            me.addSharedMethod("shared");
        }

        @Override
        public void configPlugin(LegacyPlugins plugins) {
            calls.add("configPlugin");
            plugins.add(new LegacyPlugin() {
                final List<String> log = new ArrayList<>();

                @Override
                public boolean start() {
                    log.add("start");
                    started.add("plugin-start");
                    // 关键：把插件启动也记进同一个序列，否则"插件 vs onStart"的相对顺序不可观测
                    calls.add("plugin-start");
                    return true;
                }

                @Override
                public boolean stop() {
                    log.add("stop");
                    started.add("plugin-stop");
                    calls.add("plugin-stop");
                    return true;
                }
            });
        }

        final List<String> started = new ArrayList<>();

        @Override
        public void configInterceptor(LegacyInterceptors me) {
            calls.add("configInterceptor");
            me.addGlobalActionInterceptor(new LegacyInterceptor() {
                @Override
                public void intercept(cn.eova.compat.jfinal.aop.LegacyInvocation inv) {
                }
            });
        }

        @Override
        public void configHandler(LegacyHandlers me) {
            calls.add("configHandler");
            me.add(new LegacyHandler() {
                @Override
                public void handle(String target, jakarta.servlet.http.HttpServletRequest req,
                                   jakarta.servlet.http.HttpServletResponse resp, boolean[] isHandled) {
                }
            });
        }

        @Override
        public void onStart() {
            calls.add("onStart");
            seen.add("upload.base=" + LegacyUploadConfig.getBaseUploadPath());
        }

        /** 供 configRoute 读常量（旧 EOVA 同样在 configRoute 里读静态配置） */
        LegacyConstants constants() {
            return lastConstants;
        }

        LegacyConstants lastConstants;
    }

    /** 把 constants 暴露给 RecConfig（驱动在 configConstant 之前创建容器） */
    static class Boot extends LegacyJFinalBoot {
        @Override
        public void init(LegacyJFinalConfig config) {
            if (config instanceof RecConfig) {
                ((RecConfig) config).lastConstants = getConstants();
            }
            super.init(config);
        }
    }

    @Test
    @DisplayName("引导驱动：回调顺序 = configConstant → Route → Engine → Plugin → Interceptor → Handler → onStart")
    void bootOrder() {
        RecConfig cfg = new RecConfig();
        new Boot().init(cfg);
        assertEquals(List.of("configConstant", "configRoute", "configEngine", "configPlugin",
                "configInterceptor", "configHandler", "plugin-start", "onStart"), cfg.calls,
                "顺序即语义（旧 JFinal.init 的固定序列；插件 start 必须在 onStart 之前）");
        assertEquals(List.of("plugin-start"), cfg.started);
    }

    @Test
    @DisplayName("引导驱动：常量→上传配置的下发（baseUploadPath/maxPostSize/encoding）")
    void bootPushesUploadConfig() {
        String savedPath = LegacyUploadConfig.getBaseUploadPath();
        long savedSize = LegacyUploadConfig.getMaxPostSize();
        try {
            RecConfig cfg = new RecConfig();
            LegacyJFinalBoot boot = new Boot();
            boot.init(cfg);
            assertEquals("/data/eova", LegacyUploadConfig.getBaseUploadPath(),
                    "initUploadConfig 把 baseUploadPath 推给上传配置（旧 JFinal.initUploadConfig 语义）");
            assertEquals(123L, LegacyUploadConfig.getMaxPostSize());
            assertEquals("UTF-8", LegacyUploadConfig.getEncoding());
            assertEquals("upload.base=/data/eova", cfg.seen.get(1));
            assertTrue(boot.isStarted());
            assertEquals("/data/eova", boot.getConstants().getBaseUploadPath());
        } finally {
            LegacyUploadConfig.init(savedPath, savedSize, "UTF-8");
        }
    }

    @Test
    @DisplayName("引导驱动：停止序列 beforeJFinalStop → 插件 stop → onStop")
    void bootStop() {
        RecConfig cfg = new RecConfig();
        LegacyJFinalBoot boot = new Boot();
        boot.init(cfg);
        cfg.calls.clear();
        boot.stop(cfg);
        assertEquals(List.of("plugin-stop"), cfg.calls, "停机序列：插件 stop → onStop（onStop 未记录进 calls）");
        assertEquals(List.of("plugin-start", "plugin-stop"), cfg.started,
                "停机时必须逐插件 stop（旧 JFinal 停机序列）");
        assertFalse(boot.isStarted());
    }

    @Test
    @DisplayName("容器：add 返回 this 且保序；Engine 登记项可读回")
    void containersSemantics() {
        LegacyPlugins plugins = new LegacyPlugins();
        LegacyPlugin p1 = probePlugin();
        assertSame(plugins, plugins.add(p1), "add 必须返回 this（旧实现如此，便于链式）");
        assertEquals(1, plugins.getPluginList().size());

        LegacyInterceptors inters = new LegacyInterceptors();
        LegacyInterceptor i1 = probeInterceptor();
        assertSame(inters, inters.add(i1));
        assertSame(inters, inters.addGlobalActionInterceptor(i1));
        assertEquals(1, inters.getInterceptors().size());
        assertEquals(1, inters.getGlobalActionInterceptors().size());
        assertEquals(0, inters.getGlobalServiceInterceptors().size(), "三类拦截器互不混装");

        LegacyHandlers handlers = new LegacyHandlers();
        LegacyHandler h1 = probeHandler();
        assertSame(handlers, handlers.add(h1));
        assertEquals(List.of(h1), handlers.getHandlerList(), "handler 顺序即链顺序");
        assertThrows(UnsupportedOperationException.class, () -> handlers.getHandlerList().add(h1),
                "对外只读视图（内部仍可变）");

        LegacyEngine engine = new LegacyEngine();
        engine.addDirective("json", com.jfinal.template.Directive.class);
        engine.addSharedMethod(new Object());
        assertEquals(1, engine.getDirectives().size());
        assertEquals(com.jfinal.template.Directive.class, engine.getDirectives().get("json"));
        assertEquals(1, engine.getSharedMethods().size());
    }

    @Test
    @DisplayName("JFinalConfig 基类：属性未装配时的消息逐字 + useFirstFound 装载")
    void jfinalConfigBase() {
        RecConfig cfg = new RecConfig();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> cfg.getProperty("k"));
        assertEquals("PropKit.useFirstFound(...) has not been invoked", e.getMessage(),
                "旧消息逐字保留");
        cfg.useFirstFound("eova-w3-probe.txt");
        assertEquals("1", cfg.getProperty("probe.int"));
        assertEquals(Boolean.TRUE, cfg.getPropertyToBoolean("probe.bool"));
        assertEquals(Integer.valueOf(9), cfg.getPropertyToInt("probe.missing", 9));
        cfg.onStart();
        cfg.onStop();
        cfg.afterJFinalStart();
        cfg.beforeJFinalStop();
    }

    private static LegacyPlugin probePlugin() {
        return new LegacyPlugin() {
            @Override
            public boolean start() {
                return true;
            }

            @Override
            public boolean stop() {
                return true;
            }
        };
    }

    private static LegacyInterceptor probeInterceptor() {
        return inv -> {
        };
    }

    private static LegacyHandler probeHandler() {
        return new LegacyHandler() {
            @Override
            public void handle(String target, jakarta.servlet.http.HttpServletRequest req,
                               jakarta.servlet.http.HttpServletResponse resp, boolean[] isHandled) {
            }
        };
    }
}
