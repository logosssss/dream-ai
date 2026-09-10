package com.zhu.ai.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class ZhipuMcpClientBridgeTest {

    @Test
    void pickOne_prefersWebSearch() {
        McpSchema.Tool other = tool("other");
        McpSchema.Tool search = tool(ZhipuMcpClientBridge.PREFERRED_TOOL);
        assertEquals(search, ZhipuMcpClientBridge.pickOne(List.of(other, search)));
    }

    @Test
    void pickOne_fallsBackToFirst() {
        McpSchema.Tool first = tool("alpha");
        assertEquals(first, ZhipuMcpClientBridge.pickOne(List.of(first, tool("beta"))));
    }

    @Test
    void pickOne_empty_throws() {
        assertThrows(IllegalStateException.class, () -> ZhipuMcpClientBridge.pickOne(List.of()));
    }

    /** 有 Key 时真连智谱；无 Key 跳过。 */
    @Test
    @EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
    void connect_listsPreferredTool() {
        try (ZhipuMcpClientBridge bridge = ZhipuMcpClientBridge.connect(System.getenv("ZHIPU_API_KEY"))) {
            assertEquals(ZhipuMcpClientBridge.PREFERRED_TOOL, bridge.toolCallback().getToolDefinition().name());
        }
    }

    private static McpSchema.Tool tool(String name) {
        return McpSchema.Tool.builder()
                .name(name)
                .description(name)
                .inputSchema(
                        new McpSchema.JsonSchema(
                                "object", Map.of(), List.of(), null, null, null))
                .build();
    }
}
