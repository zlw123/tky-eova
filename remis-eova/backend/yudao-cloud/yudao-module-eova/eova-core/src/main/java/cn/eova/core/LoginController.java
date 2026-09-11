/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core;

import cn.eova.tools.x;
import com.alibaba.fastjson.JSONObject;
import cn.eova.common.Ds;
import cn.eova.common.base.BaseController;
import cn.eova.common.utils.string.Base64;
import cn.eova.common.utils.string.RSAEncrypt;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.core.LoginController
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>登录控制器（70 行）</li>
 * </ol>
 */
/**
 * 首页相关业务
 * @author Jieven
 *
 */
public class LoginController extends BaseController {

    // 回调登录地址 /login/callback?token=14de2c737aa02037132d

    // 授权登录
    public void callback() {

        String publicKey = "公钥";
        String token = "JSON登录密文";
//		{
//			rid : 1,
//			role_name : "角色名称",
//			company_id : 1,
//			org_id : 1,
//			nickname : "昵称",
//		}

        try {
            // 获取第三方返回的授权内容
            byte[] res = RSAEncrypt.decrypt(RSAEncrypt.loadPublicKeyByStr(publicKey), Base64.decode(token));
            String s = new String(res, "UTF-8");

            JSONObject o = JSONObject.parseObject(s);

            int cid = o.getIntValue("cid");// 企业ID
            int rid = o.getIntValue("rid");// 角色ID
            int uid = o.getIntValue("uid");// 用户ID
            String name = o.getString("name");// 昵称
            String role_name = o.getString("role_name");// 角色


            String userDs = x.conf.get("login.user.ds", Ds.EOVA);
            String userTable = x.conf.get("login.user.table", "eova_user");

            EovaRecord user = EovaGateways.get(userDs).findById(userTable, uid);

            user.set("cid", cid);
            user.set("rid", rid);
            user.set("uid", uid);
            user.set("name", name);
            user.set("role_name", role_name);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}