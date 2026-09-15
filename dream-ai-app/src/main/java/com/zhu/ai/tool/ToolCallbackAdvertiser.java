package com.zhu.ai.tool;

import java.util.List;
import java.util.Objects;
import org.springframework.ai.tool.ToolCallback;

/**
 * 按 {@link ConfigurableToolPolicy#advertise} 过滤广告给模型的工具列表。
 */
public final class ToolCallbackAdvertiser {

    private ToolCallbackAdvertiser() {}

    public static List<ToolCallback> filter(List<ToolCallback> callbacks, ConfigurableToolPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (callbacks == null || callbacks.isEmpty()) {
            return List.of();
        }
        return callbacks.stream()
                .filter(cb -> policy.advertise(cb.getToolDefinition().name()))
                .toList();
    }
}
