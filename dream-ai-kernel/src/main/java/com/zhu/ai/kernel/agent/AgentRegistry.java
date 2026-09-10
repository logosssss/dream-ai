package com.zhu.ai.kernel.agent;

import java.util.Optional;

/**
 * Agent 名册（Registry）。
 * <p>
 * 把进程内所有 {@link AgentHandler} 编成 {@code id → 实例}。实现在 app 装配，
 * kernel 不关心是扫描 Bean 还是手写列表。
 */
public interface AgentRegistry {

    Optional<AgentHandler> find(String agentId);
}
