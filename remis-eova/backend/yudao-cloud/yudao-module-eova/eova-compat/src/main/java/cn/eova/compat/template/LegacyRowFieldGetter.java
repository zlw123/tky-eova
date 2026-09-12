/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.template;

import cn.eova.db.EovaModel;
import cn.eova.db.EovaRecord;
import com.jfinal.template.Engine;
import com.jfinal.template.expr.ast.FieldGetter;

/**
 * 模板属性读取器：让 enjoy 能按【列名】读 {@link EovaModel}/{@link EovaRecord} 的属性，
 * 即模板里的 {@code #(menu.code)}。
 *
 * <p><b>为什么必须补它（旧栈结构性事实，不是设计选择）</b>：旧栈能渲染 {@code #(menu.code)}，
 * 靠的是 <b>jfinal jar 自带</b>的 {@code com.jfinal.template.expr.ast.FieldGetters$ModelFieldGetter}
 * 与 {@code $RecordFieldGetter}（jfinal 用自己的同名类覆盖了 enjoy 的同名类）。其语义逐字为：</p>
 * <pre>
 * takeOver(clazz, _)          = Model.class.isAssignableFrom(clazz)
 * get(target, fieldName)      = ((Model) target).get(fieldName)
 * </pre>
 * <p>新栈只依赖纯 enjoy 5.3.0 —— 实测 enjoy 的 {@code FieldKit} 只注册
 * GetterMethod/RealField/Map/ArrayLength/Null 五个读取器，<b>对 IRow/Model 零支持</b>
 * ⇒ 该语义必须由本项目显式补上。</p>
 *
 * <p><b>实证（不是推断）</b>：用旧 demo 的 classpath 渲染 {@code /app/#(menu.code)}，
 * 传入旧 {@code cn.eova.model.Menu} 实例（既不是 {@code Map}，也没有 {@code getCode()}，
 * 仅 {@code implements IRow}）→ 渲染结果 {@code [/app/demo_menu]}。反证亦在：新栈缺此读取器时
 * {@code AuthUri.parseAuthUri}（硬编码模板 {@code /app/#(menu.code)}）一渲染即
 * {@code TemplateException: public field not found: "menu.code" and public getter method
 * not found: "menu.getCode()"}，直接导致 {@code /user/doLogin} 500。</p>
 */
public class LegacyRowFieldGetter extends FieldGetter {

    /** 单例（旧实现同样是 singleton 静态实例） */
    private static final LegacyRowFieldGetter SINGLETON = new LegacyRowFieldGetter();

    /** 安装标记：enjoy 的读取器列表是【进程级静态】的，避免重复安装 */
    private static volatile boolean installed;

    /**
     * 取单例
     *
     * @return 单例读取器
     */
    public static LegacyRowFieldGetter me() {
        return SINGLETON;
    }

    /**
     * 安装到 enjoy 全局读取器链首（幂等；不装则模型属性在模板里读不到）
     */
    public static void install() {
        if (installed) {
            return;
        }
        synchronized (LegacyRowFieldGetter.class) {
            if (!installed) {
                Engine.addFieldGetterToFirst(SINGLETON);
                installed = true;
            }
        }
    }

    /**
     * 只接管 Eova 模型/记录；其余类型返回 null 交给后面的读取器（与旧 takeOver 语义一致，
     * 故不会干扰 Map/公共字段/getter 方法的既有解析）
     *
     * @param clazz     目标类型
     * @param fieldName 属性名
     * @return 命中返回单例，否则 null
     */
    @Override
    public FieldGetter takeOver(Class<?> clazz, String fieldName) {
        boolean mine = EovaModel.class.isAssignableFrom(clazz) || EovaRecord.class.isAssignableFrom(clazz);
        return mine ? SINGLETON : null;
    }

    /**
     * 按列名读属性（旧实现即 {@code ((Model) target).get(fieldName)}）
     *
     * @param target    模型或记录
     * @param fieldName 列名
     * @return 列值（缺列即 null，与旧实现一致）
     * @throws Exception 读取异常
     */
    @Override
    public Object get(Object target, String fieldName) throws Exception {
        if (target instanceof EovaModel) {
            return ((EovaModel<?>) target).get(fieldName);
        }
        return ((EovaRecord) target).get(fieldName);
    }
}
