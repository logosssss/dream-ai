package com.zhu.ai.kernel.graph;

/**
 * {@link GraphPort#run} 出参。
 *
 * @param route  业务叶名，如 {@code chat} / {@code knowledge} / {@code review}
 * @param output 叶节点模型原文（不含 HTTP 层 {@code [route=…]} 前缀）
 */
public record GraphRunResult(String route, String output) {

    public GraphRunResult {
        route = route == null ? "" : route;
        output = output == null ? "" : output;
    }
}
