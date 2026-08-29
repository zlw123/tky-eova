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
        EovaOption option = new EovaOption();
        Map<String, Object> width = new HashMap<>();
        width.put("name", 240);
        option.setFieldWidth(width);

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
