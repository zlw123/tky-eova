/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.captcha;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.concurrent.ThreadLocalRandom;

import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.compat.jfinal.kit.LegacyLogKit;
import cn.eova.compat.jfinal.kit.LegacyStrKit;
import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyRenderException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import javax.imageio.ImageIO;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.captcha.CaptchaRender} 的等价接缝。
 *
 * <p>ported from: com.jfinal.captcha.CaptchaRender（jfinal 5.2.6 制品）
 *
 * <p><b>逐字节取自旧字节码的部分（判据逐条钉住）：</b>
 * <table border="1">
 *   <tr><th>项</th><th>旧值</th></tr>
 *   <tr><td>Cookie 名（{@code captchaName}）</td><td>{@code "_jfinal_captcha"}（<b>带前导下划线</b>）</td></tr>
 *   <tr><td>字符集（{@code charArray}）</td><td>{@code "3456789ABCDEFGHJKMNPQRSTUVWXYabcdefghjkmnpqrstuvwxy"}（剔除易混字符 0/1/2/I/L/O/Z 等）</td></tr>
 *   <tr><td>验证码长度</td><td>4（{@code getRandomString} 里 {@code new char[4]}，逐位 {@code ThreadLocalRandom.nextInt}）</td></tr>
 *   <tr><td>有效期</td><td>180 秒（{@code createCaptcha} → {@code new Captcha(key, value, 180)}）</td></tr>
 *   <tr><td>Cookie 属性</td><td>{@code setMaxAge(-1)}（会话 Cookie）、{@code setPath("/")}、{@code setHttpOnly(true)}</td></tr>
 *   <tr><td>响应头</td><td>{@code Pragma: no-cache}、{@code Cache-Control: no-cache}、{@code Expires: 0}（{@code setDateHeader}）</td></tr>
 *   <tr><td>内容类型/格式</td><td>{@code image/jpeg} + {@code ImageIO.write(image, "jpeg", os)}</td></tr>
 *   <tr><td>图片尺寸/类型</td><td>{@code WIDTH=108} × {@code HEIGHT=40}，{@code BufferedImage.TYPE_INT_RGB}</td></tr>
 *   <tr><td>字体池（{@code RANDOM_FONT}）</td><td>5 个：Dialog BOLD 33 / DialogInput BOLD 34 / Serif BOLD 33 / SansSerif BOLD 34 / Monospaced BOLD 34</td></tr>
 *   <tr><td>{@code getRandomColor(fc, bc, r)}</td><td>{@code fc/bc} 超过 255 先钳到 255；三个通道各为 {@code fc + r.nextInt(bc - fc)}</td></tr>
 *   <tr><td>{@code validate(key, value)}</td><td>缓存取出 ⇒ 非 null 且 {@code notExpired()} 且 {@code value.equalsIgnoreCase(captcha.getValue())} ⇒ <b>移除</b>并返回 true；否则 false</td></tr>
 *   <tr><td>{@code validate(ctrl, value)}</td><td>用 {@code ctrl.getCookie(captchaName)} 取 key，成功后 <b>再</b> {@code ctrl.removeCookie(captchaName)}</td></tr>
 *   <tr><td>cookie 取键</td><td>{@code request.getCookies()} 里 {@code getName().equals(captchaName)} 的第一个；无则 null</td></tr>
 *   <tr><td>缺 key 时</td><td>{@code StrKit.isBlank(key)} ⇒ {@code StrKit.getRandomUUID()}</td></tr>
 * </table>
 *
 * <p><b>⚠️ 已声明：本类【不声称】像素级一致（唯一一处）。</b>
 * {@code drawGraphic} 是一串 AWT 调用（随机背景 {@code getRandomColor(210,250)} + {@code fillRect(0,0,108,40)}
 * + 干扰线 + 逐字符随机字体/颜色/旋转），其字节码里各随机数的<b>取值区间与调用顺序</b>我按结构复刻，
 * 但<b>没有逐指令还原</b>。理由与实际影响：旧侧产物本身是<b>随机图</b>，既无 golden 可比、
 * 也不构成任何调用方契约（前端只把 &lt;img&gt; 指到这个 URL）。
 * 故判据只钉"值/字符集/长度/有效期/Cookie/缓存/校验/响应头/内容类型/图片尺寸与格式"，
 * 并把"图像相似度"列为<b>不验收项</b>（见 DES-002-R4 §r81）。</p>
 */
public class LegacyCaptchaRender extends LegacyRender {

    /** Cookie 名（旧字节码静态初始化） */
    protected static String captchaName = "_jfinal_captcha";

    /** 图片宽（旧字节码 {@code WIDTH = 108}） */
    protected static final int WIDTH = 108;

    /** 图片高（旧字节码 {@code HEIGHT = 40}） */
    protected static final int HEIGHT = 40;

    /** 字符集（旧字节码静态初始化，剔除易混字符） */
    protected static char[] charArray = "3456789ABCDEFGHJKMNPQRSTUVWXYabcdefghjkmnpqrstuvwxy".toCharArray();

    /** 字体池（旧字节码静态初始化的 5 个 Font，顺序即语义） */
    protected static Font[] RANDOM_FONT = {
            new Font("Dialog", Font.BOLD, 33),
            new Font("DialogInput", Font.BOLD, 34),
            new Font("Serif", Font.BOLD, 33),
            new Font("SansSerif", Font.BOLD, 34),
            new Font("Monospaced", Font.BOLD, 34)};

    /**
     * 设置字符集（旧实现 {@code setCharArray}）。
     *
     * @param charArray 字符集
     */
    public static void setCharArray(char[] charArray) {
        LegacyCaptchaRender.charArray = charArray;
    }

    /**
     * 设置 Cookie 名（旧实现 {@code setCaptchaName}）。
     *
     * @param captchaName Cookie 名
     */
    public static void setCaptchaName(String captchaName) {
        LegacyCaptchaRender.captchaName = captchaName;
    }

    /**
     * 写出验证码（旧字节码顺序：先入缓存 → 写 Cookie → 响应头 → 内容类型 → 画图 → 写流）。
     */
    @Override
    public void render() {
        LegacyCaptcha captcha = createCaptcha();
        LegacyCaptchaManager.me().getCaptchaCache().put(captcha);

        Cookie cookie = new Cookie(captchaName, captcha.getKey());
        cookie.setMaxAge(-1);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0L);
        response.setContentType("image/jpeg");

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        drawGraphic(captcha.getValue(), image);

        ServletOutputStream os = null;
        try {
            os = response.getOutputStream();
            ImageIO.write(image, "jpeg", os);
        } catch (java.io.IOException e) {
            // 旧字节码：LogKit.logNothing(t)；devMode 时抛 RenderException
            LegacyLogKit.logNothing(e);
            if (getDevMode()) {
                throw new LegacyRenderException(e);
            }
        } catch (Exception e) {
            LegacyLogKit.logNothing(e);
            if (getDevMode()) {
                throw new LegacyRenderException(e);
            }
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception e) {
                    LegacyLogKit.logNothing(e);
                }
            }
        }
    }

    /**
     * 造验证码（旧字节码逐行等价）。
     *
     * @return 验证码（key 取 Cookie，空则随机 UUID；有效 180 秒）
     */
    protected LegacyCaptcha createCaptcha() {
        String key = getCaptchaKeyFromCookie();
        if (LegacyStrKit.isBlank(key)) {
            key = LegacyStrKit.getRandomUUID();
        }
        return new LegacyCaptcha(key, getRandomString(), 180);
    }

    /**
     * 从请求 Cookie 里取验证码键（旧字节码：首个同名 Cookie 的值，无则 null）。
     *
     * @return 键；未携带时为 null
     */
    protected String getCaptchaKeyFromCookie() {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(captchaName)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 生成 4 位随机串（旧字节码：{@code char[4]}，逐位从 {@link #charArray} 取）。
     *
     * @return 随机串
     */
    protected String getRandomString() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        char[] chars = new char[4];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = charArray[random.nextInt(charArray.length)];
        }
        return String.valueOf(chars);
    }

    /**
     * 画图（结构复刻：随机背景 + 干扰线 + 逐字符随机字体/颜色/旋转；<b>不声称像素级一致</b>）。
     *
     * @param randomString 验证码文本
     * @param image        目标图
     */
    protected void drawGraphic(String randomString, BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);

        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 背景：getRandomColor(210, 250)
        g.setColor(getRandomColor(210, 250, random));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 干扰线与噪点：getRandomColor(20, 120)
        g.setColor(getRandomColor(20, 120, random));
        for (int i = 0; i < 20; i++) {
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                    random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }
        for (int i = 0; i < 120; i++) {
            String c = String.valueOf(charArray[random.nextInt(charArray.length)]);
            g.drawString(c, random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }

        // 文本：逐字符随机颜色 + 随机字体 + 随机旋转
        g.setColor(getRandomColor(20, 120, random));
        for (int i = 0; i < randomString.length(); i++) {
            g.setFont(RANDOM_FONT[random.nextInt(RANDOM_FONT.length)]);
            int x = (WIDTH / (randomString.length() + 1)) * (i + 1);
            int y = HEIGHT - random.nextInt(22) - 2;
            java.awt.geom.AffineTransform old = g.getTransform();
            g.rotate(Math.toRadians(random.nextInt(21) - 10), x, y);
            g.drawString(String.valueOf(randomString.charAt(i)), x, y);
            g.setTransform(old);
        }
        g.dispose();
    }

    /**
     * 随机色（旧字节码逐行等价：fc/bc 先钳到 255，三通道各取 {@code fc + r.nextInt(bc - fc)}）。
     *
     * @param fc     下界
     * @param bc     上界
     * @param random 随机源
     * @return 随机颜色
     */
    protected Color getRandomColor(int fc, int bc, ThreadLocalRandom random) {
        if (fc > 255) {
            fc = 255;
        }
        if (bc > 255) {
            bc = 255;
        }
        int r = fc + random.nextInt(bc - fc);
        int g = fc + random.nextInt(bc - fc);
        int b = fc + random.nextInt(bc - fc);
        return new Color(r, g, b);
    }

    /**
     * 校验验证码（旧字节码逐行等价：取缓存 → 未过期 → 不区分大小写 → <b>移除</b>）。
     *
     * @param key   验证码键
     * @param value 用户输入
     * @return 是否通过
     */
    public static boolean validate(String key, String value) {
        LegacyCaptchaCache cache = LegacyCaptchaManager.me().getCaptchaCache();
        LegacyCaptcha captcha = cache.get(key);
        if (captcha != null && captcha.notExpired() && captcha.getValue().equalsIgnoreCase(value)) {
            cache.remove(captcha.getKey());
            return true;
        }
        return false;
    }

    /**
     * 校验并清理 Cookie（旧字节码逐行等价）。
     *
     * @param c     控制器（用 {@code getCookie(captchaName)} 取键，成功后 {@code removeCookie}）
     * @param value 用户输入
     * @return 是否通过
     */
    public static boolean validate(LegacyController c, String value) {
        String key = c.getCookie(captchaName);
        if (validate(key, value)) {
            c.removeCookie(captchaName);
            return true;
        }
        return false;
    }

}
