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

    @Test
    void executesParameterizedDatetimeOffsetTool() {
        ToolCallbackPort port = new ToolCallbackPort(List.of(
                FunctionToolCallback.builder(DatetimeOffsetTool.TOOL_NAME, DatetimeOffsetTool::execute)
                        .description("offset")
                        .inputType(DatetimeOffsetTool.Request.class)
                        .build()));
        String out = port.execute(
                "datetime_offset",
                "{\"baseDateTime\":\"2026-09-10 10:00:00\",\"zoneId\":\"Asia/Shanghai\",\"amount\":3,\"unit\":\"DAYS\"}");
        assertTrue(out.contains("2026-09-13 10:00:00"));
        assertTrue(out.contains("星期日"));
    }

    @Test
    void parameterizedToolBadArgsBecomeToolError() {
        ToolCallbackPort port = new ToolCallbackPort(List.of(
                FunctionToolCallback.builder(DatetimeOffsetTool.TOOL_NAME, DatetimeOffsetTool::execute)
                        .description("offset")
                        .inputType(DatetimeOffsetTool.Request.class)
                        .build()));
        String out = port.execute("datetime_offset", "{\"amount\":1,\"unit\":\"WEEKS\"}");
        assertTrue(out.startsWith("tool error:"));
        assertTrue(out.contains("unit"));
    }
}
