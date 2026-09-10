package com.zhu.ai.agent.spi;

import com.zhu.ai.kernel.agent.AgentHandler;

/**
 * 业务 Agent 的 SPI 别名，语义等同 {@link AgentHandler}。
 * 新 Agent 直接实现 {@link AgentHandler} 即可，不必再实现本接口。
 */
public interface AgentDescriptor extends AgentHandler {
}
