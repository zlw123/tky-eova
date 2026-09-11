package cn.eova.core.sse;

import cn.eova.common.base.BaseController;
import cn.eova.common.utils.web.SseKit;
import cn.eova.compat.jfinal.kit.LegacyRet;

/**
 * <p>ported from: cn.eova.core.sse.SSEController
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>SSE 控制器（22 行）</li>
 * </ol>
 */
public class SSEController extends BaseController {
    // 开启sse连接
    public void index() {
        // System.out.println("start sse:UID=" + UID());
        SseKit.startAsync(UID() + "", this);
    }

    // 关闭sse连接
    public void close() {
        // 主动关闭连接
        // System.out.println("close sse:UID=" + UID());
        SseKit.remove(UID() + "");
        renderJson(LegacyRet.ok("OK"));
    }

}