/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.mod;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import cn.eova.compat.table.TableMetadata;

/**
 * <p>ported from: cn.eova.mod.EovaModConfig
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>扩展模块配置入口（抽象类）：GROUP/CODE/configRoute/configModel/安装卸载升级钩子</li>
 *   <li>【已声明适配 1】com.jfinal.plugin.activerecord.Table -> cn.eova.compat.table.TableMetadata</li>
 *   <li>    （configModel 的入参泛型；旧栈把它交给 jfinal TableBuilder 完成 Model 注册，新栈由 EovaTableMapping/TableMetadata 承担）</li>
 *   <li>【⚠️ 需人工确认】该形参类型变更对【外部 mod 制品】是 ABI 不兼容 ——第三方 mod 是按旧签名编译的。R7 已把 mod 体系列为成本最高项；本处沿用 R7 的处置口径：显式记录，待 mod 专项决策后再定兼容层</li>
 *   <li>getViewPath 用 File.separator 拼路径（不是 '/'）—— 属既有语义，不得'统一'</li>
 *   <li>toString 为 GROUP()-CODE()，被 EovaModPlugin 的日志使用</li>
 * </ol>
 */
/**
 * 扩展模块配置入口
 * @author Jieven
 *
 */
public abstract class EovaModConfig {

    /**组织编码**/
    public abstract String GROUP();

    /**模块编码**/
    public abstract String CODE();

    /**
     * 获取Mod View 目录
     * @return
     */
    protected String getViewPath() {
        return String.format("%s%s%s%s", EovaModConst.DIR_MOD_VIEW, GROUP(), File.separator, CODE());
    }

    public abstract void afterEovaStart();

    public abstract void beforeEovaStop();

    public abstract void configRoute(EovaModRoute me);

    public abstract void configModel(HashMap<String, List<TableMetadata>> mapping);

    // 试运行阶段暂时不开放如下权限

    //	public abstract void configConstant(Constants me);
    //
    //	public abstract void configEngine(Engine me);
    //
    //	public abstract void configPlugin(Plugins me);
    //
    // public abstract void configInterceptor(Interceptors me);
    //
    //	public abstract void configHandler(Handlers me);

    /**
     * 安装时
     */
    public abstract void onInstall();

    /**
     * 卸载时
     */
    public abstract void onUninstall();

    /**
     * 升级时
     */
    public abstract void onUpgrade();

    public String toString() {
        return String.format("%s-%s", GROUP(), CODE());
    }
}