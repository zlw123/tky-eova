/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.render;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.eova.compat.jfinal.kit.LegacyJsonKit;
import jakarta.servlet.ServletOutputStream;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.render.ErrorRender} 的等价接缝。
 *
 * <p>{@code ported from} {@code com.jfinal.render.ErrorRender}（jfinal 5.2.6）。</p>
 *
 * <p><b>{@link #render()} 逐条取自旧字节码：</b>
 * <pre>
 * response.setStatus(errorCode);
 * String ct = request.getContentType();
 * boolean json = ct != null &amp;&amp; ct.indexOf("json") != -1;
 * if (viewOrJson == null &amp;&amp; !json) viewOrJson = getErrorView(errorCode);
 * if (viewOrJson != null) {
 *     Render r = json ? RenderManager.getRenderFactory().getJsonRender(viewOrJson)
 *                     : RenderManager.getRenderFactory().getRender(viewOrJson);
 *     r.setContext(request, response).render();
 *     return;                      // ← 走视图分支即返回，不再写内建字节
 * }
 * response.setContentType(json ? contentTypeJson : contentTypeHtml);
 * ServletOutputStream os = response.getOutputStream();
 * os.write(json ? getErrorJson() : getErrorHtml());
 * // 异常：IOException 时 close(os)，随后一律包 RenderException
 * </pre>
 * 注意两处：① <b>是否 JSON 由请求的 contentType 是否含 {@code "json"} 判定</b>（不是看 Accept）；
 * ② 非 JSON 且未配置错误视图时才查 {@code getErrorView(errorCode)}。</p>
 *
 * <p><b>内建错误页逐字取自旧制品</b>（用反射从 jfinal jar 抽取，非手工转录），
 * 见 {@code docs/.local/baseline/evidence/errorrender-builtin-pages.txt}。
 * 其中 JSON 体是 jfinal 自己的 {@code {"state":"fail","msg":"..."}} ——
 * 与 EOVA 的 envelope <b>形状相近但来源不同</b>，
 * <b>不得</b>把它"顺手统一"成 EOVA 的 envelope（那是 jfinal 的输出，属既有形态）。</p>
 *
 * <p><b>回退页的 {@code getBytes()} 不带字符集参数</b>（旧字节码如此，用平台默认字符集）——
 * 属既有行为，不得改为显式 UTF-8。</p>
 */
public class LegacyErrorRender extends LegacyRender {

    /** HTML contentType（拼当前编码） */
    protected static final String CONTENT_TYPE_HTML = "text/html; charset=";

    /** JSON contentType（拼当前编码） */
    protected static final String CONTENT_TYPE_JSON = "application/json; charset=";

    /** 内建 HTML 错误页（逐字取自旧制品；键为状态码） */
    protected static final Map<Integer, byte[]> errorHtmlMap = new HashMap<>();

    /** 内建 JSON 错误体（逐字取自旧制品；键为状态码） */
    protected static final Map<Integer, byte[]> errorJsonMap = new HashMap<>();

    /** 状态码 -> 自定义错误页视图；初值为空（旧实现亦为空） */
    protected static final Map<Integer, String> errorViewMap = new HashMap<>();

    static {
        errorHtmlMap.put(400, "<html><head><title>400 Bad Request</title></head><body bgcolor='white'><center><h1>400 Bad Request</h1></center><hr><center><a href='https://gitee.com/jfinal/jfinal' target='_blank'><b>Powered by JFinal 5.2.6</b></a></center></body></html>".getBytes());
        errorHtmlMap.put(401, "<html><head><title>401 Unauthorized</title></head><body bgcolor='white'><center><h1>401 Unauthorized</h1></center><hr><center><a href='https://gitee.com/jfinal/jfinal' target='_blank'><b>Powered by JFinal 5.2.6</b></a></center></body></html>".getBytes());
        errorHtmlMap.put(403, "<html><head><title>403 Forbidden</title></head><body bgcolor='white'><center><h1>403 Forbidden</h1></center><hr><center><a href='https://gitee.com/jfinal/jfinal' target='_blank'><b>Powered by JFinal 5.2.6</b></a></center></body></html>".getBytes());
        errorHtmlMap.put(404, "<html><head><title>404 Not Found</title></head><body bgcolor='white'><center><h1>404 Not Found</h1></center><hr><center><a href='https://gitee.com/jfinal/jfinal' target='_blank'><b>Powered by JFinal 5.2.6</b></a></center></body></html>".getBytes());
        errorHtmlMap.put(500, "<html><head><title>500 Internal Server Error</title></head><body bgcolor='white'><center><h1>500 Internal Server Error</h1></center><hr><center><a href='https://gitee.com/jfinal/jfinal' target='_blank'><b>Powered by JFinal 5.2.6</b></a></center></body></html>".getBytes());

        errorJsonMap.put(400, "{\"state\":\"fail\",\"msg\":\"400 Bad Request\"}".getBytes());
        errorJsonMap.put(401, "{\"state\":\"fail\",\"msg\":\"401 Unauthorized\"}".getBytes());
        errorJsonMap.put(403, "{\"state\":\"fail\",\"msg\":\"403 Forbidden\"}".getBytes());
        errorJsonMap.put(404, "{\"state\":\"fail\",\"msg\":\"404 Not Found\"}".getBytes());
        errorJsonMap.put(500, "{\"state\":\"fail\",\"msg\":\"500 Internal Server Error\"}".getBytes());
    }

    /** 状态码 */
    protected int errorCode;

    /** 错误页视图，或直接给定的 JSON 文本 */
    protected String viewOrJson;

    /**
     * 构造（可指定错误页视图或 JSON 文本）。
     *
     * @param errorCode  状态码
     * @param viewOrJson 错误页视图或 JSON 文本；null 表示用内建页
     */
    public LegacyErrorRender(int errorCode, String viewOrJson) {
        this.errorCode = errorCode;
        this.viewOrJson = viewOrJson;
    }

    /**
     * 构造（用内建页）。
     *
     * @param errorCode 状态码
     */
    public LegacyErrorRender(int errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * 设置某状态码的错误页视图。
     *
     * @param errorCode 状态码
     * @param view      视图
     */
    public static void setErrorView(int errorCode, String view) {
        errorViewMap.put(errorCode, view);
    }

    /**
     * 取某状态码的错误页视图。
     *
     * @param errorCode 状态码
     * @return 视图；未配置返回 null
     */
    public static String getErrorView(int errorCode) {
        return errorViewMap.get(errorCode);
    }

    /**
     * 覆盖某状态码的内建 HTML 错误页。
     *
     * <p><b>注意与回退页的不对称：</b>本方法用
     * {@code content.getBytes(getEncoding())} —— <b>带编码</b>；
     * 而 {@link #getErrorHtml()} 的回退页用的是<b>不带参数</b>的 {@code getBytes()}
     * （平台默认字符集）。两处均逐字节码照抄，不得"统一"。</p>
     *
     * @param errorCode 状态码
     * @param content   HTML 内容
     */
    public static void setErrorHtmlContent(int errorCode, String content) {
        try {
            errorHtmlMap.put(errorCode, content.getBytes(getEncoding()));
        } catch (java.io.UnsupportedEncodingException e) {
            // 旧字节码抛的是 RuntimeException（【不是】RenderException），照抄
            throw new RuntimeException(e);
        }
    }

    /**
     * 覆盖某状态码的内建 JSON 错误体。
     *
     * <p>与 {@link #setErrorHtmlContent(int, String)} 同样用带编码的
     * {@code getBytes(getEncoding())}（逐字节码）。</p>
     *
     * @param errorCode 状态码
     * @param content   JSON 内容
     */
    public static void setErrorJsonContent(int errorCode, String content) {
        try {
            errorJsonMap.put(errorCode, content.getBytes(getEncoding()));
        } catch (java.io.UnsupportedEncodingException e) {
            // 同上：旧字节码为 RuntimeException
            throw new RuntimeException(e);
        }
    }

    /**
     * 渲染错误页（规则见类注释）。
     */
    public void render() {
        response.setStatus(errorCode);

        String ct = request.getContentType();
        boolean json = ct != null && ct.indexOf("json") != -1;

        if (viewOrJson == null && !json) {
            viewOrJson = getErrorView(errorCode);
        }

        if (viewOrJson != null) {
            LegacyRender r = json
                    ? LegacyRenderManager.getRenderFactory().getJsonRender(viewOrJson)
                    : LegacyRenderManager.getRenderFactory().getRender(viewOrJson);
            r.setContext(request, response).render();
            return;
        }

        ServletOutputStream os = null;
        try {
            response.setContentType(json ? CONTENT_TYPE_JSON + getEncoding()
                    : CONTENT_TYPE_HTML + getEncoding());
            os = response.getOutputStream();
            os.write(json ? getErrorJson() : getErrorHtml());
        } catch (Exception e) {
            if (e instanceof IOException) {
                close(os);
            }
            throw new LegacyRenderException(e);
        }
    }

    /**
     * 取 HTML 错误页字节：命中内建表则用之，否则按状态码现拼（回退页）。
     *
     * @return 字节
     */
    public byte[] getErrorHtml() {
        byte[] b = errorHtmlMap.get(errorCode);
        if (b != null) {
            return b;
        }
        return ("<html><head><title>" + errorCode + " Error</title></head><body bgcolor='white'>"
                + "<center><h1>" + errorCode + " Error</h1></center><hr>"
                + "<center><a href='https://gitee.com/jfinal/jfinal' target='_blank'>"
                + "<b>Powered by JFinal 5.2.6</b></a></center></body></html>").getBytes();
    }

    /**
     * 取 JSON 错误体字节：命中内建表则用之，否则现拼（回退体）。
     *
     * <p>旧实现用 {@code Okv.of("state","fail").set("msg", errorCode + " Error").toJson()}；
     * {@code Okv} 是<b>有序</b> Kv，故 JSON 键序为 {@code state, msg}。
     * 本接缝的 {@code LegacyKv} 基于 HashMap（R37/R40 的保真选择），键序无保证，
     * 故此处显式用 {@link LinkedHashMap} 保住同一键序 —— <b>结果等价</b>。</p>
     *
     * @return 字节
     */
    public byte[] getErrorJson() {
        byte[] b = errorJsonMap.get(errorCode);
        if (b != null) {
            return b;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("state", "fail");
        m.put("msg", errorCode + " Error");
        return LegacyJsonKit.toJson(m).getBytes();
    }

    /**
     * 取状态码。
     *
     * @return 状态码
     */
    public int getErrorCode() {
        return errorCode;
    }

}
