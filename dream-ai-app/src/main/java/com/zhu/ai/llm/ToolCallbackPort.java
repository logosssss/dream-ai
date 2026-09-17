package com.zhu.ai.llm;

import com.zhu.ai.kernel.tool.ToolPort;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.ai.tool.ToolCallback;

/**
 * 把 Spring AI {@link ToolCallback} 收成 {@link ToolPort}。未知工具返回错误串，不抛到 HTTP。
 * <p>
 * 失败前缀供 {@link ToolCallingLoop} 区分「可重试执行失败」与「未知工具（不重试）」。
 */
public final class ToolCallbackPort implements ToolPort {

    /** 底层回调抛错时回给模型的前缀；可重试。 */
    public static final String ERROR_PREFIX = "tool error:";

    /** 库存中无此工具名时的前缀；不重试。 */
    public static final String UNKNOWN_PREFIX = "unknown tool:";

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

    /** 执行期失败（网络/业务抛错等），与策略拒执、未知工具区分。 */
    public static boolean isExecutionFailure(String output) {
        return output != null && output.startsWith(ERROR_PREFIX);
    }

    /** 工具名不在库存；喂回模型但不重试。 */
    public static boolean isUnknownTool(String output) {
        return output != null && output.startsWith(UNKNOWN_PREFIX);
    }

    @Override
    public String execute(String name, String argumentsJson) {
        ToolCallback callback = tools.get(name);
        if (callback == null) {
            return UNKNOWN_PREFIX + " " + name;
        }
        String args = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
        try {
            return callback.call(args);
        } catch (RuntimeException ex) {
            String message = ex.getMessage();
            return ERROR_PREFIX + " " + (message != null ? message : ex.getClass().getSimpleName());
        }
    }
}
