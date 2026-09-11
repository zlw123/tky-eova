/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.service;

/**
 * <p>ported from: cn.eova.service.biz
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>本地业务注册中心：6 个静态服务字段 + init() 全部 new 出来（无参构造）</li>
 *   <li>字段为 public static —— 外部直接赋值，不可改成 getter</li>
 *   <li>依赖全部在同包 cn.eova.service，故源码无 import（这也是 raw ledger 依赖抽取看不见它们的原因；依赖规划必须用 java-units.depgraph.jsonl）</li>
 * </ol>
 */
/**
 * 本地业务注册中心
 * service 留给远程服务（本地的叫Biz）
 * @author Jieven
 *
 */
public class biz {

    /** 登录服务 **/
    public static LoginService login;
    /** 权限服务 **/
    public static AuthService auth;
    /** 元服务 **/
    public static MetaService meta;
    /** 动态表单服务 **/
    public static FormService form;
    /** 导入服务 **/
    public static ImportBiz imports;
    /** 导入服务 **/
    public static MsgBiz msg;

    /** 文件服务 **/
    // public static FileService file;
    public static void init() {
        login = new LoginService();
        auth = new AuthService();
        meta = new MetaService();
        form = new FormService();
        imports = new ImportBiz();
        msg = new MsgBiz();
        // file = new FileService();
    }
}