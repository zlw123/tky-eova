/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.hook;

import java.util.HashMap;

/**
 * <p>ported from: cn.eova.hook.HookRegistry
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>Hook 注册表（82 行）：按类型登记与分发 Hook（ActionHook/BizHook 等）</li>
 *   <li>【零替换】不 import 任何 jfinal 类型（同包引用 Hook/EovaHookType/EovaMetaHook）</li>
 * </ol>
 */
/**
 * 钩子注册中心
 */
public abstract class HookRegistry {

    public static HashMap<String, Hook> hookMap = new HashMap<>();

    public abstract void config();

    /**
     * 获取钩子
     * @param type 钩子业务域
     * @param code 钩子业务域内唯一编码
     * @return 钩子实现对象
     */
    private static Hook get(EovaHookType type, String code) {
        // 与其他几个内置钩子区分开,防止命名冲突
        return hookMap.get(type + "#" + code);
    }

    /**
     * 获取Biz.业务钩子
     * @param type 钩子业务域
     * @param code 钩子业务域内唯一编码
     * @return 钩子
     */
    public static BizHook getBiz(EovaHookType type, String code) {
        // 与其他几个内置钩子区分开,防止命名冲突
        return (BizHook) get(type, code);
    }

    /**
     * 获取Ctrl.业务钩子
     * @param type 钩子业务域
     * @param code 钩子业务域内唯一编码
     * @return 钩子实现
     */
    public static ActionHook getAction(EovaHookType type, String code) {
        return (ActionHook) get(type, code);
    }

    /**
     * 获取Eova元对象钩子
     * @param code
     * @return
     */
    public static EovaMetaHook getMeta(String code) {
        return (EovaMetaHook) get(EovaHookType.META, code);
    }

    /**
     * 注册钩子
     * @param type 钩子业务域
     * @param code 钩子业务域内唯一编码
     * @param hook 钩子实现对象
     */
    public static void add(EovaHookType type, String code, Hook hook) {
        hookMap.put(type + "#" + code, hook);
    }

    /**
     * 注册Eova元对象钩子
     * @param code 元对象编码
     * @param hook 钩子
     */
    public static void addMeta(String code, EovaMetaHook hook) {
        // 特殊钩子, 一个萝卜一个坑, 无需细分钩子类型.
        add(EovaHookType.META, code, hook);
    }


}

