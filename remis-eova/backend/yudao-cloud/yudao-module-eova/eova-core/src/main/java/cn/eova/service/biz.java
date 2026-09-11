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
 *   <li>本地业务注册中心（40 行）：6 个 public static 服务字段 + init() 全部 new 出来</li>
 *   <li>【依赖登记】导入服务 ImportBiz 与元服务 MetaService 尚未 port ⇒ 本轮按【已声明 stub】登记（见 DECLARED_STUBS），biz 的真 port 在两者的真 port 落地后才算完整</li>
 *   <li>字段为 public static，外部直接赋值，不可改成 getter</li>
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