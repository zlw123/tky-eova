/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.button;

import java.util.List;

import cn.eova.tools.x;
import cn.eova.common.Ds;
import cn.eova.common.Easy;
import cn.eova.common.base.BaseController;
import cn.eova.common.utils.db.DbUtil;
import cn.eova.common.utils.xx;
import cn.eova.config.EovaConst;
import cn.eova.model.Button;
import cn.eova.model.RoleBtn;
import cn.eova.compat.jfinal.aop.LegacyBefore;
import cn.eova.db.EovaGateways;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyNestedTransactionHelpException;
import cn.eova.db.EovaRecord;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyTx;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyTxConfig;

/**
 * <p>ported from: cn.eova.core.button.ButtonController
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>按钮管理控制器（152 行）：增删改 + 角色授权</li>
 *   <li>【已声明适配 1】com.jfinal.aop.Before -> cn.eova.compat.jfinal.aop.LegacyBefore</li>
 *   <li>【已声明适配 2】tx.Tx / tx.TxConfig -> LegacyTx / LegacyTxConfig（第 62 轮接缝）</li>
 *   <li>【已声明适配 3】NestedTransactionHelpException -> LegacyNestedTransactionHelpException</li>
 *   <li>【已声明适配 4】Db.use(ds).find(sql) -> EovaGateways.get(ds).find(sql)、Record -> EovaRecord</li>
 *   <li>@Before(Tx.class) + @TxConfig(Ds.EOVA) 的组合属事务契约，不得改</li>
 * </ol>
 */
/**
 * 按钮管理
 *
 * @author Jieven
 * @date 2014-9-11
 */
public class ButtonController extends BaseController {

    // Meta 快速添加按钮
    public void add() {
        set("menuCode", get(0));
        set("role", EovaConst.ADMIN_RID);
        render("/eova/button/add/app.html");
    }

    @LegacyBefore(LegacyTx.class)
    @LegacyTxConfig(Ds.EOVA)
    public void doAdd() {

        String menuCode = get("menu_code");

        int num = getInt("num");
        String icon = get("icon");
        String name = get("name");
        String style = get("style");
        String event = x.str.replaceBlank(get("event"));
        String ui = x.str.replaceBlank(get("ui"));
        String auth = x.str.replaceBlank(get("auth"));
        String roles = get("role", EovaConst.ADMIN_RID + "");

        try {
            Button btn = new Button();
            btn.set("menu_code", menuCode);
            Integer groupNum = getParaToInt("cat", 0);
//			btn.set("cat", groupNum);// 暂时不支持按钮组
            // 计算最大排序值
            btn.set("num", Button.dao.getMaxOrderNum(menuCode, groupNum) + 1);
            btn.set("icon", icon);
            btn.set("name", name);
            btn.set("style", style);
            btn.set("event", event);
            btn.set("ui", ui);
            btn.set("auth", auth);

            // 选了模版按钮才必填(非模版按钮在UI中执行指定)
//            String uri = bs;
//            if (!x.isEmpty(uri)) {
//                btn.set("uri", uri);
//            }
            btn.save();

            // 分配权限
            for (String role : roles.split(",")) {
                RoleBtn rb = new RoleBtn();
                rb.set("rid", role);
                rb.set("bid", btn.get("id"));
                rb.save();
            }

            OK();
        } catch (Exception e) {
            e.printStackTrace();
            NO("新增按钮失败,请看控制台日志寻找原因！");
            throw new LegacyNestedTransactionHelpException("新增按钮失败");
        }
    }

    // 菜单基本功能管理
    public void quick() {
        setAttr("menuCode", get(0));
        render("/eova/button/quick.html");
    }

    // 菜单基本功能管理
    @LegacyBefore(LegacyTx.class)
    @LegacyTxConfig(Ds.EOVA)
    public void doQuick() {
        try {
            Button btn = new Button();
            String menuCode = get("menu_code");
            btn.set("menu_code", menuCode);
            Integer groupNum = getParaToInt("cat", 0);
            btn.set("cat", groupNum);
            btn.set("icon", get("icon"));
            btn.set("name", get("name"));
            btn.set("ui", get("ui"));
            // 选了模版按钮才必填(非模版按钮在UI中执行指定)
            String uri = get("uri");
            if (!x.isEmpty(uri)) {
                btn.set("uri", uri);
            }
            btn.set("bs", xx.replaceBlank(get("bs")));
            // 计算最大排序值
            btn.set("num", Button.dao.getMaxOrderNum(menuCode, groupNum) + 1);
            btn.save();

            // 分配权限
            String roles = get("role", EovaConst.ADMIN_RID + "");
            for (String role : roles.split(",")) {
                RoleBtn rb = new RoleBtn();
                rb.set("rid", role);
                rb.set("bid", btn.get("id"));
                rb.save();
            }

            renderJson(new Easy());
        } catch (Exception e) {
            e.printStackTrace();
            renderJson(new Easy("新增按钮失败,请看控制台日志寻找原因！"));
            throw new LegacyNestedTransactionHelpException("新增按钮失败");
        }
    }

    // 导出选中按钮数据
    public void doExport() {
        String ids = get(0);

        StringBuilder sb = new StringBuilder();

        String sql = "select * from eova_button where id in (" + ids + ")";
        List<EovaRecord> objects = EovaGateways.get(Ds.EOVA).find(sql);
        DbUtil.generateSql(Ds.EOVA, "eova_button", objects, "id", sb);

        renderText(sb.toString());
    }

}