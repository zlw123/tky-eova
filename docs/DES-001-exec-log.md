# DES-001 执行记录

## 结果

已在 Kingbase `base.platform:54321` 创建并导入：

| 库 | 表数 | 状态 |
|---|---|---|
| `eova_meta` | 32 | 通过（补加 `eova_user.status`） |
| `demo` | 25 | 通过（`data_10` 去分区后重建） |

## 清洗脚本

目录：`meta-eova/eova/demo/sql/kingbase/`

主要改写：去 `SET NAMES`/`FOREIGN_KEY_CHECKS`、去 charset/collate、`USING BTREE`、`UNSIGNED`、`ON UPDATE`、索引 `ASC/DESC`、`double(m,n)`→`double`、MySQL 字符串转义→标准 SQL；`PARTITION` 在 `data_10` 上手动去掉。

## 抽样（live）

- `eova_meta`：`eova_user=21`，`eova_menu=33`，`eova_button=211`，`eova_object=42`，`eova_role=9`
- `demo`：`users=34`，`area=3410`，`orders=13`，`address=4`，`data_10=2`

## JDBC 模板（密码自备）

```
jdbc:kingbase8://base.platform:54321/eova_meta
jdbc:kingbase8://base.platform:54321/demo
# 或 PostgreSQL 协议：
jdbc:postgresql://base.platform:54321/eova_meta
jdbc:postgresql://base.platform:54321/demo
user=system
```

## 已知残留

1. 源脚本末尾 `ALTER ... MODIFY status ... AFTER id` 在源 CREATE 中本无 `status`，已改为 `ADD COLUMN status`。
2. `data_10` 原带 HASH 分区，Kingbase 未吃下，已建普通表并写入 2 行样例。
