package com.zhu.ai.kernel.graph;

/**
 * 图编排端口。实现可基于 Spring AI Alibaba Graph；kernel 不引用该库。
 * <p>
 * Gateway / Agent 只依赖本接口，不直接碰 {@code StateGraph}。
 */
public interface GraphPort {

    /**
     * 跑一次编排：内部做意图路由 + 业务叶，返回路由名与叶节点输出。
     */
    GraphRunResult run(GraphRunRequest request);
}
