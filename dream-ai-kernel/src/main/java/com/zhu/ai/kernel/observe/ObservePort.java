package com.zhu.ai.kernel.observe;

import java.util.List;

/**
 * 运行时观测端口。Gateway 开闭跨度；LLM/工具循环在同线程上报次数。
 * app 接到内存环缓冲 + 薄 {@code /api/observe}；不要在 8090 复活独立 Admin 控制面。
 */
public interface ObservePort {

    /** 开启当前线程的 invoke 跨度，返回 traceId。 */
    String begin(String sessionId, String agentId);

    void markModelCall();

    void markToolCalls(int count);

    /** 结束跨度并入库；无 begin 时返回空观测。 */
    InvokeObservation complete(boolean success, String errorMessage);

    List<InvokeObservation> recent(int limit);
}
