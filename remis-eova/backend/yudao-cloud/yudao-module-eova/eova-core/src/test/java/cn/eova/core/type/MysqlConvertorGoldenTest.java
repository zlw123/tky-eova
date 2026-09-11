/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.core.type;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.eova.model.MetaField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code MysqlConvertor}（第 60 轮 port）的判据。
 *
 * <p><b>期望值的来源（必须说明，否则判据没有证据力）：</b>
 * 本单元没有任何宿主依赖、也没有任何内部替换，故
 * {@code port-units.py --verify} 已证明<b>文件体与旧源逐字节一致</b> ——
 * 语义等价由文件级等价承载；本判据的职责是<b>回归保护</b>：
 * 把类型映射表、TINYINT 定制规则、以及两条既有缺陷钉死，
 * 防止后续"顺手重构"（例如把匿名 HashMap 改成 {@code Map.of}）改变行为。</p>
 */
class MysqlConvertorGoldenTest {

    /**
     * 造一个只填了类型名/长度的 {@code MetaField}。
     *
     * @param dataTypeName 数据库类型名
     * @param size         字段长度
     * @return 字段
     */
    private static MetaField field(String dataTypeName, Integer size) {
        MetaField f = new MetaField();
        f.set("data_type_name", dataTypeName);
        if (size != null) {
            f.set("data_size", size);
        }
        return f;
    }

    @Test
    @DisplayName("mapping()：32 条类型映射逐条钉死（含 DATE->util.Date、UNSIGNED 族、二进制族）")
    void mappingTableIsPinned() {
        Map<String, Class> expected = new LinkedHashMap<>();
        expected.put("BIT", Boolean.class);
        expected.put("TEXT", String.class);
        expected.put("LONGTEXT", String.class);
        expected.put("TINYTEXT", String.class);
        expected.put("JSON", String.class);
        expected.put("DATETIME", java.util.Date.class);
        expected.put("TIMESTAMP", java.sql.Timestamp.class);
        // 【契约】DATE 映射到 java.util.Date（秒精度），不是 java.sql.Date
        expected.put("DATE", java.util.Date.class);
        expected.put("TIME", java.sql.Time.class);
        expected.put("TINYINT", Integer.class);
        expected.put("SMALLINT", Integer.class);
        expected.put("MEDIUMINT", Integer.class);
        expected.put("INT", Integer.class);
        expected.put("BIGINT", Long.class);
        // 【契约】INT UNSIGNED 强制 Integer（不是 Long）—— 旧注释明写"大部分人不知道应该为Long"
        expected.put("SMALLINT UNSIGNED", Integer.class);
        expected.put("MEDIUMINT UNSIGNED", Integer.class);
        expected.put("TINYINT UNSIGNED", Integer.class);
        expected.put("INT UNSIGNED", Integer.class);
        expected.put("BIGINT UNSIGNED", BigInteger.class);
        expected.put("DOUBLE UNSIGNED", Double.class);
        expected.put("DECIMAL UNSIGNED", BigDecimal.class);
        expected.put("FLOAT", Float.class);
        expected.put("DOUBLE", Double.class);
        expected.put("DECIMAL", BigDecimal.class);
        expected.put("CHAR", String.class);
        expected.put("VARCHAR", String.class);
        expected.put("BINARY", Byte[].class);
        expected.put("VARBINARY", Byte[].class);
        expected.put("TINYBLOB", Byte[].class);
        expected.put("BLOB", Byte[].class);
        expected.put("MEDIUMBLOB", Byte[].class);
        expected.put("LONGBLOB", Byte[].class);

        Map<String, Class> actual = new MysqlConvertor().mapping();
        assertEquals(expected, actual, "类型映射表必须逐条一致（键集与目标类都属契约）");
        assertEquals(32, actual.size(),
                "条目数钉死（防悄悄增删）—— 我首轮写成 33，判据当场纠正：旧表实为 32 条");
    }

    @Test
    @DisplayName("mapping() 是同一个可变实例（static final 匿名 HashMap），不是每次新建的副本")
    void mappingIsTheSameMutableInstance() {
        MysqlConvertor c = new MysqlConvertor();
        Map<String, Class> a = c.mapping();
        assertSame(a, c.mapping(), "两次调用必须返回同一实例");
        assertSame(a, new MysqlConvertor().mapping(), "不同实例也共享同一张表（它是 static）");
        // 可变性是既有语义：旧实现是 HashMap，调用方 put 进去会对所有调用方生效。
        // 若改成 Map.of/不可变副本，此处会抛 UnsupportedOperationException —— 该差异必须被抓住。
        a.put("__probe__", String.class);
        try {
            assertTrue(c.mapping().containsKey("__probe__"), "写入必须对所有调用方可见");
        } finally {
            a.remove("__probe__");
        }
    }

    @Test
    @DisplayName("getJavaType：TINYINT 长度=1 自动转 Boolean（Eova 定制规则）")
    void tinyintSizeOneBecomesBoolean() {
        MysqlConvertor c = new MysqlConvertor();
        assertEquals(Boolean.class, c.getJavaType(field("TINYINT", 1)),
                "TINYINT(1) 必须转 Boolean —— 这是 Eova 的定制规则，不是 MySQL 默认");
        assertEquals(Integer.class, c.getJavaType(field("TINYINT", 2)),
                "TINYINT 非 1 长度仍是 Integer");
        // 规则用 equalsIgnoreCase，故小写同样命中
        assertEquals(Boolean.class, c.getJavaType(field("tinyint", 1)), "大小写不敏感");
        // 规则命中前【先】做大小写无关比较，但落到 mapping() 查表时用的是 getDataTypeName() 的大写形态
        assertEquals(Integer.class, c.getJavaType(field("int", null)), "小写 int 也应命中");
    }

    @Test
    @DisplayName("getJavaType：未登记类型抛运行时异常，消息逐字钉死")
    void unknownTypeThrowsWithPinnedMessage() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> new MysqlConvertor().getJavaType(field("GEOMETRY", null)));
        assertEquals("当前数据类型无法匹配数据库字段类型:GEOMETRY,请更换其它常用类型或自定义类型转换器",
                e.getMessage(), "消息由 Convertor.getJavaType 生成（%s 位置内插类型名）");
    }

    @Test
    @DisplayName("convert / convertValue：null 直通、按映射转值、convertValue 恒为 null（既有缺陷）")
    void convertAndConvertValue() {
        MysqlConvertor c = new MysqlConvertor();

        // ① null 直通（不查类型、不抛错）
        assertNull(c.convert(field("INT", null), null), "null 必须直通返回 null");

        // ② 按映射表转值：走 Convertor.rule -> LegacyTypeConverter
        assertEquals(7, c.convert(field("INT", null), "7"));
        assertEquals(7L, c.convert(field("BIGINT", null), "7"));
        assertEquals(Boolean.TRUE, c.convert(field("TINYINT", 1), "true"));
        assertEquals("abc", c.convert(field("VARCHAR", null), "abc"));

        // ③ 转换失败：包装成 RuntimeException，消息为 "无法将值[%s]转换为[%s]"
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> c.convert(field("INT", null), "abc"));
        assertTrue(e.getMessage().startsWith("无法将值[abc]转换为["),
                "失败消息必须内插原值与目标类型，实际：" + e.getMessage());

        // ④ 【缺陷钉死】convertValue 旧实现是 TODO，恒返回 null
        assertNull(c.convertValue("anything", 0),
                "convertValue 旧实现即 return null（TODO 未实现）—— 不得'顺手实现'，那是行为变更");
        assertNull(c.convertValue(null, 999));
    }
}
