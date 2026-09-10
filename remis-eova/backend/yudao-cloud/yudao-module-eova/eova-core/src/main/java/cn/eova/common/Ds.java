package cn.eova.common;

/**
 * <p>ported from: cn.eova.common.Ds
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>接口内仅两个数据源名常量 main/eova，属对外契约</li>
 * </ol>
 */
/**
 * 数据源名称
 *
 * @author Jieven
 */
public interface Ds {

    /**默认数据源名称**/
    public static final String MAIN = "main";
    /**EOVA数据源名称**/
    public static final String EOVA = "eova";
}
