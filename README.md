# remis-eova

meta-eova 技术栈迁移工作区（Yudao Cloud + Vue3 代码级 port）。

## 目录

| 路径 | 说明 |
|------|------|
| `docs/` | 设计文档、rolling docs、Automation 指令 |
| `remis-eova/` | 新代码（backend / fornt/eova-ui） |
| `meta-eova/eova/` | 旧 EOVA 只读参考（git submodule → gitee/eova） |

## 文档入口

- 任务板：`docs/ai-task-board.md`
- 后端迁移方法论：`docs/DES-002-R1-code-level-migration.md`
- Automation 流水线：`docs/automation/README.md`

## Submodule

```bash
git submodule update --init --recursive
```
