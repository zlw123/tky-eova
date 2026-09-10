/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.hook;

/**
 * <p>ported from: cn.eova.hook.EovaHookCode
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>枚举常量顺序即 values() 顺序，属契约；ACTION/BIZ 在旧源码中已注释掉，不得恢复</li>
 * </ol>
 */
/**
 * 内置钩子编码
 */
public enum EovaHookCode {

    /** 用户 **/
    USER,
    /** 审批 **/
    FLOW_ACTION,
    /** 导入 **/
    IMPORT,

}
