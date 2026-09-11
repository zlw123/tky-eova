/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cn.eova.common.base.BaseController;
import cn.eova.core.menu.MenuUtil;
import cn.eova.model.Menu;
import cn.eova.tools.x;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.eova.compat.jfinal.aop.LegacyBefore;
import cn.eova.compat.jfinal.kit.LegacyRet;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyTx;

/**
 * <p>ported from: cn.eova.core.HomeController
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>首页控制器（145 行）：首页/菜单树/用户菜单</li>
 *   <li>【已声明适配 1】Before -> LegacyBefore、Tx -> LegacyTx、Ret -> LegacyRet、Record -> EovaRecord</li>
 *   <li>【已声明适配 2】Db.use(ds) -> EovaGateways.get(ds)</li>
 *   <li>【已声明适配 3】静态 Db.findFirst/save/delete/batch -> EovaGateways 的同名静态助手（jfinal 静态 Db 走默认数据源；EovaGateways 的静态助手同样走 get(null)）</li>
 * </ol>
 */
/**
 * 首页接口
 *
 * @author Jieven
 */
public class HomeController extends BaseController {

    public void menu() {

        int rid = getUser().getRid();

        // 获取菜单和分类
        List<EovaRecord> cats = EovaGateways.get(x.DS_EOVA).find("SELECT * FROM eova_menu where is_hide = 0 and type = 'dir' ORDER BY parent_id, num");
        // 所有菜单
        //List<EovaRecord> menus = EovaGateways.get(x.DS_EOVA).find("SELECT * FROM eova_menu where is_hide = 0 and type <> 'dir' ORDER BY id");
        List<Menu> menus = MenuUtil.buildMenu(getUser());
        if (menus == null) {
            renderText("当前角色无权限, 请联系管理员分配权限!");
            return;
        }
        // 非管理员禁用平台维护
        if (!getUser().isAdmin()) {
            for (EovaRecord r : cats) {
                if (r.getStr("name").equals("平台维护")) {
                    r.set("is_hide", true);
                }
            }
        }

        renderJson(LegacyRet.ok().set("menus", menus).set("cats", cats));
    }

    @LegacyBefore(LegacyTx.class)
    public void star() {
        JSONObject pms = getJsonObj();
        System.out.println(pms.toJSONString());

        int menuId = pms.getIntValue("menu_id");
        int num = pms.getIntValue("num");

        EovaRecord r = EovaGateways.findFirst("select * from menu_user where menu_id = ?", menuId);
        if (r == null) {
            r = new EovaRecord();
            r.set("uid", UID());
            r.set("menu_id", menuId);
            r.set("num", num);
            EovaGateways.save("menu_user", r);
            x.log.info("{}-{}收藏菜单{}", getUser().getCompanyId(), getUser().getName(), menuId);
        } else {
            EovaGateways.delete("delete from menu_user where menu_id = ?", menuId);
            x.log.info("{}-{}取消收藏{}", getUser().getCompanyId(), getUser().getName(), menuId);
        }

        renderJson(LegacyRet.ok());
    }

    // 重新排序
    @LegacyBefore(LegacyTx.class)
    public void resort() {
        String s = getRawData();
        JSONArray list = JSON.parseArray(s);

        List<String> sqls = new ArrayList<>();

        for (Iterator i = list.iterator(); i.hasNext(); ) {
            String sql = "";

            JSONObject o = (JSONObject) i.next();
            int id = o.getIntValue("id");
            int userNum = o.getIntValue("user_num");

            sqls.add(String.format("update menu_user set num = %s where uid = %s and menu_id = %s", userNum, UID(), id));
        }

        // sqls.forEach(sa -> System.out.println(sa));

        EovaGateways.batch(sqls, sqls.size());

        renderJson(LegacyRet.ok());
    }


//    public void meta() {
//        renderEnjoy("/home/meta.html");
//    }
//
//    public void page(){
//        renderJson(Db.findById("menu", get("id")));
//    }
//
//    public void getMyMenu() {
//        List<EovaRecord> menus = Db.find("SELECT * FROM menu where is_hide = 0 and id in (select menu_id from menu_user where uid = ?)", UID());
//        renderJson(LegacyRet.ok().set("menus", menus));
//    }


}

//int oid = om.getIntValue("id");
//    int onum = om.getIntValue("user_num");
//    int nnum = nm.getIntValue("user_num");
//
//    int UID = 100000;
//
//// 向下移动
//        if (onum < nnum) {
//        // 向下移动算法 小于O, 大于等于N的--
//        // 1   2(O)  3   4  5(N)    6
//        // 1         2   3  4    O  6 ; O=5
//        Db.update("UPDATE menu_user SET num = num-1 WHERE uid = ? and ? < num and num <= ?", UID, onum, nnum);
//        }
//        // 向上移动
//        else {
//        // 向上移动算法 N~O ++
//        //      1(N) 2   3   4(O)  5
//        // O=1  2    3   4         5
//        Db.update("UPDATE menu_user SET num = num+1 WHERE uid = ? and ? <= num and num < ?", UID, nnum, onum);
//        }
//
//        // N->O 交换
//        Db.update("UPDATE menu_user SET num = ? WHERE uid = ? and num = ?", UID, nnum, onum);