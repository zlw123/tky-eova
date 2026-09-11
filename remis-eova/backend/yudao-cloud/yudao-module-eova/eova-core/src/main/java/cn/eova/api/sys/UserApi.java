package cn.eova.api.sys;

import cn.eova.tools.x;
import cn.eova.core.api.BaseApi;
import cn.eova.hook.EovaHookType;
import cn.eova.hook.HookRegistry;
import cn.eova.service.sm;
import cn.eova.compat.jfinal.aop.LegacyBefore;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyTx;

/**
 * <p>ported from: cn.eova.api.sys.UserApi
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>企业用户接口（82 行）：sync/updateRole/init/logout 四个 action，全部委托 USER 钩子</li>
 *   <li>【已声明适配 1】com.jfinal.aop.Before -> cn.eova.compat.jfinal.aop.LegacyBefore</li>
 *   <li>【已声明适配 2】com.jfinal.plugin.activerecord.tx.Tx -> cn.eova.compat.jfinal.plugin.activerecord.LegacyTx（仅 updateRole 标注 @LegacyBefore(LegacyTx.class)：事务边界由接缝提供）</li>
 *   <li>【已声明适配 3】com.jfinal.kit.Kv -> cn.eova.compat.jfinal.kit.LegacyKv</li>
 *   <li>【既有缺陷，原样保留】init() 的注释写『初始化当前系统的业务用户』，但钩子编码传入的是 ""（与其他三个 action 相同），未按 _biz 之外的语义区分 —— 原样保留</li>
 *   <li>四个 action 的 _biz 串 sync/updateRole/init 属对外契约（企业侧按 biz 分派），不得改名</li>
 *   <li>钩子异常一律 NO("...异常:" + e.getMessage()) 后 return；logout 走 sm.login.forceLogoutByLoginId(login_id)，login_id 取自 Kv 而非会话</li>
 * </ol>
 */
/**
 * 企业用户接口
 *
 * @author Jieven
 */
public class UserApi extends BaseApi {

    // 用户同步(创建or更新)
    public void sync() {

        LegacyKv kv = getKv();
        try {
            // 执行用户授权钩子, 此处不方便处理用户业务, 由用户自定义实现钩子
            HookRegistry.getAction(EovaHookType.USER, "").invoke(this, kv.set("_biz", "sync"));
        } catch (Exception e) {
            NO("用户同步异常:" + e.getMessage());
            return;
        }

        OK();
    }

    // 更新用户角色
    @LegacyBefore(LegacyTx.class)
    public void updateRole() {

        LegacyKv kv = getKv();
        try {
            // 执行用户授权钩子, 此处不方便处理用户业务, 由用户自定义实现钩子
            HookRegistry.getAction(EovaHookType.USER, "").invoke(this, kv.set("_biz", "updateRole"));
        } catch (Exception e) {
            x.log.error("用户角色更新异常", e);
            NO("用户角色更新异常:" + e.getMessage());
            return;
        }

        OK();
    }

    // 初始化当前系统的业务用户(对用户进行产品授权时使用)
    public void init() {
        LegacyKv kv = getKv();
        try {
            // 执行用户授权钩子, 此处不方便处理用户业务, 由用户自定义实现钩子
            HookRegistry.getAction(EovaHookType.USER, "").invoke(this, kv.set("_biz", "init"));
        } catch (Exception e) {
            x.log.error("用户初始化异常", e);
            NO("用户初始化异常:" + e.getMessage());
            return;
        }

        OK();
    }

    // 用户强制退出
    public void logout() {
        LegacyKv kv = getKv();

        String loginId = kv.getStr("login_id");
        try {
            sm.login.forceLogoutByLoginId(loginId);
        } catch (Exception e) {
            x.log.error("用户强制退出异常", e);
            NO("用户退出异常:" + e.getMessage());
            return;
        }

        OK();
    }

}