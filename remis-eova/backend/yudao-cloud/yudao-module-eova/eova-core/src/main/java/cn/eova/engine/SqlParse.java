// compile-stub for LC-011 EovaExp; not a ported unit.
// real source: meta-eova/eova/core/src/main/java/cn/eova/engine/SqlParse.java
package cn.eova.engine;

import java.util.Collections;
import java.util.List;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectOrderByItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;

/**
 * Druid SQL 解析器 stub。完整算法待 LC-011 下一单元 port SqlParse。
 */
public class SqlParse {

    public SQLSelectQueryBlock query;

    public SqlParse() {
    }

    public SqlParse(DbType dbType, String sql) {
        // stub: 真实解析逻辑在 SqlParse 单元，禁止在本 run 复制
    }

    public List<SQLSelectItem> getSelectItem() {
        return Collections.emptyList();
    }

    public List<SQLSelectOrderByItem> getOrderItem() {
        return null;
    }

    public static String getExprName(SQLExpr expr) {
        return "";
    }
}
