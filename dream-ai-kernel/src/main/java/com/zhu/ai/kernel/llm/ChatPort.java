package com.zhu.ai.kernel.llm;

/**
 * 对话模型端口（Hexagonal Port）。
 * <p>
 * agents 只依赖本接口；Spring AI {@code ChatClient} / DashScope 适配器放在 app。
 * 当前是同步补全；工具循环（限步）在 app 的 ChatPort 适配器里，不在 Agent 内。
 */
public interface ChatPort {

    String complete(ChatRequest request);
}
