/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.config;

import java.lang.reflect.Field;
import java.util.Map;

import cn.eova.tools.x;
import com.jfinal.template.Engine;

/**
 * <p>ported from: cn.eova.config.PageConst
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>Enjoy 页面相关常量与共享方法注册；Engine 由 enjoy 提供 —— 逐字节</li>
 * </ol>
 */
/**
 * 页面常量
 *
 * @author Jieven
 *
 */
public class PageConst {

    /** 当前页码 **/
    public static final String PAGENUM = "page";
    /** 显示数量 **/
    public static final String PAGESIZE = x.conf.get("ui", "layui").equals("layui") ? "limit" : "rows";
    /** 表单排序Key **/
    public static final String SORT = x.conf.get("ui", "layui").equals("layui") ? "field" : "sort";
    /** 表单排序方式Key **/
    public static final String ORDER = "order";

    /** EovaMeta 排序参数 **/
    public static final String EV_SORT = "sort";

    /** 查询参数前缀 **/
    public static final String QUERY = "query_";//query_ Vue 提交Form 不需要.
    /** 过滤参数前缀 **/
    public static final String FILTER = "filter_";
    /** 范围查询表单前缀 **/
    public static final String START = "start_";
    /** 范围查询表单前缀 **/
    public static final String END = "end_";
    /** 条件前缀 **/
    public static final String COND = "condition_";

    /** 表单名-元对象 **/
    public static final String EOVA_CODE = "eova_code";
    /** 表单名-元字段 **/
    public static final String EOVA_FIELD = "eova_field";

    /**
     * 系统启动初始化加载 将常量全局化
     */
    public static void init(Engine me, Map<String, Object> sharedVars) {
        // long time = System.currentTimeMillis();
        System.err.println("Load Page Const Starting:");
        Field[] fds = PageConst.class.getDeclaredFields();
        for (Field fd : fds) {
            String key = fd.getName();
            try {
                // 设置为FreeMarker全局变量
                // FreeMarkerRender.getConfiguration().setSharedVariable(key, fd.get(key).toString());
                // JFinal.me().getServletContext().setAttribute(key, fd.get(key).toString());
                // 设置全局变量
                sharedVars.put(key, fd.get(key).toString());
                //System.err.println(key + " = [" + fd.get(null).toString() + "]");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // System.err.println("Load Cost Time:" + (System.currentTimeMillis() - time) + "ms\n");
    }
}