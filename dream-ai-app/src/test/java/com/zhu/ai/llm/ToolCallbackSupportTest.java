package com.zhu.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

class ToolCallbackSupportTest {

    @Test
    void mergesFunctionAndMethodCallbacks() {
        ToolCallback ping = FunctionToolCallback.builder("ping", () -> "pong")
                .description("ping")
                .build();
        var provider = MethodToolCallbackProvider.builder()
                .toolObjects(new LocalMethodTools())
                .build();

        List<ToolCallback> merged = ToolCallbackSupport.merge(List.of(ping), List.of(provider));
        List<String> names = merged.stream().map(cb -> cb.getToolDefinition().name()).toList();
        assertTrue(names.contains("ping"));
        assertTrue(names.contains("text_stats"));
        assertTrue(ToolCallbackSupport.summarize(merged).stream()
                .anyMatch(s -> s.startsWith("text_stats/MethodToolCallback")));
    }

    @Test
    void duplicateAcrossProviderFailsFast() {
        ToolCallback clash = FunctionToolCallback.builder("text_stats", () -> "x")
                .description("clash")
                .build();
        var provider = MethodToolCallbackProvider.builder()
                .toolObjects(new LocalMethodTools())
                .build();
        assertThrows(
                IllegalStateException.class,
                () -> ToolCallbackSupport.merge(List.of(clash), List.of(provider)));
    }

    @Test
    void methodToolExecutableViaPort() {
        var provider = MethodToolCallbackProvider.builder()
                .toolObjects(new LocalMethodTools())
                .build();
        ToolCallbackPort port = new ToolCallbackPort(ToolCallbackSupport.fromProvider(provider));
        String out = port.execute("text_stats", "{\"text\":\"你好ABC\"}");
        assertTrue(out.contains("\"charCount\"") || out.contains("charCount"));
        assertTrue(out.contains("true") || out.contains("hasCjk"));
        assertFalse(out.startsWith("tool error:"));
    }
}
