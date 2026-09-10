package com.zhu.ai.kernel.tool;

/**
 * 工具执行端口。实现与 {@code ToolCallback} 装配在 app；
 * 限步循环也在 app，Agent 不注入本端口。
 */
public interface ToolPort {

    /**
     * 执行命名工具。未知工具或执行失败应返回错误字符串给模型，不要抛到 HTTP。
     */
    String execute(String name, String argumentsJson);
}
