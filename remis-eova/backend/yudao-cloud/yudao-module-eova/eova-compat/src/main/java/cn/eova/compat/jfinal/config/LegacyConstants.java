/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.config;

import java.util.HashMap;
import java.util.Map;

import cn.eova.compat.jfinal.captcha.LegacyCaptchaCache;
import cn.eova.compat.jfinal.captcha.LegacyCaptchaManager;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.config.Constants} 的等价接缝（**数据面**）。
 *
 * <p>ported from: com.jfinal.config.Constants（jfinal 5.2.6 制品）
 *
 * <p><b>为什么需要它：</b>旧 {@code EovaConfig.configConstant(Constants me)} 的全部行为
 * 就是往这个对象上写配置；新栈没有 jfinal 的引导，故把它作为<b>纯数据面</b>接缝 port 出来，
 * 由宿主的引导代码（见 {@code LegacyJFinalConfig} 的驱动）按同一顺序写入。
 * 它同时是 {@code LegacyUploadConfig} 的上游：jfinal 的 {@code JFinal.initUploadConfig()}
 * 会把这里的 {@code baseUploadPath/maxPostSize/encoding} 推给 {@code UploadConfig}。</p>
 *
 * <p><b>默认值逐条取自旧字节码（{@code Constants} 构造器）：</b>
 * <table border="1">
 *   <tr><th>字段</th><th>默认值</th></tr>
 *   <tr><td>{@code devMode}</td><td>{@code false}</td></tr>
 *   <tr><td>{@code baseUploadPath} / {@code baseDownloadPath}</td><td>{@code "upload"} / {@code "download"}</td></tr>
 *   <tr><td>{@code encoding}</td><td>{@code "UTF-8"}</td></tr>
 *   <tr><td>{@code urlParaSeparator}</td><td>{@code "-"}</td></tr>
 *   <tr><td>{@code viewType} / {@code viewExtension}</td><td>{@code JFINAL_TEMPLATE} / {@code ".html"}</td></tr>
 *   <tr><td>{@code maxPostSize}</td><td>{@code 10485760L}（10MB）</td></tr>
 *   <tr><td>{@code freeMarkerTemplateUpdateDelay}</td><td>{@code 3600}</td></tr>
 *   <tr><td>{@code configPluginOrder}</td><td>{@code 3}</td></tr>
 *   <tr><td>{@code denyAccessJsp}</td><td>{@code true}</td></tr>
 * </table>
 *
 * <p><b>校验消息逐字保留（旧字节码常量池）：</b>
 * {@code "encoding can not be blank."}、{@code "baseUploadPath can not be blank."}、
 * {@code "viewType can not be null"}、{@code "urlParaSepartor can not be blank and can not contains \"/\""}
 * （注意旧消息里 {@code Separtor} 是<b>拼写错误</b>，属对外可观测文本，原样保留）。
 *
 * <p><b>本类明确未包含（EOVA 未使用且新栈无对应物）：</b>
 * {@code controllerFactory}/{@code injectDependency}/{@code injectSuperClass}（jfinal 的依赖注入）、
 * {@code tokenCache}/{@code setDenyAccessJsp} 之外的 jsp 相关、{@code setRenderFactory}/{@code setLogFactory}/
 * {@code setProxyFactory} 家族（新栈由 Spring 承担）、{@code setActionMapping}、
 * {@code freeMarkerTemplateUpdateDelay} 的 setter、i18n 家族。
 * 其中<b>已包含</b>的配置项即为 EOVA 与宿主实际会用到的全部（{@code configConstant} 逐行核对）。</p>
 */
public final class LegacyConstants {

    private boolean devMode = false;

    private String baseUploadPath = "upload";

    private String baseDownloadPath = "download";

    private String encoding = "UTF-8";

    private String urlParaSeparator = "-";

    private LegacyViewType viewType = LegacyViewType.JFINAL_TEMPLATE;

    private String viewExtension = ".html";

    private long maxPostSize = 10485760L;

    private final int freeMarkerTemplateUpdateDelay = 3600;

    private int configPluginOrder = 3;

    private boolean denyAccessJsp = true;

    /** 验证码缓存（旧栈由 EOVA 注入 DbCaptchaCache） */
    private LegacyCaptchaCache captchaCache;

    /** 是否解析 JSON 请求（jfinal 5.0.0 新增；EOVA 开启） */
    private boolean resolveJsonRequest = false;

    /** JSON 工厂名（旧栈为 MixedJsonFactory；新栈只记录其存在性，序列化由 LegacyJsonKit 承担） */
    private String jsonFactoryName;

    /** 错误页：状态码 → 视图（{@code setErrorView}） */
    private final Map<Integer, String> errorViews = new HashMap<>();

    /** 错误页：状态码 → 专用 setter 写入的视图（401/403/404/500） */
    private final Map<Integer, String> errorStatusViews = new HashMap<>();

    /**
     * 设置开发模式。
     *
     * @param devMode 是否开发模式
     */
    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    /**
     * 取开发模式。
     *
     * @return 是否开发模式
     */
    public boolean getDevMode() {
        return devMode;
    }

    /**
     * 设置插件装载顺序（旧栈 EOVA 设为 1 ⇒ 插件在 configConstant 之后装载）。
     *
     * @param configPluginOrder 顺序
     */
    public void setConfigPluginOrder(int configPluginOrder) {
        this.configPluginOrder = configPluginOrder;
    }

    /**
     * 取插件装载顺序。
     *
     * @return 顺序
     */
    public int getConfigPluginOrder() {
        return configPluginOrder;
    }

    /**
     * 设置编码（空白串按旧消息抛异常）。
     *
     * @param encoding 编码
     */
    public void setEncoding(String encoding) {
        if (isBlank(encoding)) {
            throw new IllegalArgumentException("encoding can not be blank.");
        }
        this.encoding = encoding;
    }

    /**
     * 取编码。
     *
     * @return 编码
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * 设置上传根目录（空白串按旧消息抛异常）。
     *
     * @param baseUploadPath 目录
     */
    public void setBaseUploadPath(String baseUploadPath) {
        if (isBlank(baseUploadPath)) {
            throw new IllegalArgumentException("baseUploadPath can not be blank.");
        }
        this.baseUploadPath = baseUploadPath;
    }

    /**
     * 取上传根目录。
     *
     * @return 目录
     */
    public String getBaseUploadPath() {
        return baseUploadPath;
    }

    /**
     * 设置下载根目录（空白串按旧消息抛异常）。
     *
     * @param baseDownloadPath 目录
     */
    public void setBaseDownloadPath(String baseDownloadPath) {
        if (isBlank(baseDownloadPath)) {
            throw new IllegalArgumentException("baseDownloadPath can not be blank.");
        }
        this.baseDownloadPath = baseDownloadPath;
    }

    /**
     * 取下载根目录。
     *
     * @return 目录
     */
    public String getBaseDownloadPath() {
        return baseDownloadPath;
    }

    /**
     * 取 urlPara 分隔符。
     *
     * @return 分隔符
     */
    public String getUrlParaSeparator() {
        return urlParaSeparator;
    }

    /**
     * 设置 urlPara 分隔符（空白或含 {@code /} 按旧消息抛异常，含旧的拼写错误）。
     *
     * @param urlParaSeparator 分隔符
     */
    public void setUrlParaSeparator(String urlParaSeparator) {
        if (isBlank(urlParaSeparator) || urlParaSeparator.contains("/")) {
            throw new IllegalArgumentException("urlParaSepartor can not be blank and can not contains \"/\"");
        }
        this.urlParaSeparator = urlParaSeparator;
    }

    /**
     * 取视图类型。
     *
     * @return 视图类型
     */
    public LegacyViewType getViewType() {
        return viewType;
    }

    /**
     * 设置视图类型（null 按旧消息抛异常）。
     *
     * @param viewType 视图类型
     */
    public void setViewType(LegacyViewType viewType) {
        if (viewType == null) {
            throw new IllegalArgumentException("viewType can not be null");
        }
        this.viewType = viewType;
    }

    /**
     * 取视图扩展名。
     *
     * @return 扩展名
     */
    public String getViewExtension() {
        return viewExtension;
    }

    /**
     * 设置视图扩展名。
     *
     * @param viewExtension 扩展名
     */
    public void setViewExtension(String viewExtension) {
        this.viewExtension = viewExtension;
    }

    /**
     * 取 POST 体上限。
     *
     * @return 字节数
     */
    public long getMaxPostSize() {
        return maxPostSize;
    }

    /**
     * 设置 POST 体上限。
     *
     * @param maxPostSize 字节数
     */
    public void setMaxPostSize(long maxPostSize) {
        this.maxPostSize = maxPostSize;
    }

    /**
     * 取 FreeMarker 模板刷新延迟（只读，旧栈 EOVA 未改）。
     *
     * @return 秒
     */
    public int getFreeMarkerTemplateUpdateDelay() {
        return freeMarkerTemplateUpdateDelay;
    }

    /**
     * 是否拒绝访问 jsp。
     *
     * @return 是否拒绝
     */
    public boolean getDenyAccessJsp() {
        return denyAccessJsp;
    }

    /**
     * 设置是否拒绝访问 jsp。
     *
     * @param denyAccessJsp 是否拒绝
     */
    public void setDenyAccessJsp(boolean denyAccessJsp) {
        this.denyAccessJsp = denyAccessJsp;
    }

    /**
     * 设置验证码缓存（旧栈 EOVA 注入 {@code DbCaptchaCache}）。
     *
     * <p>ported from: {@code com.jfinal.config.Constants#setCaptchaCache}（jfinal 5.2.6）——
     * <b>旧实现是纯委派</b>，字节码实证（{@code javap -c com.jfinal.config.Constants}）：
     * <pre>
     *   invokestatic  CaptchaManager.me()
     *   aload_1
     *   invokevirtual CaptchaManager.setCaptchaCache(ICaptchaCache)
     * </pre>
     * 即 {@code Constants} <b>自己不存</b>该缓存，而是转交给 {@code CaptchaManager} 单例
     * （渲染期 {@code CaptchaRender} 正是从 {@code CaptchaManager.me().getCaptchaCache()} 取）。</p>
     *
     * <p>★ 第 303 轮修的真缺陷：本方法原实现**只写了本地字段、丢了委派分支** ⇒
     * {@link cn.eova.compat.jfinal.captcha.LegacyCaptchaManager} 永远未装配 ⇒
     * {@code LegacyCaptchaRender#render} 第 103 行 NPE ⇒ {@code GET /user/captcha} 返回
     * <b>500</b>（旧栈同请求 {@code 200 image/jpeg}）。它此前没被发现，是因为本环境
     * {@code isCaptcha=false}：旧登录页**不显示验证码图片**、也就不请求该端点，
     * 而真浏览器验收（S5）里 SPA 因登录页配置缺口显示了验证码，才把这个 500 暴露出来。</p>
     *
     * @param captchaCache 缓存
     */
    public void setCaptchaCache(LegacyCaptchaCache captchaCache) {
        // 旧实现的委派（缺了它，验证码端点必 NPE）
        LegacyCaptchaManager.me().setCaptchaCache(captchaCache);
        // 移植层附加的本地留存（供 getCaptchaCache()，非 jfinal API）
        this.captchaCache = captchaCache;
    }

    /**
     * 取验证码缓存。
     *
     * @return 缓存；未设置时为 null
     */
    public LegacyCaptchaCache getCaptchaCache() {
        return captchaCache;
    }

    /** 开启 JVM headless（旧 {@code setToJavaAwtHeadless()}：设置系统属性并关掉 AWT 图形环境） */
    public void setToJavaAwtHeadless() {
        System.setProperty("java.awt.headless", "true");
    }

    /**
     * 设置是否解析 JSON 请求。
     *
     * @param resolveJsonRequest 是否解析
     */
    public void setResolveJsonRequest(boolean resolveJsonRequest) {
        this.resolveJsonRequest = resolveJsonRequest;
    }

    /**
     * 是否解析 JSON 请求。
     *
     * @return 是否解析
     */
    public boolean getResolveJsonRequest() {
        return resolveJsonRequest;
    }

    /**
     * 记录 JSON 工厂（旧 {@code setJsonFactory(MixedJsonFactory.me())}）。
     *
     * @param jsonFactoryName 工厂类名
     */
    public void setJsonFactory(String jsonFactoryName) {
        this.jsonFactoryName = jsonFactoryName;
    }

    /**
     * 记录 JSON 工厂实例（旧 {@code setJsonFactory(MixedJsonFactory.me())} 的等价重载）。
     *
     * @param jsonFactory JSON 工厂实例
     */
    public void setJsonFactory(cn.eova.compat.jfinal.json.LegacyMixedJsonFactory jsonFactory) {
        this.jsonFactoryName = jsonFactory.getClass().getName();
    }

    /**
     * 取 JSON 工厂类名。
     *
     * @return 类名；未设置时为 null
     */
    public String getJsonFactoryName() {
        return jsonFactoryName;
    }

    /**
     * 设置错误页视图（旧 {@code setErrorView(int, String)}）。
     *
     * @param errorCode 状态码
     * @param view      视图
     */
    public void setErrorView(int errorCode, String view) {
        errorViews.put(errorCode, view);
    }

    /**
     * 设置 404 错误页（旧 {@code setError404View(String)}）。
     *
     * @param view 视图
     */
    public void setError404View(String view) {
        errorStatusViews.put(404, view);
    }

    /**
     * 设置 500 错误页（旧 {@code setError500View(String)}）。
     *
     * @param view 视图
     */
    public void setError500View(String view) {
        errorStatusViews.put(500, view);
    }

    /**
     * 设置 401 错误页（旧 {@code setError401View(String)}）。
     *
     * @param view 视图
     */
    public void setError401View(String view) {
        errorStatusViews.put(401, view);
    }

    /**
     * 设置 403 错误页（旧 {@code setError403View(String)}）。
     *
     * @param view 视图
     */
    public void setError403View(String view) {
        errorStatusViews.put(403, view);
    }

    /**
     * 取错误页视图。
     *
     * @param errorCode 状态码
     * @return 视图；未设置时为 null
     */
    public String getErrorView(int errorCode) {
        return errorViews.get(errorCode);
    }

    /**
     * 取专用 setter 写入的错误页视图。
     *
     * @param errorCode 状态码
     * @return 视图；未设置时为 null
     */
    public String getErrorStatusView(int errorCode) {
        return errorStatusViews.get(errorCode);
    }

    /**
     * 空白判定（与旧 {@code StrKit.isBlank} 同义：null 或全字符 ≤ 空格）。
     *
     * @param s 输入
     * @return 是否空白
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

}
