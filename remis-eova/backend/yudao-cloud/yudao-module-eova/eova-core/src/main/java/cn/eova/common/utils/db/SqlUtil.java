// compile-stub for LC-011 EovaExp; not a ported unit.
// real source: meta-eova/eova/core/src/main/java/cn/eova/common/utils/db/SqlUtil.java
package cn.eova.common.utils.db;

/**
 * 仅移植 EovaExp 调用的 {@code notNewLine} 方法体（与源文件同算法）。
 */
public final class SqlUtil {

    private SqlUtil() {
    }

    /**
     * SQL不换行格式化
     * @param str
     * @return
     */
    public static String notNewLine(String str) {
        str = str.replaceAll("\t|\r|\n", " ");
        str = str.replaceAll("  ", " ");
        return str;
    }
}
