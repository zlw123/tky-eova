/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.io;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * io 工具族的跨实现行为等价测试（acceptanceProfile: golden-behavior-equivalence）。
 *
 * <p><b>比对纪律：</b>新旧两侧必须用<b>对称</b>的调用方式（同样捕获异常并渲染为可比较值）。
 * 否则"新侧直接调用"遇到既有异常（如 getFileType("") 抛越界）会以 ERROR 中断测试，
 * 掩盖"两侧其实一致"这一事实。
 *
 * <p><b>验证边界：</b>TxtUtil 的 getTxt/read(InputStream) 依赖 JFinal 运行时全局
 * （JFinal.me() 需 javax.servlet.ServletContext），旧侧无法执行 —— 该边界由本测试显式断言。
 */
class IoUtilsGoldenTest {

    private static ClassLoader isolated;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable(),
                "旧编译产物缺失：" + OldImplementationLoader.oldClassesDir());
        try {
            isolated = OldImplementationLoader.create(null);
            // 自校验：确认确实取到旧产物，而非本次 port 的新实现
            OldImplementationLoader.assertFromOldArtifacts(
                    Class.forName("cn.eova.common.utils.io.FileUtil", false, isolated));
        } catch (Exception e) {
            throw new IllegalStateException("加载旧实现失败", e);
        }
    }

    @Test
    @DisplayName("FileUtil 纯函数与旧实现一致（formatPath / formatWebPath / getFileType / checkFileType）")
    void fileUtilPureMatches() throws Exception {
        String[] paths = {"a/b/c.txt", "a\\b\\c.txt", "a/b\\c", "", "x", "a//b", "/root/x.png", "noext", ".hidden"};
        StringBuilder diffs = new StringBuilder();
        for (String p : paths) {
            for (String sp : new String[]{"-", "/", "_"}) {
                cmp(diffs, "formatPath(" + p + "," + sp + ")", "FileUtil", "formatPath",
                        new Class<?>[]{String.class, String.class}, new Object[]{p, sp});
            }
            cmp(diffs, "formatPath(" + p + ")", "FileUtil", "formatPath",
                    new Class<?>[]{String.class}, new Object[]{p});
            cmp(diffs, "formatWebPath(" + p + ")", "FileUtil", "formatWebPath",
                    new Class<?>[]{String.class}, new Object[]{p});
            cmp(diffs, "getFileType(" + p + ")", "FileUtil", "getFileType",
                    new Class<?>[]{String.class}, new Object[]{p});
            for (boolean isImg : new boolean[]{true, false}) {
                cmp(diffs, "checkFileType(" + p + "," + isImg + ")", "FileUtil", "checkFileType",
                        new Class<?>[]{String.class, boolean.class}, new Object[]{p, isImg});
            }
        }
        assertTrue(diffs.length() == 0, "差异：\n" + diffs);
        // 显式记录既有异常语义：空串取扩展名抛越界（两侧一致）
        assertEquals("throw java.lang.StringIndexOutOfBoundsException",
                render(callNew("FileUtil", "getFileType", new Class<?>[]{String.class}, "")));
        assertEquals(render(callOld("FileUtil", "getFileType", new Class<?>[]{String.class}, "")),
                render(callNew("FileUtil", "getFileType", new Class<?>[]{String.class}, "")),
                "getFileType(\"\") 的异常语义两侧须一致");
    }

    @Test
    @DisplayName("FileUtil 文件系统操作与旧实现效果一致")
    void fileUtilFsMatches(@TempDir Path tmpA, @TempDir Path tmpB) throws Exception {
        for (Path root : List.of(tmpA, tmpB)) {
            Files.writeString(root.resolve("a.txt"), "hello");
            Files.createDirectories(root.resolve("sub"));
        }
        String aTxt = tmpA.resolve("a.txt").toString();
        String bTxt = tmpB.resolve("a.txt").toString();

        same("isExists(存在)", "FileUtil", "isExists", new Class<?>[]{String.class}, aTxt, bTxt);
        same("isExists(不存在)", "FileUtil", "isExists", new Class<?>[]{String.class},
                aTxt + ".none", bTxt + ".none");
        same("isDir", "FileUtil", "isDir", new Class<?>[]{String.class},
                tmpA.resolve("sub").toString(), tmpB.resolve("sub").toString());
        same("checkFileSize", "FileUtil", "checkFileSize", new Class<?>[]{java.io.File.class, int.class},
                new java.io.File(aTxt), new java.io.File(bTxt), 1);

        assertEquals(render(callOld("FileUtil", "fileToByte", new Class<?>[]{java.io.File.class},
                        new java.io.File(aTxt))),
                render(callNew("FileUtil", "fileToByte", new Class<?>[]{java.io.File.class},
                        new java.io.File(bTxt))), "fileToByte");

        assertEquals(((java.io.File[]) callOld("FileUtil", "getFiles", new Class<?>[]{String.class},
                        tmpA.toString())).length,
                ((java.io.File[]) callNew("FileUtil", "getFiles", new Class<?>[]{String.class},
                        tmpB.toString())).length, "getFiles 数量");

        byte[] payload = "eova".getBytes(StandardCharsets.UTF_8);
        callOld("FileUtil", "writeByteToFile", new Class<?>[]{byte[].class, String.class, String.class},
                payload, tmpA.toString(), "w.bin");
        callNew("FileUtil", "writeByteToFile", new Class<?>[]{byte[].class, String.class, String.class},
                payload, tmpB.toString(), "w.bin");
        assertArrayEquals(Files.readAllBytes(tmpA.resolve("w.bin")), Files.readAllBytes(tmpB.resolve("w.bin")),
                "writeByteToFile 内容");

        assertNotNull(callOld("FileUtil", "createTempDir", new Class<?>[]{}));
        assertNotNull(callNew("FileUtil", "createTempDir", new Class<?>[]{}));
    }

    @Test
    @DisplayName("GzipUtil gzip 逐字节一致；ungzip 结果一致（含 null 情形）")
    void gzipUtilMatches() throws Exception {
        String[] payloads = {"", "hello", "中文压缩测试", "a".repeat(5000)};
        StringBuilder diffs = new StringBuilder();
        for (String s : payloads) {
            for (String enc : new String[]{"UTF-8", "GBK"}) {
                byte[] gzBytes = GzipUtil.gzip(s, enc);
                String oldGz = render(callOld("GzipUtil", "gzip",
                        new Class<?>[]{String.class, String.class}, s, enc));
                if (!oldGz.equals(render(gzBytes))) {
                    diffs.append("  gzip(").append(s.length()).append("字符,").append(enc).append(") 不一致\n");
                }
                String oldUn = render(callOld("GzipUtil", "ungzip", new Class<?>[]{byte[].class}, (Object) gzBytes));
                String newUn = render(callNew("GzipUtil", "ungzip", new Class<?>[]{byte[].class}, (Object) gzBytes));
                if (!oldUn.equals(newUn)) {
                    diffs.append("  ungzip(").append(enc).append(") 旧=").append(oldUn)
                            .append(" 新=").append(newUn).append('\n');
                }
                byte[] ung = GzipUtil.ungzip(gzBytes);
                if (ung != null && !s.isEmpty()) {
                    assertEquals(s, new String(ung, enc), "往返应还原原文（" + enc + "）");
                }
            }
        }
        assertTrue(diffs.length() == 0, "差异：\n" + diffs);

        byte[] raw = "abc".getBytes(StandardCharsets.UTF_8);
        assertEquals(render(callOld("GzipUtil", "readData", new Class<?>[]{java.io.InputStream.class},
                        new ByteArrayInputStream(raw))),
                render(callNew("GzipUtil", "readData", new Class<?>[]{java.io.InputStream.class},
                        new ByteArrayInputStream(raw))), "readData");
    }

    @Test
    @DisplayName("PathUtil.filter 与旧实现一致（非法字符与 \\0 截断过滤）")
    void pathUtilMatches() throws Exception {
        String[] names = {"a.jsp", "a.jsp\u0000.jpg", "a/b\\c", "a:b*c?d\"e<f>g", "normal.png",
                "中文\u0000.txt", "a|b", "", "a\u0001b"};
        StringBuilder diffs = new StringBuilder();
        for (String n : names) {
            String o = render(callOld("PathUtil", "filter", new Class<?>[]{String.class}, n));
            String v = render(callNew("PathUtil", "filter", new Class<?>[]{String.class}, n));
            if (!o.equals(v)) {
                diffs.append("  filter(").append(n.replace("\u0000", "\\0")).append(") 旧=")
                        .append(o).append(" 新=").append(v).append('\n');
            }
        }
        assertTrue(diffs.length() == 0, "差异：\n" + diffs);
        assertEquals("webshell.jsp.jpg", PathUtil.filter("webshell.jsp\u0000.jpg"), "\\0 应被过滤");
    }

    @Test
    @DisplayName("StreamUtil.copy 与旧实现一致；close 对 null 安全")
    void streamUtilMatches() throws Exception {
        byte[] data = "eova-stream".getBytes(StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream o1 = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream o2 = new java.io.ByteArrayOutputStream();
        assertEquals(callOld("StreamUtil", "copy",
                        new Class<?>[]{java.io.InputStream.class, java.io.OutputStream.class},
                        new ByteArrayInputStream(data), o1),
                callNew("StreamUtil", "copy",
                        new Class<?>[]{java.io.InputStream.class, java.io.OutputStream.class},
                        new ByteArrayInputStream(data), o2), "copy 返回字节数");
        assertArrayEquals(o1.toByteArray(), o2.toByteArray(), "copy 结果");

        StreamUtil.close((java.io.InputStream) null);
        StreamUtil.close((java.io.OutputStream) null);
        StreamUtil.close((java.io.Reader) null);
        StreamUtil.close((java.io.Writer) null);
    }

    @Test
    @DisplayName("XmlUtil 保持空类形态")
    void xmlUtilStaysEmpty() {
        assertEquals(0, XmlUtil.class.getDeclaredMethods().length, "XmlUtil 应为空类");
    }

    @Test
    @DisplayName("ImageUtil.isImage 与旧实现一致（含非图片与不存在路径）")
    void imageUtilMatches(@TempDir Path tmp) throws Exception {
        Path png = tmp.resolve("t.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png.toFile());
        Path txt = tmp.resolve("t.txt");
        Files.writeString(txt, "not an image");

        for (String p : new String[]{png.toString(), txt.toString(), tmp.resolve("nope.png").toString()}) {
            assertEquals(render(callOld("ImageUtil", "isImage", new Class<?>[]{String.class}, p)),
                    render(callNew("ImageUtil", "isImage", new Class<?>[]{String.class}, p)),
                    "isImage(" + p + ")");
        }
    }

    @Test
    @DisplayName("TxtUtil：read(Reader) 可跨实现比对；getTxt 须用【已存在】文件才能触及 JFinal 全局")
    void txtUtilBoundary(@TempDir Path tmp) throws Exception {
        // ① read(Reader) 不触碰 JFinal 全局 —— 可跨实现比对
        assertEquals(render(callOld("TxtUtil", "read", new Class<?>[]{java.io.Reader.class},
                        new java.io.StringReader("a\nb"))),
                render(callNew("TxtUtil", "read", new Class<?>[]{java.io.Reader.class},
                        new java.io.StringReader("a\nb"))), "read(Reader) 应可跨实现比对");

        // ② 不存在的路径：FileInputStream 先抛 FileNotFoundException，被既有 catch(Exception) 吞掉，
        //    因此【不会】触及 JFinal —— 这一分支两侧都返回空串（可比对）
        assertEquals(render(callOld("TxtUtil", "getTxt", new Class<?>[]{String.class},
                        tmp.resolve("absent.txt").toString())),
                render(callNew("TxtUtil", "getTxt", new Class<?>[]{String.class},
                        tmp.resolve("absent.txt").toString())),
                "文件不存在分支两侧应一致（均为空串）");

        // ③ 存在的文件：才会执行 JFinal.me().getConstants().getEncoding()
        //    该调用抛 NoClassDefFoundError（Error，不被 catch(Exception) 捕获）-> 旧侧不可执行
        Path exists = tmp.resolve("exists.txt");
        Files.writeString(exists, "x");
        String oldGetTxt = render(callOld("TxtUtil", "getTxt", new Class<?>[]{String.class},
                exists.toString()));
        assertTrue(oldGetTxt.startsWith("throw "),
                "旧侧 getTxt 对【已存在】文件应因缺少 JFinal 运行时全局而失败（实际=" + oldGetTxt + "）"
                        + " —— 这正是本单元无法完整跨实现比对的原因");

        // ④ read(InputStream) 无 IO 前置异常，必然触及该全局
        String oldRead = render(callOld("TxtUtil", "read", new Class<?>[]{java.io.InputStream.class},
                new ByteArrayInputStream(new byte[0])));
        assertTrue(oldRead.startsWith("throw "),
                "旧侧 read(InputStream) 应因缺少 JFinal 运行时全局而失败（实际=" + oldRead + "）");
    }

    @Test
    @DisplayName("TxtUtil 意图断言：默认 UTF-8、行间带分隔符、缺文件返回空串")
    void txtUtilIntent(@TempDir Path tmp) throws Exception {
        assertEquals("a\nb", TxtUtil.read(new java.io.StringReader("a\nb")));
        assertEquals("", TxtUtil.read(new java.io.StringReader("")));
        assertEquals("中文", TxtUtil.read(new ByteArrayInputStream("中文".getBytes(StandardCharsets.UTF_8))));

        Path f = tmp.resolve("t.txt");
        Files.writeString(f, "l1\nl2\nl3");
        String expected = System.lineSeparator() + "l1" + System.lineSeparator() + "l2"
                + System.lineSeparator() + "l3";
        assertEquals(expected, TxtUtil.getTxt(f.toString()), "行拼接形式须与旧实现一致（含首行前缀分隔符）");
        assertEquals("", TxtUtil.getTxt(tmp.resolve("nope.txt").toString()), "文件不存在应返回空串");
    }

    // ---------------- 对称调用辅助 ----------------

    /** 调用旧实现；异常渲染为 "throw <类名>" */
    private static Object callOld(String simpleName, String method, Class<?>[] types, Object... args)
            throws Exception {
        Method m = Class.forName("cn.eova.common.utils.io." + simpleName, true, isolated).getMethod(method, types);
        return invoke(m, args);
    }

    /** 调用新实现；异常同样渲染为 "throw <类名>" —— 必须与 callOld 对称 */
    private static Object callNew(String simpleName, String method, Class<?>[] types, Object... args)
            throws Exception {
        Method m = Class.forName("cn.eova.common.utils.io." + simpleName).getMethod(method, types);
        return invoke(m, args);
    }

    /** 统一调用与异常渲染 */
    private static Object invoke(Method m, Object... args) throws Exception {
        try {
            return m.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "throw " + e.getCause().getClass().getName();
        }
    }

    /** 把返回值/异常渲染为可比较字符串；数组按内容比较而非对象标识 */
    private static String render(Object v) {
        if (v instanceof byte[] b) {
            return Arrays.toString(b);
        }
        if (v instanceof java.io.File[] f) {
            return Arrays.toString(f);
        }
        return String.valueOf(v);
    }

    /** 二参语义等价断言 */
    private static void same(String label, String simpleName, String method, Class<?>[] types,
                             Object oldArg, Object newArg) throws Exception {
        assertEquals(render(callOld(simpleName, method, types, oldArg)),
                render(callNew(simpleName, method, types, newArg)), label);
    }

    /** 三参语义等价断言（如 checkFileSize：目录不同、阈值相同） */
    private static void same(String label, String simpleName, String method, Class<?>[] types,
                             Object oldArg, Object newArg, Object extra) throws Exception {
        assertEquals(render(callOld(simpleName, method, types, oldArg, extra)),
                render(callNew(simpleName, method, types, newArg, extra)), label);
    }

    /** 收集差异（矩阵比对，避免首个差异即中断） */
    private static void cmp(StringBuilder diffs, String label, String simpleName, String method,
                            Class<?>[] types, Object[] args) throws Exception {
        String o = render(callOld(simpleName, method, types, args));
        String n = render(callNew(simpleName, method, types, args));
        if (!o.equals(n)) {
            diffs.append("  ").append(label).append(": 旧=").append(o).append(" 新=").append(n).append('\n');
        }
    }
}
