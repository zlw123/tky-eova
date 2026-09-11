// compile-stub for LC-011 EovaExp; not a ported unit.
// real source: meta-eova/eova/core/src/main/java/cn/eova/config/EovaConfig.java
package cn.eova.config;

import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import cn.eova.aop.MetaObjectIntercept;
import cn.eova.aop.UploadIntercept;
import cn.eova.aop.UserSessionIntercept;
import cn.eova.sql.dql.dialect.QueryDialect;
import cn.eova.aop.eova.EovaIntercept;
import cn.eova.core.type.Convertor;
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
 *   <tr><td>{@link #getUploadIntercept()}</td><td>125/599/603</td><td>{@code cn.eova.widget.upload.UploadUtil}（第 77 轮）</td></tr>
 *   <tr><td>{@link #getAuthUris()}</td><td>113/614</td><td>{@code cn.eova.auth.AuthInterceptor}（第 78 轮）</td></tr>
 * </table>
 */
public class EovaConfig {

    /** EOVA所在数据库的类型 **/
    // 旧源码 EovaConfig.java:97 —— 逐字一致
    public static DbType EOVA_DBTYPE = DbType.mysql;

    /** EOVA 首页地址（AuthUri 拼接鉴权 URI 时读取） */
    // 旧源码 EovaConfig.java:90 —— 逐字一致
    public static String EOVA_INDEX = "/";

    /** URI 授权集合<角色ID, URI>（AuthInterceptor 鉴权时读取/追加；由 AuthUri.build 填充） */
    // 旧源码 EovaConfig.java:113 —— 逐字一致
    protected static Map<Integer, Set<String>> authUris = new HashMap<Integer, Set<String>>();

    /** 上传拦截器（UploadUtil/UploadController 读取；由宿主装配注入） */
    // 旧源码 EovaConfig.java:125 —— 逐字一致
    private static UploadIntercept uploadIntercept = null;

    /** Mod 包的类加载器（ClassUtil 扫描 jar 内类名时读取；由宿主装配注入） */
    // 旧源码 EovaConfig.java:94 —— 逐字一致
    public static URLClassLoader modLoader = null;

    // ------------------------------------------------------------------
    // 以下三项是第 67 轮为 port cn.eova.model.MetaObject 而【按旧源码逐字补入】的：
    // MetaObject 的数据转换走 EovaConfig.getConvertor(ds)，若不补则 MetaObject 无法 port。
    // 它们与旧源码 EovaConfig.java:101/628-634 逐字对应（同一字段、同一实现），
    // 属【本 stub 内的真实子集】—— 本类整体仍未 port（640 行），故不计入进度。
    //
    // ⚠️ 已知宿主装配缺口：旧栈由 EovaDataSource 的业务方言初始化路径
    // （EovaConfig.addConvertor(ds, convertor)）在启动时填充本表；新栈的
    // EovaDataSource 是语义重实现，该注册路径【尚未 port】⇒ 新栈启动后本表为空。
    // 该缺口已记入 DES-002-R4 的风险项，待业务方言族落地时消解。
    // ------------------------------------------------------------------

    /** DB类型转换器（旧源码 EovaConfig.java:101 —— 逐字一致） */
    private static HashMap<String, Convertor> convertorMap = new HashMap<>();

    /**
     * 取数据源对应的类型转换器（旧 EovaConfig.java:628 —— 逐字一致）。
     *
     * @param ds 数据源名
     * @return 转换器；未注册时返回 null（旧实现如此）
     */
    public static Convertor getConvertor(String ds) {
        return convertorMap.get(ds);
    }

    // ------------------------------------------------------------------
    // 以下四项是第 72 轮为 port WidgetManager / ImportBiz 而【按旧源码逐字补入】的
    // （旧源码 EovaConfig.java:103/119/575-580/620-625），属本 stub 内的真实子集。
    // ------------------------------------------------------------------

    /** DQL 方言（旧源码 EovaConfig.java:103 —— 逐字一致） */
    private static HashMap<String, QueryDialect> queryDialectMap = new HashMap<>();

    /** 全局 EOVA 拦截器（旧源码 EovaConfig.java:119 —— 逐字一致） */
    private static EovaIntercept eovaIntercept = null;

    /**
     * 取全局 EOVA 拦截器（旧 EovaConfig.java:575 —— 逐字一致）。
     *
     * @return 拦截器；未设置时为 null
     */
    public static EovaIntercept getEovaIntercept() {
        return eovaIntercept;
    }

    /**
     * 设置全局 EOVA 拦截器（旧 EovaConfig.java:579 —— 逐字一致）。
     *
     * @param eovaIntercept 拦截器
     */
    public static void setEovaIntercept(EovaIntercept eovaIntercept) {
        EovaConfig.eovaIntercept = eovaIntercept;
    }

    /**
     * 取数据源对应的 DQL 方言（旧 EovaConfig.java:620 —— 逐字一致）。
     *
     * @param ds 数据源名
     * @return 方言；未注册时返回 null
     */
    public static QueryDialect getQueryDialect(String ds) {
        return queryDialectMap.get(ds);
    }

    /**
     * 注册数据源的 DQL 方言（旧 EovaConfig.java:624 —— 逐字一致）。
     *
     * @param ds 数据源名
     * @param qd 方言
     * @return 被替换的旧值
     */
    public static QueryDialect addQueryDialect(String ds, QueryDialect qd) {
        return queryDialectMap.put(ds, qd);
    }

    /** 会话拦截器（旧源码 EovaConfig.java:123 —— 逐字一致） */
    private static UserSessionIntercept userSessionIntercept = null;

    /**
     * 取会话拦截器（旧 EovaConfig.java:591 —— 逐字一致）。
     *
     * @return 拦截器；未设置时为 null
     */
    public static UserSessionIntercept getUserSessionIntercept() {
        return userSessionIntercept;
    }

    /**
     * 设置会话拦截器（旧 EovaConfig.java:595 —— 逐字一致）。
     *
     * @param userSessionIntercept 拦截器
     */
    public static void setUserSessionIntercept(UserSessionIntercept userSessionIntercept) {
        EovaConfig.userSessionIntercept = userSessionIntercept;
    }

    /** 默认的元对象业务拦截器（旧源码 EovaConfig.java:121 —— 逐字一致） */
    private static MetaObjectIntercept defaultMetaObjectIntercept = null;

    /**
     * 取默认元对象拦截器（旧 EovaConfig.java:583 —— 逐字一致）。
     *
     * <p>第 67 轮为 port {@code TemplateUtil} 而按旧源码逐字补入（同一 stub 的真实子集）。</p>
     *
     * @return 默认拦截器；未设置时为 null
     */
    public static MetaObjectIntercept getDefaultMetaObjectIntercept() {
        return defaultMetaObjectIntercept;
    }

    /**
     * 设置默认元对象拦截器（旧 EovaConfig.java:587 —— 逐字一致）。
     *
     * @param defaultMetaObjectIntercept 默认拦截器
     */
    public static void setDefaultMetaObjectIntercept(MetaObjectIntercept defaultMetaObjectIntercept) {
        EovaConfig.defaultMetaObjectIntercept = defaultMetaObjectIntercept;
    }

    /**
     * 注册数据源的类型转换器（旧 EovaConfig.java:632 —— 逐字一致）。
     *
     * @param ds 数据源名
     * @param cv 转换器
     * @return 被替换的旧值（HashMap.put 语义）
     */
    public static Convertor addConvertor(String ds, Convertor cv) {
        return convertorMap.put(ds, cv);
    }

    /**
     * 取上传拦截器（旧 EovaConfig.java:599 —— 逐字一致）。
     *
     * <p>第 77 轮为 port {@code UploadUtil}/{@code UploadController} 而按旧源码逐字补入
     * （同一 stub 的真实子集）。</p>
     *
     * @return 上传拦截器；未设置时为 null
     */
    public static UploadIntercept getUploadIntercept() {
        return uploadIntercept;
    }

    /**
     * 设置上传拦截器（旧 EovaConfig.java:603 —— 逐字一致）。
     *
     * @param uploadIntercept 上传拦截器
     */
    public static void setUploadIntercept(UploadIntercept uploadIntercept) {
        EovaConfig.uploadIntercept = uploadIntercept;
    }

    /**
     * 取 URI 授权集合（旧 EovaConfig.java:614 —— 逐字一致）。
     *
     * <p>第 78 轮为 port {@code AuthInterceptor} 而按旧源码逐字补入（同一 stub 的真实子集）。
     * <b>返回的是内部可变 Map</b>（旧实现如此）：AuthInterceptor 会把角色自定义授权
     * {@code addAll} 进取出的 Set —— 即"鉴权会改写到这份集合"，属既有语义，不得改成只读视图。</p>
     *
     * @return 角色ID → URI 模式集合
     */
    public static Map<Integer, Set<String>> getAuthUris() {
        return authUris;
    }
}
