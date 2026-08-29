package cn.eova.engine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * EovaExpParam golden 钩子。
 * TODO LC-013：对照旧 demo 录制表达式系统参数解析响应，补全 URI/CNAME 金样。
 */
public class EovaExpParamGoldenTest {

    @Test
    public void constants_matchLegacyValDefTxt() {
        Assertions.assertEquals("uri", EovaExpParam.URI.getVal());
        Assertions.assertEquals("", EovaExpParam.URI.getDef());
        Assertions.assertEquals("自定义数据查询", EovaExpParam.URI.getTxt());

        Assertions.assertEquals("cname", EovaExpParam.CNAME.getVal());
        Assertions.assertEquals("", EovaExpParam.CNAME.getDef());
        Assertions.assertEquals("文本字段名", EovaExpParam.CNAME.getTxt());

        Assertions.assertEquals("root", EovaExpParam.ROOT.getVal());
        Assertions.assertEquals("0", EovaExpParam.ROOT.getDef());
        Assertions.assertEquals("下拉树根节点的值", EovaExpParam.ROOT.getTxt());

        Assertions.assertEquals("cache", EovaExpParam.CACHE.getVal());
        Assertions.assertEquals("", EovaExpParam.CACHE.getDef());
        Assertions.assertEquals("缓存KEY", EovaExpParam.CACHE.getTxt());

        Assertions.assertEquals("ds", EovaExpParam.DS.getVal());
        Assertions.assertEquals("main", EovaExpParam.DS.getDef());
        Assertions.assertEquals("数据源KEY", EovaExpParam.DS.getTxt());
    }

    @Test
    public void setDef_mutatesDefaultLikeLegacyEnum() {
        String original = EovaExpParam.ROOT.getDef();
        try {
            EovaExpParam.ROOT.setDef("custom-root");
            Assertions.assertEquals("custom-root", EovaExpParam.ROOT.getDef());
        } finally {
            EovaExpParam.ROOT.setDef(original);
        }
        Assertions.assertEquals("0", EovaExpParam.ROOT.getDef());
    }

    @Test
    public void eovaExpGet_usesValAndDef() {
        EovaExp exp = new EovaExp();
        Assertions.assertEquals("", exp.get(EovaExpParam.URI));
        Assertions.assertEquals("0", exp.get(EovaExpParam.ROOT));
        Assertions.assertEquals("main", exp.get(EovaExpParam.DS));
        Assertions.assertEquals("", exp.get(EovaExpParam.CNAME));
        Assertions.assertEquals("", exp.get(EovaExpParam.CACHE));
    }
}
