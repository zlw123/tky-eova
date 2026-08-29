// compile-stub for LC-011 EovaExp; not a ported unit.
// real source: meta-eova/eova/core/src/main/java/cn/eova/engine/EovaExpParam.java
package cn.eova.engine;

/**
 * Eova表达式系统参数（本文件仅为 EovaExp 编译所需 stub，完整 port 待后续单元）
 */
public enum EovaExpParam {
    URI("uri", "", "自定义数据查询"),
    CNAME("cname", "", "文本字段名"),
    ROOT("root", "0", "下拉树根节点的值"),
    CACHE("cache", "", "缓存KEY"),
    DS("ds", "main", "数据源KEY");

    private String val;
    private String def;
    private String txt;

    EovaExpParam(String val, String def, String txt) {
        this.val = val;
        this.def = def;
        this.txt = txt;
    }

    public String getVal() {
        return val;
    }

    public String getTxt() {
        return txt;
    }

    public String getDef() {
        return def;
    }

    public void setDef(String def) {
        this.def = def;
    }
}
