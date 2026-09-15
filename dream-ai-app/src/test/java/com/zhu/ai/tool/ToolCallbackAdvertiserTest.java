package com.zhu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

class ToolCallbackAdvertiserTest {

    @Test
    void filtersByAllowlist() {
        List<ToolCallback> all = List.of(
                FunctionToolCallback.builder("a", () -> "a").description("a").build(),
                FunctionToolCallback.builder("b", () -> "b").description("b").build());
        var policy = new ConfigurableToolPolicy(List.of("b"), List.of(), ConfigurableToolPolicy.HitlMode.OFF);
        List<ToolCallback> visible = ToolCallbackAdvertiser.filter(all, policy);
        assertEquals(1, visible.size());
        assertEquals("b", visible.getFirst().getToolDefinition().name());
    }
}
