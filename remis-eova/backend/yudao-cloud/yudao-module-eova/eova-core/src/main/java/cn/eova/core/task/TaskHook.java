package cn.eova.core.task;

import cn.eova.aop.AopContext;
import cn.eova.hook.EovaMetaHook;
import cn.eova.plugin.cron4j.EovaCronPlugin;

/**
 * <p>ported from: cn.eova.core.task.TaskHook
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>任务 Hook（73 行）</li>
 * </ol>
 */
public class TaskHook implements EovaMetaHook {

    @Override
    public String invoke(Action action, AopContext ac) throws Exception {
        switch (action) {

            case DELETE_BEFORE:
                deleteBefore(ac);
                break;
            case ADD_SUCCEED:
                addSucceed(ac);
                break;

            case UPDATE_BEFORE:
                updateBefore(ac);
                break;

        }
        return null;
    }


    public String deleteBefore(AopContext ac) throws Exception {
        int taskId = ac.record.getInt("id");

        // 先停止任务
        EovaCronPlugin.get(taskId).stop();

        return null;
    }

    public String addSucceed(AopContext ac) throws Exception {
        int taskId = ac.record.getInt("id");
        String name = ac.record.getStr("name");
        String className = ac.record.getStr("clazz");
        String cronExp = ac.record.getStr("exp");
        int state = ac.record.getInt("state");
        String params = ac.record.getStr("params");

        // 创建任务(默认禁用)
        EovaCronPlugin.createTask(taskId, name, className, cronExp, params, false);

        return null;
    }

    public String updateBefore(AopContext ac) throws Exception {
        int taskId = ac.record.getInt("id");
        String name = ac.record.getStr("name");
        String className = ac.record.getStr("clazz");
        String cronExp = ac.record.getStr("exp");
        boolean state = ac.record.getBoolean("state");
        String params = ac.record.getStr("params");

        // 删除任务
        EovaCronPlugin.remove(taskId);

        // 禁用 例如类发生变化
        ac.record.set("state", false);
        //Task.dao.updateState(taskId, Task.STATE_STOP);
        // 重新初始化任务(任务参数有变化)
        EovaCronPlugin.createTask(taskId, name, className, cronExp, params, false);

        return null;
    }

}

