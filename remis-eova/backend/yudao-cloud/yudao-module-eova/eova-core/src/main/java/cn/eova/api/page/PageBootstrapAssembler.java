/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.api.page;

import java.util.List;

import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.model.Button;
import cn.eova.model.Menu;
import cn.eova.model.MetaObject;
import cn.eova.model.User;
import cn.eova.service.sm;

/**
 * <p>页面引导数据装配（第 129 轮）—— DES-004 §3.1 端点的**传输无关**那一半。
 *
 * <p><b>它是什么：</b>旧栈的渲染期插值（`AppController#index()` 的 `set("object"|"menu"|"menuCode")`
 * + `setAttr("btnList")`）在前后分离后必须由服务端按请求产出。本类把那段装配**原样抽出来**，
 * 不依赖任何 Web 容器 ⇒ Servlet（选项 C）、Spring MVC（选项 A）或 yudao 控制器（选项 B）
 * **都能直接复用**（HTTP 层落哪一层属待用户口径，见 master plan §124）。
 *
 * <p><b>逐条对应旧实现（`cn.eova.core.AppController#index()`，旧行号 70-96）：</b>
 * <pre>
 * String menuCode = get(0);                                   // ⇒ 入参 menuCode
 * Menu menu = Menu.dao.findByCode(menuCode);                  // ⇒ Menu#findByCode
 * String templdate = menu.getTemplate();                       // ⇒ payload.menu.template（r118 起是 SPA 分派键）
 * String objectCode = menu.getMenuConfig().getStr("object_code");// ⇒ ★ 元对象编码由菜单配置推导
 * MetaObject object = sm.meta.getMeta(objectCode);             // ⇒ sm.meta.getMeta
 * if (user == null) { renderMsg("请先登录"); return; }          // ⇒ state='no' + 同一句文案
 * List&lt;Button&gt; btnList = Button.dao.queryByMenuCode(menuCode, user.getRid()); // ⇒ 按角色查
 * set("object", object); set("menu", menu); set("menuCode", menuCode); setAttr("btnList", btnList);
 * </pre>
 *
 * <p><b>字段名口径（DES-004 §3.1）：</b>沿用旧 `setAttr` 的名字，少一层映射。★ `object` 的
 * 访问器名与 JSON 键名**并不一致**（取证 `model/MetaObject.java:87-107`）：
 * `pk_name ← getPk()`、`table ← getTable()`（底层列是 `table_name`）、`data_source ← getDs()`
 * —— 写错时前端只会拿到 undefined 而**静默失效**。
 *
 * <p><b>已声明边界（登记，不做静默降级）：</b>
 * <ol>
 *   <li>{@code btnList} **原样**放进载荷（旧栈就是 `setAttr("btnList", btnList)` 把 Model 列表交给
 *       渲染层）：`ui` 必须保持字符串（HTML 片段或 `.js` 路径），**不得结构化**；</li>
 *   <li>本类**只判"取不到"**（菜单/元对象缺失 ⇒ `state='no'` + 明确 msg）。
 *       <b>权限判定不在这里</b> —— 它属 {@code AuthInterceptor}/{@code AuthUri}（阶段 1 已 port
 *       但**没有 HTTP 入口、未接线**）⇒ DES-004 验收 6（无权 object 明确拒绝）仍待 HTTP 层口径。</li>
 * </ol>
 *
 * @author 迁移：第 129 轮
 */
public class PageBootstrapAssembler {

    /** 成功（与旧栈 `Ret.ok` 的 `state` 取值一致） */
    public static final String STATE_OK = "ok";
    /** 失败（与旧栈 `Ret.fail` 的 `state` 取值一致） */
    public static final String STATE_NO = "no";

    /** 未取到会话用户时的文案 —— ★ 逐字沿用旧 `AppController#index()` 的 `renderMsg("请先登录")` */
    public static final String MSG_NOT_LOGIN = "请先登录";

    /**
     * 装配菜单模版页的引导数据（**会查库**；HTTP 层落定后由控制器调用）。
     *
     * @param menuCode 菜单编码（旧栈是 URL 第 0 段）
     * @param user     当前会话用户（为 null ⇒ 返回未登录）
     * @param isQuery  是否查询模式（旧栈由各页面控制器 `set`；本类不推断，由调用方给出）
     * @return 载荷（`state`/`object`/`menu`/`btnList`/`loginUser`/`isQuery`）
     */
    public LegacyKv assemble(String menuCode, User user, boolean isQuery) {
        if (user == null) {
            return fail(MSG_NOT_LOGIN);
        }

        Menu menu = Menu.dao.findByCode(menuCode);
        if (menu == null) {
            return fail("菜单不存在: " + menuCode);
        }

        // ★ 元对象编码由**菜单配置**推导（旧 `menu.getMenuConfig().getStr("object_code")`）
        String objectCode = menu.getMenuConfig().getStr("object_code");
        MetaObject object = sm.meta.getMeta(objectCode);
        if (object == null) {
            return fail("元对象不存在: " + objectCode);
        }

        List<Button> btnList = Button.dao.queryByMenuCode(menuCode, user.getRid());
        return of(menu, menuCode, object, btnList, user, isQuery);
    }

    /**
     * 纯映射（**不查库**，判据用它；入参都是已取到的领域对象）。
     *
     * @param menu     菜单
     * @param menuCode 菜单编码（旧栈单独 `set("menuCode", ...)`，故保留为独立字段）
     * @param object   元对象
     * @param btnList  按角色查到的按钮（原样下发）
     * @param user     当前用户
     * @param isQuery  是否查询模式
     * @return 载荷
     */
    public static LegacyKv of(Menu menu, String menuCode, MetaObject object, List<Button> btnList, User user, boolean isQuery) {
        LegacyKv kv = new LegacyKv();
        kv.set("state", STATE_OK);
        kv.set("object", objectKv(object));
        kv.set("menu", menuKv(menu));
        kv.set("menuCode", menuCode);
        kv.set("btnList", btnList);
        kv.set("loginUser", loginUserKv(user));
        kv.set("isQuery", isQuery);
        return kv;
    }

    /**
     * `object` 的字段映射（★ 键名与访问器名不一致，见类注释的取证）。
     *
     * @param object 元对象
     * @return 只含 DES-004 §3.1 列出的 5 个键
     */
    public static LegacyKv objectKv(MetaObject object) {
        LegacyKv kv = new LegacyKv();
        kv.set("code", object.getCode());
        kv.set("name", object.getName());
        // pk_name ← getPk()（不是 getPkName）
        kv.set("pk_name", object.getPk());
        // table ← getTable()（底层列是 table_name）
        kv.set("table", object.getTable());
        kv.set("data_source", object.getDs());
        return kv;
    }

    /**
     * `menu` 的字段映射（`template` 是 r118 起 SPA 的**分派键**）。
     *
     * @param menu 菜单
     * @return 只含 code/name/template
     */
    public static LegacyKv menuKv(Menu menu) {
        LegacyKv kv = new LegacyKv();
        kv.set("code", menu.getStr("code"));
        kv.set("name", menu.getStr("name"));
        kv.set("template", menu.getTemplate());
        return kv;
    }

    /**
     * `loginUser` 的字段映射（权限面：`isAdmin` 决定 SPA 是否给超管入口）。
     *
     * @param user 当前用户
     * @return 只含 isAdmin/id/name
     */
    public static LegacyKv loginUserKv(User user) {
        LegacyKv kv = new LegacyKv();
        kv.set("isAdmin", user.isAdmin());
        kv.set("id", user.get("id"));
        kv.set("name", user.getName());
        return kv;
    }

    /**
     * 失败载荷（`state='no'` + 明确文案）。
     *
     * @param msg 文案
     * @return 载荷
     */
    public static LegacyKv fail(String msg) {
        LegacyKv kv = new LegacyKv();
        kv.set("state", STATE_NO);
        kv.set("msg", msg);
        return kv;
    }
}
