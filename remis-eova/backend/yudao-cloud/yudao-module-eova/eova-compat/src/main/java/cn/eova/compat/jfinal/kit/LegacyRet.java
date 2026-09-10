/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.kit.Ret} 的等价 port（阶段 1 `S-JSON` 切片）。
 *
 * <p><b>为什么必须 port：</b>新栈的 {@code com.jfinal:enjoy:5.3.0} <b>不提供</b>
 * {@code com.jfinal.kit.Ret}（EOVA 有 17 个文件引用它），属硬编译阻塞（R39）。
 * 且 {@code Ret} 承载 EOVA 的 {@code state,msg,data} envelope —— 已冻结契约实测为
 * {@code {"state":"ok"}} / {@code {"msg":"密码错误","state":"fail"}}，
 * 正对应本类的 {@code STATE_OK="ok"} / {@code STATE_FAIL="fail"}。
 *
 * <p>ported from: com.jfinal.kit.Ret（第三方制品，非 EOVA 源码）
 * <br>source artifact: com.jfinal:jfinal:5.2.6
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>覆盖度：5.2.6 全部公开与 protected 方法（完整 port，无裁剪）
 *
 * <p><b>与 {@link LegacyKv} 的关键差异（旧实现自身不一致，必须各自保留）：</b>
 * <ol>
 *   <li><b>取值器是强转式，不是转换式。</b>{@code Ret.getInt} 走
 *       {@code (Number) get(key)} 再 {@code intValue()}，<b>不</b>委托 TypeKit；
 *       {@code getNumber}/{@code getBoolean} 是<b>纯强转</b>。
 *       故 {@code ret.getInt("k")} 的值为字符串时抛 {@code ClassCastException}，
 *       而 {@code kv.getInt("k")} 会成功解析 —— 两者行为不同。</li>
 *   <li><b>{@code isTrue}/{@code isFalse} 用 {@code instanceof Boolean} 判定</b>：
 *       null 或非布尔值一律返回 {@code false}，<b>任何输入都不抛异常</b>；
 *       而 {@code LegacyKv.isTrue} 走 TypeKit 转换，对无法转布尔的值（如 {@code "abc"}）
 *       会抛 {@code ClassCastException}。两者对 null 都返回 false，差别在非布尔值的处理。</li>
 *   <li>{@code Ret} 没有 {@code getBigDecimal}/{@code getDate}/{@code getLocalDateTime}，
 *       也没有 {@code getXxx(key, default)} 重载与 {@code notBlank}/{@code isBlank}。</li>
 *   <li>注意强转发生在<b>判空之前</b>（字节码 {@code checkcast} 先于 {@code ifnull}）：
 *       值缺失时强转 null 合法，故返回 null 而不抛异常。</li>
 * </ol>
 *
 * <p><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>{@code isOk()}/{@code isFail()} 在 <b>state 既非 ok 也非 fail</b> 时抛
 *       {@code IllegalStateException}，消息为
 *       "调用 isOk() 之前，必须先调用 ok()、fail() 或者 setOk()、setFail() 方法"。
 *       两者判定的先后顺序<b>相反</b>（{@code isOk} 先比 ok，{@code isFail} 先比 fail）。</li>
 *   <li>{@code stateWatcher}/{@code okFailHandler}/{@code dataWithOkState} 在旧实现中
 *       是<b>包内可见静态字段且无公开 setter</b>，EOVA 无法设置，故相应分支恒不进入。
 *       本实现保留字段与分支，以便语义完整。</li>
 *   <li>{@code _setState} 会先写入再回调 watcher；{@code _setData} 在
 *       {@code dataWithOkState} 为真时才补写 state。</li>
 *   <li>{@code equals} 仅在对方也是 {@code Ret} 时才可能为真。</li>
 *   <li>底层是 {@code HashMap}，键序为哈希序。</li>
 * </ol>
 */
@SuppressWarnings("rawtypes")
public class LegacyRet extends HashMap {

    private static final long serialVersionUID = -5295942856783918425L;

    /** state 键名 */
    static String STATE = "state";

    /** 成功态取值 */
    static Object STATE_OK = "ok";

    /** 失败态取值 */
    static Object STATE_FAIL = "fail";

    /** state 变更回调；旧实现中无公开 setter，恒为 null */
    static StateWatcher stateWatcher;

    /** ok/fail 兜底判定回调；旧实现中无公开 setter，恒为 null */
    static OkFailHandler okFailHandler;

    /** data 键名 */
    static String DATA = "data";

    /** data 是否附带 ok 态；旧实现默认为 false */
    static boolean dataWithOkState = false;

    /** msg 键名 */
    static String MSG = "msg";

    public LegacyRet() {
    }

    /**
     * 建含一个键值对的 Ret
     */
    public static LegacyRet of(Object key, Object value) {
        return new LegacyRet().set(key, value);
    }

    /**
     * 建含一个键值对的 Ret（{@code of} 的别名）
     */
    public static LegacyRet by(Object key, Object value) {
        return new LegacyRet().set(key, value);
    }

    /**
     * 建空 Ret
     */
    public static LegacyRet create() {
        return new LegacyRet();
    }

    /**
     * 成功态 Ret
     */
    public static LegacyRet ok() {
        return new LegacyRet().setOk();
    }

    /**
     * 成功态 Ret 并带消息
     */
    public static LegacyRet ok(String msg) {
        return new LegacyRet().setOk()._setMsg(msg);
    }

    /**
     * 成功态 Ret 并带一个键值对
     */
    public static LegacyRet ok(Object key, Object value) {
        return new LegacyRet().setOk().set(key, value);
    }

    /**
     * 失败态 Ret
     */
    public static LegacyRet fail() {
        return new LegacyRet().setFail();
    }

    /**
     * 失败态 Ret 并带消息
     */
    public static LegacyRet fail(String msg) {
        return new LegacyRet().setFail()._setMsg(msg);
    }

    /**
     * 失败态 Ret 并带一个键值对
     */
    public static LegacyRet fail(Object key, Object value) {
        return new LegacyRet().setFail().set(key, value);
    }

    /**
     * 仅设置 state
     */
    public static LegacyRet state(Object state) {
        return new LegacyRet()._setState(state);
    }

    /**
     * 仅设置 data
     */
    public static LegacyRet data(Object data) {
        return new LegacyRet()._setData(data);
    }

    /**
     * 仅设置 msg
     */
    public static LegacyRet msg(String msg) {
        return new LegacyRet()._setMsg(msg);
    }

    /**
     * 写入 state，并在存在 watcher 时回调
     */
    protected LegacyRet _setState(Object state) {
        super.put(STATE, state);
        if (stateWatcher != null) {
            stateWatcher.call(this, STATE, state);
        }
        return this;
    }

    /**
     * 写入 data；{@code dataWithOkState} 为真时补写成功态
     */
    protected LegacyRet _setData(Object data) {
        super.put(DATA, data);
        if (dataWithOkState) {
            _setState(STATE_OK);
        }
        return this;
    }

    /**
     * 写入 msg
     */
    protected LegacyRet _setMsg(String msg) {
        super.put(MSG, msg);
        return this;
    }

    /**
     * 置为成功态
     */
    public LegacyRet setOk() {
        return _setState(STATE_OK);
    }

    /**
     * 置为失败态
     */
    public LegacyRet setFail() {
        return _setState(STATE_FAIL);
    }

    /**
     * 是否成功态；state 既非 ok 也非 fail 时抛 IllegalStateException（旧实现既有行为）
     */
    public boolean isOk() {
        Object state = get(STATE);
        if (STATE_OK.equals(state)) {
            return true;
        }
        if (STATE_FAIL.equals(state)) {
            return false;
        }
        if (okFailHandler != null) {
            return okFailHandler.call(this, Boolean.TRUE);
        }
        throw new IllegalStateException("调用 isOk() 之前，必须先调用 ok()、fail() 或者 setOk()、setFail() 方法");
    }

    /**
     * 是否失败态；state 既非 ok 也非 fail 时抛 IllegalStateException（旧实现既有行为）
     */
    public boolean isFail() {
        Object state = get(STATE);
        if (STATE_FAIL.equals(state)) {
            return true;
        }
        if (STATE_OK.equals(state)) {
            return false;
        }
        if (okFailHandler != null) {
            return okFailHandler.call(this, Boolean.FALSE);
        }
        throw new IllegalStateException("调用 isFail() 之前，必须先调用 ok()、fail() 或者 setOk()、setFail() 方法");
    }

    /**
     * 设置键值对并返回 this
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public LegacyRet set(Object key, Object value) {
        // 走原始类型 put：旧实现基于原始 HashMap，允许非 String 键原样存入
        ((Map) this).put(key, value);
        return this;
    }

    /**
     * 值非空白串时才设置
     */
    public LegacyRet setIfNotBlank(Object key, String value) {
        if (value != null && value.trim().length() > 0) {
            set(key, value);
        }
        return this;
    }

    /**
     * 值非 null 时才设置
     */
    public LegacyRet setIfNotNull(Object key, Object value) {
        if (value != null) {
            set(key, value);
        }
        return this;
    }

    /**
     * 批量并入一个 Map
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public LegacyRet set(Map map) {
        ((Map) this).putAll(map);
        return this;
    }

    /**
     * 批量并入另一个 Ret
     */
    public LegacyRet set(LegacyRet ret) {
        super.putAll(ret);
        return this;
    }

    /**
     * 删除键并返回 this
     */
    public LegacyRet delete(Object key) {
        super.remove(key);
        return this;
    }

    /**
     * 取值为指定类型；缺键或值为 null 时返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getAs(Object key) {
        return (T) get(key);
    }

    /**
     * 取值为指定类型；缺键或值为 null 时返回默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAs(Object key, T defaultValue) {
        Object value = get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 取值经函数转换；缺键或值为 null 时返回 null
     */
    public <T> T getAs(Object key, Function<Object, T> func) {
        Object value = get(key);
        return value != null ? func.apply(value) : null;
    }

    /**
     * 取值经函数转换；缺键或值为 null 时返回默认值
     */
    public <T> T getAs(Object key, T defaultValue, Function<Object, T> func) {
        Object value = get(key);
        return value != null ? func.apply(value) : defaultValue;
    }

    /**
     * 取字符串；null 值返回 null
     */
    public String getStr(Object key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 取 Integer：<b>强转 Number</b>（非 Number 抛 ClassCastException，与 Kv 不同）
     */
    public Integer getInt(Object key) {
        Number value = (Number) get(key);
        return value != null ? value.intValue() : null;
    }

    /**
     * 取 Long：<b>强转 Number</b>
     */
    public Long getLong(Object key) {
        Number value = (Number) get(key);
        return value != null ? value.longValue() : null;
    }

    /**
     * 取 Double：<b>强转 Number</b>
     */
    public Double getDouble(Object key) {
        Number value = (Number) get(key);
        return value != null ? value.doubleValue() : null;
    }

    /**
     * 取 Float：<b>强转 Number</b>
     */
    public Float getFloat(Object key) {
        Number value = (Number) get(key);
        return value != null ? value.floatValue() : null;
    }

    /**
     * 取 Number：<b>纯强转</b>
     */
    public Number getNumber(Object key) {
        return (Number) get(key);
    }

    /**
     * 取 Boolean：<b>纯强转</b>
     */
    public Boolean getBoolean(Object key) {
        return (Boolean) get(key);
    }

    /**
     * 值是否非 null
     */
    public boolean notNull(Object key) {
        return get(key) != null;
    }

    /**
     * 值是否为 null
     */
    public boolean isNull(Object key) {
        return get(key) == null;
    }

    /**
     * 值是否为 {@code Boolean.TRUE}；非布尔（含 null）返回 false，不抛异常
     */
    public boolean isTrue(Object key) {
        Object value = get(key);
        return value instanceof Boolean && (Boolean) value;
    }

    /**
     * 值是否为 {@code Boolean.FALSE}；非布尔（含 null）返回 false，不抛异常
     */
    public boolean isFalse(Object key) {
        Object value = get(key);
        return value instanceof Boolean && !(Boolean) value;
    }

    /**
     * 序列化为 JSON（等价于旧的 {@code Json.getJson().toJson(this)}）
     */
    public String toJson() {
        return LegacyJsonKit.toJson(this);
    }

    /**
     * 仅当对方也是 Ret 时才比较内容
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof LegacyRet && super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * state 变更回调（对应 5.2.6 的 {@code Func.F30}）。
     *
     * <p>说明：旧实现的该回调字段为包内可见且无公开 setter，EOVA 无法设置，
     * 故本接口在阶段 1 内不会被实现类绑定；保留它是为了语义完整。
     */
    public interface StateWatcher {

        /**
         * 回调：ret 本身、被改的键名、新值
         */
        void call(LegacyRet ret, String key, Object value);
    }

    /**
     * ok/fail 兜底判定回调（对应 5.2.6 的 {@code Func.F21}）。
     */
    public interface OkFailHandler {

        /**
         * 回调：ret 本身、该分支的默认结论
         */
        boolean call(LegacyRet ret, Boolean defaultVal);
    }
}
