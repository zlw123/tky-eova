# Cloud Agent 挂库指南（remis-eova / 内网 GitLab）

> 对应故障：`repos: null`、`/agent` 为空、`gh 未登录`、读不到 `docs/automation/*`  
> **Cursor Cloud Agent 推荐仓库**：`https://github.com/zlw123/tky-eova.git`（分支 dev）  
> 内网 GitLab 备份：`http://10.20.110.206:45001/remis/modules/remis-eova.git`

---

## 1. 根因（你这次 run 在说什么）

| 现象 | 含义 |
|------|------|
| `environment.repos: null` | Cloud **Environment 没选任何仓库** |
| `workspace /agent 为空` | 机器起来了，但没 clone 代码 |
| `gh 未登录` | 未接 GitLab/GitHub 源码集成时的默认报错文案（不一定是真要用 GitHub） |
| `enabled: false` | 自动化当时未启用，即使环境修好也不会按计划跑 |

**结论**：不是 prompt 写错，是 **Cloud Agent 环境 + 源码集成** 没配完。

---

## 2. 去哪里挂？（两步，顺序不能反）

### 第一步：Cursor 连上你的 GitLab（账号级，一次性）

1. 打开 [Cursor Dashboard → Integrations](https://cursor.com/dashboard?tab=integrations)
2. **Advanced → GitLab Self-Hosted**（内网实例走这条，不是 gitlab.com）
3. 在 **GitLab 管理端**（需 Admin）创建 OAuth Application：
   - Redirect URI：`https://cursor.com/gitlab-connected`
   - Trusted：`true`，Confidential：`true`
   - Scopes：`api`、`write_repository`
4. 把 **Application ID / Secret** 填回 Cursor，点 **Connect**
5. Integrations 里对该连接点 **Manage → Sync Repos**，把 `remis/modules/remis-eova` 同步进来

**内网可达性**（`10.20.110.206` 若公网访问不到 Cursor）：

- 方案 A：GitLab 对 Cursor 出口 IP 做白名单（文档推荐 IP 见 [GitLab 集成文档](https://cursor.com/docs/integrations/gitlab)）
- 方案 B：**Enterprise** 用 [Private Connectivity](https://cursor.com/docs/cloud-agent/private-connectivity)（PrivateLink / Cloudflare Tunnel / Reverse Proxy）

没有连通性，Integrations 连上了 Cloud Agent 也 clone 失败。

**计划要求**：

- Cursor：**Teams 或 Enterprise** 才支持 Self-Hosted GitLab
- GitLab：**Premium / Ultimate**（Free 无 Project Access Token）

---

### 第二步：给 Cloud Agent Environment 绑定 remis-eova

1. 打开 [Cloud Agents Dashboard](https://cursor.com/dashboard?tab=cloud-agents)
2. 找到环境 `f3d0660e-…`（或 **New environment** 重建，更干净）
3. **Agent-driven setup**（推荐）或编辑现有环境：
   - **Select repositories** → 选 **`remis/modules/remis-eova`**
   - 默认分支选 **dev**
   - 若有 submodule：install 脚本里加  
     `git submodule update --init --recursive`  
     （本仓库 `.cursor/environment.json` 已含此命令）
4. 跑完 **Build** 并等状态 **Active**
5. 确认环境详情里 **repos 不再是 null**

---

### 第三步：Automation 指到这个 Environment

1. [Automations](https://cursor.com/automations) → 打开 `eova-migration-orchestrator`
2. **Enable**（你那次是 `enabled: false`）
3. **Repository / Branch**：`remis-eova` + **dev**
4. **Compute / Environment**：选刚绑定仓库的 Environment（不要选 repos 为空的环境）
5. Save → **Manual Run** 试跑，再看 run 详情里是否已 checkout 到 `docs/`

---

## 3. 能挂刚才的 Git 库吗？

**能**，就是 `http://10.20.110.206:45001/remis/modules/remis-eova.git`，在 Cursor 里显示为 **`remis/modules/remis-eova`**（group 项目）。

注意：

| 点 | 说明 |
|----|------|
| 路径在 **group** `remis` 下 | 社区反馈：GitLab **组项目** 选分支偶发 500 / `ERROR_GITHUB_NO_USER_CREDITS`；个人 namespace 更稳。若选不了分支，向 Cursor 反馈或临时 mirror 到个人项目试跑 |
| **submodule** `meta-eova/eova` | 指向 gitee；Cloud Build 的 install 需 `submodule update`；Worker 读旧代码依赖此步 |
| **main 受保护** | Automation / Agent 统一用 **dev** |

---

## 4. 内网 GitLab 一时接不通 Cloud 时的退路

Automation 编辑页把 **Compute 从 Cloud 改 Local**：

- 用本机已 clone 的 `/Users/zhouliwei/eova`（dev 分支）
- 不依赖 Cursor 云端访问 `10.20.110.206`
- 适合 Orchestrator 只改 docs、本机 Keychain 已能 push 的阶段

Cloud 打通后再切回 Cloud Agent（并行跑、不占用本机）。

---

## 5. 修完后的验收清单

- [ ] Integrations → GitLab Self-Hosted → Connected，Sync Repos 能看到 remis-eova
- [ ] Cloud Environment → repos 含 remis-eova，Build **Active**
- [ ] Automation **enabled**，Environment 选对，branch = **dev**
- [ ] Manual Run 日志里能 `cat docs/automation/orchestrator-instructions.md`
- [ ] 能 `git push origin dev`

---

## 6. 相关链接

- [Cloud Environment Setup](https://cursor.com/docs/cloud-agent/setup)
- [GitLab 集成（含 Self-Hosted）](https://cursor.com/docs/integrations/gitlab)
- [Private Connectivity（Enterprise 内网）](https://cursor.com/docs/cloud-agent/private-connectivity)
- 手建 Automation：`docs/automation/MANUAL-SETUP.md`
