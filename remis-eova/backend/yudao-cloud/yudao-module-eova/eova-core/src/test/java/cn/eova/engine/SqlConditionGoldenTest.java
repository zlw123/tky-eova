package cn.eova.engine;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * SqlCondition golden 钩子。
 * TODO LC-013：对照旧 demo 录制表达式条件拼装响应，补全 params 金样。
 */
public class SqlConditionGoldenTest {

    @Test
    public void ctor_listParams_keepsSqlAndSameList() {
        List<Object> params = Arrays.asList(1, "a");
        SqlCondition cond = new SqlCondition("id = ? and name = ?", params);
        Assertions.assertEquals("id = ? and name = ?", cond.getSql());
        Assertions.assertSame(params, cond.getParams());
        Assertions.assertEquals(2, cond.getParams().size());
        Assertions.assertEquals(1, cond.getParams().get(0));
        Assertions.assertEquals("a", cond.getParams().get(1));
    }

    @Test
    public void ctor_varargs_wrapsAsListLikeLegacy() {
        SqlCondition cond = new SqlCondition("status = ?", 9);
        Assertions.assertEquals("status = ?", cond.getSql());
        Assertions.assertEquals(Collections.singletonList(9), cond.getParams());
    }

    @Test
    public void ctor_sqlOnly_leavesParamsNull() {
        SqlCondition cond = new SqlCondition("1 = 1");
        Assertions.assertEquals("1 = 1", cond.getSql());
        Assertions.assertNull(cond.getParams());
    }

    @Test
    public void setters_mutateLikeLegacyPojo() {
        SqlCondition cond = new SqlCondition("old");
        cond.setSql("new");
        List<Object> params = Arrays.asList("x");
        cond.setParams(params);
        Assertions.assertEquals("new", cond.getSql());
        Assertions.assertSame(params, cond.getParams());
    }
}
