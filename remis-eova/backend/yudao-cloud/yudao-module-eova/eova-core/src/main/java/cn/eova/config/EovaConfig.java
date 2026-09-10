// compile-stub for LC-011 EovaExp; not a ported unit.
// real source: meta-eova/eova/core/src/main/java/cn/eova/config/EovaConfig.java
package cn.eova.config;

import java.net.URLClassLoader;

import com.alibaba.druid.DbType;

/**
 * <b>已声明的 compile-stub</b> —— 本类<b>不是</b> port 单元，完整 {@code EovaConfig}
 * （640 行、46 个依赖）为 D 类，因依赖 {@code JFinalConfig} 生命周期与一批
 * Controller / Interceptor 而尚不可 port。
 *
 * <p><b>本 stub 的口径（必须遵守，否则会退化成"未声明的部分 port"）：</b>
 * <ol>
 *   <li>只声明<b>已被真实 port 单元实际读取</b>的静态成员；</li>
 *   <li>每个成员的声明必须与旧源码<b>逐字一致</b>（含类型、初值、修饰符），
 *       并在注释里注明旧源码行号 —— 取值语义因此正确；</li>
 *   <li>依赖本 stub 的单元一律标记为 {@code blockedBy: cn.eova.config.EovaConfig}，
 *       <b>不得</b>在其上声明 verified；</li>
 *   <li>完整 port 落地时，本文件应被真实实现<b>整体取代</b>（而非逐字段合并）。</li>
 * </ol>
 *
 * <p><b>已声明成员与使用方：</b>
 * <table border="1">
 *   <tr><th>成员</th><th>旧源码行</th><th>读取方</th></tr>
 *   <tr><td>{@link #EOVA_DBTYPE}</td><td>97</td><td>{@code cn.eova.common.utils.xx}（4 处方言判断）</td></tr>
 *   <tr><td>{@link #EOVA_INDEX}</td><td>90</td><td>{@code cn.eova.auth.AuthUri}</td></tr>
 *   <tr><td>{@link #modLoader}</td><td>94</td><td>{@code cn.eova.common.utils.io.ClassUtil}</td></tr>
 * </table>
 */
public class EovaConfig {

    /** EOVA所在数据库的类型 **/
    // 旧源码 EovaConfig.java:97 —— 逐字一致
    public static DbType EOVA_DBTYPE = DbType.mysql;

    /** EOVA 首页地址（AuthUri 拼接鉴权 URI 时读取） */
    // 旧源码 EovaConfig.java:90 —— 逐字一致
    public static String EOVA_INDEX = "/";

    /** Mod 包的类加载器（ClassUtil 扫描 jar 内类名时读取；由宿主装配注入） */
    // 旧源码 EovaConfig.java:94 —— 逐字一致
    public static URLClassLoader modLoader = null;
}
