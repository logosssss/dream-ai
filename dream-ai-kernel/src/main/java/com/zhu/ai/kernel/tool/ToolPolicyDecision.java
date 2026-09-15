package com.zhu.ai.kernel.tool;

/**
 * 对一次工具调用的策略结论。
 */
public enum ToolPolicyDecision {
    /** 允许执行。 */
    ALLOW,
    /** 不在白名单或不存在：拒执，把原因串回模型。 */
    DENY,
    /** 需人工审批且本轮未批准：拒执，提示需审批。 */
    NEED_APPROVAL
}
