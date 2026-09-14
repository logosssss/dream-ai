package com.zhu.ai.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.tool.ToolCallback;

class ZhipuMcpClientBridgeTest {

    @Test
    void requireTools_keepsAllInOrder() {
        McpSchema.Tool other = tool("other");
        McpSchema.Tool search = tool(ZhipuMcpClientBridge.PREFERRED_TOOL);
        assertEquals(List.of(other, search), ZhipuMcpClientBridge.requireTools(List.of(other, search)));
    }

    @Test
    void requireTools_empty_throws() {
        assertThrows(IllegalStateException.class, () -> ZhipuMcpClientBridge.requireTools(List.of()));
        assertThrows(IllegalStateException.class, () -> ZhipuMcpClientBridge.requireTools(null));
    }

    @Test
    void requireTools_duplicateName_throws() {
        assertThrows(
                IllegalStateException.class,
                () -> ZhipuMcpClientBridge.requireTools(List.of(tool("dup"), tool("dup"))));
    }

    @Test
    void modeFrom_defaultsAndParses() {
        assertEquals(ZhipuMcpClientBridge.Mode.SYNC, ZhipuMcpClientBridge.Mode.from(null));
        assertEquals(ZhipuMcpClientBridge.Mode.SYNC, ZhipuMcpClientBridge.Mode.from("sync"));
        assertEquals(ZhipuMcpClientBridge.Mode.ASYNC, ZhipuMcpClientBridge.Mode.from("ASYNC"));
        assertThrows(IllegalArgumentException.class, () -> ZhipuMcpClientBridge.Mode.from("sse"));
    }

    /** 有 Key 时真连智谱；无 Key 跳过。 */
    @Test
    @EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
    void connect_listsAllTools() {
        try (ZhipuMcpClientBridge bridge = ZhipuMcpClientBridge.connect(System.getenv("ZHIPU_API_KEY"))) {
            assertEquals(ZhipuMcpClientBridge.Mode.SYNC, bridge.mode());
            assertContainsPreferred(bridge.toolCallbacks(), "SyncMcpToolCallback");
            assertEquals(bridge.toolCallbacks().size(), bridge.getToolCallbacks().length);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
    void connectAsync_listsAllTools() {
        try (ZhipuMcpClientBridge bridge = ZhipuMcpClientBridge.connect(
                System.getenv("ZHIPU_API_KEY"), ZhipuMcpClientBridge.Mode.ASYNC)) {
            assertEquals(ZhipuMcpClientBridge.Mode.ASYNC, bridge.mode());
            assertContainsPreferred(bridge.toolCallbacks(), "AsyncMcpToolCallback");
        }
    }

    private static void assertContainsPreferred(List<ToolCallback> callbacks, String callbackType) {
        assertTrue(callbacks.size() >= 1);
        List<String> names = callbacks.stream().map(cb -> cb.getToolDefinition().name()).toList();
        assertTrue(names.contains(ZhipuMcpClientBridge.PREFERRED_TOOL), () -> "tools=" + names);
        assertTrue(callbacks.stream().allMatch(cb -> callbackType.equals(cb.getClass().getSimpleName())));
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
