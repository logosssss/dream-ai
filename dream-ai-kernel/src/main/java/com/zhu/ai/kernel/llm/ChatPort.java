package com.zhu.ai.kernel.llm;

/**
 * 对话模型端口（Hexagonal Port）。
 * <p>
 * agents 只依赖本接口；Spring AI {@code ChatClient} / DashScope 适配器放在 app。
 * 当前是同步补全；工具循环（限步）在 app 的 ChatPort 适配器里，不在 Agent 内。
 */
public interface ChatPort {

    String complete(ChatRequest request);

    /**
     * 流式补全：边生成边 {@link TokenSink#onDelta}，返回全文。
     * 默认实现走 {@link #complete} 后一次性推送，便于测试桩；生产适配器应走模型 stream。
     */
    default String stream(ChatRequest request, TokenSink sink) {
        String output = complete(request);
        if (sink != null && output != null && !output.isEmpty() && !sink.cancelled()) {
            sink.onDelta(output);
        }
        return output;
    }
}
