/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova;

import cn.eova.config.MetaConfigHook;
import cn.eova.core.menu.MenuHook;
import cn.eova.core.msg.MsgHook;
import cn.eova.core.object.MetaFieldHook;
import cn.eova.core.object.ObjectHook;
import cn.eova.core.role.RoleHook;
import cn.eova.core.task.TaskHook;
import cn.eova.hook.HookRegistry;
import cn.eova.user.UserHook;

/**
 * <p>ported from: cn.eova.EovaMetaHooks
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>元数据 Hook 注册表（36 行）</li>
 * </ol>
 */
/**
 * 元对象钩子注册
 *
 * @author Jieven
 */
public class EovaMetaHooks extends HookRegistry {

    @Override
    public void config() {
        addMeta("eova_object_code", new ObjectHook());
        addMeta("eova_field_code", new MetaFieldHook());
        addMeta("eova_menu_code", new MenuHook());
        addMeta("eova_config", new MetaConfigHook());
        addMeta("eova_user", new UserHook());
        addMeta("eova_role", new RoleHook());
        addMeta("eova_task", new TaskHook());
        addMeta("eova_msg", new MsgHook());
    }
}