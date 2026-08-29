package cn.eova.engine;

import java.util.ArrayList;
import java.util.List;

import cn.eova.sql.dql.TableSource;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectOrderByItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * SqlParse golden 钩子。
 * TODO LC-013：对照旧 demo 录制表达式 SQL 解析响应，补全 join/order 金样。
 */
public class SqlParseGoldenTest {

    @Test
    public void getExprName_propertyIdentifierAndMethod() {
        SQLPropertyExpr property = new SQLPropertyExpr("t1", "a");
        Assertions.assertEquals("a", SqlParse.getExprName(property));
        Assertions.assertEquals("t1", SqlParse.getExprOw(property));
        Assertions.assertEquals("t1", SqlParse.getExprAlias(property));

        Assertions.assertEquals("id", SqlParse.getExprName(new SQLIdentifierExpr("id")));
        Assertions.assertEquals("count()", SqlParse.getExprName(new SQLMethodInvokeExpr("count")));
        SqlParse countStar = new SqlParse(DbType.mysql, "select count(*) from t1");
        Assertions.assertEquals("count(*)", SqlParse.getExprName(countStar.getSelectItem().get(0).getExpr()));
        Assertions.assertEquals("", SqlParse.getExprName(null));
    }

    @Test
    public void constructor_parsesSelectItems() {
        SqlParse sp = new SqlParse(DbType.mysql,
                "select t1.a 呵呵, t1.b , t2.c from t1 left join t2 on t1.id = t2.pid where a.id = 1 order by id,indexNum desc");
        List<SQLSelectItem> items = sp.getSelectItem();
        Assertions.assertEquals(3, items.size());
        Assertions.assertEquals("a", SqlParse.getExprName(items.get(0).getExpr()));
        Assertions.assertEquals("呵呵", items.get(0).getAlias());
        Assertions.assertEquals("b", SqlParse.getExprName(items.get(1).getExpr()));
        Assertions.assertEquals("c", SqlParse.getExprName(items.get(2).getExpr()));
        Assertions.assertNotNull(sp.query.getFrom());
        Assertions.assertNotNull(sp.query.getWhere());
    }

    @Test
    public void getOrderItem_mysqlFallsThroughLegacyJdbcUtilsEquals() {
        // 源码用 dbType.equals(JdbcUtils.MYSQL)（String），DbType 枚举走 else/sqlselect 分支，保持同源。
        SqlParse sp = new SqlParse(DbType.mysql,
                "select t1.a from t1 order by id,indexNum desc");
        List<SQLSelectOrderByItem> items = sp.getOrderItem();
        Assertions.assertNotNull(items);
        Assertions.assertEquals(2, items.size());
        Assertions.assertEquals("id", ((SQLIdentifierExpr) items.get(0).getExpr()).getName());
        Assertions.assertEquals("indexNum", ((SQLIdentifierExpr) items.get(1).getExpr()).getName());
    }

    @Test
    public void parseTableSource_leftJoinFillsTableSourceStub() throws Exception {
        SqlParse sp = new SqlParse(DbType.mysql,
                "select t1.a, t2.c from t1 left join t2 on t1.id = t2.pid");
        List<TableSource> sources = new ArrayList<>();
        SqlParse.parseTableSource(sp.query.getFrom(), sources);
        Assertions.assertEquals(2, sources.size());

        TableSource left = sources.get(0);
        Assertions.assertEquals("t1", left.getTable());
        Assertions.assertEquals("id", left.getLeftField());
        Assertions.assertEquals("t1", left.getLeftAlias());
        Assertions.assertEquals("pid", left.getRigthField());
        Assertions.assertEquals("t2", left.getRigthAlias());

        TableSource right = sources.get(1);
        Assertions.assertEquals("t2", right.getTable());
        Assertions.assertEquals("id", right.getLeftField());
        Assertions.assertEquals("pid", right.getRigthField());
    }

    @Test
    public void parseTableSource_missingJoinConditionThrowsLegacyMessage() {
        SqlParse sp = new SqlParse(DbType.mysql, "select t1.a from t1 left join t2");
        Exception ex = Assertions.assertThrows(Exception.class,
                () -> SqlParse.parseTableSource(sp.query.getFrom(), new ArrayList<>()));
        Assertions.assertEquals("Eova当前仅支持Left Join查询方式的View进行自动持久化操作，请手工自定义新增，修改等操作！",
                ex.getMessage());
    }
}
