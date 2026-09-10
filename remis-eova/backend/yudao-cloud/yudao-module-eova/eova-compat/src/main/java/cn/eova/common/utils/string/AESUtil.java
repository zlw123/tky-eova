/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.string;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import cn.eova.tools.x;

/**
 * <p>ported from: cn.eova.common.utils.string.AESUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>密钥经 {@code x.conf.get("aes.key", "eova.cn")} 读取，默认值即 {@code eova.cn}</li>
 *   <li>加解密失败时返回 {@code null}；{@code decrypt} 失败则抛 {@code RuntimeException}（与非对称侧行为相反）</li>
 *   <li>{@code KeyGenerator.init(128, new SecureRandom(password.getBytes()))} 的用法原样保留</li>
 *   <li>类内保留原 {@code main} 调试方法</li>
 * </ol>
 */
public class AESUtil {

    private static String getKey() {
        // 之前的key = yunyou17lw
        final String key = "eova.cn";
        return x.conf.get("aes.key", key);
    }

    /**
     * 加密
     * @param content 需要加密的内容
     * @param password  加密密码
     * @return
     */
    private static byte[] encryptByte(String content, String password) {
        try {
            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            kgen.init(128, new SecureRandom(password.getBytes()));
            SecretKey secretKey = kgen.generateKey();
            byte[] enCodeFormat = secretKey.getEncoded();
            SecretKeySpec key = new SecretKeySpec(enCodeFormat, "AES");
            Cipher cipher = Cipher.getInstance("AES");// 创建密码器
            byte[] byteContent = content.getBytes("utf-8");
            cipher.init(Cipher.ENCRYPT_MODE, key);// 初始化
            byte[] result = cipher.doFinal(byteContent);
            return result; // 加密
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 解密
     * @param content  待解密内容
     * @param password 解密密钥
     * @return
     */
    private static byte[] decryptByte(byte[] content, String password) {
        try {
            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            kgen.init(128, new SecureRandom(password.getBytes()));
            SecretKey secretKey = kgen.generateKey();
            byte[] enCodeFormat = secretKey.getEncoded();
            SecretKeySpec key = new SecretKeySpec(enCodeFormat, "AES");
            Cipher cipher = Cipher.getInstance("AES");// 创建密码器
            cipher.init(Cipher.DECRYPT_MODE, key);// 初始化
            byte[] result = cipher.doFinal(content);
            return result; // 加密
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * AES加密
     * @param str
     * @return
     */
    public static String encrypt(String str) {
        return BinaryHex.binary2Hex(encryptByte(str, getKey()));
    }

    /**
     * AES解密
     * @param str
     * @return
     */
    public static String decrypt(String str) {
        try {
            // 16进制转2进制
            byte[] decryptFrom = BinaryHex.hex2Byte(str);
            // 根据byte进行解码
            byte[] decryptResult = decryptByte(decryptFrom, getKey());
            return new String(decryptResult);
        } catch (Exception e) {
            throw new RuntimeException("AES解密异常,请检查是否AES密文, 密文=" + str);
        }
    }

    public static void main(String[] args) {

        String str = "root";

        // 加密
        System.out.println("加密前：" + str);
        String s = AESUtil.encrypt(str);
        System.out.println("加密后：" + s);

        System.out.println("解密后：" + AESUtil.decrypt(s));
    }
}