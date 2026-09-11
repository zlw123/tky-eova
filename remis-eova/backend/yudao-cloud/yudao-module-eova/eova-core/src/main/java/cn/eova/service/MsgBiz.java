/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.service;

import cn.eova.common.Ds;
import cn.eova.common.utils.web.SseKit;
import cn.eova.model.MsgType;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.service.MsgBiz
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>消息推送服务（42 行）：写 eova_msg + 走 SSE 推送</li>
 *   <li>【既有缺陷，原样保留】r.set("type", title) 把 type 列写成了【title】的值 ——方法签名里的 MsgType type 只用于 SSE 载荷，从未入库。不得改成 r.set("type", type…)</li>
 *   <li>【已声明适配 1】com.jfinal.plugin.activerecord.Record -> cn.eova.db.EovaRecord</li>
 *   <li>【已声明适配 2】Db.use(Ds.EOVA).save(table, record) -> EovaGateways.get(Ds.EOVA).save(table, record)</li>
 *   <li>【已声明适配 3】com.jfinal.kit.Kv -> cn.eova.compat.jfinal.kit.LegacyKv</li>
 *   <li>    （SseKit.pushMsg 的形参就是 LegacyKv）</li>
 * </ol>
 */
/**
 * 消息服务
 *
 * @author Jieven
 */
public class MsgBiz {


    /**
     * 发送消息推送
     * @param formUid 发信人UID
     * @param toUid 收信人UID
     * @param title 标题
     * @param info 内容
     * @param type 弹出类型
     */
    public void send(int formUid, int toUid, String title, String info, MsgType type) {
        EovaRecord r = new EovaRecord();
        r.set("type", title);
        r.set("from_uid", formUid);
        r.set("to_uid", toUid);
        r.set("info", info);
        EovaGateways.get(Ds.EOVA).save("eova_msg", r);

        SseKit.pushMsg(toUid, LegacyKv.of("title", title).set("info", info).set("type", type.toString().toLowerCase()));
    }

}