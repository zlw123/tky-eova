/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.string;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * string 工具族的跨实现行为等价测试（acceptanceProfile: golden-behavior-equivalence）。
 */
class StringUtilsGoldenTest {

    private static ClassLoader isolated;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(OldImplementationLoader.oldClassesAvailable(),
                "旧编译产物缺失：" + OldImplementationLoader.oldClassesDir());
        try {
            isolated = OldImplementationLoader.create(null);

        // 自校验：确认确实取到旧产物，而非本次 port 的新实现
        OldImplementationLoader.assertFromOldArtifacts(Class.forName("cn.eova.common.utils.string.Base64", false, isolated));
        } catch (Exception e) {
            throw new IllegalStateException("加载旧实现失败", e);
        }
    }

    // ---------------- StringPool ----------------

    @Test
    @DisplayName("StringPool 是 interface（非 class），常量取值与旧实现一致")
    void stringPoolIsInterfaceWithSameConstants() throws Exception {
        Class<?> oldC = Class.forName("cn.eova.common.utils.string.StringPool", false, isolated);
        assertTrue(oldC.isInterface(), "旧 StringPool 应为 interface");
        assertTrue(StringPool.class.isInterface(), "新 StringPool 必须同为 interface（改为 class 会破坏静态导入方）");

        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        for (Field f : oldC.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            checked++;
            Object oldV = f.get(null);
            Field nf = StringPool.class.getField(f.getName());
            Object newV = nf.get(null);
            // 数组常量须按内容比较 —— String.valueOf 对数组给出对象标识
            String ov = oldV != null && oldV.getClass().isArray()
                    ? java.util.Arrays.toString((Object[]) oldV) : String.valueOf(oldV);
            String nv = newV != null && newV.getClass().isArray()
                    ? java.util.Arrays.toString((Object[]) newV) : String.valueOf(newV);
            if (!ov.equals(nv)) {
                mismatches.add("  " + f.getName() + " 旧=" + ov + " 新=" + nv);
            }
        }
        assertTrue(mismatches.isEmpty(), "常量取值差异：\n" + String.join("\n", mismatches));
        assertTrue(checked >= 50, "应比对应有常量数量，实际 " + checked);
    }

    // ---------------- BinaryHex ----------------

    @Test
    @DisplayName("BinaryHex 全方法等价（含 null/非法输入的既有返回）")
    void binaryHexMatches() throws Exception {
        // binary2Hex(String) —— 显式给类型，避免 null 实参触发重载歧义
        for (String s : new String[]{"1010101111001101", "00000000", "", null, "101", "1010101", "1111"}) {
            assertEquals(oldStatic("BinaryHex", "binary2Hex", new Class<?>[]{String.class}, s),
                    BinaryHex.binary2Hex(s), "binary2Hex(String " + s + ")");
        }
        // hex2Binary(String)
        for (String s : new String[]{"ABCD", "abcd", "", null, "A", "ABC", "0123456789"}) {
            assertEquals(oldStatic("BinaryHex", "hex2Binary", new Class<?>[]{String.class}, s),
                    BinaryHex.hex2Binary(s), "hex2Binary(" + s + ")");
        }
        // binary2Hex(byte[])
        for (byte[] b : new byte[][]{new byte[0], {0, 1, -1, 127, -128}, "eova".getBytes(StandardCharsets.UTF_8)}) {
            assertEquals(oldStatic("BinaryHex", "binary2Hex", new Class<?>[]{byte[].class}, (Object) b),
                    BinaryHex.binary2Hex(b), "binary2Hex(byte[" + b.length + "])");
        }
        // hex2Byte(String)
        for (String s : new String[]{"", "ABCD", "00FF"}) {
            Object oldR = oldStatic("BinaryHex", "hex2Byte", new Class<?>[]{String.class}, s);
            byte[] newR = BinaryHex.hex2Byte(s);
            assertEquals(render(oldR), render(newR), "hex2Byte(" + s + ")");
        }
        assertNull(BinaryHex.binary2Hex("101"), "长度非 8 倍数应返回 null");
        assertNull(BinaryHex.hex2Binary("A"), "奇数长度应返回 null");
    }

    // ---------------- Base64 ----------------

    @Test
    @DisplayName("Base64 encode/decode 与旧实现逐字节一致（含 padding / 空白 / 非法输入）")
    void base64Matches() throws Exception {
        byte[][] inputs = {
                new byte[0], {0}, {0, 1}, {0, 1, 2}, "eova".getBytes(StandardCharsets.UTF_8),
                "中文测试".getBytes(StandardCharsets.UTF_8), {1, 2, 3, 4, 5, 6, 7},
                {-1, -2, -3}, "a".getBytes(), "ab".getBytes(),
        };
        for (byte[] in : inputs) {
            Object oldE = oldStatic("Base64", "encode", new Class<?>[]{byte[].class}, (Object) in);
            String newE = Base64.encode(in);
            assertEquals(String.valueOf(oldE), String.valueOf(newE), "encode(" + in.length + " bytes)");

            Object oldD = oldStatic("Base64", "decode", newE);
            byte[] newD = Base64.decode(newE);
            assertEquals(render(oldD), render(newD), "decode round-trip");
        }
        // 边界与既有返回
        assertNull(Base64.encode(null), "encode(null) 应返回 null");
        assertNull(Base64.decode(null), "decode(null) 应返回 null");
        assertEquals("", Base64.encode(new byte[0]), "空数组应编码为空串");
        assertEquals(0, Base64.decode("").length, "空串应解码为空数组");
        // 含空白应被移除
        assertEquals("eova", new String(Base64.decode("ZW\n 92 YQ=="), StandardCharsets.UTF_8));
        assertEquals(render(oldStatic("Base64", "decode", "ZW\n 92 YQ==")),
                render(Base64.decode("ZW\n 92 YQ==")), "含空白输入两侧一致");
        // 长度非 4 倍数 -> null
        assertNull(Base64.decode("ABC"), "长度非 4 倍数应返回 null");
        // 非法字符 -> null
        assertNull(Base64.decode("****"), "非法字符应返回 null");

        // 与 JDK Base64 互为印证（正例）
        assertEquals(java.util.Base64.getEncoder().encodeToString("eova".getBytes()),
                Base64.encode("eova".getBytes()), "与 JDK Base64 标准实现一致");
    }

    // ---------------- EncodeUtil ----------------

    @Test
    @DisplayName("EncodeUtil 各编码转换与旧实现一致（null 入参返回 null）")
    void encodeUtilMatches() throws Exception {
        String[] probes = {"abc", "中文测试", "", "a\u00e9b"};
        for (String s : probes) {
            for (String m : new String[]{"toASCII", "toISO_8859_1", "toUTF_8", "toUTF_16BE",
                    "toUTF_16LE", "toUTF_16", "toGBK"}) {
                assertEquals(oldStatic("EncodeUtil", m, s), invokeEncode(m, s), m + "(" + s + ")");
            }
        }
        assertNull(EncodeUtil.toGBK(null), "null 入参应返回 null");
        assertNull(EncodeUtil.changeCharset(null, "UTF-8"), "null 入参应返回 null");
        assertEquals(oldStatic("EncodeUtil", "changeCharset", "中文", "UTF-8", "GBK"),
                EncodeUtil.changeCharset("中文", "UTF-8", "GBK"));
    }

    private static String invokeEncode(String method, String arg) throws Exception {
        return (String) EncodeUtil.class.getMethod(method, String.class).invoke(null, arg);
    }

    // ---------------- RSAUtil / RSAEncrypt ----------------

    @Test
    @DisplayName("RSAUtil 签名/验签与旧实现等价（同一密钥下签名值一致）")
    void rsaUtilMatches() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(1024, new SecureRandom(new byte[]{1, 2, 3}));
        KeyPair kp = gen.generateKeyPair();
        String priv = Base64.encode(kp.getPrivate().getEncoded());
        String pub = Base64.encode(kp.getPublic().getEncoded());
        String content = "eova-migration";

        // SHA1WithRSA 为确定性签名：同密钥同内容 -> 同签名
        Object oldSign = oldStatic("RSAUtil", "sign", content, priv);
        String newSign = RSAUtil.sign(content, priv);
        assertEquals(oldSign, newSign, "签名值应一致（确定性）");
        assertNotNull(newSign);

        // 验签等价（新旧互验）
        assertTrue(RSAUtil.doCheck(content, newSign, pub), "新实现应验签通过");
        assertEquals(oldStatic("RSAUtil", "doCheck", content, newSign, pub), RSAUtil.doCheck(content, newSign, pub));
        assertFalse(RSAUtil.doCheck("tampered", newSign, pub), "篡改内容应验签失败");

        // 异常吞掉后返回 null / false（既有契约）
        assertNull(RSAUtil.sign(content, "not-a-key"), "非法私钥应返回 null 而非抛异常");
        assertFalse(RSAUtil.doCheck(content, newSign, "not-a-key"), "非法公钥应返回 false");
    }

    @Test
    @DisplayName("RSAEncrypt.byteArrayToString 与旧实现一致；密钥读写往返可用")
    void rsaEncryptMatches() throws Exception {
        byte[][] inputs = {new byte[0], {0}, {0x0f, 0x10, (byte) 0xff}, "eova".getBytes()};
        for (byte[] b : inputs) {
            assertEquals(oldStatic("RSAEncrypt", "byteArrayToString", new Class<?>[]{byte[].class}, (Object) b),
                    RSAEncrypt.byteArrayToString(b));
        }
        assertEquals("0f 10 ff", RSAEncrypt.byteArrayToString(new byte[]{0x0f, 0x10, (byte) 0xff}),
                "小写十六进制、空格分隔");
    }

    // ---------------- AESUtil ----------------

    @Test
    @DisplayName("AESUtil：锁定既有缺陷 —— 加密非确定性、加解密互不兼容（新旧一致）")
    void aesUtilMatchesIncludingKnownDefect() throws Exception {
        String plain = "root";

        // 既有缺陷 1：encrypt 非确定性（SecureRandom 自播种导致每次密钥不同）
        String c1 = AESUtil.encrypt(plain);
        String c2 = AESUtil.encrypt(plain);
        assertNotNull(c1);
        assertNotNull(c2);
        assertFalse(c1.equals(c2), "encrypt 应为非确定性（既有行为，port 予以保留）");

        // 既有缺陷 2：自身往返必然失败（密钥每次不同 -> BadPadding -> 包装为 RuntimeException）
        assertDecryptFails(c1, "新实现自身往返");
        assertDecryptFails(c2, "新实现自身往返(第二次)");

        // 与旧实现一致：旧实现加密的密文，新实现同样无法解密（失败模式一致）
        Object oldCipher = oldStatic("AESUtil", "encrypt", new Class<?>[]{String.class}, plain);
        assertNotNull(oldCipher, "旧实现加密应返回非空密文");
        assertDecryptFails((String) oldCipher, "旧实现密文 -> 新实现解密");

        // 失败路径统一为 RuntimeException 且携带中文文案
        try {
            AESUtil.decrypt("ZZZZ");
            assertTrue(false, "非法密文应抛 RuntimeException");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("AES解密异常"), "异常文案应含 AES解密异常");
        }
    }

    /** 断言解密失败（既有缺陷使然），并核对异常类型与文案一致性 */
    private static void assertDecryptFails(String cipher, String label) {
        try {
            AESUtil.decrypt(cipher);
            assertTrue(false, label + " 预期解密失败（既有缺陷），但成功了");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("AES解密异常"),
                    label + " 异常文案应含 AES解密异常");
        }
    }

    // ---------------- 反射辅助 ----------------

    /** 按【显式参数类型】精确解析重载 —— 仅按参数个数会在 null 实参时挑错重载 */
    private static Object oldStatic(String simpleName, String method, Class<?>[] types, Object... args)
            throws Exception {
        Class<?> c = Class.forName("cn.eova.common.utils.string." + simpleName, true, isolated);
        Method m = c.getMethod(method, types);
        try {
            return m.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "throw " + e.getCause().getClass().getName();
        }
    }

    /** 便捷重载：参数类型由实参推断（实参不含 null 时使用） */
    private static Object oldStatic(String simpleName, String method, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        return oldStatic(simpleName, method, types, args);
    }

    /** 值比较：byte[] 按内容比较，其余按 toString */
    private static String render(Object v) {
        if (v instanceof byte[] b) {
            return java.util.Arrays.toString(b);
        }
        return String.valueOf(v);
    }
}
