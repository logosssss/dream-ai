# Intent: Agent 开发岗下一阶段（已确认）

- **Outcome：** 按 Agent 开发岗技术栈优先级，在 Dream-AI 上先扩能力，再对着新能力深入学，形成可面试叙事
- **User：** 求职准备（本人）
- **Why now：** 课表主路径（模型/工具/Memory/RAG/观测/MCP）已学完，再堆同类 Tool 边际收益低
- **Success：** 能稳定讲清「单 Agent 工具循环 → 多 Agent/Graph 编排」差异，并有可跑代码 + 一条演示故事线
- **Constraint：** 仍守模块化单体 + Port 边界；不先造完整 SaaS 产品面
- **Out of scope：** 本阶段不优先公众号连载、不优先再加花哨本地 Tool、不上多租户/计费/Admin

## 技术栈推进顺序

1. Graph / 多 Agent 编排（完成；含 GraphPort + review 第三叶）
2. 流式 SSE + 取消/超时（完成）
3. HITL / 工具白名单等控制面（完成）
4. 评测与回归（完成）

## 下一刀候选（技术栈）

- observe 结构化（route / block）（完成）
- eval 工具类硬断言（完成：route / mustBlockTools / mustExecuteTools）
- Memory 摘要策略

Confirmed: 2026-09-15
Updated: 2026-09-16 GraphPort + review；observe；eval 硬断言
