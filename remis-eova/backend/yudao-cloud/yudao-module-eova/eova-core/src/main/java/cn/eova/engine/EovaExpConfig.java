package cn.eova.engine;

import cn.eova.compat.jfinal.kit.LegacyKv;

/**
 * <p>ported from: cn.eova.engine.EovaExpConfig
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>表达式配置容器；字段名与嵌套结构属契约</li>
 * </ol>
 */
/**
 * 表达式配置
 *
 * @author Jieven
 */
@Deprecated
public class EovaExpConfig {

    private String code;// 编码
    private String sql;// SQL
    private String ds;// 数据源
    private String cache;// 缓存
    private String valField;// 值字段名
    private String txtField;// 文本字段名
    private LegacyKv kv;

    public EovaExpConfig(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getDs() {
        return ds;
    }

    public void setDs(String ds) {
        this.ds = ds;
    }

    public String getCache() {
        return cache;
    }

    public void setCache(String cache) {
        this.cache = cache;
    }

    public String getValField() {
        return valField;
    }

    public void setValField(String valField) {
        this.valField = valField;
    }

    public String getTxtField() {
        return txtField;
    }

    public void setTxtField(String txtField) {
        this.txtField = txtField;
    }

    public LegacyKv getKv() {
        return kv;
    }

    public void setKv(LegacyKv kv) {
        this.kv = kv;
    }
}
