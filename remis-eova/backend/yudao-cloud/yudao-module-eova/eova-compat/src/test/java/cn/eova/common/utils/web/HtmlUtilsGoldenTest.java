/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.web;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code cn.eova.common.utils.web} 家族（本批 `HtmlUtil` 等）的跨实现等价判据。
 *
 * <p><b>为什么这个家族值得专门判据：</b>{@code HtmlUtil.XSSEncode} / {@code clearHtml} /
 * {@code replaceExcludeTag} 属<b>安全契约</b> —— 它们决定不可信内容能否穿透到页面。
 * 这类函数"看起来等价"没有意义：一个字符的转义差异就是一个 XSS 缺口。
 * 故让旧实现上场、逐输入比对。
 *
 * <p><b>判据只取确定性输入：</b>本家族里 {@code IpUtil}（依赖 servlet 请求）、
 * {@code MacUtil}（依赖平台命令）、{@code HttpUtil}/{@code QQUtil}（依赖网络）
 * 的结果随环境而变，<b>不适合</b>跨实现比对 —— 它们由逐字节复核覆盖（byte-identity
 * 即语义等价），本判据不纳入。这也划清了"哪些单元靠字节复核、哪些靠实跑"的边界。
 *
 * <p>acceptanceProfile: golden-html-utils
 */
class HtmlUtilsGoldenTest {

    /** 待比对的纯函数（新旧同名同签名） */
    private static final List<String> METHODS = List.of(
            "XSSEncode", "HTMLEncode", "htmlEncode", "clearHtml",
            "clearUBB", "formatJsStr", "UBBToHTML");

    /** 输入矩阵：覆盖标签/引号/实体/UBB/脚本/中文/边界 */
    private static Map<String, String> inputs() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("空串", "");
        m.put("纯文本", "hello world");
        m.put("中文", "你好，世界");
        m.put("标签", "<div class=\"a\">x</div>");
        m.put("script", "<script>alert('xss')</script>");
        m.put("script混大写", "<SCRIPT SRC=//evil.com/x.js></SCRIPT>");
        m.put("事件属性", "<img src=x onerror=alert(1)>");
        m.put("onclick", "<a href=\"#\" onclick=\"evil()\">c</a>");
        m.put("双引号", "a\"b");
        m.put("单引号", "a'b");
        m.put("尖括号", "a<b>c");
        m.put("已编码实体", "&lt;b&gt; &amp; &#39;");
        m.put("javascript协议", "<a href=\"javascript:alert(1)\">x</a>");
        m.put("iframe", "<iframe src=//evil.com></iframe>");
        m.put("换行制表", "a\nb\tc");
        m.put("反斜杠", "a\\b");
        m.put("UBB加粗", "[b]粗[/b]");
        m.put("UBB图片", "[img]http://a/b.png[/img]");
        m.put("UBB链接", "[url=http://a]t[/url]");
        m.put("UBB表情", "[face:1/]");
        m.put("混合", "<p>[b]x[/b]</p><script>y</script>");
        m.put("未闭合标签", "<div><span>x");
        m.put("注释", "<!-- c -->x");
        m.put("数字实体", "&#x3c;script&#x3e;");
        // —— 以下按 XSSEncode 的【每一条过滤规则】逐条补齐输入 ——
        // （首版矩阵漏了其中数条，导致"去掉某条过滤"的反向对照【不会触发】——
        //   即矩阵对那几条规则零判别力。这是 R45 的又一实例：输入必须压在会分叉的量上）
        m.put("script变种%3C", "%3Cscript%3Ealert(1)%3C/script%3E");
        m.put("eval调用", "eval(x)");
        m.put("alert调用", "alert(1)");
        m.put("prompt调用", "prompt('x')");
        m.put("confirm调用", "confirm('x')");
        m.put("document.cookie", "document.cookie");
        m.put("document.cookie混合", "<script>document.cookie</script>");
        m.put("union select", "1 union select password from eova_user");
        m.put("and exists", "1 and exists(select 1)");
        m.put("or exists", "1 or exists(select 1)");
        m.put("javascript协议2", "javascript:alert(1)");
        m.put("script带属性", "<script src=x></script>ok");
        m.put("大小写SCRIPT", "<ScRiPt>x</ScRiPt>");
        return m;
    }

    @Test
    @DisplayName("HtmlUtil 纯函数逐输入比对：输出应与旧实现逐字节一致（安全契约）")
    void htmlUtilFunctionsMatchOld() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable(),
                "旧产物缺失：" + OldImplementationLoader.oldClassesDir());
        ClassLoader loader = OldImplementationLoader.create(OldImplementationLoader.locateRepoRoot());
        Class<?> oldClass = loader.loadClass("cn.eova.common.utils.web.HtmlUtil");
        // 自校验：确保上场的是旧产物，而非本次 port 的新实现
        OldImplementationLoader.assertFromOldArtifacts(oldClass);

        List<String> diffs = new ArrayList<>();
        int compared = 0;
        int changed = 0;

        for (String name : METHODS) {
            Method oldM;
            Method newM;
            try {
                oldM = oldClass.getMethod(name, String.class);
                newM = HtmlUtil.class.getMethod(name, String.class);
            } catch (NoSuchMethodException e) {
                diffs.add("方法签名不一致：" + name);
                continue;
            }
            for (Map.Entry<String, String> e : inputs().entrySet()) {
                compared++;
                String oldOut = invoke(oldM, e.getValue());
                String newOut = invoke(newM, e.getValue());
                if (!e.getValue().equals(oldOut)) {
                    changed++;
                }
                if (!String.valueOf(oldOut).equals(String.valueOf(newOut))) {
                    diffs.add(name + "(" + e.getKey() + "):\n    输入=" + e.getValue()
                            + "\n    旧=" + oldOut + "\n    新=" + newOut);
                }
            }
        }

        System.out.println("[web 工具比对] 方法 " + METHODS.size() + " × 输入 " + inputs().size()
                + " = 比对 " + compared + " 条；其中输出与输入不同（确有处理发生）的 " + changed
                + " 条；差异 " + diffs.size());
        // 非空转证据：若所有输出都等于输入，说明矩阵没压到任何处理逻辑
        assertTrue(changed > compared / 4,
                "比对矩阵退化：仅 " + changed + "/" + compared + " 条发生了实际处理，判据不具判别力");
        assertEquals(0, diffs.size(),
                "web 工具差异 " + diffs.size() + " 条：\n" + String.join("\n", diffs));
    }

    private static String invoke(Method m, String arg) {
        try {
            return (String) m.invoke(null, arg);
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            return "throw:" + t.getClass().getName() + ":" + t.getMessage();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
