package com.zhu.ai.tool;

import java.util.List;
import java.util.Objects;
import org.springframework.ai.tool.ToolCallback;

/**
 * 按 {@link ConfigurableToolPolicy#visibleToModel} 筛出声明给模型的工具 schema。
 * 白名单外的工具不进入 ChatModel options，模型看不见也就不会去调。
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
