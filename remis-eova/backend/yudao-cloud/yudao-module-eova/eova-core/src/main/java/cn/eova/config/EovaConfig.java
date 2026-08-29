// compile-stub for LC-011 EovaExp; not a ported unit.
// real source: meta-eova/eova/core/src/main/java/cn/eova/config/EovaConfig.java
package cn.eova.config;

import com.alibaba.druid.DbType;

/**
 * 仅暴露 EovaExp 所需的 EOVA_DBTYPE。完整 EovaConfig 为 D 类，禁止本 run 重写。
 */
public class EovaConfig {

    /** EOVA所在数据库的类型 **/
    public static DbType EOVA_DBTYPE = DbType.mysql;
}
