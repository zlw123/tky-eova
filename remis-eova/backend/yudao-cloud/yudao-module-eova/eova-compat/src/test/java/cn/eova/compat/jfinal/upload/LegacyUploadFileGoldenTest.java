/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.upload;

import java.io.File;
import java.lang.reflect.Constructor;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LegacyUploadFile} 的<b>跨实现</b>判据（对照旧 jfinal 制品）。
 *
 * <p>旧 {@code com.jfinal.upload.UploadFile} 是<b>可直接实例化的普通类</b>
 * （无 servlet 依赖），故本判据能做真正的跨实现比对 —— 不需要像 Controller 那样退化为声明面比对。</p>
 *
 * <p>acceptanceProfile: golden-uploadfile-seam</p>
 */
class LegacyUploadFileGoldenTest {

    /**
     * {@code getFile()} 的三种情形 + 五个 getter 逐项对照旧制品。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("LegacyUploadFile：五个 getter 与 getFile() 逐项对照旧 jfinal 制品")
    void matchesOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.upload.UploadFile", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());
        Constructor<?> oldCtor = oldCls.getConstructor(String.class, String.class,
                String.class, String.class, String.class);

        // ① 正常情形：参数顺序必须一致（parameterName, uploadPath, fileName, originalFileName, contentType）
        Object oldF = oldCtor.newInstance("file", "/tmp/up", "a.png", "orig.png", "image/png");
        LegacyUploadFile newF = new LegacyUploadFile("file", "/tmp/up", "a.png", "orig.png", "image/png");
        for (String g : new String[]{"getParameterName", "getUploadPath", "getFileName",
                "getOriginalFileName", "getContentType"}) {
            assertEquals(oldCls.getMethod(g).invoke(oldF),
                    LegacyUploadFile.class.getMethod(g).invoke(newF),
                    g + " 必须一致（参数顺序错位会在此暴露）");
        }
        File oldFile = (File) oldCls.getMethod("getFile").invoke(oldF);
        assertEquals(oldFile, newF.getFile(), "getFile 必须一致");
        assertEquals("/tmp/up" + File.separator + "a.png", newF.getFile().getPath(),
                "路径拼接为 uploadPath + File.separator + fileName");

        // ② uploadPath 为 null → 两侧都返回 null
        Object oldNull1 = oldCtor.newInstance("file", null, "a.png", "orig.png", "image/png");
        assertEquals(oldCls.getMethod("getFile").invoke(oldNull1),
                new LegacyUploadFile("file", null, "a.png", "orig.png", "image/png").getFile());
        assertNull(new LegacyUploadFile("file", null, "a.png", "orig.png", "image/png").getFile(),
                "uploadPath 为 null 时应返回 null");

        // ③ fileName 为 null → 两侧都返回 null
        Object oldNull2 = oldCtor.newInstance("file", "/tmp/up", null, "orig.png", "image/png");
        assertEquals(oldCls.getMethod("getFile").invoke(oldNull2),
                new LegacyUploadFile("file", "/tmp/up", null, "orig.png", "image/png").getFile());

        assertTrue(oldCls != LegacyUploadFile.class, "旧侧不得就是新接缝本身");
    }

}
