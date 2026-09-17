# Dream-AI Web

开源配套前端：对接本仓 `dream-ai-app`（默认 **8090**），覆盖对话、SSE、HITL、观测与评测。

## 能力

| 能力 | 接口 |
|------|------|
| 同步对话 | `POST /api/agent/invoke` |
| 流式对话 | `POST /api/agent/invoke/stream`（SSE） |
| HITL | 请求头 `X-Dream-Tool-Approvals` |
| 观测 | `GET /api/observe` |
| 评测 | `GET /api/eval/cases`、`POST /api/eval/run` |
| 健康 | `GET /actuator/health` |

开发时由 Vite 把 `/api`、`/actuator` 代理到 `http://127.0.0.1:8090`。

## 启动

1. 先启动后端（见仓库根 README，端口 8090）。
2. 前端：

```bash
cd dream-ai-web
npm install
npm run dev
```

浏览器打开控制台打印的地址（默认 `http://127.0.0.1:5173`）。

## 演示建议

1. `agentId=graph`，输入「从知识库检索 AgentGateway」→ 看 SSE `route` / `retrieve` / `delta`。
2. 清空审批头再触发需 HITL 的工具 → Observe 里出现 `blockedTools`。
3. 侧栏 Eval → `POST /api/eval/run` 跑 golden。

## 构建

```bash
npm run build
npm run preview
```

预览若直连 8090，后端已放行 `localhost` / `127.0.0.1` 的 CORS（见 `CorsConfig`）。
