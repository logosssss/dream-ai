package com.zhu.ai.kernel.observe;

import com.zhu.ai.kernel.knowledge.RetrieveHitSummary;
import java.util.List;

/**
 * 运行时观测能力契约（hexagonal Port，不是 HTTP/TCP 端口）。
 * Gateway 开闭跨度；LLM/工具循环 / Graph 在同线程上报。
 * app 接到内存环缓冲 + 薄 {@code /api/observe}；不要在 8090 复活独立 Admin 控制面。
 */
public interface ObservePort {

    /** 开启当前线程的 invoke 跨度，返回 traceId。 */
    String begin(String sessionId, String agentId);

    void markModelCall();

    /** 仅增加执行次数（兼容旧调用）；优先用 {@link #markToolExecuted}。 */
    void markToolCalls(int count);

    /** 记录一次真正执行的工具（次数 +1，并记入 executedTools）。 */
    void markToolExecuted(String toolName);

    /** 记录 Graph Supervisor 选中的业务叶（chat / knowledge / review …）。 */
    void markRoute(String route);

    /** 记录本轮实际选用的聊天模型 id（按任务路由或适配器默认）。 */
    void markModel(String model);

    /** 记录被策略拦截的工具名（不计入 toolCalls / executedTools）。 */
    void markToolBlocked(String toolName);

    /** 记录本轮检索命中摘要（id/score/source）；无命中可不调。 */
    default void markRetrieveHits(List<RetrieveHitSummary> hits) {}

    /** 结束跨度并入库；无 begin 时返回空观测。 */
    InvokeObservation complete(boolean success, String errorMessage);

    List<InvokeObservation> recent(int limit);
}
