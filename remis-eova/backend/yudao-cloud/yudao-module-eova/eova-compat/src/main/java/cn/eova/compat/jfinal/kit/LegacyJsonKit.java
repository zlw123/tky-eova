/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.kit.JsonKit} + {@code com.jfinal.json.JFinalJsonKit}
 * 的等价 port（阶段 1 `S-JSON` 切片）。
 *
 * <p><b>为什么必须 port（R40）：</b>新栈用的 {@code com.jfinal:enjoy:5.3.0} <b>整个不提供</b>
 * {@code com.jfinal.kit.JsonKit}（EOVA 调用 26 处）。更要紧的是序列化<b>不是 fastjson</b>：
 * <pre>
 *   JsonKit.toJson(Object)
 *     -&gt; com.jfinal.json.Json.getJson()          // 静态初始化即 new JFinalJsonFactory()
 *     -&gt; MixedJson / JFinalJson
 *     -&gt; JFinalJsonKit                            // 手写序列化器，30+ 类型处理器
 * </pre>
 * 按 R5，序列化行为直接决定 {@code state,msg,data} envelope，故必须逐处理器等价。
 *
 * <p>ported from: com.jfinal.kit.JsonKit + com.jfinal.json.JFinalJsonKit（第三方制品）
 * <br>source artifact: com.jfinal:jfinal:5.2.6
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08（锁定上述制品版本）
 *
 * <p><b>类型分派顺序（照抄 {@code JFinalJsonKit.createToJson} 的判定顺序，顺序即语义）：</b>
 * {@code String} → {@code Number}(Integer/Long/Double/Float/其它) → {@code Boolean} →
 * {@code Character} → {@code Enum} → {@code Date}(Timestamp/Time/其它) →
 * {@code Temporal}(LocalDateTime/LocalDate/LocalTime) → 记录容器 → {@code Map} →
 * {@code Collection} → 数组 → {@code Enumeration} → {@code Iterator} → {@code Iterable} →
 * <b>{@code buildBeanToJson}（POJO 反射 getter）</b> → 兜底 {@code UnknownToJson}（{@code toString()}）。
 * <b>注意 {@code java.sql.Time}/{@code Timestamp} 是按精确类注册的，先于 {@code java.util.Date} 命中</b>，
 * 故它们不会走日期时间格式；而 {@code java.sql.Date} 未注册，向上命中 {@code Date}，
 * 走完整 {@code yyyy-MM-dd HH:mm:ss}（实测输出 {@code "2019-09-20 00:00:00"}）。
 *
 * <p><b>Bean 分支 + 嵌套深度上限（第 76 轮补齐，修 R59）：</b>
 * 第 55 轮首版<b>只实现了兜底 {@code UnknownToJson}</b>，把"未注册类型"一律按
 * {@code toString()} 写出 —— 那是**错的**：{@code createToJson} 的倒数第二步是
 * {@code buildBeanToJson(value)}（反射 getter 序列化），只有它返回 {@code null}
 * （类上没有任何 0 参非 void 的 {@code getXxx/isXxx}）才落到 {@code UnknownToJson}。
 * 实测差异：同一个 POJO，旧侧 {@code {"code":0,"msg":"ok","data":null}}，新侧 {@code cn.eova…@hash}。
 * <ul>
 *   <li><b>取值器判定</b>（{@code buildBeanToJson} 逐字节）：{@code parameterCount == 0} 且
 *       返回类型 {@code != void}；{@code name.indexOf("get") == 0 && name.length() > 3} ⇒ 取
 *       {@code substring(3)}；否则 {@code name.indexOf("is") == 0 && name.length() > 2} ⇒ 取
 *       {@code substring(2)}（<b>{@code is} 分支不校验返回类型</b>，故 {@code isXxx()} 之外的
 *       {@code island()} 这类名字同样会被收进来 —— 既有行为，原样保留）；{@code getClass} 被排除。</li>
 *   <li><b>键名</b>：{@code StrKit.firstCharToLowerCase(裁剪名)} —— 仅当首字符在 {@code 'A'..'Z'}
 *       时整体小写化首字符；<b>是按取值器名而非字段名</b>（{@code isOk()} → {@code ok}）。</li>
 *   <li><b>键名不转义</b>（{@code JsonResult.addStrNoEscape} = 加引号但不转义），与 Map 键
 *       （{@code addMapKey} = 加引号<b>并</b>转义）不同。</li>
 *   <li><b>键序不排序</b>：用 {@code getClass().getMethods()} 的返回顺序（与旧侧同 JVM 同序才能逐字比对）。</li>
 *   <li><b>取值器抛出的异常</b>：{@code Method.invoke} 的 {@code ReflectiveOperationException}
 *       被包成 {@code RuntimeException(e)} 抛出（Java 17 下 JDK 内部类型的不可导出成员即此路径，见 R42）。</li>
 *   <li><b>深度上限</b>：{@code JFinalJson.defaultConvertDepth = 16}，顶层调用即以 16 进入；
 *       每个容器处理器进入时执行 {@code checkDepth(depth--)} ⇒ 深度 {@code < 0} 时写出 {@code null}
 *       且不再展开该容器；因此嵌套超过 16 层的部分退化为 {@code null}。</li>
 * </ul>
 *
 * <p><b>本单元为部分 port，明确未包含：</b>
 * <ol>
 *   <li>{@code toJson(Object, int maxBufferSize)} —— EOVA 无调用方，同样不声明。</li>
 *   <li>{@code JFinalJsonKit} 的 {@code treatModelAsBean} / {@code skipNullValueField} /
 *       {@code modelAndRecordFieldNameConverter} 三个可配置开关 —— EOVA 未配置，按默认行为实现
 *       （{@code treatModelAsBean=false}、{@code skipNullValueField=false}）。</li>
 *   <li>{@code toJsonFactory} 自定义钩子与 {@code cache}（类 → 处理器的 SyncWriteMap）——
 *       EOVA 未配置自定义工厂（静态初始化为 {@code null}），且缓存只是性能手段、不改变输出。
 *       注意：<b>{@code toJsonFactory} 非空时优先于整条 instanceof 链</b>，本类未暴露该开关，
 *       故若将来有人调用 {@code JFinalJson.setToJsonFactory} 需要一并补做。</li>
 * </ol>
 *
 * <p><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li><b>转义集合是特定的三段</b>：{@code c <= 31} ∪ {@code [127,159]} ∪ {@code [8192,8447]}，
 *       其中 7 个字符用短形式（{@code \" \\ \b \f \n \r \t}），其余走 U+XXXX 转义（十六进制<b>大写</b>、
 *       零填充到 4 位）。<b>中文不被转义</b>（CJK 起点 19968 不在三段内），
 *       但 U+2000–U+20FF 会被转义 —— 空转义集合会让输出<b>变成非法 JSON</b>（裸控制字符）。</li>
 *   <li>日期时间一律用 {@code yyyy-MM-dd HH:mm:ss}（{@code Json.defaultDatePattern} 默认值），
 *       而<b>不是</b> {@code Date.toString()}；{@code LocalDate} 用 {@code yyyy-MM-dd}，
 *       {@code LocalTime}/{@code java.sql.Time} 用 {@code HH:mm:ss}。</li>
 *   <li>嵌套 {@code Map}/{@code Collection}/数组按<b>结构</b>展开，不倒成字符串。</li>
 *   <li><b>POJO 也按结构展开</b>（Bean 分支）：{@code {"code":0,"msg":"ok","data":null}}，
 *       而不是 {@code toString()}。企业侧 {@code /router/eova/*} 的应答体即 {@code ApiResponse} POJO，
 *       这条是<b>对外契约</b>。</li>
 *   <li>键序<b>不承诺</b>（§3.8 第 3 条：SP6 实测旧实现 toJson 键序非插入序，故键序非契约）。</li>
 *   <li>{@code null} 值序列化为 {@code null}，不跳过。</li>
 * </ol>
 */
public final class LegacyJsonKit {

    /** jfinal {@code JFinalJson.defaultConvertDepth} 的默认值（静态初始化 {@code bipush 16}） */
    private static final int DEFAULT_CONVERT_DEPTH = 16;

    /** jfinal {@code JFinalJsonKit.skipNullValueField} 的默认值（静态初始化 {@code iconst_0}） */
    private static final boolean SKIP_NULL_VALUE_FIELD = false;

    /** 日期时间格式（对应 {@code Json.defaultDatePattern} 的默认值） */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 纯日期格式（{@code LocalDate} 专用） */
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    /** 纯时间格式（{@code LocalTime} 与 {@code java.sql.Time} 专用） */
    private static final String TIME_PATTERN = "HH:mm:ss";

    /** 日期格式化器（SimpleDateFormat 非线程安全，按线程持有） */
    private static final ThreadLocal<SimpleDateFormat> DATE_TIME_FMT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat(DATE_TIME_PATTERN));

    /** 纯时间格式化器 */
    private static final ThreadLocal<SimpleDateFormat> TIME_FMT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat(TIME_PATTERN));

    /** LocalDateTime 格式化器（不可变、线程安全） */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    private LegacyJsonKit() {
    }

    /**
     * 对象 → JSON 字符串（与 jfinal 5.2.6 {@code JsonKit.toJson(Object)} 等价）
     */
    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, DEFAULT_CONVERT_DEPTH);
        return sb.toString();
    }

    /**
     * JSON 字符串 → 对象（等价于 jfinal {@code JsonKit.parse(String, Class)}）。
     *
     * <p><b>为什么用 fastjson 是忠实的、而非"降级"：</b>
     * 实测 jfinal 的 {@code Json.getJson()} 返回 {@code MixedJson}，而
     * <b>{@code MixedJson.parse} 与 {@code MixedJson.toJson} 走的是两条不同路径</b>：
     * <ul>
     *   <li>{@code toJson} → <b>JFinalJson</b>（手写序列化器，本类已逐处理器固化）</li>
     *   <li>{@code parse} → <b>{@code FastJson.parse}</b> → {@code JSON.parseObject(json, type)}</li>
     * </ul>
     * 故反序列化侧直接用 fastjson 与旧栈<b>同构</b>。这条不对称（写用手写器、读用 fastjson）
     * 是旧实现的既有形态，不应"统一"成同一套。
     *
     * <p><b>本方法是刻意延后补上的：</b>{@code S-JSON} 切片首版<b>不声明</b>它，
     * 以便 port 消费者时在<b>编译期</b>报错、强制补做，而不是运行期静默失败。
     * 消费者（{@code Menu.getMenuConfig}）现已到位，故补齐。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 反序列化结果
     */
    public static <T> T parse(String json, Class<T> type) {
        return com.alibaba.fastjson.JSON.parseObject(json, type);
    }

    /**
     * 按 {@code JFinalJsonKit.createToJson} 的判定顺序分派并写出。
     *
     * @param sb    输出
     * @param value 待写出对象
     * @param depth 剩余嵌套深度（顶层 = {@link #DEFAULT_CONVERT_DEPTH}）；
     *              容器处理器以 {@code checkDepth(depth--)} 进入，子元素拿到的已是 {@code depth-1}
     */
    private static void write(StringBuilder sb, Object value, int depth) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof String || value instanceof Character) {
            appendString(sb, value.toString());
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            // 【第 76 轮纠错 1】旧实现按精确类分派：Double/Float 各有专门处理器，且
            // isInfinite() || isNaN() 一律写出 null（JSON 没有 NaN/Infinity 字面量）；
            // 其余 Number 走 addNumber ⇒ toString()，Boolean 走 addBoolean ⇒ 原样。
            if (value instanceof Double) {
                Double d = (Double) value;
                sb.append(d.isInfinite() || d.isNaN() ? "null" : String.valueOf(d.doubleValue()));
                return;
            }
            if (value instanceof Float) {
                Float f = (Float) value;
                sb.append(f.isInfinite() || f.isNaN() ? "null" : String.valueOf(f.floatValue()));
                return;
            }
            if (value instanceof Number) {
                sb.append(value.toString());
                return;
            }
            sb.append(((Boolean) value).booleanValue());
            return;
        }
        if (value instanceof Enum) {
            // 【第 76 轮纠错 2】旧实现是 addEnum ⇒ Enum.toString()（不是 name()）：
            // 覆写过 toString() 的枚举会输出覆写后的文本
            sb.append('"').append(((Enum<?>) value).toString()).append('"');
            return;
        }
        // 精确类优先：Time / Timestamp 先于 Date 命中
        if (value instanceof Time) {
            sb.append('"').append(TIME_FMT.get().format((Time) value)).append('"');
            return;
        }
        if (value instanceof Timestamp) {
            sb.append('"').append(DATE_TIME_FMT.get().format((Timestamp) value)).append('"');
            return;
        }
        if (value instanceof Date) {
            // java.sql.Date 也落在此分支（未单独注册），故输出带 00:00:00
            sb.append('"').append(DATE_TIME_FMT.get().format((Date) value)).append('"');
            return;
        }
        if (value instanceof LocalDateTime) {
            // 直接按 pattern 格式化，不经 Date 中转（避免系统时区引入偏差）
            sb.append('"').append(((LocalDateTime) value).format(DATE_TIME_FORMATTER)).append('"');
            return;
        }
        if (value instanceof LocalDate) {
            sb.append('"').append(((LocalDate) value)).append('"');
            return;
        }
        if (value instanceof LocalTime) {
            sb.append('"').append(((LocalTime) value)).append('"');
            return;
        }
        // 【第 76 轮纠错】旧版本此处有一条 `instanceof Temporal -> toString()` 的兜底，
        // 那是【错的】：createToJson 的 Temporal 分支是**嵌套判定**（Temporal 内再判
        // LocalDateTime/LocalDate/LocalTime），三者都不匹配时**继续往下走**
        // （Model → Map → Collection → … → buildBeanToJson），并非按 toString 写出。
        // 故 ZonedDateTime/Instant/OffsetDateTime 等未注册类型会走 Bean 分支。
        // ★ Model 分支（第 248 轮补齐 —— 此前**只有声明没有实现**，属未声明的行为缺口）：
        //   旧 jfinal `JFinalJsonKit.createToJson` 的顺序是 `!treatModelAsBean && value instanceof Model`
        //   → `ModelToJson` → `CPI.getAttrs(model)` → `modelAndRecordToJson(...)` ⇒ **模型按属性写出**，
        //   与 Record 走同一条键转换路径；`treatModelAsBean` 默认 false 且 EOVA 未配置（本类第 78-81 行
        //   已声明该默认值）⇒ 必须走本分支，而不是落到 JavaBean 分支。
        //   实测症状（r248）：缺本分支时 `Button`/`Menu` 被当普通 bean，序列化成 `{"dao":…,"configured":…}`
        //   ——`btnList[].ui`（DES-004 §2.1 契约）直接拿不到；旧栈实测 `/api/home/menu` 的 `menus[0]`
        //   键是 `code/name/icon/id/parent_id/…`（模型属性），可作为外部对照。
        //   顺序也在对齐旧实现：Model 在 Record（本移植的 `JsonColumns`）**之前**。
        if (value instanceof cn.eova.db.EovaModel) {
            if (checkDepth(sb, depth--)) {
                return;
            }
            writeMap(sb, attrsOf((cn.eova.db.EovaModel<?>) value), depth);
            return;
        }
        if (value instanceof JsonColumns) {
            if (checkDepth(sb, depth--)) {
                return;
            }
            writeMap(sb, ((JsonColumns) value).jsonColumns(), depth);
            return;
        }
        if (value instanceof Map) {
            if (checkDepth(sb, depth--)) {
                return;
            }
            writeMap(sb, (Map<?, ?>) value, depth);
            return;
        }
        if (value instanceof Collection) {
            if (checkDepth(sb, depth--)) {
                return;
            }
            writeIterable(sb, ((Collection<?>) value).iterator(), depth);
            return;
        }
        if (value.getClass().isArray()) {
            // 【第 76 轮纠错 3】旧实现用 `object.getClass().isArray()`（ArrayToJson + ArrayIterator），
            // 即**基本类型数组同样按结构展开**（`int[]` → [1,2,3]）。
            // 此前只判 `instanceof Object[]`，于是 int[]/char[]/double[] 落到兜底变成 "[I@hash"。
            if (checkDepth(sb, depth--)) {
                return;
            }
            writeArray(sb, value, depth);
            return;
        }
        if (value instanceof Enumeration) {
            if (checkDepth(sb, depth--)) {
                return;
            }
            writeIterable(sb, new Iterator<Object>() {
                @Override
                public boolean hasNext() {
                    return ((Enumeration<?>) value).hasMoreElements();
                }

                @Override
                public Object next() {
                    return ((Enumeration<?>) value).nextElement();
                }
            }, depth);
            return;
        }
        if (value instanceof Iterator) {
            if (checkDepth(sb, depth--)) {
                return;
            }
            writeIterable(sb, (Iterator<?>) value, depth);
            return;
        }
        if (value instanceof Iterable) {
            if (checkDepth(sb, depth--)) {
                return;
            }
            writeIterable(sb, ((Iterable<?>) value).iterator(), depth);
            return;
        }
        // Bean 分支（对应 createToJson 的倒数第二步 buildBeanToJson）：
        // 只有"类上没有任何 0 参非 void 的 getXxx/isXxx"时才落到下面的兜底
        if (checkDepth(sb, depth--)) {
            return;
        }
        if (!writeBean(sb, value, depth)) {
            // 兜底：按字符串写出（JFinalJsonKit$UnknownToJson）
            appendString(sb, value.toString());
        }
    }

    /**
     * 深度检查（等价 {@code JFinalJsonKit.checkDepth}）：深度用尽时写出 {@code null} 并告知调用方立即返回。
     *
     * <p>调用点必须写成 {@code if (checkDepth(sb, depth--))} —— 旧字节码是
     * {@code checkDepth(depth--, ret)}：<b>判据拿到的是减一之前的值，子元素拿到的是减一之后的值</b>。</p>
     *
     * @param sb    输出
     * @param depth 进入该容器时的剩余深度
     * @return true 表示已写出 {@code null}，调用方必须立即返回
     */
    private static boolean checkDepth(StringBuilder sb, int depth) {
        if (depth < 0) {
            sb.append("null");
            return true;
        }
        return false;
    }

    /**
     * Bean 分支（等价 {@code JFinalJsonKit$BeanToJson.toJson}）。
     *
     * @param sb    输出
     * @param bean  待序列化对象
     * @param depth 已减一的剩余深度（子值用）
     * @return false 表示该类没有可序列化取值器（调用方应走 {@code UnknownToJson}）
     */
    private static boolean writeBean(StringBuilder sb, Object bean, int depth) {
        Method[] getters = beanGetters(bean.getClass());
        if (getters == null) {
            return false;
        }
        sb.append('{');
        boolean first = true;
        for (Method m : getters) {
            Object v;
            try {
                v = m.invoke(bean);
            } catch (ReflectiveOperationException e) {
                // 旧字节码异常表：ReflectiveOperationException -> RuntimeException(e)
                throw new RuntimeException(e);
            }
            if (v == null && SKIP_NULL_VALUE_FIELD) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                sb.append(',');
            }
            // 键名：addStrNoEscape —— 加引号但【不转义】（与 Map 键的 addMapKey 不同）
            sb.append('"').append(beanFieldName(m)).append('"');
            sb.append(':');
            // 旧实现此处 getToJson(value) 后递归；null 走 addNull ⇒ 与 write 的 null 分支一致
            write(sb, v, depth);
        }
        sb.append('}');
        return true;
    }

    /**
     * 收集可序列化取值器（等价 {@code JFinalJsonKit.buildBeanToJson}）。
     *
     * <p>判定逐字节取自旧字节码：{@code parameterCount == 0} 且返回类型 {@code != void}；
     * {@code indexOf("get") == 0 && length > 3} ⇒ {@code substring(3)}（{@code Class} 除外）；
     * 否则 {@code indexOf("is") == 0 && length > 2} ⇒ {@code substring(2)}。
     * <b>{@code is} 分支不校验返回类型</b>，故 {@code island()} 这类名字也会被收入 —— 原样保留。</p>
     *
     * @param type 目标类
     * @return 取值器数组；无可用取值器时返回 null（旧实现返回 null，调用方落到 UnknownToJson）
     */
    private static Method[] beanGetters(Class<?> type) {
        java.util.List<Method> getters = new java.util.ArrayList<>();
        for (Method m : type.getMethods()) {
            if (m.getParameterCount() != 0 || m.getReturnType() == Void.TYPE) {
                continue;
            }
            String name = m.getName();
            if (name.indexOf("get") == 0 && name.length() > 3) {
                if (!name.substring(3).equals("Class")) {
                    getters.add(m);
                }
            } else if (name.indexOf("is") == 0 && name.length() > 2) {
                getters.add(m);
            }
        }
        return getters.isEmpty() ? null : getters.toArray(new Method[0]);
    }

    /**
     * 取值器 → JSON 键名（等价 {@code StrKit.firstCharToLowerCase(裁剪名)}）。
     *
     * @param m 取值器
     * @return 键名
     */
    private static String beanFieldName(Method m) {
        String name = m.getName();
        String field = name.indexOf("get") == 0 && name.length() > 3
                ? name.substring(3) : name.substring(2);
        return firstCharToLowerCase(field);
    }

    /**
     * 首字符小写化（与 jfinal {@code StrKit.firstCharToLowerCase} 逐字节等价）。
     *
     * <p>旧实现只管 {@code 'A'..'Z'}（{@code c += 32}），其余原样返回；
     * 无空串保护 —— 本类只会传入长度 &gt; 0 的裁剪名，故不会触发越界。</p>
     *
     * @param str 输入
     * @return 首字符小写化后的字符串
     */
    private static String firstCharToLowerCase(String str) {
        char firstChar = str.charAt(0);
        if (firstChar >= 'A' && firstChar <= 'Z') {
            char[] arr = str.toCharArray();
            arr[0] += 32;
            return new String(arr);
        }
        return str;
    }

    /**
     * 取模型属性（对应 jfinal `JFinalJsonKit$ModelToJson` 里的 `CPI.getAttrs(model)`）。
     *
     * <p>用 {@code _getAttrsEntrySet()} 而不是反射读字段：那是模型自己暴露的属性视图
     * （jfinal 的 {@code Model.getAttrs()} 等价物），键序**不承诺**（§3.8 第 3 条）。</p>
     *
     * @param model 模型
     * @return 属性映射（保持模型给出的顺序）
     */
    private static Map<String, Object> attrsOf(cn.eova.db.EovaModel<?> model) {
        Map<String, Object> attrs = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> e : model._getAttrsEntrySet()) {
            attrs.put(e.getKey(), e.getValue());
        }
        return attrs;
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map, int depth) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            write(sb, e.getValue(), depth);
        }
        sb.append('}');
    }

    private static void writeIterable(StringBuilder sb, Iterator<?> it, int depth) {
        sb.append('[');
        boolean first = true;
        while (it.hasNext()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            write(sb, it.next(), depth);
        }
        sb.append(']');
    }

    /**
     * 数组写出（等价 {@code JFinalJsonKit$ArrayToJson} + {@code $ArrayIterator}）。
     *
     * <p>用 {@code java.lang.reflect.Array} 取值，故基本类型数组与对象数组走同一条路径
     * （旧字节码 {@code ArrayIterator.next()} = {@code Array.get(array, index++)}）。</p>
     *
     * @param sb    输出
     * @param array 任意数组（基本类型亦可）
     * @param depth 已减一的剩余深度
     */
    private static void writeArray(StringBuilder sb, Object array, int depth) {
        sb.append('[');
        boolean first = true;
        int len = java.lang.reflect.Array.getLength(array);
        for (int i = 0; i < len; i++) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            write(sb, java.lang.reflect.Array.get(array, i), depth);
        }
        sb.append(']');
    }

    /** 写出带引号并转义的字符串 */
    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        escape(s, sb);
        sb.append('"');
    }

    /**
     * 字符串转义：与 {@code JFinalJsonKit.escape} 逐字符等价。
     *
     * <p>转义集合为三段并集，7 个常用字符用短形式，其余用 U+XXXX 转义。
     * 注意 {@code >= 127} 的字符并非一律转义 —— 只有 {@code [127,159]} 与 {@code [8192,8447]}
     * 两段才转义，故中文（CJK）原样输出。
     */
    static void escape(String s, StringBuilder sb) {
        if (s == null) {
            return;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c <= 31 || (c >= 127 && c <= 159) || (c >= 8192 && c <= 8447)) {
                        String hex = Integer.toHexString(c).toUpperCase();
                        sb.append("\\u");
                        for (int k = hex.length(); k < 4; k++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
    }

    /**
     * 可被 JSON 序列化器按"记录容器"处理的类型（对应 jfinal 的 {@code RecordToJson}）。
     *
     * <p>存在理由：{@code eova-compat} 不能反向依赖 {@code eova-db-adapter}，
     * 故不能直接引用 {@code EovaRecord}。由记录容器实现本接口，
     * 使<b>嵌套在 Kv/Map 里</b>的记录也能按结构展开，而非被兜底成字符串。
     */
    public interface JsonColumns {

        /**
         * 返回参与序列化的列（键值对）
         */
        Map<String, Object> jsonColumns();
    }
}
