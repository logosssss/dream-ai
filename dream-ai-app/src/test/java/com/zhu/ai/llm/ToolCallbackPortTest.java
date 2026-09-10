package com.zhu.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.function.FunctionToolCallback;

class ToolCallbackPortTest {

    @Test
    void executesKnownTool() {
        ToolCallbackPort port = new ToolCallbackPort(List.of(
                FunctionToolCallback.builder("ping", () -> "pong").description("ping").build()));
        assertEquals("\"pong\"", port.execute("ping", "{}"));
    }

    @Test
    void unknownToolReturnsError() {
        ToolCallbackPort port = new ToolCallbackPort(List.of());
        assertEquals("unknown tool: nope", port.execute("nope", "{}"));
    }

    @Test
    void duplicateNameFailsFast() {
        var ping = FunctionToolCallback.builder("ping", () -> "pong").description("ping").build();
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> new ToolCallbackPort(List.of(ping, ping)));
        assertTrue(ex.getMessage().contains("duplicate tool name 'ping'"));
    }
}
