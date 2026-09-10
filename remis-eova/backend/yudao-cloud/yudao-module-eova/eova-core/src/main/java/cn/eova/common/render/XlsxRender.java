/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.common.render;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import cn.eova.common.utils.excel.ExceUtil;
import cn.eova.model.MetaField;
import cn.eova.compat.jfinal.kit.LegacyLogKit;
import cn.eova.db.EovaRecord;
import cn.eova.compat.render.LegacyRender;
import cn.eova.compat.render.LegacyRenderException;

/**
 * <p>ported from: cn.eova.common.render.XlsxRender
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>XLSX 下载渲染：Content-disposition 附件名 + ExceUtil.export</li>
 *   <li>【已声明适配】LogKit -> LegacyLogKit；Record -> EovaRecord；com.jfinal.render.{Render,RenderException} -> 接缝</li>
 * </ol>
 */
public class XlsxRender extends LegacyRender {

    private final List<MetaField> fields;
    private final List<EovaRecord> data;

    private final String fileName;

    public XlsxRender(List<MetaField> fields, List<EovaRecord> data, String fileName) {
        this.fields = fields;
        this.data = data;
        this.fileName = fileName + ".xlsx";
    }

    @Override
    public void render() {
        response.reset();
        try {
            response.setHeader("Content-disposition", "attachment; filename=" + URLEncoder.encode(fileName, getEncoding()));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        OutputStream os = null;
        try {
            os = response.getOutputStream();
            ExceUtil.export(fields, data, os);
        } catch (Exception e) {
            throw new LegacyRenderException(e);
        } finally {
            try {
                if (os != null) {
                    os.flush();
                    os.close();
                }
            } catch (IOException e) {
                LegacyLogKit.error(e.getMessage(), e);
            }

        }
    }

}