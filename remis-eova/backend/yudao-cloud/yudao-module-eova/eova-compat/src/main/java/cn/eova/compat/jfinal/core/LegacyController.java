/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.core;

import java.util.Date;
import java.util.Map;

import cn.eova.compat.jfinal.core.converter.LegacyTypeConverter;
import cn.eova.compat.jfinal.kit.LegacyStrKit;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyRenderManager;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.core.Controller} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.core.Controller}（jfinal 5.2.6）。
 * EOVA 的 31 个 Controller 类（含 {@code BaseController}）全部继承本类。</p>
 *
 * <p><b>方法集口径：旧类 81 个方法，EOVA 实际只调用 28 个</b>（全树普查，见
 * {@code MvcFoundationGoldenTest}）。本类<b>分批实现</b>，每一批都由判据把
 * "已实现的方法集"钉死，未实现部分<b>显式声明</b>而非留成看似可用的空壳：</p>
 *
 * <table border="1">
 *   <tr><th>批次</th><th>内容</th><th>状态</th></tr>
 *   <tr><td>W1a</td><td>请求/参数/属性/Kv/rawData/Cookie 无关部分 + {@code render(Render)} 赋值语义</td>
 *       <td><b>本批已实现</b></td></tr>
 *   <tr><td>W1b</td><td>{@code getLong}/{@code getCookie} 族（已完成）；{@code getDate} 族
 *       （第 59 轮完成 —— 阻塞它的 {@code TypeConverter} 已在第 57 轮 port）；
 *       剩余 {@code getFile}/{@code getModel}</td><td>部分待</td></tr>
 *   <tr><td>W2</td><td>{@code render(String)} / {@code renderJson} / {@code renderText} /
 *       {@code renderHtml} / {@code renderError} / {@code renderTemplate} / {@code redirect}
 *       （全部经渲染工厂）</td><td><b>已完成</b></td></tr>
 * </table>
 *
 * <p><b>本批已逐条取自旧字节码的语义（含 4 处易错点）：</b>
 * <ol>
 *   <li>{@code getPara(String)}：取到的值<b>为空串时返回 null</b>（不是空串）。</li>
 *   <li>{@code getPara(int)}：{@code index < 0} 时回落到 {@link #getPara()}；
 *       urlPara 为 null 或空串时用空数组；否则按 {@code "-"} 切分；
 *       切分后<b>逐段把空串就地改写为 null</b>（改写发生在<b>缓存数组</b>上，故只做一次）。</li>
 *   <li>{@code toInt}：{@code "N"}/{@code "n"} 前缀表示 <b>取负</b>
 *       （{@code "N5"} → {@code -5}），<b>不是</b> "null" —— 这是 jfinal 为规避
 *       URL 里 {@code "-"} 被当作 urlPara 分隔符而设的约定。失败时抛
 *       {@link LegacyActionException}(400, 错误渲染, 固定消息)。</li>
 *   <li>{@code set(name,value)} 与 {@code setAttr(name,value)} <b>实现完全相同</b>
 *       （都只做 {@code request.setAttribute}）—— 旧实现如此，不得"顺手合并"或"顺手区分"。</li>
 * </ol>
 */
public class LegacyController {

    /** 空 urlPara 数组（旧实现为 static final，避免每次新建） */
    private static final String[] NULL_URL_PARA_ARRAY = new String[0];

    /** urlPara 分隔符（旧 {@code Const.DEFAULT_URL_PARA_SEPARATOR} = "-"） */
    private static final String URL_PARA_SEPARATOR = "-";

    /**
     * action 元信息 —— <b>包级私有</b>，与旧实现一致（jfinal 的
     * {@code Controller.action} 同样是包级字段，由同包的 {@code ActionHandler} 直接写入）。
     * 故<b>不</b>提供 public 的 setter/getter —— 那会新增旧实现没有的对外成员。
     */
    LegacyAction action;

    private HttpServletRequest request;

    private HttpServletResponse response;

    private String urlPara;

    private String[] urlParaArray;

    private String rawData;

    /**
     * 上传部件容器（宿主/框架在 action 执行前注入）。
     *
     * <p>对应旧实现"按需把 request 包成 {@code MultipartRequest}"的那一半：新栈的
     * multipart <b>解析</b>由 Spring 完成，而<b>落盘</b>语义（最终目录/命名/白名单）
     * 在 {@link cn.eova.compat.jfinal.upload.LegacyMultipartRequest} 内，故此处只持有句柄。</p>
     */
    private cn.eova.compat.jfinal.upload.LegacyMultipartRequest multipartRequest;

    /** 待渲染对象；{@code render*} 族只负责赋值，真正渲染由框架在 action 返回后进行 */
    private LegacyRender render;

    /**
     * 回收本实例（旧实现由 ControllerFactory 在每次请求前后调用，
     * 与 {@code Controller} 实例复用配套）。逐字段置空，顺序与旧字节码一致。
     */
    protected void _clear_() {
        action = null;
        request = null;
        response = null;
        urlPara = null;
        urlParaArray = null;
        render = null;
        rawData = null;
        multipartRequest = null;
    }

    /**
     * 注入上传部件容器（宿主在 action 执行前调用）。
     *
     * @param multipartRequest 部件容器
     */
    public void setMultipartRequest(cn.eova.compat.jfinal.upload.LegacyMultipartRequest multipartRequest) {
        this.multipartRequest = multipartRequest;
    }

    /**
     * 取上传部件容器。
     *
     * @return 部件容器；未注入时为 null
     */
    public cn.eova.compat.jfinal.upload.LegacyMultipartRequest getMultipartRequest() {
        return multipartRequest;
    }

    // ---------------- 验证码（jfinal renderCaptcha/validateCaptcha 族） ----------------

    /**
     * 设置验证码渲染（逐字节等价 jfinal {@code Controller.renderCaptcha()}）。
     *
     * <p>旧实现只做一件事：{@code render = renderManager.getRenderFactory().getCaptchaRender();}
     * —— 真正生成/校验在 {@link cn.eova.compat.jfinal.captcha.LegacyCaptchaRender}。</p>
     */
    public void renderCaptcha() {
        this.render = LegacyRenderManager.getRenderFactory().getCaptchaRender();
    }

    /**
     * 校验验证码（逐字节等价 jfinal {@code Controller.validateCaptcha(String)}）。
     *
     * <p><b>注意参数语义</b>：入参是<b>表单字段名</b>（EOVA 传 {@code "captcha"}），
     * 旧实现是 {@code CaptchaRender.validate(this, getPara(name))}；Cookie 名固定取
     * {@code CaptchaRender.captchaName}（{@code _jfinal_captcha}），与入参无关。</p>
     *
     * @param name 表单字段名
     * @return 是否通过（通过时会移除验证码 Cookie，且缓存项已在 validate 里被移除 —— 一次性）
     */
    public boolean validateCaptcha(String name) {
        return cn.eova.compat.jfinal.captcha.LegacyCaptchaRender.validate(this, getPara(name));
    }

    // ---------------- 上传（jfinal getFile/getFiles 族） ----------------

    /**
     * 取全部上传文件（等价 jfinal {@code Controller.getFiles()}）。
     *
     * <p>旧实现用<b>无参</b>构造包装 request，而无参构造用的是
     * {@code UploadConfig.baseUploadPath} 本身作为上传目录，故此处同构。</p>
     *
     * @return 上传文件列表（不可变）
     */
    public java.util.List<cn.eova.compat.jfinal.upload.LegacyUploadFile> getFiles() {
        return getFiles(cn.eova.compat.jfinal.upload.LegacyUploadConfig.getBaseUploadPath());
    }

    /**
     * 按上传目录取全部上传文件（等价 jfinal {@code Controller.getFiles(uploadPath)}）。
     *
     * @param uploadPath 上传目录
     * @return 上传文件列表（不可变）
     */
    public java.util.List<cn.eova.compat.jfinal.upload.LegacyUploadFile> getFiles(String uploadPath) {
        if (multipartRequest == null) {
            // 旧栈此处会 new MultipartRequest(request, uploadPath)：非 multipart 请求由 COS 报错。
            // 新栈由 Spring 拦截，宿主若未注入部件容器即为接线缺陷，故【响亮】抛出而不是静默空列表。
            throw new IllegalStateException("multipart 部件容器未注入（宿主应在 action 前调用 "
                    + "setMultipartRequest）：controller=" + getClass().getName());
        }
        return multipartRequest.getFiles(uploadPath);
    }

    /**
     * 取第一个上传文件（等价 jfinal {@code Controller.getFile()}）。
     *
     * @return 第一个上传文件；无上传时为 null
     */
    public cn.eova.compat.jfinal.upload.LegacyUploadFile getFile() {
        java.util.List<cn.eova.compat.jfinal.upload.LegacyUploadFile> files = getFiles();
        return files.isEmpty() ? null : files.get(0);
    }

    /**
     * 按参数名取上传文件（等价 jfinal {@code Controller.getFile(parameterName)}）。
     *
     * @param parameterName 表单参数名
     * @return 命中的第一个上传文件；无命中为 null
     */
    public cn.eova.compat.jfinal.upload.LegacyUploadFile getFile(String parameterName) {
        for (cn.eova.compat.jfinal.upload.LegacyUploadFile file : getFiles()) {
            // 旧字节码：uploadFile.getParameterName().equals(parameterName)（注意 null 参数会 NPE，属既有语义）
            if (file.getParameterName().equals(parameterName)) {
                return file;
            }
        }
        return null;
    }

    /**
     * 按参数名 + 上传目录取上传文件（等价 jfinal {@code Controller.getFile(parameterName, uploadPath)}）。
     *
     * <p><b>注意调用顺序与旧字节码一致</b>：先 {@code getFiles(uploadPath)}（触发落盘、丢弃返回值），
     * 再 {@code getFile(parameterName)}（在已解析列表里按参数名过滤）。</p>
     *
     * @param parameterName 表单参数名
     * @param uploadPath    上传目录
     * @return 命中的第一个上传文件；无命中为 null
     */
    public cn.eova.compat.jfinal.upload.LegacyUploadFile getFile(String parameterName, String uploadPath) {
        getFiles(uploadPath);
        return getFile(parameterName);
    }

    // ---------------- 上下文注入（宿主/框架调用） ----------------

    /**
     * 注入请求。
     *
     * @param request 请求
     */
    public void setHttpServletRequest(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * 注入响应。
     *
     * @param response 响应
     */
    public void setHttpServletResponse(HttpServletResponse response) {
        this.response = response;
    }

    /**
     * 注入 urlPara（URL 中 action 之后的附加路径段）。
     *
     * @param urlPara 附加路径段
     */
    public void setUrlPara(String urlPara) {
        this.urlPara = urlPara;
        this.urlParaArray = null;
    }

    /** 取请求 */
    public HttpServletRequest getRequest() {
        return request;
    }

    /** 取响应 */
    public HttpServletResponse getResponse() {
        return response;
    }

    // ---------------- 参数 ----------------

    /**
     * 取 urlPara。
     *
     * <p>旧实现：{@code "".equals(urlPara)} 时先把字段置 null 再返回 ——
     * 即<b>空串会被永久归一为 null</b>（有副作用的自归一）。</p>
     *
     * @return urlPara；空串返回 null
     */
    public String getPara() {
        if ("".equals(urlPara)) {
            urlPara = null;
        }
        return urlPara;
    }

    /**
     * 取第 index 段 urlPara。
     *
     * @param index 下标；小于 0 时回落到 {@link #getPara()}
     * @return 该段；无该段返回 null
     */
    public String getPara(int index) {
        if (index < 0) {
            return getPara();
        }
        if (urlParaArray == null) {
            if (urlPara == null || "".equals(urlPara)) {
                urlParaArray = NULL_URL_PARA_ARRAY;
            } else {
                urlParaArray = urlPara.split(URL_PARA_SEPARATOR);
            }
            for (int i = 0; i < urlParaArray.length; i++) {
                if ("".equals(urlParaArray[i])) {
                    urlParaArray[i] = null;
                }
            }
        }
        return urlParaArray.length > index ? urlParaArray[index] : null;
    }

    /**
     * 取第 index 段 urlPara，缺省回落。
     *
     * @param index        下标
     * @param defaultValue 缺省值
     * @return 该段；为 null 时返回缺省值
     */
    public String getPara(int index, String defaultValue) {
        String v = getPara(index);
        return v != null ? v : defaultValue;
    }

    /**
     * 取请求参数（<b>空串归一为 null</b>）。
     *
     * @param name 参数名
     * @return 值；缺失或空串返回 null
     */
    public String getPara(String name) {
        String v = request.getParameter(name);
        return (v != null && v.length() != 0) ? v : null;
    }

    /**
     * 取请求参数，缺省回落。
     *
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return 值；为 null 时返回缺省值
     */
    public String getPara(String name, String defaultValue) {
        String v = getPara(name);
        return v != null ? v : defaultValue;
    }

    /**
     * 取请求参数（旧实现是 {@link #getPara(String)} 的别名）。
     *
     * <p><b>这是全树调用最多的 Controller 方法（238 处）</b>，此前我漏了它 ——
     * 因为第一版普查只统计<b>裸调用</b>，而"持有 Controller 的普通类"
     * （如 {@code SseKit}）是以 {@code c.get(x)} 形式调用的。
     * 该漏检已由 {@code MvcFoundationGoldenTest.controllerMethodCoverage} 兜住。</p>
     *
     * @param name 参数名
     * @return 值
     */
    public String get(String name) {
        return getPara(name);
    }

    /**
     * 取请求参数，缺省回落（别名）。
     *
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return 值
     */
    public String get(String name, String defaultValue) {
        return getPara(name, defaultValue);
    }

    /**
     * 取第 index 段 urlPara（别名）。
     *
     * @param index 下标
     * @return 该段
     */
    public String get(int index) {
        return getPara(index);
    }

    /**
     * 取第 index 段 urlPara，缺省回落（别名）。
     *
     * @param index        下标
     * @param defaultValue 缺省值
     * @return 该段
     */
    public String get(int index, String defaultValue) {
        return getPara(index, defaultValue);
    }

    /**
     * 设置文本渲染（旧字节码：{@code render = factory.getTextRender(text)}）。
     *
     * @param text 文本
     */
    public void renderText(String text) {
        this.render = LegacyRenderManager.getRenderFactory().getTextRender(text);
    }

    /**
     * 设置 HTML 渲染（旧字节码：{@code render = factory.getHtmlRender(text)}）。
     *
     * @param text HTML 文本
     */
    public void renderHtml(String text) {
        this.render = LegacyRenderManager.getRenderFactory().getHtmlRender(text);
    }

    /**
     * 设置空渲染（旧字节码：{@code render = factory.getNullRender()}）。
     */
    public void renderNull() {
        this.render = LegacyRenderManager.getRenderFactory().getNullRender();
    }

    /**
     * 取全部参数表（原样返回容器的 map，含 String[] 值）。
     *
     * @return 参数表
     */
    @SuppressWarnings("unchecked")
    public Map<String, String[]> getParaMap() {
        return request.getParameterMap();
    }

    /**
     * 请求原始体（首次读取后缓存；旧实现经 {@code HttpKit.readData}）。
     *
     * @return 原始体
     */
    public String getRawData() {
        if (rawData == null) {
            rawData = readData();
        }
        return rawData;
    }

    /**
     * 读取请求体（按 {@code Content-Length} 读满，UTF-8）。
     *
     * @return 请求体
     */
    private String readData() {
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader br = request.getReader()) {
            char[] buf = new char[1024];
            int n;
            while ((n = br.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    // ---------------- 参数转换（私有助手，语义取自旧字节码） ----------------

    /**
     * 转 Integer。
     *
     * <p><b>注意 {@code "N"}/{@code "n"} 前缀表示取负</b>（{@code "N5"} → {@code -5}），
     * 详见类注释。</p>
     *
     * @param value        原始值
     * @param defaultValue 缺省值
     * @return Integer
     */
    private Integer toInt(String value, Integer defaultValue) {
        try {
            if (LegacyStrKit.isBlank(value)) {
                return defaultValue;
            }
            value = value.trim();
            if (value.startsWith("N") || value.startsWith("n")) {
                return -Integer.parseInt(value.substring(1));
            }
            return Integer.parseInt(value);
        } catch (Exception e) {
            throw new LegacyActionException(400,
                    LegacyRenderManager.getRenderFactory().getErrorRender(400),
                    "Can not parse the parameter \"" + value + "\" to Integer value.");
        }
    }

    /**
     * 转 Long。
     *
     * <p>旧字节码与 {@link #toInt} <b>同构</b>：blank 回落缺省；trim 后
     * {@code "N"}/{@code "n"} 前缀表示<b>取负</b>（{@code "N9"} → {@code -9L}）；
     * 失败抛 400，消息为 {@code Can not parse the parameter "X" to Long value.}。</p>
     *
     * @param value        原始值
     * @param defaultValue 缺省值
     * @return Long
     */
    private Long toLong(String value, Long defaultValue) {
        try {
            if (LegacyStrKit.isBlank(value)) {
                return defaultValue;
            }
            value = value.trim();
            if (value.startsWith("N") || value.startsWith("n")) {
                return -Long.parseLong(value.substring(1));
            }
            return Long.parseLong(value);
        } catch (Exception e) {
            throw new LegacyActionException(400,
                    LegacyRenderManager.getRenderFactory().getErrorRender(400),
                    "Can not parse the parameter \"" + value + "\" to Long value.");
        }
    }

    /**
     * 取参数转 Long。
     *
     * @param name 参数名
     * @return Long
     */
    public Long getParaToLong(String name) {
        return toLong(request.getParameter(name), null);
    }

    /**
     * 取参数转 Long，缺省回落。
     *
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return Long
     */
    public Long getParaToLong(String name, Long defaultValue) {
        return toLong(request.getParameter(name), defaultValue);
    }

    /**
     * 转 Date。
     *
     * <p>旧字节码（{@code Controller.toDate}）：blank 回落缺省；否则交给
     * {@code TypeConverter.me().convert(Date.class, value)}；
     * 任何异常都转成 400，消息为
     * {@code Can not parse the parameter "X" to Date value.}。</p>
     *
     * <p><b>与 toInt/toLong 的差异（不许统一）：</b>本方法【没有】trim，
     * 也【没有】{@code "N"} 取负前缀；空值判定用 {@code StrKit.isBlank}
     * （空白串同样回落缺省），而不是 {@code isEmpty}。</p>
     *
     * @param value        原始值
     * @param defaultValue 缺省值
     * @return Date
     */
    private Date toDate(String value, Date defaultValue) {
        try {
            if (LegacyStrKit.isBlank(value)) {
                return defaultValue;
            }
            return LegacyTypeConverter.me().convert(Date.class, value);
        } catch (Exception e) {
            throw new LegacyActionException(400,
                    LegacyRenderManager.getRenderFactory().getErrorRender(400),
                    "Can not parse the parameter \"" + value + "\" to Date value.");
        }
    }

    /**
     * 取参数转 Date。
     *
     * @param name 参数名
     * @return Date
     */
    public Date getParaToDate(String name) {
        return toDate(request.getParameter(name), null);
    }

    /**
     * 取参数转 Date，缺省回落。
     *
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return Date
     */
    public Date getParaToDate(String name, Date defaultValue) {
        return toDate(request.getParameter(name), defaultValue);
    }

    /**
     * 取 urlPara 转 Date。
     *
     * <p>旧字节码走 {@code getPara()}（urlPara 第 0 段），不是 request 参数。</p>
     *
     * @return Date
     */
    public Date getParaToDate() {
        return toDate(getPara(), null);
    }

    /**
     * 取参数转 Date（别名，供 EOVA 的 {@code BaseController} 使用）。
     *
     * @param name 参数名
     * @return Date
     */
    public Date getDate(String name) {
        return getParaToDate(name);
    }

    /**
     * 取参数转 Date，缺省回落（别名）。
     *
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return Date
     */
    public Date getDate(String name, Date defaultValue) {
        return getParaToDate(name, defaultValue);
    }

    /**
     * 取第 index 段 urlPara 转 Integer（旧 {@code Controller.getInt(int)}）。
     *
     * <p><b>第 69 轮补：</b>port {@code MenuController}/{@code AuthController} 时
     * {@code getInt(0)} 编译失败 —— 本接缝此前只有 {@code getInt(String)}。
     * jfinal 的下标版就是委托 urlPara 版（字节码：{@code getParaToInt(index)}）。
     * 全树按下标取值普查：{@code get(0)}×92、{@code get(1)}×15、{@code getInt(0)}×3、
     * {@code getParaToInt(0/1)}×3 —— 其中 {@code get(int)} 与 {@code getParaToInt(int)} 已有。</p>
     *
     * @param index urlPara 下标
     * @return Integer
     */
    public Integer getInt(int index) {
        return getParaToInt(index);
    }

    /**
     * 取第 index 段 urlPara 转 Integer，缺省回落（旧 {@code Controller.getInt(int, Integer)}）。
     *
     * @param index        urlPara 下标
     * @param defaultValue 缺省值
     * @return Integer
     */
    public Integer getInt(int index, Integer defaultValue) {
        return getParaToInt(index, defaultValue);
    }

    /**
     * 取第 index 段 urlPara 转 Long（旧 {@code Controller.getLong(int)}）。
     *
     * @param index urlPara 下标
     * @return Long
     */
    public Long getLong(int index) {
        return getParaToLong(index);
    }

    /**
     * 取第 index 段 urlPara 转 Boolean（旧 {@code Controller.getBoolean(int)}）。
     *
     * @param index urlPara 下标
     * @return Boolean
     */
    public Boolean getBoolean(int index) {
        return getParaToBoolean(index);
    }

    /**
     * 取第 index 段 urlPara 转 Long。
     *
     * @param index 下标
     * @return Long
     */
    public Long getParaToLong(int index) {
        return toLong(getPara(index), null);
    }

    /**
     * 取第 index 段 urlPara 转 Long，缺省回落。
     *
     * @param index        下标
     * @param defaultValue 缺省值
     * @return Long
     */
    public Long getParaToLong(int index, Long defaultValue) {
        return toLong(getPara(index), defaultValue);
    }

    /**
     * 取参数转 Long（旧实现是 {@link #getParaToLong(String)} 的别名）。
     *
     * @param name 参数名
     * @return Long
     */
    public Long getLong(String name) {
        return getParaToLong(name);
    }

    /**
     * 取参数转 Long，缺省回落（别名）。
     *
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return Long
     */
    public Long getLong(String name, Long defaultValue) {
        return getParaToLong(name, defaultValue);
    }

    /**
     * 转 Boolean。
     *
     * <p>旧字节码：先 {@code trim().toLowerCase()}，{@code "1"}/{@code "true"} → TRUE，
     * {@code "0"}/{@code "false"} → FALSE，其余抛 400。</p>
     *
     * @param value        原始值
     * @param defaultValue 缺省值
     * @return Boolean
     */
    private Boolean toBoolean(String value, Boolean defaultValue) {
        try {
            if (LegacyStrKit.isBlank(value)) {
                return defaultValue;
            }
            value = value.trim().toLowerCase();
            if ("1".equals(value) || "true".equals(value)) {
                return Boolean.TRUE;
            }
            if ("0".equals(value) || "false".equals(value)) {
                return Boolean.FALSE;
            }
        } catch (Exception e) {
            throw new LegacyActionException(400,
                    LegacyRenderManager.getRenderFactory().getErrorRender(400),
                    "Can not parse the parameter \"" + value + "\" to Boolean value.");
        }
        throw new LegacyActionException(400,
                LegacyRenderManager.getRenderFactory().getErrorRender(400),
                "Can not parse the parameter \"" + value + "\" to Boolean value.");
    }

    /**
     * 取参数转 Integer。
     *
     * @param name 参数名
     * @return Integer；缺失返回 null
     */
    public Integer getParaToInt(String name) {
        return toInt(request.getParameter(name), null);
    }

    /**
     * 取参数转 Integer，缺省回落。
     *
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return Integer
     */
    public Integer getParaToInt(String name, Integer defaultValue) {
        return toInt(request.getParameter(name), defaultValue);
    }

    /**
     * 取第 index 段 urlPara 转 Integer。
     *
     * @param index 下标
     * @return Integer
     */
    public Integer getParaToInt(int index) {
        return toInt(getPara(index), null);
    }

    /**
     * 取第 index 段 urlPara 转 Integer，缺省回落。
     *
     * @param index        下标
     * @param defaultValue 缺省值
     * @return Integer
     */
    public Integer getParaToInt(int index, Integer defaultValue) {
        return toInt(getPara(index), defaultValue);
    }

    /**
     * 取参数转 Boolean。
     *
     * @param name 参数名
     * @return Boolean
     */
    public Boolean getParaToBoolean(String name) {
        return toBoolean(request.getParameter(name), null);
    }

    /**
     * 取参数转 Boolean，缺省回落。
     *
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return Boolean
     */
    public Boolean getParaToBoolean(String name, Boolean defaultValue) {
        return toBoolean(request.getParameter(name), defaultValue);
    }

    /**
     * 取第 index 段 urlPara 转 Boolean。
     *
     * @param index 下标
     * @return Boolean
     */
    public Boolean getParaToBoolean(int index) {
        return toBoolean(getPara(index), null);
    }

    /**
     * 取参数转 Integer（旧实现是 {@link #getParaToInt(String)} 的别名）。
     *
     * @param name 参数名
     * @return Integer
     */
    public Integer getInt(String name) {
        return getParaToInt(name);
    }

    /**
     * 取参数转 Integer，缺省回落（别名）。
     *
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return Integer
     */
    public Integer getInt(String name, Integer defaultValue) {
        return getParaToInt(name, defaultValue);
    }

    /**
     * 取参数转 Boolean（旧实现是 {@link #getParaToBoolean(String)} 的别名）。
     *
     * @param name 参数名
     * @return Boolean
     */
    public Boolean getBoolean(String name) {
        return getParaToBoolean(name);
    }

    /**
     * 取参数转 Boolean，缺省回落（别名）。
     *
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return Boolean
     */
    public Boolean getBoolean(String name, Boolean defaultValue) {
        return getParaToBoolean(name, defaultValue);
    }

    // ---------------- 属性 ----------------

    /**
     * 设置请求属性（与 {@link #setAttr(String, Object)} <b>实现相同</b>，旧实现如此）。
     *
     * @param name  名称
     * @param value 值
     * @return this
     */
    public LegacyController set(String name, Object value) {
        request.setAttribute(name, value);
        return this;
    }

    /**
     * 设置请求属性（与 {@link #set(String, Object)} <b>实现相同</b>）。
     *
     * @param name  名称
     * @param value 值
     * @return this
     */
    public LegacyController setAttr(String name, Object value) {
        request.setAttribute(name, value);
        return this;
    }

    /**
     * 批量设置请求属性（逐项 {@code setAttribute}，旧字节码如此）。
     *
     * @param attrs 属性表
     * @return this
     */
    public LegacyController setAttrs(Map<String, Object> attrs) {
        for (Map.Entry<String, Object> e : attrs.entrySet()) {
            request.setAttribute(e.getKey(), e.getValue());
        }
        return this;
    }

    /**
     * 取请求属性。
     *
     * @param name 名称
     * @param <T>  类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttr(String name) {
        return (T) request.getAttribute(name);
    }

    /**
     * 取请求属性，缺省回落。
     *
     * @param name         名称
     * @param defaultValue 缺省值
     * @param <T>          类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttr(String name, T defaultValue) {
        Object v = request.getAttribute(name);
        return v != null ? (T) v : defaultValue;
    }

    /**
     * 移除请求属性。
     *
     * @param name 名称
     * @return this
     */
    public LegacyController removeAttr(String name) {
        request.removeAttribute(name);
        return this;
    }

    /**
     * 把参数保留为请求属性（使 forward 后仍可取到）。
     *
     * <p>旧字节码：对每个名字取 {@code getParameterValues}；
     * <b>为 null 时什么都不做</b>；长度为 1 时存单个 String，否则存整个数组。</p>
     *
     * @param names 参数名
     * @return this
     */
    public LegacyController keepPara(String... names) {
        for (String name : names) {
            String[] values = request.getParameterValues(name);
            if (values != null) {
                if (values.length == 1) {
                    request.setAttribute(name, values[0]);
                } else {
                    request.setAttribute(name, values);
                }
            }
        }
        return this;
    }

    /**
     * 取参数构成的 Kv（单值；<b>空串归一为 null</b>）。
     *
     * <p>旧字节码：遍历参数表；每个键取 String[] 的<b>第 0 个</b>（数组为空取 null），
     * 再 {@code "".equals(v) ? null : v} 放入 Kv。</p>
     *
     * @return Kv
     */
    public LegacyKv getKv() {
        LegacyKv kv = new LegacyKv();
        // 【补上的保真分支】旧字节码开头有：
        //   if (request instanceof JsonRequest) {
        //       JsonRequest jr = (JsonRequest) request;
        //       if (jr.getJSONObject() != null) kv.putAll(jr.getJSONObject());
        //   }
        // 我此前实现 getKv 时漏了这一支（当时还没有 JsonRequest 接缝）。
        if (request instanceof cn.eova.compat.jfinal.core.paragetter.LegacyJsonRequest) {
            cn.eova.compat.jfinal.core.paragetter.LegacyJsonRequest jr =
                    (cn.eova.compat.jfinal.core.paragetter.LegacyJsonRequest) request;
            if (jr.getJSONObject() != null) {
                kv.putAll(jr.getJSONObject());
            }
        }
        for (Map.Entry<String, String[]> e : getParaMap().entrySet()) {
            String[] arr = e.getValue();
            String v = (arr != null && arr.length > 0) ? arr[0] : null;
            kv.put(e.getKey(), "".equals(v) ? null : v);
        }
        return kv;
    }

    // ---------------- 渲染（W1a 只实现"赋值"语义） ----------------

    /**
     * 设置待渲染对象。
     *
     * <p><b>旧字节码只是赋值</b>（{@code this.render = render}），
     * 真正的渲染由框架在 action 返回后进行 —— 故本方法<b>不</b>调用 {@code render()}。
     * 这一点容易"顺手写错"成立即渲染。</p>
     *
     * @param render 渲染对象
     */
    public void render(LegacyRender render) {
        this.render = render;
    }

    /**
     * 取待渲染对象。
     *
     * @return 渲染对象
     */
    public LegacyRender getRender() {
        return render;
    }

    /**
     * 设置视图渲染（旧字节码：{@code render = factory.getRender(view)}）。
     *
     * @param view 视图名
     */
    public void render(String view) {
        this.render = LegacyRenderManager.getRenderFactory().getRender(view);
    }

    /**
     * 设置模板渲染（旧字节码：{@code render = factory.getTemplateRender(view)}）。
     *
     * @param view 视图名
     */
    public void renderTemplate(String view) {
        this.render = LegacyRenderManager.getRenderFactory().getTemplateRender(view);
    }

    /**
     * 设置"输出全部请求属性"的 JSON 渲染。
     */
    public void renderJson() {
        this.render = LegacyRenderManager.getRenderFactory().getJsonRender();
    }

    /**
     * 设置输出指定请求属性的 JSON 渲染。
     *
     * @param attrs 属性名数组
     */
    public void renderJson(String[] attrs) {
        this.render = LegacyRenderManager.getRenderFactory().getJsonRender(attrs);
    }

    /**
     * 设置输出给定 JSON 文本的渲染。
     *
     * @param jsonText JSON 文本
     */
    public void renderJson(String jsonText) {
        this.render = LegacyRenderManager.getRenderFactory().getJsonRender(jsonText);
    }

    /**
     * 设置输出单个对象的 JSON 渲染。
     *
     * <p>旧字节码有一处分支：若入参<b>本身已是 {@code Render}</b> 则直接用它，
     * 否则交给工厂造。该分支原样保留。</p>
     *
     * @param object 对象，或已构造好的渲染
     */
    public void renderJson(Object object) {
        this.render = (object instanceof LegacyRender)
                ? (LegacyRender) object
                : LegacyRenderManager.getRenderFactory().getJsonRender(object);
    }

    /**
     * 设置输出"名 - 对象"单键 JSON 的渲染。
     *
     * @param attr   键名
     * @param object 值
     */
    public void renderJson(String attr, Object object) {
        this.render = LegacyRenderManager.getRenderFactory().getJsonRender(attr, object);
    }

    /**
     * <b>抛</b>出带错误渲染的异常（旧字节码：本方法不设置 {@code render}，而是直接抛）。
     *
     * @param errorCode HTTP 状态码
     */
    public void renderError(int errorCode) {
        throw new LegacyActionException(errorCode,
                LegacyRenderManager.getRenderFactory().getErrorRender(errorCode));
    }

    /**
     * <b>抛</b>出带错误渲染的异常（指定错误页视图）。
     *
     * @param errorCode HTTP 状态码
     * @param view      错误页视图
     */
    public void renderError(int errorCode, String view) {
        throw new LegacyActionException(errorCode,
                LegacyRenderManager.getRenderFactory().getErrorRender(errorCode, view));
    }

    /**
     * 设置跳转渲染。
     *
     * @param url 目标 URL
     */
    public void redirect(String url) {
        this.render = LegacyRenderManager.getRenderFactory().getRedirectRender(url);
    }

    /**
     * 设置跳转渲染（可指定是否附带原查询串）。
     *
     * @param url             目标 URL
     * @param withQueryString 是否附带原查询串
     */
    public void redirect(String url, boolean withQueryString) {
        this.render = LegacyRenderManager.getRenderFactory()
                .getRedirectRender(url, withQueryString);
    }

    // ---------------- Cookie ----------------

    /**
     * 按名取 Cookie 对象。
     *
     * <p>旧字节码：遍历 {@code request.getCookies()}（可能为 null），
     * 用 {@code getName().equals(name)} <b>区分大小写</b>比对，
     * <b>返回首个命中</b>；无命中或 cookies 为 null 返回 null。</p>
     *
     * @param name Cookie 名
     * @return Cookie；未命中返回 null
     */
    public Cookie getCookieObject(String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (c.getName().equals(name)) {
                    return c;
                }
            }
        }
        return null;
    }

    /**
     * 按名取 Cookie 值。
     *
     * @param name Cookie 名
     * @return 值；未命中返回 null
     */
    public String getCookie(String name) {
        return getCookie(name, null);
    }

    /**
     * 按名取 Cookie 值，缺省回落（旧字节码：命中取 {@code getValue()}，否则返回缺省值）。
     *
     * @param name         Cookie 名
     * @param defaultValue 缺省值
     * @return 值
     */
    public String getCookie(String name, String defaultValue) {
        Cookie c = getCookieObject(name);
        return c != null ? c.getValue() : defaultValue;
    }

    /**
     * 设置 Cookie（各便捷重载的统一实现）。
     *
     * <p><b>语义逐条取自旧字节码，含一处极易写错的地方：</b>
     * <pre>
     * Cookie cookie = new Cookie(name, value);
     * cookie.setMaxAge(maxAge);
     * if (StrKit.isBlank(path)) path = "/";      // 空 path 归一为 "/"
     * cookie.setPath(path);
     * if (domain != null) cookie.setDomain(domain);
     * if (secure != null) cookie.setHttpOnly(secure.booleanValue());   // ← 不是 setSecure！
     * response.addCookie(cookie);
     * return this;
     * </pre>
     * 最后一个形参在旧签名里叫 {@code secure}，但实际调用的是 <b>{@code setHttpOnly}</b>
     * （jfinal 的命名与行为不一致，属既有形态，<b>不得</b>改成 {@code setSecure}）。</p>
     *
     * @param name   Cookie 名
     * @param value  值
     * @param maxAge 最大存活秒数
     * @param path   路径；空则归一为 "/"
     * @param domain 域；null 则不设置
     * @param secure 是否 HttpOnly；null 则不设置
     * @return this
     */
    protected LegacyController doSetCookie(String name, String value, int maxAge,
                                          String path, String domain, Boolean secure) {
        Cookie cookie = new Cookie(name, value);
        cookie.setMaxAge(maxAge);
        if (LegacyStrKit.isBlank(path)) {
            path = "/";
        }
        cookie.setPath(path);
        if (domain != null) {
            cookie.setDomain(domain);
        }
        if (secure != null) {
            cookie.setHttpOnly(secure);
        }
        response.addCookie(cookie);
        return this;
    }

    /**
     * 设置 Cookie（最大存活秒数）。
     *
     * @param name   Cookie 名
     * @param value  值
     * @param maxAge 最大存活秒数
     * @return this
     */
    public LegacyController setCookie(String name, String value, int maxAge) {
        return doSetCookie(name, value, maxAge, null, null, null);
    }

    /**
     * 设置 Cookie（并指定是否 HttpOnly）—— 逐字节等价 jfinal
     * {@code setCookie(String, String, int, boolean)}：
     * {@code doSetCookie(name, value, maxAge, null, null, Boolean.valueOf(httpOnly))}。
     *
     * @param name       Cookie 名
     * @param value      值
     * @param maxAge     最大存活秒数
     * @param isHttpOnly 是否 HttpOnly
     * @return this
     */
    public LegacyController setCookie(String name, String value, int maxAge, boolean isHttpOnly) {
        return doSetCookie(name, value, maxAge, null, null, Boolean.valueOf(isHttpOnly));
    }

    /**
     * 移除 Cookie（旧字节码：{@code doSetCookie(name, null, 0, null, null, null)} ——
     * 即置空值 + maxAge 0，path 由 doSetCookie 归一为 "/"）。
     *
     * @param name Cookie 名
     * @return this
     */
    public LegacyController removeCookie(String name) {
        return doSetCookie(name, null, 0, null, null, null);
    }

    // ---------------- 路径信息 ----------------

    /**
     * 取 Controller 键（旧实现从 action 取）。
     *
     * @return Controller 键；无 action 时为 null
     */
    public String getControllerKey() {
        return action != null ? controllerKeyOf(action.getControllerPath()) : null;
    }

    /**
     * 取视图路径（旧实现从 action 取）。
     *
     * @return 视图路径；无 action 时为 null
     */
    public String getViewPath() {
        return action != null ? action.getViewPath() : null;
    }

    /**
     * 取 Controller 路径（旧实现从 action 取）。
     *
     * @return Controller 路径；无 action 时为 null
     */
    public String getControllerPath() {
        return action != null ? action.getControllerPath() : null;
    }

    /**
     * 由 Controller 路径推出 Controller 键（去掉 {@code "/"} 前缀）。
     *
     * @param controllerPath Controller 路径
     * @return Controller 键
     */
    private static String controllerKeyOf(String controllerPath) {
        if (controllerPath != null && controllerPath.length() > 1 && controllerPath.charAt(0) == '/') {
            return controllerPath.substring(1);
        }
        return controllerPath;
    }

}
