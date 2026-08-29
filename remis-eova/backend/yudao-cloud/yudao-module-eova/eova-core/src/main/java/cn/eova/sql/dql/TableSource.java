// compile-stub for LC-011 SqlParse; not a ported unit.
// real source: meta-eova/eova/core/src/main/java/cn/eova/sql/dql/TableSource.java
package cn.eova.sql.dql;

/**
 * SQL 表信息 stub。完整 POJO 待后续单元 port TableSource。
 */
public class TableSource {

    private String table;
    private String alias;
    private String leftField;
    private String leftAlias;
    private String rigthField;
    private String rigthAlias;

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getLeftField() {
        return leftField;
    }

    public void setLeftField(String leftField) {
        this.leftField = leftField;
    }

    public String getLeftAlias() {
        return leftAlias;
    }

    public void setLeftAlias(String leftAlias) {
        this.leftAlias = leftAlias;
    }

    public String getRigthField() {
        return rigthField;
    }

    public void setRigthField(String rigthField) {
        this.rigthField = rigthField;
    }

    public String getRigthAlias() {
        return rigthAlias;
    }

    public void setRigthAlias(String rigthAlias) {
        this.rigthAlias = rigthAlias;
    }

}
