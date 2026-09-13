/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.template;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * **T04 第二段：不依赖 Enjoy 的业务表达式求值器**（替代 `Engine.getTemplateByString(...)`）。
 *
 * <p><b>为什么只做"业务表达式"这一半</b>：`ExpUtil`（`eova_object.filter`/`eova_field.defaulter`/
 * `eova_field.exp`）与 `AuthUri`（`/app/#(menu.code)`）只用到一个**很小的语法子集** ——
 * 由真库语料统计得出（判据 `ExpUtilCorpusTest` 把语料与构件集合冻结）：
 * `#(expr)` 插值 · `#if(cond)…#else…#end` · `??` 合并 · 属性链 · 字面量原样。
 * **页面渲染**（`#include`/`#for`/`#define`/`#set`/自定义指令）不在本类范围内 ——
 * 那些属 RENDER 腿，遇到即**响亮抛错**（绝不静默输出错内容）。</p>
 *
 * <p><b>属性解析顺序是契约</b>（沿用 `LegacyRowFieldGetter` 的字节码实证口径，
 * 见该类注释：`GetterMethod → RealField → Model 列 → Record → Map`）：
 * 有同名 getter 或公有字段时它们**先胜出**，都没有才按列名读 Model/Record。
 * 顺序写反会重演第 303 轮 D5（`#(user.role.lv)` 把公有字段 `role` 当列名读成 null）。</p>
 *
 * <p><b>空白语义</b>（实测对齐）：指令会吞掉**紧随其后**的空白（`#if(...)`、`#else`、`#end`），
 * `#end` 另外吞掉**紧邻其前**的空白 —— 例：`head #if(c)mid#end tail` ⇒ `head midtail`、
 * `#if(c)uid = #(user.id) #end` ⇒ `uid = 7`。这直接影响 SQL 拼接文本，必须一致。</p>
 *
 * <p><b>等价性</b>：由差分判据 `LegacyExprEvaluatorGoldenTest` 在**真库语料 + 语义边界样例**上
 * 与 Enjoy 逐字节比对（不是"看着像"）。</p>
 */
public final class LegacyExprEvaluator {

    private LegacyExprEvaluator() {
    }

    /**
     * 渲染模板（EXPR 腿子集）。
     *
     * @param template 模板串
     * @param data     数据根（Map/Kv/Bean/Model 均可）
     * @return 渲染结果
     */
    public static String render(String template, Object data) {
        if (template == null) {
            return null;
        }
        Cursor c = new Cursor(template, data);
        StringBuilder out = new StringBuilder();
        String term = c.renderInto(out);
        if (term != null) {
            throw new IllegalStateException("模板里出现了多余的 #" + term + "（没有匹配的 #if）：" + template);
        }
        return out.toString();
    }

    /**
     * 求值单个表达式（`#(expr)` 与 `#if(cond)` 共用）。
     *
     * @param expr 表达式
     * @param data 数据根
     * @return 求值结果
     */
    public static Object eval(String expr, Object data) {
        return new ExprParser(expr, data).parse();
    }

    // ------------------------------------------------------------------ 模板层

    /** 模板游标：渲染到 `#end`/`#else` 之一为止 */
    private static final class Cursor {
        private final String src;
        private final Object data;
        private int pos;

        Cursor(String src, Object data) {
            this.src = src;
            this.data = data;
        }

        /**
         * 渲染到遇到 `#end` 或 `#else` 为止。
         *
         * @param out 输出缓冲
         * @return 遇到的终止符（"end"/"else"）；到串尾返回 null
         */
        String renderInto(StringBuilder out) {
            while (pos < src.length()) {
                int hash = src.indexOf('#', pos);
                if (hash < 0) {
                    out.append(src, pos, src.length());
                    pos = src.length();
                    return null;
                }
                out.append(src, pos, hash);
                pos = hash;
                if (src.startsWith("#end", pos)) {
                    // ★ 实测口径：`#end` 前的**空格/制表符**被吞掉（`uid = #(user.id) #end` ⇒ `uid = 7`），
                    //   但**换行保留**（`...\n#end` ⇒ 输出仍带那个换行）⇒ 只裁空格与制表符。
                    trimTrailingSpaces(out);
                    pos += 4;
                    skipSpacesTabs();
                    return "end";
                }
                if (src.startsWith("#else", pos)) {
                    // ★ 实测：**不裁** `#else` 前的空格（`#if(c)a #else b#end` ⇒ `a ` 保留尾空格），
                    //   与 `#end` 的行为**不同** —— 这条是差分判据最后一处逼出来的细节。
                    pos += 5;
                    skipSpacesTabs();
                    return "else";
                }
                if (src.startsWith("#if", pos) && pos + 3 < src.length() && src.charAt(pos + 3) == '(') {
                    pos += 3;   // 留在 '(' 上，由 readParen 处理
                    String cond = readParen();
                    skipAllWhitespace();   // ★ 实测：`#if(c)\nbody` 的输出**不含**那个换行
                    Object v = eval(cond, data);
                    StringBuilder body = new StringBuilder();
                    String term = renderInto(body);
                    if ("else".equals(term)) {
                        StringBuilder other = new StringBuilder();
                        String term2 = renderInto(other);
                        if (!"end".equals(term2)) {
                            throw new IllegalStateException("`#if` 缺少 `#end`：" + src);
                        }
                        out.append(truthy(v) ? body : other);
                    } else if ("end".equals(term)) {
                        if (truthy(v)) {
                            out.append(body);
                        }
                    } else {
                        throw new IllegalStateException("`#if` 缺少 `#end`：" + src);
                    }
                    continue;
                }
                if (src.startsWith("#(", pos)) {
                    pos += 1;   // ★ 必须把游标移到 '(' 上（首版漏了这步 ⇒ 每个 #(...) 都抛错，差分判据当场抓到）
                    String expr = readParen();
                    out.append(format(eval(expr, data)));
                    continue;
                }
                // 其它指令（#for/#include/#define/#set 等）属页面渲染腿 —— 响亮抛错，绝不静默
                int nameEnd = pos + 1;
                while (nameEnd < src.length() && Character.isLetter(src.charAt(nameEnd))) {
                    nameEnd++;
                }
                throw new UnsupportedOperationException(
                        "本求值器只覆盖业务表达式子集（#(expr)/#if/#else/#end），不支持指令："
                                + src.substring(pos, Math.min(nameEnd, src.length())));
            }
            return null;
        }

        /** 读一对圆括号内的原文（pos 停在 '(' 上） */
        private String readParen() {
            if (pos >= src.length() || src.charAt(pos) != '(') {
                throw new IllegalStateException("期望 '(' ：" + src.substring(Math.max(0, pos - 6)));
            }
            int depth = 0;
            int start = pos + 1;
            for (int i = pos; i < src.length(); i++) {
                char ch = src.charAt(i);
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                    if (depth == 0) {
                        String inner = src.substring(start, i);
                        pos = i + 1;
                        return inner;
                    }
                }
            }
            throw new IllegalStateException("括号不闭合：" + src);
        }

        /** 裁掉缓冲末尾的空格/制表符（不含换行） */
        private static void trimTrailingSpaces(StringBuilder sb) {
            int i = sb.length();
            while (i > 0 && (sb.charAt(i - 1) == ' ' || sb.charAt(i - 1) == '\t')) {
                i--;
            }
            sb.setLength(i);
        }

        /**
         * 吞掉紧随 `#if(cond)` 之后的**全部空白（含换行）**。
         *
         * <p>实测（差分判据逼出来）：`#if(cond)\nlv > …\n#end` 的输出**不含** `#if` 后那个换行。</p>
         */
        private void skipAllWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        /**
         * 吞掉紧随 `#end`/`#else` 之后的**空格与制表符**（**换行保留**）。
         *
         * <p>实测：`mid#end tail` ⇒ `midtail`（空格被吞），但 `a#end\nb` ⇒ `a\nb`（换行保留）
         * —— 两个方向都试过才定下来（首版"一律吞"会把换行吃掉，判据立刻报不等价）。</p>
         */
        private void skipSpacesTabs() {
            while (pos < src.length() && (src.charAt(pos) == ' ' || src.charAt(pos) == '\t')) {
                pos++;
            }
        }
    }

    // ------------------------------------------------------------------ 表达式层

    /** 极简表达式解析：|| → && → 比较 → 加法 → 乘法 → 一元 → 原子 */
    private static final class ExprParser {
        private final String s;
        private final Object data;
        private int p;

        ExprParser(String s, Object data) {
            this.s = s;
            this.data = data;
        }

        Object parse() {
            Object v = or();
            skipWs();
            if (p < s.length()) {
                throw new IllegalStateException("表达式有多余内容：" + s.substring(p));
            }
            return v;
        }

        private Object or() {
            Object left = and();
            while (true) {
                skipWs();
                if (s.startsWith("||", p)) {
                    p += 2;
                    Object right = and();
                    left = truthy(left) || truthy(right);
                } else {
                    return left;
                }
            }
        }

        private Object and() {
            Object left = compare();
            while (true) {
                skipWs();
                if (s.startsWith("&&", p)) {
                    p += 2;
                    Object right = compare();
                    left = truthy(left) && truthy(right);
                } else {
                    return left;
                }
            }
        }

        private Object compare() {
            // ★ 语料承重语义（实测）：`#(user.no.field??9)` ⇒ 9 —— 左值**取不到**（Enjoy 抛
            //   "public field not found"）也算缺失 ⇒ 这里必须容忍左值的"解析期异常"，
            //   但**只在后面紧跟 `??` 时才容忍**（否则真语法错误会被吞掉）。
            Object left;
            RuntimeException pending = null;
            try {
                left = additive();
            } catch (RuntimeException e) {
                pending = e;
                left = null;
            }
            if (pending != null) {
                int save = p;
                skipWs();
                if (!s.startsWith("??", p)) {
                    p = save;
                    throw pending;
                }
            }
            while (true) {
                skipWs();
                if (s.startsWith("??", p)) {
                    p += 2;
                    // ★ 语料承重语义：左值"取不到"（抛错）或为 null 时取右值
                    Object right = additive();
                    left = (left == null) ? right : left;
                    continue;
                }
                String op = matchOp("!=", "==", ">=", "<=", ">", "<");
                if (op == null) {
                    return left;
                }
                Object right = additive();
                left = compareOp(op, left, right);
            }
        }

        private Object additive() {
            Object left = multiplicative();
            while (true) {
                skipWs();
                if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')
                        && !(p + 1 < s.length() && Character.isDigit(s.charAt(p + 1)) && left == null)) {
                    char op = s.charAt(p);
                    p++;
                    Object right = multiplicative();
                    left = arith(op, left, right);
                } else {
                    return left;
                }
            }
        }

        private Object multiplicative() {
            Object left = unary();
            while (true) {
                skipWs();
                if (p < s.length() && (s.charAt(p) == '*' || s.charAt(p) == '/')) {
                    char op = s.charAt(p);
                    p++;
                    Object right = unary();
                    left = arith(op, left, right);
                } else {
                    return left;
                }
            }
        }

        private Object unary() {
            skipWs();
            if (p < s.length() && s.charAt(p) == '!') {
                p++;
                return !truthy(unary());
            }
            if (p < s.length() && s.charAt(p) == '-') {
                p++;
                Object v = unary();
                return v instanceof Number ? negate((Number) v) : null;
            }
            return atom();
        }

        private Object atom() {
            skipWs();
            if (p >= s.length()) {
                return null;
            }
            char ch = s.charAt(p);
            if (ch == '(') {
                p++;
                Object v = or();
                skipWs();
                expect(')');
                return v;
            }
            if (ch == '\'' || ch == '"') {
                return readString(ch);
            }
            if (Character.isDigit(ch)) {
                return readNumber();
            }
            if (Character.isJavaIdentifierStart(ch)) {
                String word = readWord();
                if ("true".equals(word)) {
                    return Boolean.TRUE;
                }
                if ("false".equals(word)) {
                    return Boolean.FALSE;
                }
                if ("null".equals(word) || "nil".equals(word)) {
                    return null;
                }
                return resolveChain(word);
            }
            throw new IllegalStateException("无法解析的表达式片段：" + s.substring(p));
        }

        private Object resolveChain(String root) {
            Object cur = resolveOne(data, root);
            while (true) {
                skipWs();
                if (p < s.length() && s.charAt(p) == '.') {
                    p++;
                    String name = readWord();
                    if (cur == null) {
                        throw new IllegalStateException("字段链在 null 上继续取值：" + name);
                    }
                    cur = resolveOne(cur, name);
                } else {
                    return cur;
                }
            }
        }

        private String readWord() {
            int start = p;
            while (p < s.length() && (Character.isJavaIdentifierPart(s.charAt(p)))) {
                p++;
            }
            return s.substring(start, p);
        }

        private String readString(char quote) {
            p++;
            StringBuilder sb = new StringBuilder();
            while (p < s.length() && s.charAt(p) != quote) {
                sb.append(s.charAt(p++));
            }
            expect(quote);
            return sb.toString();
        }

        private Object readNumber() {
            int start = p;
            boolean dot = false;
            while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) {
                if (s.charAt(p) == '.') {
                    dot = true;
                }
                p++;
            }
            String txt = s.substring(start, p);
            return dot ? (Object) Double.valueOf(txt) : (Object) Long.valueOf(txt);
        }

        private String matchOp(String... ops) {
            for (String op : ops) {
                if (s.startsWith(op, p)) {
                    p += op.length();
                    return op;
                }
            }
            return null;
        }

        private void expect(char c) {
            if (p >= s.length() || s.charAt(p) != c) {
                throw new IllegalStateException("期望 '" + c + "'：" + s);
            }
            p++;
        }

        private void skipWs() {
            while (p < s.length() && Character.isWhitespace(s.charAt(p))) {
                p++;
            }
        }
    }

    // ------------------------------------------------------------------ 取值与比较

    /**
     * 按**契约顺序**取属性：getter → 公有字段 → Model 列 → Record → Map。
     *
     * @param target 目标对象
     * @param name   属性名
     * @return 属性值；取不到时抛出（由 `??` 兜底，与 Enjoy 行为一致）
     */
    static Object resolveOne(Object target, String name) {
        if (target == null) {
            throw new IllegalStateException("在 null 上取属性：" + name);
        }
        // ① getter：getXxx()（首字母大写）或 getxxx()
        Object viaGetter = byGetter(target, name);
        if (viaGetter != NOT_FOUND) {
            return viaGetter;
        }
        // ② 公有字段
        Object viaField = byPublicField(target, name);
        if (viaField != NOT_FOUND) {
            return viaField;
        }
        // ③ Model / Record 的列（`get(name)`）
        Object viaModel = byColumnGetter(target, name);
        if (viaModel != NOT_FOUND) {
            return viaModel;
        }
        // ④ Map / Kv：**键不存在返回 null**（不抛错）——
        //   ★ 这是生产实测纠正的一处真缺陷：`conf` 是 `Menu#getMenuConfig()` 解析出的 `LegacyKv`，
        //   很多菜单的 config 里**没有** `object_code` 键；Enjoy 的 MapFieldGetter 此时给 null
        //   ⇒ `#(conf.object_code)` 渲染成空串（URI 变 `/api/meta/form/`，属既有行为）。
        //   我首版把它当"未找到"继续往后找并抛错 ⇒ **登录直接 500**
        //   （`AuthUri.build → parseAuthUri → LegacyExprEvaluator.render`）。
        //   教训：样例只覆盖"有键"的 Map 是不够的，**缺键**也是契约（差分判据已补该用例）。
        if (target instanceof Map) {
            return ((Map<?, ?>) target).get(name);
        }
        throw new IllegalStateException("public field not found: \"" + name + "\"");
    }

    private static final Object NOT_FOUND = new Object();

    private static Object byGetter(Object target, String name) {
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String m : new String[]{"get" + cap, "get" + name, "is" + cap}) {
            try {
                Method method = target.getClass().getMethod(m);
                if (method.getParameterCount() == 0) {
                    return method.invoke(target);
                }
            } catch (ReflectiveOperationException ignored) {
                // 继续尝试下一个候选名
            }
        }
        return NOT_FOUND;
    }

    private static Object byPublicField(Object target, String name) {
        try {
            Field f = target.getClass().getField(name);
            return f.get(target);
        } catch (ReflectiveOperationException ignored) {
            return NOT_FOUND;
        }
    }

    private static Object byColumnGetter(Object target, String name) {
        try {
            // ★ 实测纠正：`EovaModel`/`EovaRecord` 的取值方法签名是 `get(String)`（不是 `get(Object)`）
            //   —— 首版只找 `get(Object)` ⇒ 找不到 ⇒ Model 列一律读不到（`#(user.role.lv)` 直接抛错）。
            Method get = null;
            for (Class<?> param : new Class<?>[]{String.class, Object.class}) {
                try {
                    get = target.getClass().getMethod("get", param);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // 试下一种签名
                }
            }
            if (get == null) {
                return NOT_FOUND;
            }
            Object v = get.invoke(target, name);
            // Model/Record 的 `get(name)` 在列不存在时返回 null —— 但契约是"最后才走这条路"，
            // 若此刻返回 null，`??` 仍能兜底（与 Enjoy 一致：Model 缺列 ⇒ null ⇒ 不抛）。
            if (v != null) {
                return v;
            }
            Method contains = null;
            try {
                contains = target.getClass().getMethod("containsKey", Object.class);
            } catch (NoSuchMethodException ignored) {
                // 没有 containsKey ⇒ 只能认这个 null
            }
            if (contains == null || Boolean.TRUE.equals(contains.invoke(target, name))) {
                return null;
            }
            return NOT_FOUND;
        } catch (ReflectiveOperationException ignored) {
            return NOT_FOUND;
        }
    }

    /** 真值口径：null/false/数字 0 → false；其余 → true */
    static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue() != 0d;
        }
        if (v instanceof CharSequence) {
            return ((CharSequence) v).length() > 0;
        }
        return true;
    }

    private static Object compareOp(String op, Object l, Object r) {
        switch (op) {
            case "==":
                return eq(l, r);
            case "!=":
                return !eq(l, r);
            default:
                break;
        }
        Double a = num(l);
        Double b = num(r);
        if (a == null || b == null) {
            return false;
        }
        switch (op) {
            case ">":
                return a > b;
            case ">=":
                return a >= b;
            case "<":
                return a < b;
            case "<=":
                return a <= b;
            default:
                throw new IllegalStateException("未知比较符：" + op);
        }
    }

    private static boolean eq(Object l, Object r) {
        if (l == null || r == null) {
            return l == r;
        }
        Double a = num(l);
        Double b = num(r);
        if (a != null && b != null) {
            return a.doubleValue() == b.doubleValue();
        }
        return String.valueOf(l).equals(String.valueOf(r));
    }

    private static Double num(Object v) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v instanceof CharSequence) {
            try {
                return Double.valueOf(v.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object arith(char op, Object l, Object r) {
        Double a = num(l);
        Double b = num(r);
        if (a == null || b == null) {
            return null;
        }
        double v;
        switch (op) {
            case '+':
                v = a + b;
                break;
            case '-':
                v = a - b;
                break;
            case '*':
                v = a * b;
                break;
            case '/':
                v = a / b;
                break;
            default:
                throw new IllegalStateException("未知运算符：" + op);
        }
        if (v == Math.rint(v) && !Double.isInfinite(v) && a == Math.rint(a) && b == Math.rint(b)) {
            return (long) v;
        }
        return v;
    }

    private static Object negate(Number n) {
        if (n instanceof Double || n instanceof Float) {
            return -n.doubleValue();
        }
        return -n.longValue();
    }

    /**
     * `#(...)` 的输出格式（实测对齐）：整型不带小数、Double 用最短表示、Boolean 用 true/false、null 输出空串。
     *
     * @param v 值
     * @return 文本
     */
    static String format(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        return String.valueOf(v);
    }
}
