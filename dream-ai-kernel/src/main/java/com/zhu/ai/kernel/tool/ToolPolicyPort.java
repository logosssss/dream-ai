package com.zhu.ai.kernel.tool;

/**
 * 工具执行策略判定：白名单 / HITL 审批。
 * 实现在 app；Agent 与工具循环只看 {@link ToolPort}。
 */
public interface ToolPolicyPort {

    ToolPolicyDecision decide(String toolName);
}
