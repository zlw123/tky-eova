/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.api.page;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.model.Button;
import cn.eova.model.Menu;
import cn.eova.model.MetaObject;
import cn.eova.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 页面引导数据装配的判据（第 129 轮）—— 钉 DES-004 §3.1 的**字段名口径**与边界。
 *
 * <p>为什么必须单独钉字段名：§3.1 写的"字段名沿用旧 `setAttr` 的名字，少一层映射"意味着
 * 装配里全是**手写键名**（`pk_name`/`table`/`data_source`/`btnList`/`isQuery`…）。
 * 写错时前端只会拿到 `undefined` 并**静默失效**（拼 URL 得空值、按钮区渲染为空），
 * 而后端"编译通过、测试全绿"—— 与前端 r127 判据防的是同一类错。
 *
 * <p>本判据**不依赖数据库**：`EovaModel` 的 `set/get` 内存语义已在既有 golden 测试中确认
 * （"set/put 语义一致"），故直接构造领域对象。
 */
class PageBootstrapAssemblerGoldenTest {

    /**
     * 造元对象（键名刻意用**列名**，好让"键名≠列名"的映射被判别）。
     *
     * @return 元对象
     */
    private static MetaObject metaObject() {
        MetaObject o = new MetaObject();
        Map<String, Object> attrs = new HashMap<>();
        // ★ 第 299 轮补：`id` 是**旧载荷本来就有**的键（旧模板 `_page/form.html` 写的是
        //   `object_id: '#(object.id)'`），ported 侧当初漏了 ⇒ 见 DES-004-R2 §1.2
        attrs.put("id", 42);
        attrs.put("code", "meta_hotel");
        attrs.put("name", "酒店");
        attrs.put("pk_name", "hotel_id");
        attrs.put("table_name", "meta_hotel");
        attrs.put("data_source", "eova");
        o._setAttrs(attrs);
        return o;
    }

    /**
     * 造菜单。
     *
     * @return 菜单
     */
    private static Menu menu() {
        Menu m = new Menu();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("code", "meta_hotel");
        attrs.put("name", "酒店管理");
        attrs.put("template", "table");
        // config 列（原文 JSON 串）：tree/tree_table 页面族的 object_code/tree_object_code 来源
        attrs.put("config", "{\"object_code\":\"goods_style\",\"tree_object_code\":\"demo_cat\"}");
        m._setAttrs(attrs);
        return m;
    }

    /**
     * 造用户。
     *
     * <p>超管判定走 `rid`：`User#isAdmin()` 的实现是
     * `get("rid").toString().equals(EovaConst.ADMIN_RID + "")`（`EovaConst.ADMIN_RID = 1`），
     * 故这里用 `rid` 造两态（不是 `setRole`）。
     *
     * @param id   用户 id
     * @param name 用户名
     * @param rid  角色 id（1 = 超管）
     * @return 用户
     */
    private static User user(int id, String name, int rid) {
        User u = new User();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", id);
        attrs.put("name", name);
        attrs.put("rid", rid);
        u._setAttrs(attrs);
        return u;
    }

    /**
     * 造按钮（`ui` 用字符串：一类是 HTML 片段、一类是 `.js` 路径）。
     *
     * @param name 按钮名
     * @param ui   `ui` 字符串
     * @return 按钮
     */
    private static Button button(String name, String ui) {
        Button b = new Button();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("name", name);
        attrs.put("ui", ui);
        attrs.put("is_base", 1);
        b._setAttrs(attrs);
        return b;
    }

    @Test
    @DisplayName("① 载荷键集合与 DES-004 §3.1 一致（state/object/menu/menuCode/btnList/loginUser/isQuery）")
    void payloadKeys() {
        LegacyKv kv = PageBootstrapAssembler.of(menu(), "meta_hotel", metaObject(), new ArrayList<>(), user(1, "admin", 1), true);
        // ★ 断言**字面量**（旧栈 `Ret.ok` 的 state 取值），不拿常量自比 —— 否则改常量两边一起变、判据恒真
        //   （R74 已记；前端是按字面量 'ok' 判成功的：`p['state'] !== 'ok'` ⇒ 后端改常量就会断链而无人发现）。
        assertEquals("ok", kv.get("state"));
        assertEquals("ok", PageBootstrapAssembler.STATE_OK);
        assertEquals("meta_hotel", kv.get("menuCode"));
        assertTrue((Boolean) kv.get("isQuery"));
        for (String key : new String[] {"state", "object", "menu", "menuCode", "btnList", "loginUser", "isQuery"}) {
            assertTrue(kv.containsKey(key), "载荷缺字段: " + key);
        }
        assertEquals(7, kv.size(), "载荷字段数应为 7（不得夹带别的键）");
    }

    @Test
    @DisplayName("② object 的键名与访问器映射：pk_name←getPk()、table←getTable()（底层列 table_name）、data_source←getDs()")
    void objectKeyMapping() {
        LegacyKv obj = PageBootstrapAssembler.objectKv(metaObject());
        assertEquals("meta_hotel", obj.get("code"));
        assertEquals("酒店", obj.get("name"));
        // ★ 访问器名与键名不一致的三处
        assertEquals("hotel_id", obj.get("pk_name"));
        assertEquals("meta_hotel", obj.get("table"));
        assertEquals("eova", obj.get("data_source"));
        // ★ 第 299 轮（DES-004-R2 §1.2）：`id` 必须下发 —— 旧栈 `#(object.id)` 渲染期插值用它，
        //   新栈前端三处（TemplateTable:510 / TemplateTree:423 / TemplateTreeTable:454）读
        //   `object['id']` 写 `uzoo.page.object_id`；缺它时冻结脚本会拼出 `?id=undefined`。
        assertEquals(42, obj.get("id"), "id 必须原样映射（object.get(\"id\")）");
        // 反面：不得出现驼峰笔误
        assertNull(obj.get("pkName"));
        assertFalse(obj.containsKey("table_name"), "载荷键应是 table（不是列名 table_name）");
        // 键集**精确**：§3.1 的 5 键 + 第 299 轮补的 id（多一个都不行 —— 反空断言之外的另一道闸）
        assertEquals(6, obj.size(), "object = §3.1 的 5 键 + id（DES-004-R2 §1.2），不得夹带别的键");
        assertEquals(
                java.util.Set.of("id", "code", "name", "pk_name", "table", "data_source"),
                new java.util.HashSet<>(obj.keySet()),
                "object 的键集合必须精确");
    }

    @Test
    @DisplayName("③ menu 含 code/name/template/conf（template 是 SPA 的分派键；conf 是 tree 族的元对象来源）")
    void menuKeyMapping() {
        LegacyKv m = PageBootstrapAssembler.menuKv(menu());
        assertEquals("meta_hotel", m.get("code"));
        assertEquals("酒店管理", m.get("name"));
        assertEquals("table", m.get("template"));
        // ★ r303：`conf` 是 `config` 列**原文**（旧 `#(menu.conf)`），tree/tree_table 页面靠它拿
        //   object_code/tree_object_code —— 漏了它这两个页面族的请求会带着 `{{object}}` 占位符发出去（实测 500）
        assertEquals("{\"object_code\":\"goods_style\",\"tree_object_code\":\"demo_cat\"}", m.get("conf"),
                "conf 必须是 config 列原文（前端 menuConfOf 会解析它）");
        // 键集合精确：少一个（如 conf）会让 tree 族整页失效，多一个则说明映射超出旧页所需
        assertEquals(
                java.util.Set.of("code", "name", "template", "conf"),
                new java.util.HashSet<>(m.keySet()),
                "menu 的键集合必须精确");
    }

    @Test
    @DisplayName("④ loginUser 两态：isAdmin 真/假必须不同（DES-004 验收 4）")
    void loginUserTwoStates() {
        LegacyKv admin = PageBootstrapAssembler.loginUserKv(user(1, "admin", 1));
        LegacyKv normal = PageBootstrapAssembler.loginUserKv(user(2, "zhang", 9));
        assertEquals(Boolean.TRUE, admin.get("isAdmin"));
        assertEquals(Boolean.FALSE, normal.get("isAdmin"));
        assertEquals(1, admin.get("id"));
        assertEquals("zhang", normal.get("name"));
        assertFalse(admin.get("isAdmin").equals(normal.get("isAdmin")));
    }

    @Test
    @DisplayName("⑤ btnList 原样下发：`.js` 与 HTML 片段两类 ui 都保持字符串（DES-004 验收 3）")
    void btnListVerbatim() {
        List<Button> btns = new ArrayList<>();
        btns.add(button("导出脚本", "/demo/test/btn.js"));
        btns.add(button("内置查询", "<button class=\"\" onclick=\"handlerButtonEvent('test')\">x</button>"));
        LegacyKv kv = PageBootstrapAssembler.of(menu(), "meta_hotel", metaObject(), btns, user(1, "admin", 1), false);

        @SuppressWarnings("unchecked")
        List<Button> out = (List<Button>) kv.get("btnList");
        // ★ 原样：同一个列表实例（旧栈 `setAttr("btnList", btnList)`）
        assertSame(btns, out);
        assertEquals("/demo/test/btn.js", out.get(0).getStr("ui"));
        assertTrue(out.get(1).getStr("ui").startsWith("<button"));
        // 不得被结构化
        assertTrue(out.get(0).getStr("ui") instanceof String);
    }

    @Test
    @DisplayName("⑥ 取不到时给 state='no' + 明确文案；未登录文案逐字沿用旧 renderMsg（不做静默降级）")
    void failures() {
        LegacyKv notLogin = PageBootstrapAssembler.fail(PageBootstrapAssembler.MSG_NOT_LOGIN);
        // 同上：断言字面量 'no'（旧栈 Ret.fail 的取值；前端按 `state !== 'ok'` 走失败分支）
        assertEquals("no", notLogin.get("state"));
        assertEquals("no", PageBootstrapAssembler.STATE_NO);
        assertEquals("请先登录", notLogin.get("msg"), "未登录文案必须逐字沿用旧 AppController#index() 的 renderMsg");

        LegacyKv missing = PageBootstrapAssembler.fail("元对象不存在: x");
        assertEquals("no", missing.get("state"));
        assertTrue(String.valueOf(missing.get("msg")).contains("元对象不存在"));
        // 失败载荷不得带 object/btnList（否则前端会当成"部分引导数据"用）
        assertFalse(notLogin.containsKey("object"));
        assertFalse(notLogin.containsKey("btnList"));
    }
}
