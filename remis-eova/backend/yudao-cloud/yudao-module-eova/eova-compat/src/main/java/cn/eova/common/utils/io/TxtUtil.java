/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import cn.eova.compat.LegacySettings;

/**
 * 文本读取工具。
 *
 * <p>ported from: cn.eova.common.utils.io.TxtUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 *
 * <p><b>allowedAdaptation（唯一一处，非语义变更）：</b>
 * 旧实现的编码取自 JFinal 宿主全局
 * {@code JFinal.me().getConstants().getEncoding()}（2 处调用点），
 * 新宿主无此全局，改为 {@link LegacySettings#getEncoding()}。
 * 两者默认值一致（均为 {@code "UTF-8"}），且新接缝可配置 —— 行为等价。
 *
 * <p><b>其余部分逐行等价</b>，包括：
 * <ol>
 *   <li>{@code getTxt} 失败时吞异常（printStackTrace）并返回已累积内容（可能为部分内容或空串）；</li>
 *   <li>行间使用 {@link System#lineSeparator()} 前缀拼接 —— 首行前也会带一个分隔符；</li>
 *   <li>{@code read(InputStream)} 在编码不支持时抛 {@code IllegalStateException}；</li>
 *   <li>{@code read(Reader)} 在 IO 异常时抛 {@code IllegalStateException("read error", ex)}。</li>
 * </ol>
 *
 * <p><b>验证方式说明：</b>本单元<b>无法</b>用跨实现比对（见
 * {@code TxtUtilGoldenTest}）—— 旧实现依赖 JFinal 运行时全局，而
 * {@code JFinal.me()} 需要 {@code javax.servlet.ServletContext}，
 * 在无 servlet 容器时抛 {@code NoClassDefFoundError}。
 * 故本单元采用<b>意图断言</b>验证，并在测试中显式记录该限制。
 */
public class TxtUtil {

    public final static int DEFAULT_BUFFER_SIZE = 1024 * 4;

    /**
     * 读取文件全部文本；失败时返回已读内容（不抛异常）
     */
    public static String getTxt(String path) {
        File file = new File(path);
        StringBuilder result = new StringBuilder();
        try {
            InputStreamReader isr = new InputStreamReader(new FileInputStream(file), LegacySettings.getEncoding());
            BufferedReader br = new BufferedReader(isr);
            String s = null;
            while ((s = br.readLine()) != null) {
                result.append(System.lineSeparator() + s);
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result.toString();
    }

    /**
     * 按默认编码读取输入流全部文本
     */
    public static String read(InputStream in) {
        InputStreamReader reader;
        try {
            reader = new InputStreamReader(in, LegacySettings.getEncoding());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return read(reader);
    }

    /**
     * 读取字符流全部文本
     */
    public static String read(Reader reader) {
        try {

            StringWriter writer = new StringWriter();

            char[] buffer = new char[DEFAULT_BUFFER_SIZE];
            int n = 0;
            while (-1 != (n = reader.read(buffer))) {
                writer.write(buffer, 0, n);
            }

            return writer.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("read error", ex);
        }
    }

}
