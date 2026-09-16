package com.zhu.ai.kernel.llm;

/**
 * 流式补全与编排生命周期出口。HTTP SSE 适配器实现本接口；Agent / ChatPort 不依赖 Servlet。
 * <p>
 * 除文本增量外，还可上报 route / 工具起止，便于客户端与排障对齐 {@code /api/observe}。
 * 新增回调均有默认空实现，旧调用方不必改。
 */
public interface TokenSink {

    /** 一段模型增量文本；空串可忽略。 */
    void onDelta(String delta);

    /** Graph Supervisor 选定的业务叶（如 chat / knowledge / review）。 */
    default void onRoute(String route) {}

    /** 本轮实际选用的聊天模型 id。 */
    default void onModel(String model) {}

    /** 即将执行某工具（策略拦截前也会先报，便于对照后续 blocked）。 */
    default void onToolStart(String toolName) {}

    /** 工具被白名单 / HITL 拦截，未真正执行。 */
    default void onToolBlocked(String toolName) {}

    /** 工具执行成功（与观测 {@code executedTools} 对齐）。 */
    default void onToolExecuted(String toolName) {}

    /** 客户端断开或超时后为 true；ChatPort 应尽快停。 */
    default boolean cancelled() {
        return false;
    }
}
