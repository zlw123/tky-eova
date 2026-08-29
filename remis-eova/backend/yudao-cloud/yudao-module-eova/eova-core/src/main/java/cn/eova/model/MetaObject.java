// compile-stub for LC-011 EovaExp; not a ported unit.
// real source: meta-eova/eova/core/src/main/java/cn/eova/model/MetaObject.java
package cn.eova.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MetaObject 最小 stub：去掉 JFinal Model/Db，仅保留 EovaExp.getObject 用到的 dao/getTemplate/put。
 */
public class MetaObject {

    public static final MetaObject dao = new MetaObject();

    private final Map<String, Object> attrs = new LinkedHashMap<>();

    public MetaObject getTemplate() {
        return new MetaObject();
    }

    public MetaObject put(String key, Object value) {
        attrs.put(key, value);
        return this;
    }

    public Object get(String key) {
        return attrs.get(key);
    }
}
