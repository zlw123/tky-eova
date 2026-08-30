# DES-DB-ADAPTER EovaDbGateway 适配层设计

> 状态：Design-only
> 目的：给 B/C 类代码级迁移提供唯一数据库访问边界，保持 JFinal `Db`/`Record` 的可观察语义。

## 1. 最小接口

```java
public interface EovaDbGateway {
    /** 查询多条记录，保持旧 Db.find 的空结果和列访问语义。 */
    List<EovaRecord> find(String dataSource, String sql, Object... args);
    /** 查询第一条记录，保持旧 Db.findFirst 的空结果语义。 */
    Optional<EovaRecord> findFirst(String dataSource, String sql, Object... args);
    /** 执行分页查询，保持旧分页起始值和总数语义。 */
    PageResult<EovaRecord> paginate(String dataSource, int page, int size,
                                    String select, String sql, Object... args);
    /** 执行更新并返回影响行数。 */
    int update(String dataSource, String sql, Object... args);
    /** 保存一条记录并返回影响行数或生成键结果。 */
    int save(String dataSource, String table, EovaRecord record);
    /** 删除记录并返回影响行数。 */
    int delete(String dataSource, String sql, Object... args);
}
```

方法签名以旧代码实际调用为准；新增能力必须先更新本设计。`EovaRecord` 要定义列名大小写、链式 set、缺失列、null、数值/日期转换和 JSON 序列化语义。

## 2. 必须冻结的运行语义

- 数据源名称映射：`Ds.EOVA`、`Ds.MAIN` 和业务数据源的配置键。
- 参数顺序、占位符绑定和分页起始值。
- 查询无结果时返回空列表还是空 Optional。
- update/save/delete 的影响行数与异常映射。
- 事务边界、传播级别、回滚异常范围。
- Kingbase 方言下标识符、分页、布尔和日期类型处理。

## 3. 实现边界

第一版由 MyBatis/动态 SQL 实现，但业务迁移单元只能依赖 `EovaDbGateway`，不得直接把旧 SQL 改造成新的业务 Mapper。网关不支持某个旧调用时，Worker 必须停在 blocked 并创建适配层设计变更，不得删除调用或改业务分支。

## 4. 验证

为 `find/findFirst/paginate/save/update/delete` 各准备空结果、单行、多行、null、分页边界、事务回滚和双数据源测试；与旧 demo 的 SQL 输入和 Record 输出做 golden 对照。未完成这些测试前，DES-DB-ADAPTER 不得标 Done。
