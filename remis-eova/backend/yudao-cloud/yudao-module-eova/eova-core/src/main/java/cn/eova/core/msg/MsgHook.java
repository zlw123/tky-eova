package cn.eova.core.msg;

import cn.eova.aop.AopContext;
import cn.eova.engine.SqlCondition;
import cn.eova.hook.EovaMetaHook;

/**
 * <p>ported from: cn.eova.core.msg.MsgHook
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>消息 Hook（32 行）</li>
 * </ol>
 */
public class MsgHook implements EovaMetaHook {

    @Override
    public String invoke(Action action, AopContext ac) throws Exception {
        switch (action) {

            case QUERY_BEFORE:
                queryBefore(ac);
                break;

        }
        return null;
    }

    public String queryBefore(AopContext ac) throws Exception {
        // 2=查全部
        int status = ac.ctrl.getInt("status", 2);
        if (status == 2) {
            ac.setCondition("status", new SqlCondition("and status >= 0"));
        }

        return null;
    }

}

