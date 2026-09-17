# Dream-AI

用 **Java 模块化单体** 实现的 Agent 运行时：单进程可跑，Maven 按能力切模块，用 Port 隔离厂商与中间件。

当前已打通：**网关、工具循环（白名单 / HITL / 失败重试）、会话 / Memory、分阶段 RAG、Supervisor Graph（含 Knowledge 子图）、SSE、观测、评测，以及配套前端 `dream-ai-web`**。

本仓库会**持续维护并扩展**：在现有主路径上继续加能力、补文档与示例。欢迎 Star / Watch 跟进更新。

- 产品站：[dream-saas.com](https://www.dream-saas.com/)
- 公众号：见文末二维码（扫码关注，系列文章会同步更新）

---

## 目录

- [适合谁](#适合谁)
- [它解决什么问题](#它解决什么问题)
- [能做什么](#能做什么)
- [请求主路径](#请求主路径)
- [模块划分](#模块划分)
- [技术栈](#技术栈)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [演示路径（约 10 分钟）](#演示路径约-10-分钟)
- [配置说明](#配置说明)
- [HTTP API](#http-api)
- [能力说明](#能力说明)
- [测试](#测试)
- [项目结构](#项目结构)
- [设计原则](#设计原则)
- [演进与维护](#演进与维护)
- [安全提示](#安全提示)
- [相关链接](#相关链接)
- [License](#license)

---

## 适合谁

- 想用 **Spring Boot + Spring AI** 自己搭一条可运行的 Agent 主路径  
- 关心 **网关、工具循环、Graph 编排、会话 / Memory、RAG、观测、MCP Client** 各自切在哪一层  
- 需要一个 **能 clone、能改、能演示** 的参考实现（含配套前端），而不是只有概念图  

本仓库面向学习与工程实践：默认不包含生产级多租户、计费、管理后台等完整 SaaS 产品面。

---

## 它解决什么问题

做 Agent 时常见两种极端：

1. **一切塞进一个胖应用**：分层糊掉，换模型 / 换存储就牵全身。  
2. **过早拆成很多进程与空 jar**：还没跑通主路径，先陷入编排和依赖迷宫。

Dream-AI 选第三条路：**模块化单体**——一个 JVM 跑通主路径，用 Maven 模块和 Port 把「协议边界」立住。你可以先把请求链路讲清楚、跑起来，再按需要把 RAG、工具服务等拆出去。

---

## 能做什么

| 能力 | 说明 |
|------|------|
| 对话网关 | HTTP 只进 `AgentGateway`；`agentId=chat` 直达工具循环，`agentId=graph` 走 Supervisor |
| Graph 编排 | 意图路由 → chat / knowledge / review；knowledge 为子图（gate → generate → cite \| refuse） |
| 工具循环 | 显式限步；进程内工具 + 可选 MCP；白名单 / HITL；执行失败可重试，拒执与失败分字段 |
| 会话 / Memory | MySQL 短会话 + Redis 长期记忆（可异步摘要） |
| RAG | 分阶段检索（recall / filter / rerank 等）+ fusion；知识意图才检索 |
| 流式 | `POST /api/agent/invoke/stream`（SSE：route / model / tool_* / retrieve / delta / done） |
| 观测 | `traceId`、耗时、route、model、blocked / executed / failed、retrieveHits |
| 评测 | classpath golden + `GET/POST /api/eval/*` |
| 配套前端 | [`dream-ai-web`](dream-ai-web/README.md)：对话 / SSE / HITL / Observe / Eval |
| MCP Client | 可选对接智谱 SSE；本仓不做自环 MCP Server |

---

## 请求主路径

```text
HTTP POST /api/agent/invoke  或  /invoke/stream
        │
        ▼
 AgentInvokeController   （可选头 X-Dream-Tool-Approvals）
        │  只调用 AgentGateway
        ▼
 DefaultAgentGateway
        │  begin 观测 → history / memory / retrieve → Handler
        ▼
 ┌─ chat ─► ChatAgent ─► ChatPort + ToolCallingLoop
 │
 └─ graph ► GraphAgent ─► SupervisorGraph
              ├ chat 叶
              ├ KnowledgeSubGraph（gate / generate / cite|refuse）
              └ review 叶
        ▼
 HTTP JSON 或 SSE done（output + 观测摘要）
```

**分层约定：**

- Agent **不**注入 Conversation / Memory / Retrieve / 持久化 Mapper。  
- DAO 与 Spring 装配集中在 `dream-ai-app`。  
- `knowledge` 实现 `RetrievePort`；`agents` 只依赖 `kernel`。

---

## 模块划分

| 模块 | 职责 | 允许依赖 |
|------|------|----------|
| `dream-ai-bom` | 第三方版本对齐 | 无业务代码 |
| `dream-ai-kernel` | Port / SPI | 无 Web、无厂商 SDK |
| `dream-ai-knowledge` | 进程内 RAG | 仅 `kernel` |
| `dream-ai-agents` | `ChatAgent` / `GraphAgent` | 仅 `kernel` |
| `dream-ai-app` | Boot 入口、Graph 实现、HTTP、适配器 | kernel + knowledge + agents |
| `dream-ai-web` | 配套前端（Vite + React） | 仅调 HTTP API |

---

## 技术栈

| 用途 | 选型 |
|------|------|
| 语言 | Java 21 |
| 运行时 | Spring Boot 3.5.x |
| LLM / Graph | Spring AI + Spring AI Alibaba（DashScope / StateGraph） |
| 会话库 | MySQL + MyBatis-Plus |
| 向量库 | PostgreSQL + pgvector（可降级内存关键词） |
| 缓存 / Memory | Redis（可降级内存） |
| 前端 | Vite 5 + React 18 + TypeScript |
| MCP | Spring AI MCP Client → 智谱 SSE（可选） |

---

## 环境要求

- **JDK 21**、Maven 3.9+；前端需 **Node 20+** / npm  
- 本地中间件：MySQL、Redis；RAG 用 PostgreSQL + pgvector（可按配置降级）  
- 云服务：DashScope API Key；可选智谱 Key（MCP）  

单元测试使用 `test` profile，**不强制**本机已启动全部中间件。

---

## 快速开始

### 1. 准备本地密钥（不要提交到 Git）

```bash
cp dream-ai-app/application-local.yml.example dream-ai-app/application-local.yml
# 编辑：数据库密码、DashScope Key 等
```

工作目录请能加载到该文件（建议在 `dream-ai-app` 下启动）。也可参考 [`.env.example`](.env.example) 导出环境变量（Boot **不会**自动读 `.env`）。

### 2. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS dream_ai_lab
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 编译与测试

```bash
mvn -q -pl dream-ai-app -am test
```

### 4. 启动后端

```bash
cd dream-ai-app
mvn spring-boot:run
```

默认端口 **8090**。健康检查：`GET http://127.0.0.1:8090/actuator/health`。

### 5. 启动配套前端

```bash
cd dream-ai-web
npm install
npm run dev
```

打开 `http://127.0.0.1:5173`。Vite 将 `/api`、`/actuator` 代理到 8090。详见 [`dream-ai-web/README.md`](dream-ai-web/README.md)。

---

## 演示路径（约 10 分钟）

同一浏览器会话（或固定一个 `sessionId`）按顺序点一遍：

1. **闲聊 / 工具** — `agentId=chat`，问「现在几点了」→ 看工具执行与 `executedTools`。  
2. **Graph 知识** — `agentId=graph`，问「从知识库检索 AgentGateway」→ SSE 出现 `route=knowledge`；有命中则回答含 `[1]`，无命中则拒答「资料不足」。  
3. **Graph 审查** — 问「请审查这段代码有没有风险点」→ `route=review`。  
4. **HITL** — 配置 `require-approval` 的工具，清空审批头再问需联网的问题 → `blockedTools`；填上 `X-Dream-Tool-Approvals` 再试。  
5. **评测** — 前端 Eval 页点「POST /api/eval/run」，或 `curl -X POST http://127.0.0.1:8090/api/eval/run`。  
6. **观测** — 对照 `/api/observe` 或前端 Observe：route / model / blocked / executed / failed。

---

## 配置说明

| 文件 | 用途 |
|------|------|
| [`dream-ai-app/src/main/resources/application.yml`](dream-ai-app/src/main/resources/application.yml) | 主配置 |
| `dream-ai-app/application-local.yml` | 本地私密覆盖（gitignore） |
| [`dream-ai-web/vite.config.ts`](dream-ai-web/vite.config.ts) | 前端开发代理 |
| [`.env.example`](.env.example) | 环境变量清单 |

### 常用环境变量 / 配置

| 项 | 含义 |
|------|------|
| `AI_DASHSCOPE_API_KEY` | 通义 Key |
| `dream.tools.allowlist` / `require-approval` / `hitl-mode` | 工具白名单与 HITL |
| `dream.tools.max-retries` | 工具执行失败额外重试次数（默认 2） |
| `DREAM_MCP_CLIENT_ENABLED` / `ZHIPU_API_KEY` | 可选 MCP Client |

DashScope 使用原生 `https://dashscope.aliyuncs.com`；部分模型需 `multi-model: true`。

---

## HTTP API

### `POST /api/agent/invoke`

```json
{
  "agentId": "graph",
  "sessionId": "demo-1",
  "input": "从知识库检索 AgentGateway"
}
```

可选头：`X-Dream-Tool-Approvals: web_search_prime,datetime_offset`。

响应要点：`output`、`traceId`、`durationMs`、`modelCalls`、`toolCalls`、`route`、`model`、`blockedTools` / `executedTools` / `failedTools`、`retrieveHits` / `retrieveHitSummaries`。

### `POST /api/agent/invoke/stream`

SSE 事件：`retrieve`、`route`、`model`、`tool_start` / `tool_blocked` / `tool_executed` / `tool_failed`、`delta`、`done`、`error`。

### `GET /api/observe?limit=20`

最近若干轮观测（进程内环缓冲，重启清空）。

### `GET /api/eval/cases` · `POST /api/eval/run`

列出 / 执行 classpath golden 用例。

### Actuator

`GET /actuator/health` 等（以暴露配置为准）。

---

## 能力说明

### 1. 工具循环与控制面

- `ToolCallingLoop`：限步；策略拒执 → `blockedTools`（不重试）；`tool error:` → 按 `max-retries` 重试，耗尽 → `failedTools`；未知工具不重试。  
- `GuardedToolPort`：白名单 / HITL；广告给模型的列表可与执行策略分离。

### 2. Graph

- 主图只做意图路由；knowledge 嵌入 `KnowledgeSubGraph`（CompiledGraph）。  
- 检索仍由 Gateway 注入；子图负责门禁与引用补全。  
- 与 `chat` 的差别：graph 有叶级人设与路由，chat 是单 Agent 工具循环。

### 3. 会话与 Memory

| | ConversationPort | MemoryPort |
|--|------------------|------------|
| 存什么 | 多轮原文 | 可压缩长期记忆 |
| 默认实现 | MySQL | Redis（可降级） |
| 进模型 | history | memoryNotes → system |

超长记忆可走异步摘要（事件驱动），不阻塞 invoke。

### 4. RAG

- `RetrievePort` + 分阶段管线（可配 fusion / stages）。  
- 知识类意图才检索；命中摘要进观测与 SSE `retrieve`。

### 5. 观测与评测

- 观测契约面向排障（拒执 / 失败 / 路由），不是运营 Admin。  
- 评测为硬断言 golden，不另开 LLM-as-judge。

### 6. MCP Client（可选）

开关打开并配置智谱 Key 后，远端工具进入同一 `ToolPort`。确认方式：启动日志含 MCP 工具名，且调用时出现 `tool executed`。

---

## 测试

```bash
mvn -pl dream-ai-app -am test -Dsurefire.failIfNoSpecifiedTests=false

cd dream-ai-web && npm run build
```

测试配置见 `dream-ai-app/src/test/resources/application-test.yml`（stub ChatPort，不强制真中间件）。

---

## 项目结构

```text
dream-ai/
├── dream-ai-bom/
├── dream-ai-kernel/       # Port & SPI
├── dream-ai-agents/       # ChatAgent / GraphAgent
├── dream-ai-knowledge/    # RAG 实现
├── dream-ai-app/          # Boot：gateway / graph / llm / web …
├── dream-ai-web/          # 配套前端
├── docs/assets/
└── README.md
```

---

## 设计原则

1. **先跑通主路径，再谈拆分**  
2. **用 Port 挡变化**（契约不是 HTTP 端口）  
3. **HTTP 入口收敛** — 业务扩展走 Agent，不到处加 Controller  
4. **少造空架子** — 不为「看起来像平台」预拆进程  
5. **密钥不进仓库**

---

## 演进与维护

- 会继续修问题、跟进依赖与模型侧必要变更。  
- 扩展仍守模块化单体边界；产品级 Admin / 多租户不在当前范围。  
- 更新渠道：GitHub；说明同步公众号与 [dream-saas.com](https://www.dream-saas.com/)。

---

## 安全提示

- 勿提交真实密码、API Key、内网地址。  
- 使用 `application-local.yml` 或环境变量；模板见 example / `.env.example`。  
- 若密钥曾进历史，公开前请先轮换。

---

## 相关链接

| | |
|--|--|
| 产品与演示 | [https://www.dream-saas.com/](https://www.dream-saas.com/) |
| 微信公众号 | 扫下方二维码关注（Dream AI/SaaS） |

<p align="center">
  <img src="docs/assets/wechat-mp-qrcode.jpg" alt="微信公众号二维码" width="220" />
</p>

欢迎 Issue / Discussion。

---

## License

本仓库采用 [Apache License 2.0](LICENSE)。
