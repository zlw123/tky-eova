/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code EovaDbGateway.forPaginate} 接缝的判据（第 72 轮为 port {@code WidgetManager} 而补）。
 *
 * <p><b>期望值取自旧制品实测</b>：直接调用 jfinal 5.2.6 的
 * {@code MysqlDialect.forPaginate(pageNumber, pageSize, StringBuilder)} 打印结果，
 * 而不是照 javap 推断（本工程在"javap 吃空格/拼接顺序"上栽过）。</p>
 */
class PaginateSeamGoldenTest {

    /** 网关（不触碰 DataSource） */
    private final JdbcEovaDbGateway gateway = new JdbcEovaDbGateway(null);

    @Test
    @DisplayName("forPaginate：偏移量 = (pageNumber-1)*pageSize，pageNumber=0 时为【负数】（旧制品实测）")
    void forPaginateMatchesOldArtifact() {
        // 实测：forPaginate(1, 100, "select * from t") -> "select * from t limit 0, 100"
        assertEquals("select * from t limit 0, 100",
                gateway.forPaginate(1, 100, new StringBuilder("select * from t")));

        // 实测：forPaginate(1, 10, ...) -> "select * from t limit 0, 10"
        assertEquals("select * from t limit 0, 10",
                gateway.forPaginate(1, 10, new StringBuilder("select * from t")));

        // 实测：forPaginate(0, 100, ...) -> "select * from t where x=1 limit -100, 100"
        // —— 负偏移是旧实现的既有行为（该 SQL 在 MySQL 上不可执行），本接缝原样保留
        assertEquals("select * from t where x=1 limit -100, 100",
                gateway.forPaginate(0, 100, new StringBuilder("select * from t where x=1")),
                "pageNumber=0 时偏移量必须是 -pageSize（不得'顺手修正'为 0）");
    }

    @Test
    @DisplayName("forPaginate：【不做 trim】，且第 2 页偏移为 pageSize（旧制品实测）")
    void forPaginateDoesNotTrimAndSecondPage() {
        // 实测：forPaginate(1,5,"  select 1  ") -> "  select 1   limit 0, 5"
        // （我最初实现成 trim 后拼接 —— 由本条断言纠正）
        assertEquals("  select 1   limit 0, 5",
                gateway.forPaginate(1, 5, new StringBuilder("  select 1  ")),
                "入参 SQL 必须原样拼接（不 trim）");

        // 实测：forPaginate(2,5,"select 1") -> "select 1 limit 5, 5"
        assertEquals("select 1 limit 5, 5",
                gateway.forPaginate(2, 5, new StringBuilder("select 1")),
                "第 2 页偏移量 = 1*pageSize");
    }
}
