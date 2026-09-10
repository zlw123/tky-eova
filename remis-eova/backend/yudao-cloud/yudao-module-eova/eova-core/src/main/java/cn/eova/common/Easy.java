/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common;

/**
 * <p>ported from: cn.eova.common.Easy
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>纯 JDK 实现；Easy 为 EOVA 的便捷门面，方法名与返回形态属契约</li>
 * </ol>
 */
/**
 * {"msg":"主键不可为空！","success":false}
 * @author Jieven
 *
 */
public class Easy {

    /**处理结果**/
    private boolean success = true;
    private String msg = "操作成功";
    private Object data;

    public static Easy sucess() {
        return new Easy();
    }

    public static Easy sucess(String msg) {
        Easy e = new Easy();
        e.setSuccess(true);
        e.setMsg(msg);
        return e;
    }

    public static Easy fail(String msg) {
        Easy e = new Easy();
        e.setSuccess(false);
        e.setMsg(msg);
        return e;
    }

    /**
     * 默认操作成功
     */
    public Easy() {
    }

    /**
     * 操作失败构造
     * @param msg
     */
    public Easy(String msg) {
        this.msg = msg;
        this.success = false;
    }

    /**
     * 自定义构造
     * @param msg
     * @param status
     */
    public Easy(String msg, boolean status) {
        this.msg = msg;
        this.success = status;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    /**
     * <pre>
     * 移动到MetaObjectIntercept
     * 语法变更：
     * return Easy.info("xxx"); -> return info("xxx");
     * </pre>
     */
    @Deprecated
    public static String info(String msg) {
        return "info:" + msg;
    }

    /**
     * <pre>
     * 移动到MetaObjectIntercept
     * 语法变更：
     * return Easy.warn("xxx"); -> return warn("xxx");
     * </pre>
     */
    @Deprecated
    public static String warn(String msg) {
        return "warn:" + msg;
    }

    /**
     * <pre>
     * 移动到MetaObjectIntercept
     * 语法变更：
     * return Easy.error("xxx"); -> return error("xxx");
     * </pre>
     */
    @Deprecated
    public static String error(String msg) {
        return "error:" + msg;
    }
}