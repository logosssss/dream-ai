# Dream-AI

用 **Java 模块化单体** 实现的 Agent 运行时：单进程可跑，Maven 按能力切模块，用 Port 隔离厂商与中间件。当前已打通网关、工具循环、会话 / Memory、RAG、观测与 MCP Client。

本仓库会**持续维护并扩展**：在现有主路径上继续加能力、补文档与示例，逐步演进为更完整的 Agent 项目。欢迎 Star / Watch 跟进更新。

一条请求从 HTTP 进来，经过网关组装上下文，再进入 Agent 与模型 / 工具循环，最后带上观测信息返回——方便对照源码理解平台层边界，也适合作为后续能力扩展的底盘。

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
- 关心 **网关边界、工具循环、会话与 Memory、RAG、观测、MCP Client** 各自切在哪一层  
- 需要一个 **能 clone、能改、能演示** 的参考实现，而不是只有概念图  

本仓库面向学习与工程实践：默认不包含生产级多租户、计费、管理后台等完整 SaaS 产品面，但会在同一底盘上**持续迭代代码与功能**（更多 Agent、工具、检索与可观测能力等）。

---

## 它解决什么问题

做 Agent 时常见两种极端：

1. **一切塞进一个胖应用**：分层糊掉，换模型 / 换存储就牵全身。  
2. **过早拆成很多进程与空 jar**：还没跑通主路径，先陷入编排和依赖迷宫。

Dream-AI 选第三条路：**模块化单体**——一个 JVM 跑通主路径，用 Maven 模块和 Port 把「协议边界」立住。你可以先把请求链路讲清楚、跑起来，再按需要把 RAG、工具服务等拆出去。

---

## 能做什么

当前示例业务只做一个 Agent：`chat`。

| 能力 | 说明 |
|------|------|
| 对话网关 | HTTP 只进入 `AgentGateway`，再路由到 `AgentHandler` |
| 工具循环 | 显式限步（默认最多 5 步）；进程内工具 + 可选 MCP 工具 |
| 会话 | 按 `sessionId` 落库 MySQL（`conversation_turn`） |
| Memory | 经 `MemoryPort`（Redis 实现，缺省可降级为内存） |
| RAG | 进程内 pgvector 检索，结果写入 system prompt |
| 观测 | 每轮 `traceId`、耗时、`modelCalls` / `toolCalls`；`GET /api/observe` |
| MCP Client | 可选对接智谱 SSE（如 `web_search`）；本仓库不做「自环」MCP Server |

---

## 请求主路径

```text
HTTP POST /api/agent/invoke
        │
        ▼
 AgentInvokeController
        │  只调用 AgentGateway（不直接碰 ChatPort / Mapper）
        ▼
 DefaultAgentGateway
        │  开启观测 → 组装 history / memory / retrieve
        ▼
 ChatAgent
        │  编写 system，调用 ChatPort
        ▼
 DashScopeChatAdapter + ToolCallingLoop
        │  模型一步 → 若有 tool call 则经 ToolPort 执行 → 结果喂回
        │  直到无工具调用或达到步数上限
        ▼
 HTTP 响应（output + 观测摘要）
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
| `dream-ai-kernel` | Port / SPI：Gateway、Chat、Tool、Memory、Conversation、Retrieve、Observe 等 | 无 Web、无厂商 SDK |
| `dream-ai-knowledge` | 进程内 RAG（检索适配） | 仅 `kernel` |
| `dream-ai-agents` | `AgentHandler` 实现（当前为 `chat`） | 仅 `kernel` |
| `dream-ai-app` | 唯一可启动模块：装配、HTTP、Actuator、厂商适配 | kernel + knowledge + agents |

业务 Agent 用 **Java 包** 隔离，不为每个 Agent 单独拆 Maven 模块。

---

## 技术栈

| 用途 | 选型 |
|------|------|
| 语言 | Java 21 |
| 运行时 | Spring Boot 3.5.x + Spring Cloud 2025.x + Spring Cloud Alibaba |
| LLM | Spring AI 1.1.x + Spring AI Alibaba（DashScope） |
| 会话库 | MySQL + MyBatis-Plus |
| 向量库 | PostgreSQL + pgvector（Spring AI VectorStore） |
| 缓存 / Memory | Redis（+ Redisson） |
| 配置 / 注册 | Nacos Config + Discovery（单进程也可注册） |
| MCP | Spring AI MCP Client → 智谱 SSE（可选） |

---

## 环境要求

- **JDK 21**、Maven 3.9+  
- 本地中间件：
  - MySQL（建议库名 `dream_ai_lab`）
  - Redis（默认 `127.0.0.1:6379`）
  - Nacos（命名空间建议 `dream-ai`，可用环境变量覆盖）
  - PostgreSQL + pgvector（RAG；若暂时不做检索，可按配置降级）
- 云服务：
  - 阿里云 DashScope API Key（对话 / Embedding）
  - 可选：智谱 API Key（MCP 联网搜索）

单元测试使用 `test` profile，**不强制**本机已启动全部中间件。

---

## 快速开始

### 1. 准备本地密钥（不要提交到 Git）

```bash
cp dream-ai-app/application-local.yml.example dream-ai-app/application-local.yml
# 编辑该文件，填入数据库密码、DashScope Key、Nacos 等
```

也可参考仓库根目录 [`.env.example`](.env.example) 自行导出同名环境变量。注意：Spring Boot **默认不会**自动加载 `.env` 文件，需在 shell / IDE 中配置 Environment。

### 2. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS dream_ai_lab
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

会话表可在启动时由 `spring.sql.init` 按 [`schema.sql`](dream-ai-app/src/main/resources/schema.sql) 自动创建。

### 3. 编译与测试

```bash
mvn -q -pl dream-ai-app -am test
```

### 4. 启动

```bash
mvn -pl dream-ai-app spring-boot:run
```

默认端口 **8090**。

- 健康检查：`GET http://127.0.0.1:8090/actuator/health`  
- 启动日志中可看到已注册工具，例如：`ToolPort registered tools=[current_date_time, …]`  
- 若开启 MCP Client，还会看到：`MCP Client connected … tool=web_search`

---

## 配置说明

| 文件 | 用途 |
|------|------|
| [`dream-ai-app/src/main/resources/application.yml`](dream-ai-app/src/main/resources/application.yml) | 主配置（占位符 + 本地默认） |
| `dream-ai-app/application-local.yml` | 本地私密覆盖（gitignore，optional import） |
| [`nacos/dream-ai.yml`](nacos/dream-ai.yml) | Nacos 配置样例 |
| [`.env.example`](.env.example) | 环境变量清单 |

### 常用环境变量

| 变量 | 含义 | 说明 |
|------|------|------|
| `MYSQL_*` | 会话库 | 默认指向本机 `dream_ai_lab` |
| `REDIS_HOST` / `REDIS_PORT` | Memory | 默认 `127.0.0.1:6379` |
| `NACOS_ADDR` / `NACOS_NAMESPACE` | 配置与注册 | 默认 `127.0.0.1:8848` / `dream-ai` |
| `POSTGRES_*` | 向量库 | RAG 使用 |
| `AI_DASHSCOPE_API_KEY` | 通义 Key | 跑真实模型时必填 |
| `AI_DASHSCOPE_MODEL` | 聊天模型 | 如 `qwen3.8-27b`（部分模型需 `multi-model=true`） |
| `DREAM_MCP_CLIENT_ENABLED` | 是否启用智谱 MCP Client | 默认 `false` |
| `ZHIPU_API_KEY` | 智谱 Key | 开启 MCP 时必填 |

### DashScope 提示

- `base-url` 使用原生地址：`https://dashscope.aliyuncs.com`（不要误用 OpenAI compatible-mode 路径，否则容易 404）。  
- 部分多模态模型需要 `spring.ai.dashscope.chat.options.multi-model: true`；本仓库适配器会在请求选项里显式带上，避免被工具相关选项冲掉。

---

## HTTP API

### `POST /api/agent/invoke`

请求示例：

```json
{
  "agentId": "chat",
  "sessionId": "demo-1",
  "input": "你好，介绍一下你自己"
}
```

响应字段：

| 字段 | 含义 |
|------|------|
| `agentId` | 处理该请求的 Agent |
| `output` | 最终文本回复 |
| `traceId` | 本轮观测 ID |
| `durationMs` | 耗时（毫秒） |
| `modelCalls` | 模型调用次数 |
| `toolCalls` | 工具执行次数 |

```bash
curl -s http://127.0.0.1:8090/api/agent/invoke \
  -H "Content-Type: application/json" \
  -d "{\"agentId\":\"chat\",\"sessionId\":\"demo-1\",\"input\":\"现在几点了？\"}"
```

询问当前时间时，通常会调用本地工具 `current_date_time`（日志中可见 `tool executed name=current_date_time`）。

### `GET /api/observe?limit=20`

返回最近若干轮 invoke 的观测摘要（内存环缓冲，进程重启后清空）。

### Actuator

- `GET /actuator/health`  
- `GET /actuator/info`、`/actuator/metrics`（以暴露配置为准）

---

## 能力说明

### 1. 工具循环

- 实现类：`ToolCallingLoop`（位于 app）。  
- 停止条件：模型本步不再发起 tool call，或达到最大步数（默认 5）。  
- 内置工具：`current_date_time`（上海时区）。  
- 统一执行口：`ToolPort` ← 容器中注册的全部 `ToolCallback`。

### 2. 会话与 Memory

| | ConversationPort | MemoryPort |
|--|------------------|------------|
| 存什么 | 多轮 USER / ASSISTANT 原文 | 可压缩的长期记忆文本 |
| 默认实现 | MySQL `conversation_turn` | Redis（可降级内存） |
| 如何进模型 | Gateway 组装为 history | Gateway 组装为 memoryNotes，由 Agent 写入 system |

### 3. RAG

- 端口：`RetrievePort`（kernel）。  
- 实现：pgvector VectorStore（app 装配；向量库与 MySQL 使用不同数据源）。  
- Gateway 检索后交给 Agent，Agent 把片段写入 system 中的「参考资料」。

### 4. 观测

- 端口：`ObservePort`；默认实现为进程内环缓冲，并配合 MDC `traceId`。  
- invoke 响应中携带本轮摘要；也可用 `/api/observe` 查询。  
- 本仓库只提供轻量观测契约，不附带完整运营后台 UI。

### 5. MCP Client（可选）

- 角色：本仓库作为 **MCP Client**；工具由外部 MCP Server（示例为智谱联网搜索）提供。  
- 开关：`DREAM_MCP_CLIENT_ENABLED=true`，并配置 `ZHIPU_API_KEY`。  
- 示例策略：最多挂载 **1** 个远端工具（优先 `web_search`）→ 转为 `ToolCallback` → 进入同一 `ToolPort`。  
- 和进程内 `@Tool` / `FunctionToolCallback` 的关系：对模型而言最终都是可调用工具；差别在于贡献路径——本地直接注册，或经 MCP 的 `list_tools` / `call_tool`。

**如何确认走了 MCP：**

1. 启动日志出现 `MCP Client connected`，且 `ToolPort registered tools` 中包含 `web_search`。  
2. 使用新的 `sessionId`，明确要求联网，例如：「请用 web_search 搜索今天杭州天气」。  
3. 日志出现 `tool call(s) web_search` 与 `tool executed name=web_search`，响应里 `toolCalls > 0`。

说明：仅打开开关并不保证模型一定调工具；若 RAG 已命中或提问不够「时效 / 联网」，模型可能直接回答。

---

## 测试

```bash
mvn -pl dream-ai-app -am test -Dsurefire.failIfNoSpecifiedTests=false

# MCP 相关单测；若本机设置了 ZHIPU_API_KEY，可额外跑真连用例
mvn -pl dream-ai-app test -Dtest=ZhipuMcpClientBridgeTest
```

测试配置见 `dream-ai-app/src/test/resources/application-test.yml`：关闭 Nacos / Redis / 真实数据源与真模型，使用 stub `ChatPort` 验证网关与 HTTP。

---

## 项目结构（精简）

```text
dream-ai/
├── pom.xml
├── dream-ai-bom/
├── dream-ai-kernel/          # Port & SPI
├── dream-ai-agents/          # ChatAgent
├── dream-ai-knowledge/       # RAG
├── dream-ai-app/             # 唯一 Spring Boot 应用
│   └── src/main/java/com/zhu/ai/
│       ├── config/           # 装配接线
│       ├── gateway/
│       ├── llm/              # 模型适配、工具循环
│       ├── conversation/
│       ├── memory/
│       ├── knowledge/
│       ├── mcp/
│       ├── observe/
│       └── web/
├── nacos/                    # Nacos 配置样例
└── docs/assets/              # 公众号二维码等静态资源
```

---

## 设计原则

1. **先跑通主路径，再谈拆分** — 单进程验证网关 → Agent → 模型 / 工具 / 检索是否闭环。  
2. **用 Port 挡变化** — kernel 不依赖 Web 与厂商 SDK；换模型或换存储时改 app 适配即可。  
3. **HTTP 入口收敛** — 对外只暴露有限 API，业务扩展走 Agent，而不是到处加 Controller。  
4. **少造空架子** — 不为「看起来像平台」预先拆出用不到的进程和模块。  
5. **密钥不进仓库** — 本地覆盖文件与环境变量承载密码和 API Key。

---

## 演进与维护

- **会继续维护**：修复问题、跟进依赖与 Spring AI / 模型侧必要变更。  
- **会继续扩展**：在模块化单体边界内增加 Agent、工具、检索、观测、MCP 等能力，而不是停在当前这一条演示路径。  
- **更新渠道**：GitHub 仓库提交；系列说明同步到 [公众号](#相关链接) 与 [dream-saas.com](https://www.dream-saas.com/)。  
- 建议通过 Issue / Discussion 反馈需求与缺陷，便于排进后续迭代。

---

## 安全提示

- 请勿将真实密码、API Key、内网地址提交到 Git。  
- 使用 `application-local.yml` 或环境变量；模板见 `application-local.yml.example`、`.env.example`。  
- `.idea/`、`.env`、`application-local.yml` 已写入 [`.gitignore`](.gitignore)。  
- 若密钥曾出现在本地历史文件中，对外公开仓库前请先在云平台与数据库侧 **轮换**。

---

## 相关链接

| | |
|--|--|
| 产品与演示 | [https://www.dream-saas.com/](https://www.dream-saas.com/) |
| 微信公众号 | 扫下方二维码关注（Dream AI/SaaS），仓库更新与系列讲解会同步到公众号 |

<p align="center">
  <img src="docs/assets/wechat-mp-qrcode.jpg" alt="微信公众号二维码" width="220" />
</p>

欢迎 Issue / Discussion 交流实现细节。

---

## License

本仓库采用 [Apache License 2.0](LICENSE)。
