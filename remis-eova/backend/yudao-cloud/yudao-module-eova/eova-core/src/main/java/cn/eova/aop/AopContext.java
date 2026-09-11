/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.aop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cn.eova.engine.SqlCondition;
import cn.eova.model.MetaField;
import cn.eova.model.MetaObject;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.db.EovaRecord;


/**
 * <p>ported from: cn.eova.aop.AopContext
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>AOP 上下文（162 行）：元对象/字段/条件的解析上下文，继承 EovaAopContext</li>
 *   <li>【已声明适配 1】com.jfinal.core.Controller -> LegacyController</li>
 *   <li>【已声明适配 2】com.jfinal.plugin.activerecord.Record -> cn.eova.db.EovaRecord</li>
 *   <li>依赖 MetaObject（当前为已声明 stub）—— 真 port 后本单元行为方可完整验证</li>
 * </ol>
 */
/**
 * AOP 上下文
 *
 * @author Jieven
 * @date 2014-8-29
 */
public class AopContext extends EovaAopContext {

    /**
     * 当前元对象(只读)
     * PS:仅用于读取, 因为前台是单独获取的, 拦截器变更也不会生效
     */
    public MetaObject object;

    /**
     * 当前元字段(读写)
     */
    public List<MetaField> fields;

    /**
     * 当前操作数据集(批量操作)
     */
    public List<EovaRecord> records;

    /**
     * 当前操作对象(单条数据操作)
     */
    public EovaRecord record;

    /**
     * 当前操作对象固定值
     * 用途：新增/编辑时预设固定初始值
     * 推荐：固定初始值，建议禁用字段使用addBefore()拦截添加值
     */
    public EovaRecord fixed;

    /**
     * 追加SQL条件
     * @see AopContext#getCondition()
     */
    @Deprecated
    public String condition = "";
    /**
     * 自定义SQL覆盖默认查询条件
     * 格式: where xxx = xxx
     */
    public String where;
    /**
     * 自定义SQL参数
     */
    public List<Object> params = new ArrayList<Object>();
    /**
     * 自定义SQL覆盖默认排序
     * 格式: order by xxx desc
     */
    public String sort;
    /**
     * 完全自定义整个SQL语句(可以支持任意语法,多层嵌套,多表连接查询等)
     * PS:包括查询字段权限
     */
    public String sql;
    /**
     * 自定义from查询子视图
     * 主要用于多表联查
     */
    public String from;

    public AopContext(LegacyController ctrl) {
        super(ctrl);
    }

    public AopContext(LegacyController ctrl, List<EovaRecord> records) {
        this(ctrl);
        this.records = records;
        // 小白兼容
        if (records != null && records.size() == 1) {
            this.record = this.records.get(0);
        }
    }

    public AopContext(LegacyController ctrl, EovaRecord record) {
        this(ctrl);
        this.record = record;
    }

    public AopContext(LegacyController ctrl, MetaObject object) {
        this(ctrl);
        this.object = object;
        this.fields = object.getFields();
    }

    public AopContext(LegacyController ctrl, MetaObject object, EovaRecord record) {
        this(ctrl, object);
        this.record = record;
    }

    public AopContext(LegacyController ctrl, MetaObject object, List<EovaRecord> records) {
        this(ctrl, object);
        this.records = records;
        // 小白兼容
        if (records != null && records.size() == 1) {
            this.record = this.records.get(0);
        }
    }

    public String getStart(String en) {
        return ctrl.get("start_" + en);
    }

    public String getEnd(String en) {
        return ctrl.get("end_" + en);
    }

    public String getQuery(String en) {
        return ctrl.get("query_" + en);
    }

    /**
     * 字段条件
     */
    private HashMap<String, SqlCondition> conds = new HashMap<>();
    public final static String COND_SQL = "sql";
    public final static String COND_PMS = "params";

    /**
     * 设置字段查询条件
     * 可查询字段, 覆盖条件
     * 不可查询字段, 追加条件
     * @param field 字段名
     * @param cond SQL条件
     */
    public void setCondition(String field, SqlCondition cond) {
        conds.put(field, cond);
    }

    /**
     * 获取字段查询条件
     */
    public HashMap<String, SqlCondition> getCondition() {
        return conds;
    }


}