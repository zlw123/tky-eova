# DES-001 Kingbase 54321 创建 eova_meta / demo 库

## 1. 背景

EOVA Demo 需要两个库（README 约定）：

| 角色 | 库名 | 源脚本 | 表数 |
|---|---|---|---|
| 平台库 | `eova_meta` | `meta-eova/eova/demo/sql/eova_meta.sql` | 32 |
| 业务库 | `demo` | `meta-eova/eova/demo/sql/demo.sql` | 25 |

源脚本为 MySQL 8 Navicat 导出。目标实例：`base.platform:54321`（KingbaseES V008R006C008B0020）。

## 2. Live 事实

1. 54321 上现有库：`fhmix` / `gw` / `kingbase` / `security` / `test` / `xxl_job`，**尚无** `eova_meta`、`demo`。
2. 当前库 `kingbase` 的 `database_mode = mysql`，整簇偏 MySQL 兼容，利于少改脚本导入。
3. 账号 `system` 具备 `rolcreatedb=true`，可建库。
4. 可用查询通道：Tabularis `baseplatform-db`；MCP `kingbase-baseplatform` 密码失效，不可依赖。
5. 本机无 `psql`/`ksql`，大批量导入需走 Tabularis、Node PG 客户端，或拿哥提供可用 CLI/密码。

## 3. 方案决策（待确认）

### 3.1 对象形态

- **推荐**：创建两个 **DATABASE**：`eova_meta`、`demo`（对齐 EOVA JDBC 双库模型，也对齐现网多库习惯）。
- 不推荐：挤在 `kingbase` 下建两个 schema（与 README/JDBC URL 模型不一致）。

### 3.2 脚本策略

- **优先路径**：MySQL 兼容簇下 **轻量清洗后原样导入**（保留反引号、AUTO_INCREMENT、ENGINE 等）。
- 必清项（即使兼容模式也建议去掉）：
  - `SET NAMES utf8mb4;`
  - `SET FOREIGN_KEY_CHECKS = 0;`
- 若导入失败，再按失败点做定向改写（charset/collate、`USING BTREE`、`ON UPDATE CURRENT_TIMESTAMP`、保留字列 `` `full` `` / `` `key` `` 等）。
- **备选路径**：完整转 PG 方言（成本高，仅在兼容模式导入失败时启用）。产出目录建议：`meta-eova/eova/demo/sql/kingbase/`。

### 3.3 执行步骤（授权后）

1. `CREATE DATABASE eova_meta;` / `CREATE DATABASE demo;`（owner=`system`，编码 UTF8）。
2. 生成清洗版 SQL → `demo/sql/kingbase/`。
3. 分别导入两库。
4. VAL：表数量、关键表行数抽样、`eova_user`/`eova_menu`/`users` 等冒烟查询。
5. 记录 JDBC 连接串模板（不写明文密码进仓库）。

### 3.4 风险与边界

- 共享实例，建库前确认命名无冲突（已 live 确认无同名）。
- 导入含 demo 业务样例数据，非空库。
- 不在本任务改应用代码/JDBC 配置；配置切换另开任务。
- 不做 drop 已有业务库；若重跑仅 drop 本次新建的 `eova_meta`/`demo`（需二次确认）。

## 4. 验收标准

1. `pg_database` 可见 `eova_meta`、`demo`。
2. `eova_meta` 表数 = 32；`demo` 表数 = 25。
3. 至少各抽查 3 张表有预期数据或空表与源脚本一致。
4. rolling docs 记录导入证据与失败回退点。

## 5. 放行检查

- [x] 确认库名就是 `eova_meta` + `demo`
- [x] 确认允许在共享 54321 上建库并导入样例数据
- [x] 确认导入通道（Node PG + 已验证 system 密码）
- [x] 用户明确说「按 DES-001 执行」后再动库

## 6. 执行结论（2026-08-25）

已完成。详见 `docs/DES-001-exec-log.md`。
