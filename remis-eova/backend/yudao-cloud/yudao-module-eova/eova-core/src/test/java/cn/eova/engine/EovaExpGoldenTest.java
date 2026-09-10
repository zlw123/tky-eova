package cn.eova.engine;

import java.util.HashMap;
import java.util.Map;

import cn.eova.model.EovaOption;
import cn.eova.model.MetaField;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * EovaExp golden 钩子。
 * TODO LC-013：对照旧 demo 录制表达式解析响应，补全 parse/select/from 金样。
 */
public class EovaExpGoldenTest {

    @Test
    public void isFormNull_matchesLegacyBranch() {
        Assertions.assertTrue(EovaExp.isFormNull(null));
        Assertions.assertTrue(EovaExp.isFormNull(""));
        Assertions.assertTrue(EovaExp.isFormNull("select id,name from null"));
        Assertions.assertTrue(EovaExp.isFormNull("SELECT a FROM NULL"));
        Assertions.assertFalse(EovaExp.isFormNull("select id, name from users"));
    }

    @Test
    public void buildItem_imgSuffixAndDefaultWidth() {
        MetaField field = EovaExp.buildItem(2, "Avatar", "头像_IMG", true, null);
        Assertions.assertEquals("avatar", field.get("en"));
        Assertions.assertEquals("头像", field.get("cn"));
        Assertions.assertEquals(150, field.get("width"));
        Assertions.assertEquals(Boolean.TRUE, field.get("is_query"));
        Assertions.assertEquals("文本框", field.get("type"));
        Assertions.assertEquals(
                "function(value,row,index,field){if(value){return `<img src=`+ value +` />`}return value}",
                field.get("formatter"));
    }

    @Test
    public void buildItem_fieldWidthFromOptionMap() {
        // 注意：本用例原先调用 option.setFieldWidth(Map) —— 而【新旧 EovaOption 都没有这个方法】：
        // 它是 compile-stub 时期为了让 EovaExp 编译而虚构出来的 API，测试因此测的是不存在的东西。
        // 现改用真实生产路径：config 是一个 JSON 对象，其【值本身是 JSON 字符串】，
        // 由 getConfObj("field_width") 二次解析（见 EovaOption.getConf/getConfObj）。
        EovaOption option = new EovaOption();
        cn.eova.compat.jfinal.kit.LegacyKv widths = cn.eova.compat.jfinal.kit.LegacyKv.create();
        widths.set("name", 240);
        cn.eova.compat.jfinal.kit.LegacyKv conf = cn.eova.compat.jfinal.kit.LegacyKv.create();
        conf.set("field_width", widths.toJson());
        option.setConfig(conf);

        MetaField field = EovaExp.buildItem(1, "name", "name", false, option);
        Assertions.assertEquals(240, field.get("width"));
        Assertions.assertEquals(Boolean.FALSE, field.get("is_show"));
        Assertions.assertEquals(Boolean.FALSE, field.get("is_query"));
    }

    @Test
    public void getParam_defaultFromEovaExpParam() {
        EovaExp exp = new EovaExp();
        Assertions.assertEquals("", exp.get(EovaExpParam.CNAME));
        Assertions.assertEquals("main", exp.get(EovaExpParam.DS));
        Assertions.assertEquals("fallback", exp.get("missing", "fallback"));
    }
}
