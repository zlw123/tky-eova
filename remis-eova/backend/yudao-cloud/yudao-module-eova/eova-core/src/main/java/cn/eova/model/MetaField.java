// compile-stub for LC-011 EovaExp; not a ported unit.
// real source: meta-eova/eova/core/src/main/java/cn/eova/model/MetaField.java
package cn.eova.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MetaField 最小 stub：去掉 JFinal Model/Db，仅保留 EovaExp.buildItem 用到的 put/remove/getTemplate。
 */
public class MetaField {

    public static final MetaField dao = new MetaField();

    private final Map<String, Object> attrs = new LinkedHashMap<>();

    public MetaField getTemplate() {
        return new MetaField();
    }

    public MetaField put(String key, Object value) {
        attrs.put(key, value);
        return this;
    }

    public MetaField remove(String key) {
        attrs.remove(key);
        return this;
    }

    public Object get(String key) {
        return attrs.get(key);
    }
}
