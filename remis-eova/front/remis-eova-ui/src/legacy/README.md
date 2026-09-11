# src/legacy —— 旧前端 120 个资产的**冻结副本**

本目录是 `DES-002-R4` **阶段 2** 的起点：把旧 EOVA 前端的源码资产**逐字节**搬进独立前端工程。

| 项 | 值 |
|---|---|
| 资产数 | 120（`docs/.local/ledger/frontend-assets.jsonl` 为准） |
| 来源 | `meta-eova/eova/view/src/main/resources/webapp/**` |
| source revision | `1b1d39e7350f7e031b216aad0399fc8cc55dce08` |
| 落点 | 即本目录（按账本冻结的 `targetPath`，**不是**自选路径） |
| 校验 | 落地时 sha256 逐条比对 **120/120 一致**；复核工具 `docs/.local/spikes/verify-frontend-legacy-assets.py` |

## 规矩

1. **本目录内容不得手改。** 阶段 1 的口径是"前端一行不动并冻结基线"，
   这些文件就是那份基线的副本；改动会让 `verify-frontend-baseline.py`（以旧树 sha 为准）与
   本目录的复核同时失效。
2. **新代码不要 import 这里。** 改造后的页面放 `src/views`、`src/components`（见工程 README §5）。
   本目录的用途是：① 可逐字节复核的起点；② 迁移期的对照物（旧渲染结果与新 SFC 的行为对照）。
3. **删除时机**：由阶段 2 的迁移清单逐项确认替代完成后统一删除，不在本轮清理。

## 与工程 README §5 的差异（已记录，待 r90 归并）

工程 README 的目标目录结构里没有 `legacy/`，而冻结账本的 `targetPath` 指向 `src/legacy/**`。
两者都是早前轮次定下的口径 —— 本轮按**账本**落地（资产单元的 hash 复核以账本为准），
差异登记为待归并项，避免"同一件事两处口径"继续漂移。
