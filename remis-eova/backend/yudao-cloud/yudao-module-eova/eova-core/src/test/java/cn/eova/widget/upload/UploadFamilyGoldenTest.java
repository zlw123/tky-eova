/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.widget.upload;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import cn.eova.aop.UploadIntercept;
import cn.eova.common.Ds;
import cn.eova.compat.jfinal.kit.LegacyRet;
import cn.eova.compat.jfinal.upload.LegacyMultipartRequest;
import cn.eova.compat.jfinal.upload.LegacyUploadConfig;
import cn.eova.compat.jfinal.upload.LegacyUploadFile;
import cn.eova.config.EovaConfig;
import cn.eova.db.EovaDbGateway;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;
import cn.eova.model.MetaFieldConfig;
import cn.eova.tools.x;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传族 2 单元（第 77 轮 port：{@code UploadUtil} 276 + {@code UploadController} 111）与其接缝的判据。
 *
 * <p><b>判据分三层，理由各自不同：</b></p>
 * <ol>
 *   <li><b>接缝层</b>（{@link LegacyMultipartRequest}）：旧栈的 multipart 解析由
 *       jfinal + {@code com.jfinal:cos} 从原始 HTTP 体完成，新栈改由 Spring 解析 ——
 *       这是<b>唯一被替换的机制</b>，因此"落盘目录/重名策略/jsp 防护/白名单/只解析一次"
 *       这五条<b>旧栈可观测语义</b>必须逐条被钉住，否则替换机制时最容易悄悄改掉。</li>
 *   <li><b>单元层</b>（{@code UploadUtil.upload}）：端到端走
 *       "取参 → 目录/文件名策略 → 白名单 → 落库 → 改名"，判据同时断言
 *       <b>返回值契约</b>（fileName/oldFileName/uploadDir）、<b>落盘事实</b>与
 *       <b>{@code eova_file} 的列语义</b>（code=newFileName、name=临时名、kb=字节数）。</li>
 *   <li><b>失败路径</b>：旧实现"哪些失败走 {@code Ret.fail} 的中文串、哪些走 catch 兜底"
 *       是对外可见的契约（前端按 msg 展示），故逐条断言消息文本。</li>
 * </ol>
 *
 * <p><b>环境纪律（R58）</b>：本判据会改两处<b>全局状态</b> ——
 * {@link LegacyUploadConfig}（baseUploadPath）与 {@code x.conf}（file.dir.base）；
 * 两者在每个用例后<b>还原</b>，避免"单跑绿、全量红"。</p>
 */
class UploadFamilyGoldenTest {

    /** 可用的原件内容（非图片） */
    private static final byte[] TEXT_BYTES = "hello-eova".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tmp;

    /** 记录 {@code eova_file} 落库的替身 */
    static final class Rec {
        final List<String> tables = new ArrayList<>();
        final List<EovaRecord> records = new ArrayList<>();
    }

    /**
     * 建记录式网关。
     *
     * @param rec 记录器
     * @return 替身
     */
    private static EovaDbGateway gateway(Rec rec) {
        InvocationHandler h = (p, m, args) -> {
            switch (m.getName()) {
                case "save":
                    rec.tables.add((String) args[0]);
                    rec.records.add((EovaRecord) args[1]);
                    return true;
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                default:
                    return null;
            }
        };
        return (EovaDbGateway) Proxy.newProxyInstance(
                UploadFamilyGoldenTest.class.getClassLoader(),
                new Class<?>[]{EovaDbGateway.class}, h);
    }

    /**
     * 造一个临时源文件。
     *
     * @param name    文件名
     * @param content 内容
     * @return 文件
     * @throws Exception 写失败
     */
    private File source(String name, byte[] content) throws Exception {
        Path p = tmp.resolve("src-" + System.nanoTime() + "-" + name);
        Files.write(p, content);
        return p.toFile();
    }

    /**
     * 装配接缝环境：baseUploadPath = 临时目录，并把 {@code file.dir.base} 也指向它
     * （后者决定 UploadUtil 的"预创建目录"，两者在旧栈同源）。
     */
    private void installSeam() {
        LegacyUploadConfig.init(tmp.toString(), 10485760L, "UTF-8");
        x.conf.addConfig("file.dir.base", tmp.toString());
    }

    @AfterEach
    void restoreGlobals() {
        LegacyUploadConfig.init("upload", 10485760L, "UTF-8");
        x.conf.getProps().remove("file.dir.base");
        EovaConfig.setUploadIntercept(null);
        EovaGateways.clear();
    }

    /**
     * 建带部件的控制器。
     *
     * @param parts 部件三元组：参数名 / 原始文件名 / 内容
     * @return 控制器
     * @throws Exception 建临时文件失败
     */
    private ProbeController ctrlWith(Object[]... parts) throws Exception {
        ProbeController ctrl = new ProbeController("test_code");
        LegacyMultipartRequest req = new LegacyMultipartRequest();
        for (Object[] p : parts) {
            req.addPart((String) p[0], (String) p[1], "text/plain", source((String) p[1], (byte[]) p[2]));
        }
        ctrl.setMultipartRequest(req);
        return ctrl;
    }

    /** 上传控制器探针：{@code get(0)} 返回 urlPara 首段；无 servlet 上下文，故 get(name)/getUser() 返回 null */
    public static class ProbeController extends cn.eova.common.base.BaseController {

        private final String urlParaFirst;

        /**
         * @param urlParaFirst urlPara 首段（对应旧栈 get(0)）
         */
        public ProbeController(String urlParaFirst) {
            this.urlParaFirst = urlParaFirst;
        }

        @Override
        public String get(int index) {
            return index == 0 ? urlParaFirst : null;
        }

        @Override
        public String get(String name) {
            // 无请求上下文：等价于请求里没有 code/en 参数（UploadUtil 两条取配置分支都不命中）
            return null;
        }

        @Override
        public cn.eova.model.User getUser() {
            // 无会话：UploadUtil 走 uid/cid = "0" 分支
            return null;
        }
    }

    // ------------------------------------------------------------ 接缝层

    @Test
    @DisplayName("接缝：最终目录 = baseUploadPath + uploadPath（绝对/相对两条分支）")
    void finalPathRules() throws Exception {
        installSeam();
        ProbeController h = ctrlWith(new Object[]{"name", "a.txt", TEXT_BYTES});

        // 以 '/' 开头：直接拼接（不补分隔符）
        List<LegacyUploadFile> files = h.getFiles("/dir");
        assertEquals(1, files.size());
        assertEquals(tmp + "/dir", files.get(0).getUploadPath(), "最终目录 = baseUploadPath + uploadPath");
        assertTrue(new File(tmp + "/dir/a.txt").isFile(), "文件应落在最终目录下");

        // 相对路径：补 File.separator
        ProbeController h2 = ctrlWith(new Object[]{"name", "b.txt", TEXT_BYTES});
        List<LegacyUploadFile> files2 = h2.getFiles("reldir");
        assertEquals(tmp + File.separator + "reldir", files2.get(0).getUploadPath());
    }

    @Test
    @DisplayName("接缝：uploadPath 为 null 抛 IllegalArgumentException（旧消息逐字）")
    void nullUploadPathMessage() {
        installSeam();
        LegacyMultipartRequest req = new LegacyMultipartRequest();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> req.getFiles(null));
        assertEquals("uploadPath can not be null.", e.getMessage());
    }

    @Test
    @DisplayName("接缝：白名单外扩展名 → 删除全部已落盘文件 + 抛旧消息（含文件名）")
    void illegalExtensionDeletesAndThrows() throws Exception {
        installSeam();
        ProbeController h = ctrlWith(new Object[]{"name", "bad.exe", TEXT_BYTES});

        RuntimeException e = assertThrows(RuntimeException.class, () -> h.getFiles("/dir"));
        assertEquals("上传文件类型白名单不支持上传该文件: \"bad.exe\"", e.getMessage());
        assertFalse(new File(tmp + "/dir/bad.exe").exists(), "非法文件必须被删除");

        // 首个合法 + 次个非法：合法的那个也要被删（旧实现删除 uploadFiles 全部）
        ProbeController h2 = ctrlWith(
                new Object[]{"ok", "good.txt", TEXT_BYTES},
                new Object[]{"bad", "bad.exe", TEXT_BYTES});
        assertThrows(RuntimeException.class, () -> h2.getFiles("/dir"));
        assertFalse(new File(tmp + "/dir/good.txt").exists(),
                "发现非法文件时，已通过校验的文件也要被删除（handleIllegalUpload 语义）");
    }

    @Test
    @DisplayName("接缝：重名走 COS 策略 body+count+ext；.jsp 先加 _unsafe 再被白名单拒绝")
    void renamePolicyAndJspGuard() throws Exception {
        installSeam();
        ProbeController h = ctrlWith(
                new Object[]{"p1", "same.txt", TEXT_BYTES},
                new Object[]{"p2", "same.txt", TEXT_BYTES});
        List<LegacyUploadFile> files = h.getFiles("/dir");
        assertEquals(2, files.size());
        assertEquals("same.txt", files.get(0).getFileName());
        assertEquals("same1.txt", files.get(1).getFileName(), "已存在则 body+count+ext（COS 语义）");
        assertTrue(new File(tmp + "/dir/same.txt").isFile());
        assertTrue(new File(tmp + "/dir/same1.txt").isFile());

        // .jsp：先被改名成 a.jsp_unsafe，随后扩展名 jsp_unsafe 不在白名单 ⇒ 拒绝
        ProbeController h2 = ctrlWith(new Object[]{"p", "a.jsp", TEXT_BYTES});
        RuntimeException e = assertThrows(RuntimeException.class, () -> h2.getFiles("/dir"));
        assertEquals("上传文件类型白名单不支持上传该文件: \"a.jsp_unsafe\"", e.getMessage());
        assertFalse(new File(tmp + "/dir/a.jsp_unsafe").exists());
    }

    @Test
    @DisplayName("接缝：只解析一次（换 uploadPath 不重新解析，文件留在首次目录）")
    void resolveOnceOnly() throws Exception {
        installSeam();
        ProbeController h = ctrlWith(new Object[]{"name", "a.txt", TEXT_BYTES});
        List<LegacyUploadFile> first = h.getFiles("/dir");
        List<LegacyUploadFile> second = h.getFiles("/other");
        assertEquals(1, second.size(), "第二次调用复用首次解析结果");
        assertEquals(first.get(0).getFileName(), second.get(0).getFileName());
        assertEquals(tmp + "/dir", second.get(0).getUploadPath(), "目录仍是首次的");
        assertFalse(new File(tmp + "/other").exists(), "不得为第二次调用建目录");
    }

    @Test
    @DisplayName("接缝：控制器 getFile 族按参数名过滤；未注入部件容器时响亮失败")
    void controllerFileApi() throws Exception {
        installSeam();
        ProbeController h = ctrlWith(
                new Object[]{"fileA", "a.txt", TEXT_BYTES},
                new Object[]{"fileB", "b.txt", TEXT_BYTES});

        assertEquals("b.txt", h.getFile("fileB", "/dir").getFileName(), "按参数名过滤");
        assertEquals("a.txt", h.getFile("fileA", "/dir").getFileName());
        assertNull(h.getFile("nope"), "无命中返回 null（旧字节码 aconst_null）");

        // 未注入：不得静默返回空列表（那是接线缺陷被掩盖）
        ProbeController bare = new ProbeController("test_code");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> bare.getFiles("/dir"));
        assertTrue(e.getMessage().contains("setMultipartRequest"), e.getMessage());

        // 无参 getFiles()：旧实现用 baseUploadPath 自身作为 uploadPath
        ProbeController h2 = ctrlWith(new Object[]{"name", "c.txt", TEXT_BYTES});
        List<LegacyUploadFile> files = h2.getFiles();
        // 无参形态 = getFinalPath(baseUploadPath)：base 以 '/' 开头 ⇒ 直接拼接（+ 号，不补分隔符）
        assertEquals(tmp.toString() + tmp.toString(), files.get(0).getUploadPath(),
                "无参 getFiles() 的最终目录 = baseUploadPath + baseUploadPath（旧实现直接拼接）");
    }

    // ------------------------------------------------------------ 单元层（UploadUtil）

    @Test
    @DisplayName("UploadUtil：成功路径的返回值契约 + 落盘事实 + eova_file 三列语义")
    void uploadSuccess() throws Exception {
        installSeam();
        Rec rec = new Rec();
        EovaGateways.register(Ds.EOVA, gateway(rec));
        ProbeController h = ctrlWith(new Object[]{"name", "a.txt", TEXT_BYTES});

        LegacyRet rt = UploadUtil.upload(h, "file", "name", "/dir");

        assertTrue(rt.isOk(), "应成功：" + rt.getStr("msg"));
        String newFileName = rt.getStr("fileName");
        String uploadDir = rt.getStr("uploadDir");
        assertNotNull(newFileName);
        assertTrue(newFileName.endsWith(".txt"), "保留原扩展名：" + newFileName);
        assertFalse(newFileName.equals("a.txt"), "默认策略是随机文件名");
        assertEquals("a.txt", rt.getStr("oldFileName"), "oldFileName 是落盘前的临时名");
        assertEquals("/dir", uploadDir, "uploadDir 是 EOVA 侧的目录（不含 baseUploadPath）");

        File saved = new File(tmp + "/dir" + File.separator + newFileName);
        assertTrue(saved.isFile(), "改名的目标文件必须存在：" + saved);
        assertEquals(TEXT_BYTES.length, saved.length(), "内容必须完整搬过去");
        assertFalse(new File(tmp + "/dir/a.txt").exists(), "临时名不得残留（已被 rename 走）");

        assertEquals(1, rec.tables.size(), "恰好落一条 eova_file");
        assertEquals("eova_file", rec.tables.get(0));
        EovaRecord r = rec.records.get(0);
        assertEquals(newFileName, r.getStr("code"), "code = newFileName");
        assertEquals("a.txt", r.getStr("name"), "name = 临时文件名（旧实现如此）");
        assertEquals((long) TEXT_BYTES.length, ((Number) r.get("kb")).longValue(), "kb = 临时文件字节数");
        assertEquals("0", r.getStr("user_id"), "未取到 User 时 uid 固定 0");
        assertEquals("0", r.getStr("company_id"), "未取到 User 时 cid 固定 0");
    }

    @Test
    @DisplayName("UploadUtil：file 为 null 的失败路径是 NPE（既有缺陷）；图片非法走 Ret.fail 文案")
    void uploadFailPaths() throws Exception {
        installSeam();
        EovaGateways.register(Ds.EOVA, gateway(new Rec()));

        // ① 未上传任何部件（name == null 走 getFiles 分支）⇒ 列表为空，旧实现 `return Ret.fail("请选择一个文件")`
        //    但 finally 仍会执行，且 `file` 此刻为 null ⇒ NPE 覆盖该 return。
        //    ⇒ 默认配置（isOriginal=false）下，"请选择一个文件" 这条 Ret.fail 实际上【不可达】。
        ProbeController none = new ProbeController("test_code");
        none.setMultipartRequest(new LegacyMultipartRequest());
        NullPointerException npe1 = assertThrows(NullPointerException.class,
                () -> UploadUtil.upload(none, "file", null, "/dir"));
        assertTrue(npe1.getMessage() != null && npe1.getMessage().contains("getFile"),
                "必须是 finally 里 file.getFile() 的 NPE，实际：" + npe1.getMessage());

        // ② 白名单外扩展名：接缝的异常【在 ctrl.getFile(name, uploadDir) 内部】抛出 ⇒ `file` 仍为 null ⇒ 同样 NPE
        //    （而不是 catch 里的 "系统异常：文件上传失败,请稍后再试"）
        ProbeController bad = ctrlWith(new Object[]{"name", "bad.exe", TEXT_BYTES});
        NullPointerException npe2 = assertThrows(NullPointerException.class,
                () -> UploadUtil.upload(bad, "file", "name", "/dir"));
        assertTrue(npe2.getMessage() != null && npe2.getMessage().contains("getFile"),
                "必须是 finally 里 file.getFile() 的 NPE，实际：" + npe2.getMessage());

        // ③ 图片类型但内容不是图片：此时 `file` 已赋值 ⇒ finally 安全，Ret.fail 文案可达
        ProbeController img = ctrlWith(new Object[]{"name", "fake.png", TEXT_BYTES});
        LegacyRet r3 = UploadUtil.upload(img, "img", "name", "/dir");
        assertTrue(r3.isFail());
        assertEquals("该文件不是标准的图片文件格式，请勿手工修改文件格式", r3.getStr("msg"));
    }

    @Test
    @DisplayName("UploadUtil：上传拦截器短路（返回值直通 + 不再落库）")
    void uploadInterceptShortCircuits() throws Exception {
        installSeam();
        Rec rec = new Rec();
        EovaGateways.register(Ds.EOVA, gateway(rec));

        List<String> seen = new ArrayList<>();
        EovaConfig.setUploadIntercept(new UploadIntercept() {
            @Override
            public LegacyRet upload(String code, String en, MetaFieldConfig config, String newFileName,
                                    String uploadDir, LegacyUploadFile file) {
                seen.add(code + "|" + en + "|" + (config == null) + "|" + newFileName + "|" + uploadDir
                        + "|" + file.getOriginalFileName());
                return LegacyRet.fail("钩子拒绝");
            }

            @Override
            public LegacyRet query(List<EovaRecord> list) {
                return null;
            }
        });

        ProbeController h = ctrlWith(new Object[]{"name", "a.txt", TEXT_BYTES});
        LegacyRet rt = UploadUtil.upload(h, "file", "name", "/dir");

        assertTrue(rt.isFail());
        assertEquals("钩子拒绝", rt.getStr("msg"), "钩子的返回值必须直通");
        assertEquals(1, seen.size(), "钩子恰好调用一次");
        String[] parts = seen.get(0).split("\\|", -1);
        assertEquals("null", parts[0], "code 为 null（控制器未提供 code 参数）");
        assertEquals("null", parts[1], "en 为 null");
        assertEquals("true", parts[2], "config 为 null（无元字段配置）");
        assertEquals("/dir", parts[4]);
        assertEquals("a.txt", parts[5]);
        assertEquals(0, rec.tables.size(), "钩子短路后不得落 eova_file");
    }

    @Test
    @DisplayName("UploadUtil：文件名策略（ORIGINAL / ORIGINAL_TIME / 固定名）")
    void fileNameStrategies() throws Exception {
        installSeam();
        EovaGateways.register(Ds.EOVA, gateway(new Rec()));

        // 说明：ORIGINAL/ORIGINAL_TIME/固定名三种策略都由【元字段配置】驱动，而配置来自
        // code+en 查库（MetaField.dao.getByObjectCodeAndEn），本判据无可注入点 ⇒ 那三条
        // 记 pending（见 DES-002-R4 §r77 待验收清单）。此处只钉住【无配置】时的随机名公式。
        ProbeController h = ctrlWith(new Object[]{"name", "photo.jpg", new byte[]{1, 2, 3}});
        LegacyRet rt = UploadUtil.upload(h, "file", "name", "/dir");
        assertTrue(rt.isOk(), rt.getStr("msg"));
        String name = rt.getStr("fileName");
        String ts = name.substring(0, name.length() - 9);
        assertTrue(ts.matches("\\d{13}"), "前缀是 13 位毫秒时间戳：" + name);
        assertTrue(name.substring(name.length() - 9, name.length() - 4).matches("\\d{5}"),
                "中段是 5 位随机数：" + name);
        assertTrue(name.endsWith(".jpg"));
    }

}
