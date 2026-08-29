// compile-stub for LC-011 EovaExp; not a ported unit.
// real source: cn.eova.tools.x（eova-web 依赖，本仓无源码）
package cn.eova.tools;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * eova-web {@code x} 工具最小 stub，覆盖 EovaExp 用到的 isEmpty / log.error / str.delEnd。
 */
public final class x {

    public static final XLog log = new XLog();
    public static final XStr str = new XStr();

    private x() {
    }

    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof String) {
            return ((String) obj).length() == 0;
        }
        if (obj instanceof Collection) {
            return ((Collection<?>) obj).isEmpty();
        }
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).isEmpty();
        }
        if (obj.getClass().isArray()) {
            return Array.getLength(obj) == 0;
        }
        return false;
    }

    public static final class XLog {
        public void error(String msg) {
            // stub：真实实现走 eova-web 日志门面；EovaExp 仅调用此方法
        }
    }

    public static final class XStr {
        public String delEnd(String str, String end) {
            if (str == null || end == null) {
                return str;
            }
            if (str.endsWith(end)) {
                return str.substring(0, str.length() - end.length());
            }
            return str;
        }
    }
}
