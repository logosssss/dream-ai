package com.zhu.ai.tool;

import java.util.List;
import java.util.Objects;
import org.springframework.ai.tool.ToolCallback;

/**
 * 按 {@link ConfigurableToolPolicy#visibleToModel} 筛出声明给模型的工具 schema。
 * 当前白名单只拦执行，不在此隐藏工具，避免模型侧直接 no tool call、看不到拦截过程。
 */
public final class ModelVisibleTools {

    private ModelVisibleTools() {}

    public static List<ToolCallback> filter(List<ToolCallback> callbacks, ConfigurableToolPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (callbacks == null || callbacks.isEmpty()) {
            return List.of();
        }
        return callbacks.stream()
                .filter(cb -> policy.visibleToModel(cb.getToolDefinition().name()))
                .toList();
    }
}
