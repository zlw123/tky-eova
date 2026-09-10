/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.config;

import java.io.File;
import java.util.HashMap;

import com.jfinal.kit.PathKit;

/**
 * <p>ported from: cn.eova.config.EovaConst
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>常量名与取值属对外契约（含 SEQ_ 等拼接前缀，SqlUtil.getSequence 依赖它）</li>
 *   <li>DIR_PLUGINS 在【静态初始化】里调 PathKit.getWebRootPath()，故新栈必须在任何 EovaConst 访问之前先 PathKit.setWebRootPath(<webRoot>) —— Spring Boot 没有 JFinal 的 web 容器去设置该静态值（见 DES-002-R4 §2.1）」</li>
 * </ol>
 */
/**
 * 系统配置
 *
 * @author Jieven
 * @date 2014-5-15
 */
public class EovaConst {

    /** 当前版本号 **/
    private static final String VERSION = "4.0.0";

    /** 默认 数据源名称 **/
    public static final String DS_MAIN = "main";
    /** EOVA 数据源名称 **/
    public static final String DS_EOVA = "eova";
    /** Oracle 默认Sequence前缀 **/
    public static final String SEQ_ = "seq_";

    /** 登录密码错误次数标识 **/
    public static final String LOGIN_NUM = "login_num";
    /** 登录用户标识 **/
    public static final String USER = "user";
    // TODO 3.x改为 $.login
    // 1.避免和前台业务SessionKey混淆
    // 2.login != user

    /** EovaDB缓存表 **/
    public static final String EOVA_CACHE = "eova_cache";

    /** 本地语言标识 **/
    public static final String LOCAL = "eova_local";

    /** Cache Key 所有菜单信息 **/
    public static final String ALL_MENU = "all_menu";

    /**超级管理员角色(创建菜单自动给角色授权) **/
    // public static String ADMIN_UID = "1";// 有且只有1个超管 字符串兼容UUID
    public static int ADMIN_RID = 1;// 超管角色ID,有且只有1个
    /** 默认管理员用户ID **/
    public static int SYS_ADMIN_UID = 2;

    /** 插件目录 **/
    public static final String DIR_PLUGINS = PathKit.getWebRootPath() + File.separator + "plugins" + File.separator;

    /** Eova插件包URL **/
    public static final String PLUGINS_URL = "http://7xign9.com1.z0.glb.clouddn.com/eova_plugins.zip";

    /** 虚拟字段标识 **/
    public static final String VIRTUAL = "virtual";

    /** 系统业务端-字段名 **/
    public static final String USER_SYS_FIELD = "biz";//sys xxx
    /** 系统业务端-字段值 **/
    public static int SYS_BIZ = 0;

    /** 启动时间 **/
    public static String START_TIME = "";

    /**
     * 全局页面常量
     * @return
     */
    public static HashMap<String, String> getPageConst() {
        HashMap<String, String> map = new HashMap<>();
        String put = map.put("STATIC", "web.static");
        map.put("CDN", "web_cdn");
        map.put("IMG", "web_file");
        map.put("FILE", "web_file");
        map.put("VER", "ver");
        map.put("ENV", "env");
        map.put("ZOOM", "ui.zoom");
        map.put("SKIN", "ui.skin");
        map.put("INCLUDE", "ui.include");
        return map;
    }

    /**
     * 获取Eova版本号, 直接读取常量会被直接编译写死
     * @return
     */
    public static String getEovaVer() {
        return VERSION;
    }
}