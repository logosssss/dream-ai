package com.zhu.ai.kernel.graph;

/**
 * {@link GraphPort#run} 出参。
 *
 * @param route  业务叶名，如 {@code chat} / {@code knowledge} / {@code review}
 * @param output 叶节点模型原文（不含 HTTP 层 {@code [route=…]} 前缀）
 * @param model  本轮叶节点实际选用的聊天模型 id；未配置为空
 */
public record GraphRunResult(String route, String output, String model) {

    public GraphRunResult {
        route = route == null ? "" : route;
        output = output == null ? "" : output;
        model = model == null ? "" : model;
    }

    /** 兼容旧调用：无模型时为空串。 */
    public GraphRunResult(String route, String output) {
        this(route, output, "");
    }
}
