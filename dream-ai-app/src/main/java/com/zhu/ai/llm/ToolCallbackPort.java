package com.zhu.ai.llm;

import com.zhu.ai.kernel.tool.ToolPort;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.ai.tool.ToolCallback;

/**
 * 把 Spring AI {@link ToolCallback} 收成 {@link ToolPort}。未知工具返回错误串，不抛到 HTTP。
 */
public final class ToolCallbackPort implements ToolPort {

    private final Map<String, ToolCallback> tools;

    public ToolCallbackPort(List<ToolCallback> callbacks) {
        this.tools = callbacks.stream()
                .collect(Collectors.toUnmodifiableMap(
                        cb -> cb.getToolDefinition().name(),
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "duplicate tool name '" + a.getToolDefinition().name() + "'");
                        }));
    }

    @Override
    public String execute(String name, String argumentsJson) {
        ToolCallback callback = tools.get(name);
        if (callback == null) {
            return "unknown tool: " + name;
        }
        String args = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
        try {
            return callback.call(args);
        } catch (RuntimeException ex) {
            String message = ex.getMessage();
            return "tool error: " + (message != null ? message : ex.getClass().getSimpleName());
        }
    }
}
