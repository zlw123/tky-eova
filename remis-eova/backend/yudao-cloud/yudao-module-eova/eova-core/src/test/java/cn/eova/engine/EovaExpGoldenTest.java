package cn.eova.engine;


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

    // 注意：buildItem 的两个用例【已迁移】到 eova-db-adapter 的
    // EovaExpBuildItemDbGoldenTest —— MetaField 由 compile-stub 变为真实 port 后，
    // MetaField.dao.getTemplate() 会真的查 eova_field（需要 CacheService + 数据源），
    // 而 §4 规定只有 eova-db-adapter 可以接触数据源。原用例在 stub 时期"通过"，
    // 但那是 stub 行为，不构成证据。

    @Test
    public void getParam_defaultFromEovaExpParam() {
        EovaExp exp = new EovaExp();
        Assertions.assertEquals("", exp.get(EovaExpParam.CNAME));
        Assertions.assertEquals("main", exp.get(EovaExpParam.DS));
        Assertions.assertEquals("fallback", exp.get("missing", "fallback"));
    }
}
