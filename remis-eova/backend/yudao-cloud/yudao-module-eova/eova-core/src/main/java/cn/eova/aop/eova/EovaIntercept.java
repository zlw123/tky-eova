/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.aop.eova;

import java.util.List;

import cn.eova.model.MetaField;
import cn.eova.model.MetaObject;

/**
 * <p>ported from: cn.eova.aop.eova.EovaIntercept
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>EOVA 拦截器（77 行）：查询/新增/更新/删除的统一扩展点（EovaConfig 持有其实例）</li>
 * </ol>
 */
/**
 * <pre>
 * 全局Eova业务拦截器
 * </pre>
 * @author Jieven
 *
 */
public class EovaIntercept {

    /**
     * <pre>
     * 过滤查询数据
     *
     * ac.ctrl 当前控制器
     * ac.user 当前用户
     * ac.menu 当前菜单
     * ac.object 当前元对象
     * ec.object.getFields() 当前元字段
     *
     * 技巧:
     * #判定某表
     * ec.object.getView().equals("project")
     *
     * #遍历元字段判定
     * for (MetaField f : ec.object.getFields()) {
     *    if (f.getEn().equals("team_id")) {
     *
     * </pre>
     * @param ec
     */
    public String filterQuery(EovaContext ec) throws Exception {
        return "";
    }

    /**
     * <pre>
     * 过滤表达式数据
     *
     * ac.ctrl 当前控制器
     * ac.user 当前用户
     * ac.exp 当前表达式
     * </pre>
     * @param ec
     */
    public String filterExp(EovaContext ec) throws Exception {
        return "";
    }

    /**
     * 是否存在此字段
     *
     * @param object
     * @param fieldName
     * @return
     */
    protected boolean findField(MetaObject object, String fieldName) {
        List<MetaField> fields = object.getFields();
        for (MetaField f : fields) {
            if (f.getEn().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }
}