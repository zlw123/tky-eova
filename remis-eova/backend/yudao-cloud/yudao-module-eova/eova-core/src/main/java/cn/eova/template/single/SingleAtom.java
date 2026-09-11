/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.template.single;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;

import cn.eova.tools.x;
import cn.eova.aop.AopContext;
import cn.eova.model.MetaObject;
import cn.eova.compat.jfinal.core.LegacyController;
import cn.eova.db.EovaGateways;
import cn.eova.compat.jfinal.plugin.activerecord.LegacyIAtom;
import cn.eova.db.EovaRecord;

/**
 * <p>ported from: cn.eova.template.single.SingleAtom
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>单表事务体（134 行）</li>
 * </ol>
 */
public class SingleAtom implements LegacyIAtom {

    private final LegacyController ctrl;
    private File file;
    private MetaObject object;
    private SingleIntercept intercept;
    private List<EovaRecord> records;
    private Exception runExp;

    public SingleAtom(File file, MetaObject object, SingleIntercept intercept, LegacyController ctrl) {
        super();
        this.file = file;
        this.object = object;
        this.intercept = intercept;
        this.ctrl = ctrl;
    }

    @Override
    public boolean run() throws SQLException {
        InputStream is = null;
        try {
            // 导入Excel
            is = new FileInputStream(file);
            records = null;// 已废弃。 ExcelJxlUtil.importExcel(is, object.getFields());

            if (x.isEmpty(records)) {
                throw new Exception("无法从导入的Excel获取到任何数据，请认真检查Excel模版！");
            }

            // 导入前置任务
            if (intercept != null) {
                AopContext ac = new AopContext(ctrl, records);
                ac.object = object;
                ac.fields = object.getFields();
                intercept.importBefore(ac);
            }

            // 保存导入数据
            for (EovaRecord re : records) {
                // TODO 拦截器 控制字段是否持久化，不需要的 比如 自增的 foreach remove 即可
                // 有主键是更新，没主键是新增
                Object pk = re.get(object.getPk());
                if (pk == null) {
                    EovaGateways.get(object.getDs()).save(object.getTable(), object.getPk(), re);
                } else {
                    EovaGateways.get(object.getDs()).update(object.getTable(), object.getPk(), re);
                }
            }

            // 导入后置任务
            if (intercept != null) {
                AopContext ac = new AopContext(ctrl, records);
                ac.object = object;
                ac.fields = object.getFields();
                intercept.importAfter(ac);
            }
        } catch (Exception e) {
            runExp = e;
            return false;
        } finally {
            if (is != null) try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 自动删除废弃临时文件
            if (file.exists()) file.delete();
        }
        return true;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public MetaObject getObject() {
        return object;
    }

    public void setObject(MetaObject object) {
        this.object = object;
    }

    public SingleIntercept getIntercept() {
        return intercept;
    }

    public void setIntercept(SingleIntercept intercept) {
        this.intercept = intercept;
    }

    public List<EovaRecord> getRecords() {
        return records;
    }

    public void setRecords(List<EovaRecord> records) {
        this.records = records;
    }

    public Exception getRunExp() {
        return runExp;
    }

    public void setRunExp(Exception runExp) {
        this.runExp = runExp;
    }

}