/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.utils.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * 克隆工具。
 *
 * <p>ported from: cn.eova.common.utils.data.CloneUtil
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port。
 *
 * <p><b>刻意保留的既有语义：</b>异常被 <b>吞掉</b>（仅 printStackTrace）并返回 {@code null}，
 * 不向上抛。调用方依赖"失败返回 null"这一约定，不得改为抛异常。
 */
public class CloneUtil {

    /**
     * 序列化深度克隆数据
     * @param model
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static <T> T clone(T model) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(model);
            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bis);
            Object obj = ois.readObject();
            return (T) obj;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
