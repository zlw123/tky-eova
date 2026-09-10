/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.net;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * <p>ported from: cn.eova.common.utils.net.MacUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>纯 JDK 实现；MAC 地址读取依赖平台命令输出，行为随环境而异 —— 逐字节保真即可</li>
 * </ol>
 */
/*
 * 物理地址是48位，别和ipv6搞错了
 */
public class MacUtil {
    public static void main(String[] args) throws UnknownHostException, SocketException {

        InetAddress ia = InetAddress.getLocalHost();
        System.out.println(ia);
        getLocalMac(ia);
    }

    private static void getLocalMac(InetAddress ia) throws SocketException {
        //获取网卡，获取地址
        byte[] mac = NetworkInterface.getByInetAddress(ia).getHardwareAddress();
        System.out.println("mac数组长度：" + mac.length);
        StringBuffer sb = new StringBuffer("");
        for (int i = 0; i < mac.length; i++) {
            if (i != 0) {
                sb.append("-");
            }
            //字节转换为整数
            int temp = mac[i] & 0xff;
            String str = Integer.toHexString(temp);
            System.out.println("每8位:" + str);
            if (str.length() == 1) {
                sb.append("0" + str);
            } else {
                sb.append(str);
            }
        }
        System.out.println("本机MAC地址:" + sb.toString().toUpperCase());
    }
}