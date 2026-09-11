package cn.eova.api.sys;

import java.util.List;

import cn.eova.tools.x;
import cn.eova.common.Ds;
import cn.eova.core.api.BaseApi;
import cn.eova.model.Button;
import cn.eova.model.Role;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.db.EovaGateways;

/**
 * <p>ported from: cn.eova.api.sys.RoleApi
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>企业角色同步（140 行）：add/delete/update/sync/sync1/menus 六个 action</li>
 *   <li>【已声明适配 1】com.jfinal.kit.Kv -> cn.eova.compat.jfinal.kit.LegacyKv</li>
 *   <li>【已声明适配 2】com.jfinal.plugin.activerecord.Db -> cn.eova.db.EovaGateways</li>
 *   <li>      Db.use(Ds.EOVA).delete(sql, paras) -> EovaGateways.get(Ds.EOVA).delete(sql, paras)</li>
 *   <li>【既有缺陷，原样保留 1】update() 调的是 delete(...)：`Db.use(Ds.EOVA).delete("update eova_role set name = ? where id = ?", name, roleId)` —— 用 delete 入口执行 UPDATE SQL。jfinal 的 DbPro.delete 不做 SQL 类型校验，故旧行为是『真的执行了 UPDATE』；接缝的 delete(String, Object...) 同为直连 execute，行为等价。不得改成 update 入口</li>
 *   <li>【既有缺陷，原样保留 2】delete() action 改名冲突：action 名 delete 与 BaseApi 无冲突，但与『删除角色』语义无关的 lv 读取仍保留（读而不用）</li>
 *   <li>【既有缺陷，原样保留 3】sync() 里 role == null 分支走 addRole（新增），else 分支注释却写『不存在新增角色』（注释与代码相反）—— 注释原样保留</li>
 *   <li>Role.dao.isExist("select count(*) from eova_role where id = ?", roleId) 走 BaseModel.isExist，其内部 queryNumber(...).longValue() 先拆箱（第 52 行的既有口径），roleId 为 null 时参数为 null（SQL 里永不相等），属既有语义</li>
 *   <li>lv 默认值 900 取自 x.conf.getInt("eova.role.lv.default", 900)：配置键名属对外契约</li>
 *   <li>menus() 返回 List<String>（Button.dao.queryMenuCodeByRid），直接作为 OK(data) 的载荷</li>
 * </ol>
 */
/**
 * 企业用户角色同步
 *
 * @author Jieven
 */
public class RoleApi extends BaseApi {

    // 新增角色
    public void add() {
        LegacyKv kv = getKv();

        // 默认权限级别(同步范围限定, 防止和本系统角色冲突)
        int lv = x.conf.getInt("eova.role.lv.default", 900);

        Integer companyId = kv.getInt("company_id");
        Integer roleId = kv.getInt("role_id");
        String name = kv.getStr("name");

        boolean isExist = Role.dao.isExist("select count(*) from eova_role where id = ?", roleId);
        if (isExist) {
            NO("已存在该角色");
            return;
        }

        addRole(companyId, roleId, name, lv);

        OK();
    }

    // 删除角色
    public void delete() {
        LegacyKv kv = getKv();

        // 默认权限级别(同步范围限定, 防止和本系统角色冲突)
        int lv = x.conf.getInt("eova.role.lv.default", 900);

//        Integer companyId = kv.getInt("company_id");
//        String name = kv.getStr("name");

        Integer roleId = kv.getInt("role_id");

        // 删除角色
        EovaGateways.get(Ds.EOVA).delete("delete from eova_role where id = ?", roleId);

        OK();
    }

    // 更新角色
    public void update() {
        LegacyKv kv = getKv();

        // 默认权限级别(同步范围限定, 防止和本系统角色冲突)
        int lv = x.conf.getInt("eova.role.lv.default", 900);

        Integer companyId = kv.getInt("company_id");
        Integer roleId = kv.getInt("role_id");
//        String newname = kv.getStr("newname");
        String name = kv.getStr("name");

        // 更新角色
        EovaGateways.get(Ds.EOVA).delete("update eova_role set name = ? where id = ?", name, roleId);

        OK();
    }

    // 批量同步
    public void sync() {
        List<LegacyKv> kvs = getKvs();// base 批量传入角色

        int lv = x.conf.getInt("eova.role.lv.default", 900);

        for (LegacyKv kv : kvs) {
            Integer roleId = kv.getInt("role_id");
            Integer companyId = kv.getInt("company_id");
            String name = kv.getStr("name");

            Role role = Role.dao.findById(roleId);
            // 存在就更新名称
            if (role == null) {
                addRole(companyId, roleId, name, lv);
            }
            // 不存在新增角色
            else {
                role.set("name", name);
                role.update();
            }
        }

        OK();
    }

    /**
     * 1.对比base.role 和 erp.role 新增/更新/删除
     * 2.特殊企业不更新
     */
    public void sync1() {
        // base
    }

    /**
     * 添加角色
     * @param companyId
     * @param roleId
     * @param name
     * @param lv
     */
    private void addRole(Integer companyId, Integer roleId, String name, int lv) {
        Role role = new Role();
        role.set("id", roleId);
        role.set("company_id", companyId);
        role.set("name", name);
        role.set("lv", lv);
        role.save();
    }

    // 获取角色已授权菜单
    public void menus() {
        LegacyKv kv = getKv();

        Integer roleId = kv.getInt("role_id");

        // 获取已授权菜单ID
        List<String> menus = Button.dao.queryMenuCodeByRid(roleId);

        OK(menus);
    }

}
