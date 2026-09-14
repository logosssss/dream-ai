package com.zhu.ai.llm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * 汇总各类 {@link ToolCallback} 贡献路径：直接 Bean、以及 {@link ToolCallbackProvider}
 *（如 {@code MethodToolCallbackProvider}）。同名工具 fail-fast。
 */
public final class ToolCallbackSupport {

    private ToolCallbackSupport() {}

    public static List<ToolCallback> merge(
            List<ToolCallback> direct, List<ToolCallbackProvider> providers) {
        Map<String, ToolCallback> byName = new LinkedHashMap<>();
        if (direct != null) {
            for (ToolCallback callback : direct) {
                put(byName, callback);
            }
        }
        if (providers != null) {
            for (ToolCallbackProvider provider : providers) {
                if (provider == null) {
                    continue;
                }
                ToolCallback[] fromProvider = provider.getToolCallbacks();
                if (fromProvider == null) {
                    continue;
                }
                for (ToolCallback callback : fromProvider) {
                    put(byName, callback);
                }
            }
        }
        return List.copyOf(byName.values());
    }

    public static List<String> summarize(List<ToolCallback> callbacks) {
        List<String> lines = new ArrayList<>();
        for (ToolCallback callback : callbacks) {
            lines.add(callback.getToolDefinition().name()
                    + "/"
                    + callback.getClass().getSimpleName());
        }
        return lines;
    }

    private static void put(Map<String, ToolCallback> byName, ToolCallback callback) {
        Objects.requireNonNull(callback, "callback");
        String name = callback.getToolDefinition().name();
        ToolCallback previous = byName.put(name, callback);
        if (previous != null) {
            throw new IllegalStateException(
                    "duplicate tool name '"
                            + name
                            + "' ("
                            + previous.getClass().getSimpleName()
                            + " vs "
                            + callback.getClass().getSimpleName()
                            + ")");
        }
    }

    /** 测试辅助：展开单个 Provider。 */
    public static List<ToolCallback> fromProvider(ToolCallbackProvider provider) {
        return merge(List.of(), List.of(provider));
    }

    public static List<ToolCallback> of(ToolCallback... callbacks) {
        return merge(Arrays.asList(callbacks), List.of());
    }
}
