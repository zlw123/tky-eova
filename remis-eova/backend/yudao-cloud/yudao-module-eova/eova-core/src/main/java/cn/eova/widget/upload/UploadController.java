/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.widget.upload;

import java.util.Collections;
import java.util.List;

import cn.eova.aop.UploadIntercept;
import cn.eova.common.Ds;
import cn.eova.common.base.BaseController;
import cn.eova.common.utils.io.FileUtil;
import cn.eova.common.utils.xx;
import cn.eova.config.EovaConfig;
import cn.eova.tools.x;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.eova.compat.jfinal.kit.LegacyRet;
import cn.eova.db.EovaGateways;
import cn.eova.db.EovaRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>ported from: cn.eova.widget.upload.UploadController
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>上传组件控制器（111 行）：img/file/temp/editor/query 五个 action</li>
 *   <li>【已声明适配 1】com.jfinal.kit.Ret -> cn.eova.compat.jfinal.kit.LegacyRet</li>
 *   <li>【已声明适配 2】com.jfinal.plugin.activerecord.Record -> cn.eova.db.EovaRecord；Db.use(Ds.EOVA).find(sql, paras) -> EovaGateways.get(Ds.EOVA).find(sql, paras)</li>
 *   <li>【既有缺陷，原样保留 1】editor() 在 domain_img 未配置时抛 RuntimeException，消息含配置项提示串（domain.config domain_img=图片服务域名）——逐字保留</li>
 *   <li>【既有缺陷，原样保留 2】temp() 的目录 /temp 硬编码；被注释掉的原写法（读 file.dir.temp）不得恢复</li>
 *   <li>query() 用 Collections.nCopies 生成 ? 占位符串，SQL 用【反引号】包 `eova_file`——逐字保留</li>
 *   <li>img()/file() 的目录取自 x.conf.get("file.dir." + code, "/")：code 取 get(0)（urlPara 首段），属对外契约（防目录注入）</li>
 * </ol>
 */
/**
 * 上传组件
 *
 * @author Jieven
 *
 */
public class UploadController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    // 异步传图
    public void img() {
        // 上传使用编码+预配置的方式获取目录(防止用户构造恶意目录)
        String code = get(0);
        String dir = x.conf.get(String.format("file.dir.%s", code), "/");// 为降低新人理解成本默认为更目录
        LegacyRet r = UploadUtil.upload(this, "img", get("name"), dir);
        renderJson(r);
    }

    // 异步传文件
    public void file() {
        // 上传使用编码+预配置的方式获取目录(防止用户构造恶意目录)
        String code = get(0);
        String dir = x.conf.get(String.format("file.dir.%s", code), "/");// 为降低新人理解成本默认为更目录
        LegacyRet rt = UploadUtil.upload(this, "file", get("name"), dir);
        renderJson(rt);
    }

    // 异步传临时文件
    public void temp() {
//        String dir = x.conf.get(String.format("file.dir.%s", code), "/file");
        LegacyRet rt = UploadUtil.upload(this, "file", get("name"), "/temp");
        renderJson(rt);
    }

    // 编辑器上传图片(wangEditor)
    public void editor() {
        // 开始上传
        LegacyRet rt = UploadUtil.upload(this, "img", null, "/editor");
        if (rt.isFail()) {
            renderText("error|" + rt.getStr("msg"));
            return;
        }
        // 获取最终上传目录
        String uploadDir = rt.getStr("uploadDir");
        // 获取图片服务域名
        String domain = x.conf.get("domain_img");
        if (x.isEmpty(domain)) {
            throw new RuntimeException("图片上传异常,请先配置图片服务域名!配置项:domain.config domain_img=图片服务域名");
        }

        uploadDir = FileUtil.formatWebPath(uploadDir);

        String url = String.format("%s/%s/%s", x.str.delEnd(domain, "/"), xx.delStartEnd(uploadDir, "/"), rt.getStr("fileName"));

        JSONObject o = new JSONObject();
        JSONArray urls = new JSONArray();
        urls.add(0, url);
        o.put("errno", 0);
        o.put("data", urls);

        renderJson(o);
    }

    /**
     * 文件上传记录
     * 用于根据文件名翻译
     */
    public void query() {
        JSONArray files = getJsonObj().getJSONArray("files");
        // 生成?占位符
        String marks = String.join(",", Collections.nCopies(files.size(), "?"));
        String sql = String.format("SELECT * FROM `eova_file` where code in (%s)", marks);
        List<EovaRecord> list = EovaGateways.get(Ds.EOVA).find(sql, files.toArray());

        // 上传文件拦截
        UploadIntercept intercept = EovaConfig.getUploadIntercept();
        if (intercept != null) {
            intercept.query(list);
        }

        LegacyRet ret = LegacyRet.ok().set("data", list);

        renderJson(ret);
    }
}