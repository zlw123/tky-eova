package cn.eova.core.object;

import cn.eova.tools.x;
import cn.eova.aop.AopContext;
import cn.eova.common.Ds;
import cn.eova.hook.EovaMetaHook;
import cn.eova.model.MetaField;
import cn.eova.service.sm;
import cn.eova.db.EovaGateways;

/**
 * <p>ported from: cn.eova.core.object.MetaFieldHook
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>元字段 Hook（51 行）：字段增删改后的元数据联动</li>
 *   <li>【已声明适配】Db.use -> EovaGateways.get</li>
 * </ol>
 */
public class MetaFieldHook implements EovaMetaHook {

    @Override
    public String invoke(Action action, AopContext ac) throws Exception {
        switch (action) {
            case EDIT_AFTER:
                return editAfter(ac);
            case DELETE_BEFORE:
                return deleteBefore(ac);
        }
        return null;
    }

    private String editAfter(AopContext ac) throws Exception {
        Integer pk = ac.record.getInt("pk");
        String val = ac.record.getStr("value");
        MetaField mf = MetaField.dao.findById(pk);
        String object_code = mf.getStr("object_code");
        String en = mf.getStr("en");
        String cn = mf.getStr("cn");

        // 字段中文名变化时更新字段个性化中文名
        if (!x.isEmpty(val) && cn.equals(val)) {
            EovaGateways.get(Ds.EOVA).update("update eova_field_diy set cn = ? where object_code = ? and en = ?", val, object_code, en);
        }

        return null;
    }

    public String deleteBefore(AopContext ac) throws Exception {
        String code = ac.record.getStr("object_code");
        String en = ac.record.getStr("en");

        // 删除对象关联元字段属性
        sm.meta.deleteMetaField(code, en);

        return null;
    }

}

