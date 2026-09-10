/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.engine;

import java.util.Map;

import cn.eova.compat.cache.CacheServices;
import cn.eova.compat.cache.EhCacheService;
import cn.eova.compat.jfinal.kit.LegacyKv;
import cn.eova.db.EovaGateways;
import cn.eova.db.JdbcEovaDbGateway;
import cn.eova.model.EovaOption;
import cn.eova.model.MetaField;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code EovaExp.buildItem} 的 <b>DB 支撑</b>行为判据。
 *
 * <p><b>为什么这些用例必须搬到 eova-db-adapter：</b>
 * 它们原先在 eova-core 的 {@code EovaExpGoldenTest} 里，靠的是当时 <b>MetaField compile-stub</b>
 * 的假实现 —— 那个 stub 的 {@code getTemplate()} 直接 {@code return new MetaField()}，
 * <b>不读库</b>。MetaField 真实 port 落地后，{@code buildItem} 的第一步
 * {@code MetaField.dao.getTemplate()} 会走
 * {@code queryFisrtByCache("select * from eova_field where id = 1")}
 * → 需要 <b>CacheService + 数据源</b>，于是原用例在 eova-core 里因
 * 「EovaModel 未注入 CacheService」而失败。
 * 这不是回归，而是<b>原先的"通过"本身不构成证据</b>（测的是 stub 行为）。
 *
 * <p><b>为什么落在 eova-db-adapter：</b>§4 规定本模块是唯一允许接触数据源与事务的边界；
 * eova-core 不得依赖 db-adapter（会成环），故 DB 用例只能放这里。</p>
 *
 * <p><b>数据来源：</b>S00 录制的 baseline MySQL（127.0.0.1:13306），
 * {@code eova_meta.eova_field} 共 405 行，其中 {@code id=1} 是"元字段模板"行
 * （{@code object_code='eova_meta_template'}，{@code en='meta'}，{@code width=130}）。</p>
 *
 * <p><b>断言的适用范围（避免把模板数据当成契约）：</b>下面断言的每一个键都由
 * {@code EovaExp.buildItem} <b>自己 put</b>（{@code en}/{@code cn}/{@code type}/
 * {@code is_query}/{@code width}/{@code num}/{@code formatter}），
 * 故不随模板行内容漂移；模板只贡献"未被覆盖的其余属性"。
 * 唯一例外是 {@link #templateRowIsActuallyRead()}，它<b>故意</b>断言模板行的存在性，
 * 用来证明 DB 路径真的被走到了（否则本测试会退化成"离线也算过"）。</p>
 *
 * <p>acceptanceProfile: golden-eovaexp-db</p>
 */
class EovaExpBuildItemDbGoldenTest {

    private static final String URL = "jdbc:mysql://127.0.0.1:13306/eova_meta"
            + "?useUnicode=true&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull"
            + "&useSSL=false&serverTimezone=Asia/Shanghai";

    private static JdbcEovaDbGateway gw;
    private static boolean ready;

    /**
     * 接线：默认网关指向 baseline 的 eova_meta 库，并注入 EhCache 缓存接缝。
     *
     * <p>只需挂 {@code fallback}：{@code EovaModel.gw()} 在
     * {@code _getConfigName()} 未注册时回落到默认网关（与旧实现"用错数据源就会出错"的
     * 可见性保持一致）。</p>
     */
    @BeforeAll
    static void setUp() {
        try {
            // 【顺序无关性】先强制清掉旧的 CacheManager 单例。
            // 原因（实测踩到）：ehcache 的 CacheManager.create(configUrl) 按配置返回【同一个】实例，
            // 而 db-adapter 里 BaseModelGoldenTest 的 @AfterAll 会调 jfinal EhCachePlugin.stop()，
            // 把该共享实例关掉。此时 EhCacheService.sharedManager 变成【非 null 但已死】，
            // 后续 fromClasspath() 直接返回它 → 运行期抛
            // "The CacheManager has been shut down. It can no longer be used."。
            // shutdown() 会把静态引用置空，于是下一句 fromClasspath() 重建一个健康实例。
            EhCacheService.shutdown();
            MysqlDataSource ds = new MysqlDataSource();
            ds.setUrl(URL);
            ds.setUser("root");
            ds.setPassword("root");
            gw = new JdbcEovaDbGateway(ds);
            // 探一次库，确认可用；不可用则整类跳过并标记（不伪装成通过）
            Number n = gw.queryNumber("select count(*) from eova_field", new Object[0]);
            Assumptions.assumeTrue(n != null && n.intValue() > 0,
                    "baseline eova_field 为空，跳过（golden: skipped）");
            EovaGateways.setFallback(gw);
            // 【必须同时注入两处】——本项目的缓存接缝有两个独立注入点，缺一个就会在
            // 运行期抛「EovaModel 未注入 CacheService」：
            //   ① cn.eova.compat.cache.CacheServices（BaseCache 经 BaseCache.setCacheService 转发到它）
            //   ② cn.eova.db.EovaModel.cacheService（EovaModel 自己的静态字段，读的是它）
            // 二者没有任何一方转发到另一方。这是接缝的既有形态（非本次引入），
            // 已记为接线隐患：生产侧 Spring Boot 装配必须两处都调。见 DES-002-R4 §R48。
            CacheServices.set(EhCacheService.fromClasspath());
            cn.eova.db.EovaModel.setCacheService(CacheServices.get());
            ready = true;
        } catch (Throwable t) {
            Assumptions.abort("baseline MySQL 不可用，跳过（golden: skipped）：" + t.getMessage());
        }
    }

    @AfterAll
    static void tearDown() {
        CacheServices.clear();
        EovaGateways.clear();
        EhCacheService.shutdown();
    }

    /**
     * <b>非空洞性自检</b>：证明 buildItem 真的读了库 ——
     * 模板行 {@code id=1} 存在，且 {@code buildItem} 的产物里能看到模板贡献的属性
     * （{@code object_code} 来自模板行，buildItem 并不 put 它）。
     */
    /**
     * 取属性并显式转成 {@code Object}。
     *
     * <p><b>为什么要这个包装：</b>{@code MetaField.get(String)} 是 {@code <T> T}
     * （与旧 jfinal {@code Model.get} 一致）。把它的返回值<b>直接</b>作为实参传给
     * {@code assertEquals} 时，javac 的目标类型推断会被 JUnit 的
     * {@code assertEquals(char, char, String)} 等基本类型重载带偏，
     * 编译出 {@code checkcast char[]} —— 运行期抛
     * {@code ClassCastException: String cannot be cast to [C}。
     * 经 {@code Object} 中转即消除该歧义，且不改变断言语义。</p>
     *
     * @param model 模型
     * @param key   属性名
     * @return 属性值
     */
    private static Object attr(Object model, String key) {
        Object v = ((MetaField) model).get(key);
        return v;
    }

    @Test
    @DisplayName("非空洞性自检：getTemplate 真的读到了 eova_field 的模板行")
    void templateRowIsActuallyRead() {
        assertTrue(ready, "前置接线必须成功");
        MetaField template = MetaField.dao.getTemplate();
        assertNotNull(template, "模板行必须存在");
        // getTemplate() 会 remove("id")，故 id 应已消失
        assertNull(attr(template, "id"), "getTemplate 应移除 id");
        // object_code 由【模板行】带来，buildItem/getTemplate 都不 put 它 —— 它的存在即 DB 路径的证据
        assertEquals("eova_meta_template", attr(template, "object_code"),
                "模板行的 object_code 应来自 eova_field(id=1)");
    }

    @Test
    @DisplayName("buildItem：IMG 后缀分支 + 默认宽度 150 + type 固定为文本框")
    void buildItem_imgSuffixAndDefaultWidth() {
        assertTrue(ready, "前置接线必须成功");
        MetaField field = EovaExp.buildItem(2, "Avatar", "头像_IMG", true, null);

        assertEquals("avatar", attr(field, "en"), "en 应被小写化");
        assertEquals("头像", attr(field, "cn"), "cn 应去掉 _IMG 后缀");
        // (Object) 显式消歧：MetaField.get(String) 是 <T> T（与旧 jfinal Model.get 一致）
        assertEquals(150, (Object) attr(field, "width"), "未配 field_width 时默认宽度 150");
        assertEquals(Boolean.TRUE, attr(field, "is_query"));
        assertEquals("文本框", attr(field, "type"), "type 由 buildItem 固定覆盖为文本框");
        assertEquals(2, (Object) attr(field, "num"), "num 应等于传入的 index");
        assertEquals(
                "function(value,row,index,field){if(value){return `<img src=`+ value +` />`}return value}",
                attr(field, "formatter"), "IMG 分支的 formatter 属契约");
    }

    @Test
    @DisplayName("buildItem：EovaOption.field_width 覆盖默认宽度；en==cn 时隐藏且不查询")
    void buildItem_fieldWidthFromOptionMap() {
        assertTrue(ready, "前置接线必须成功");
        // 真实生产路径：config 是 JSON 对象，其【值本身是 JSON 字符串】，
        // 由 getConfObj("field_width") 二次解析（见 EovaOption.getConf/getConfObj）。
        EovaOption option = new EovaOption();
        LegacyKv widths = LegacyKv.create();
        widths.set("name", 240);
        LegacyKv conf = LegacyKv.create();
        conf.set("field_width", widths.toJson());
        option.setConfig(conf);

        MetaField field = EovaExp.buildItem(1, "name", "name", false, option);

        assertEquals(240, (Object) attr(field, "width"), "field_width 命中时覆盖默认宽度");
        assertEquals(Boolean.FALSE, attr(field, "is_show"), "en==cn 时 is_show 应为 false");
        assertEquals(Boolean.FALSE, attr(field, "is_query"), "en==cn 时 is_query 应为 false");
    }

    @Test
    @DisplayName("buildItem：cn 为空回落为 en（注意取的是小写化【之前】的 en，属既有语义）")
    void buildItem_emptyCnFallsBackToEn() {
        assertTrue(ready, "前置接线必须成功");
        MetaField field = EovaExp.buildItem(3, "UUID", "", false, null);

        // 既有语义（逐行读 EovaExp.buildItem 得出，不是推断）：
        //     if (x.isEmpty(cn)) cn = en;   // cn = "UUID" —— 此时 en 尚未小写化
        //     en = en.toLowerCase();        // en = "uuid"
        //     ei.put("cn", x.isEmpty(cn) ? en : cn);   // cn 非空，保留 "UUID"
        // 故 cn 保留【原样大小写】，且 en != cn，于是 en==cn 的隐藏分支不触发。
        // 这是旧实现的可观测怪异行为，阶段 1 原样保留，不"顺手修正"。
        assertEquals("uuid", attr(field, "en"), "en 被小写化");
        assertEquals("UUID", attr(field, "cn"), "cn 取小写化之前的 en，保留原大小写");
        assertEquals(Boolean.FALSE, attr(field, "is_query"), "is_query 由入参决定");
        // en != cn ⇒ 不进入 en==cn 分支 ⇒ is_show 保持【模板行】的值。
        // 实测为 Boolean.TRUE（而非 Integer 1）：eova_field.is_show 是 tinyint(1)，
        // MySQL Connector/J 默认 tinyInt1isBit=true 会把它映射成 Boolean。
        // 这里按【实测】断言而不是按列类型推断 —— 该类型差异在业务代码里
        // 经 xx.isTrue(...) 归一，属既有形态。
        assertEquals(Boolean.TRUE, attr(field, "is_show"),
                "en != cn 时不触发隐藏分支，is_show 保持模板行取值（tinyint(1) 经驱动映射为 Boolean.TRUE）");
    }

    @Test
    @DisplayName("buildItem：不修改模板本身（getTemplate 每次取新实例）")
    void templateIsNotMutatedByBuildItem() {
        assertTrue(ready, "前置接线必须成功");
        MetaField before = MetaField.dao.getTemplate();
        // 只快照关心的键：EovaModel 等价于 jfinal Model，没有 getColumnNames()
        //（那是 EovaRecord 的方法），故按显式键列表取。
        String[] keys = {"object_code", "en", "cn", "type", "width", "num", "is_show", "is_query"};
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        for (String k : keys) {
            snapshot.put(k, attr(before, k));
        }
        EovaExp.buildItem(9, "polluted", "污染_IMG", true, null);
        MetaField after = MetaField.dao.getTemplate();
        for (String k : snapshot.keySet()) {
            assertEquals(snapshot.get(k), attr(after, k),
                    "模板行的 " + k + " 不应被 buildItem 污染");
        }
    }

}
