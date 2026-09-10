/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

/**
 * ActiveRecord 层异常：替代 jfinal 的
 * {@code com.jfinal.plugin.activerecord.ActiveRecordException}。
 *
 * <p><b>为什么可以改名（已实测，非推测）：</b>
 * <ul>
 *   <li>旧类只 {@code extends java.lang.RuntimeException}，属非受检异常；</li>
 *   <li><b>EOVA 全项目对 {@code ActiveRecordException} 的引用数为 0</b>
 *       （没有任何 {@code catch} 或类型声明），故改名不会改变任何既有分支；</li>
 *   <li>本类保持"抛出非受检异常 + <b>消息逐字一致</b>"这一真正可观测的契约
 *       （如 {@code The attribute name does not exist: "x"}、
 *       {@code Primary keyid can not be null}）。</li>
 * </ul>
 * 若将来发现某处确实按类型捕获，应改为继承该类以兼容 —— 但当前证据是零引用。
 */
public class EovaActiveRecordException extends RuntimeException {

    private static final long serialVersionUID = -6629050669242769388L;

    public EovaActiveRecordException(String message) {
        super(message);
    }

    public EovaActiveRecordException(String message, Throwable cause) {
        super(message, cause);
    }
}
