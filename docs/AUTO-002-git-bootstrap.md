# AUTO-002 — remis-eova Git 仓库初始化

> 远程（拿哥提供）：`http://10.20.110.206:45001/remis/modules/remis-eova.git`  
> 本地工作区：`/Users/zhouliwei/eova`

---

## 1. 仓库范围建议

Automation（Orchestrator / Worker）需要在 checkout 里同时看到 **治理文档** 与 **旧代码参考**，推荐结构：

```
remis-eova.git（根 = eova 工作区）
├── docs/                 # rolling docs + automation 指令
├── remis-eova/           # 新代码（backend / fornt）
└── meta-eova/eova/       # 旧代码只读参考（建议 submodule）
```

`meta-eova/eova` 当前已是独立 clone（`gitee.com/eova/eova`），**不要**把嵌套的 `.git` 目录直接 commit 进父仓库；用 **submodule** 或首次 push 前删掉子目录 `.git` 再 add（不推荐，难同步上游）。

---

## 2. 拿哥在 GitLab 建空库后 — 本地执行

在 **空远程已创建** 且账号有 push 权限后，于本机执行：

```bash
cd /Users/zhouliwei/eova

# 初始化
git init -b main

# 注册 submodule（保留与 gitee 上游同步能力）
git submodule add https://gitee.com/eova/eova.git meta-eova/eova

# 首次提交
git add .gitignore docs/ remis-eova/ .gitmodules meta-eova/eova
git commit -m "$(cat <<'EOF'
chore: 初始化 remis-eova 工作区

含 docs 治理文档、remis-eova 新代码目录、meta-eova 子模块（只读参考）。
EOF
)"

# 绑定远程并推送
git remote add origin http://10.20.110.206:45001/remis/modules/remis-eova.git
git push -u origin main
```

若 **submodule 已存在**（目录里已有 `.git`），可先：

```bash
cd /Users/zhouliwei/eova
rm -rf meta-eova/eova/.git   # 仅当改用 submodule 重新 add 时；会丢失子仓库本地分支信息，请先确认无未 push 改动
git submodule add https://gitee.com/eova/eova.git meta-eova/eova
```

---

## 3. 认证提示

- 内网 GitLab `10.20.110.206:45001` 可能需要 **Personal Access Token** 或 SSH。
- HTTP push 示例：`git remote set-url origin http://<user>:<token>@10.20.110.206:45001/remis/modules/remis-eova.git`
- 或改用 SSH：`git@10.20.110.206:remis/modules/remis-eova.git`（以 GitLab 实际配置为准）

---

## 4. 完成后验收

- [ ] `git remote -v` 指向 remis-eova.git
- [ ] `git submodule status` 显示 meta-eova/eova
- [ ] GitLab 上可见 `docs/automation/`、`docs/ai-task-board.md`
- [ ] Cursor Automations 编辑器里 **gitConfig.repo** 选该仓库、**dev** 分支（日常开发；main 受保护）
- [ ] 任务板 **AUTO-002** → Done，**AUTO-003** → Ready

---

## 5. 与 Automation 的关系

| 文件 | 必须已 push |
|------|-------------|
| `docs/automation/*.md` | 是 |
| `docs/ai-task-board.md` | 是 |
| `meta-eova/eova/...`（Worker 读源） | 是（submodule 初始化后） |

push 成功后，按 **`docs/automation/MANUAL-SETUP.md`** 在 Automations UI **手建**三条 Automation（或用 Agents Window 让 Agent 预填编辑器）。
