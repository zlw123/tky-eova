package cn.eova.core.menu;

import cn.eova.aop.AopContext;
import cn.eova.common.base.BaseCache;
import cn.eova.config.EovaConst;
import cn.eova.hook.EovaMetaHook;
import cn.eova.model.Button;
import cn.eova.model.Menu;
import cn.eova.model.RoleBtn;

/**
 * <p>ported from: cn.eova.core.menu.MenuHook
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>菜单 Hook（93 行）</li>
 * </ol>
 */
public class MenuHook implements EovaMetaHook {

    @Override
    public String invoke(Action action, AopContext ac) throws Exception {
        switch (action) {
            case GET_DATA:
                getData(ac);
                break;
            case ADD_SUCCEED:
                addSucceed(ac);
                break;
            case UPDATE_SUCCEED:
                updateSucceed(ac);
                break;
            case DELETE_BEFORE:
                deleteBefore(ac);
                break;
            case DELETE_SUCCEED:
                deleteSucceed(ac);
                break;
            case HIDE_BEFORE:
                hideBefore(ac);
                break;
        }
        return null;
    }

    public String hideBefore(AopContext ac) throws Exception {
        System.out.println("隐藏菜单:" + ac.record.getInt("id"));
        return null;
    }

    public String deleteBefore(AopContext ac) throws Exception {
        int id = ac.record.getInt("id");
        Menu menu = Menu.dao.findById(id);
        String code = menu.getStr("code");

        // 有子菜单禁止删除
        //		boolean isPaent = Menu.dao.isParent(id);
        //		if (isPaent) {
        //			// 开发小常识：有子的父不能删！
        //			return error("如果爹没了，仔仔们会很伤心的！");
        //		}

        // 删除菜单按钮关联权限
        RoleBtn.dao.deleteByMenuCode(code);

        // 删除菜单关联按钮
        Button.dao.deleteByMenuCode(code);

        // 删除菜单关联对象,不能删除对象，因为对象可能被多个菜单用
        // MenuObject.dao.deleteByMenuCode(code);

        return null;
    }

    public String addSucceed(AopContext ac) throws Exception {
        // 菜单使缓存失效
        BaseCache.delSer(EovaConst.ALL_MENU);

        return null;
    }

    public String deleteSucceed(AopContext ac) throws Exception {
        // 菜单使缓存失效
        BaseCache.delSer(EovaConst.ALL_MENU);

        return null;
    }

    public String getData(AopContext ac) throws Exception {
        return null;
    }

    public String updateSucceed(AopContext ac) throws Exception {
        // 菜单使缓存失效
        BaseCache.delSer(EovaConst.ALL_MENU);

        return null;
    }

}

