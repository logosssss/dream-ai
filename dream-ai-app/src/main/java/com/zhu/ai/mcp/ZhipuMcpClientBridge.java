package com.zhu.ai.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;

/**
 * MCP <b>Client</b>：SSE 对接智谱开放平台（对齐现网 {@code zhipu-web-search}）。
 * <p>
 * Server 在智谱侧；本仓只当 Client，把远端工具收成 {@link ToolCallback} 进已有工具循环。
 * 课表约束：最多挂 1 个 Tool（优先 {@code web_search}）。
 */
public final class ZhipuMcpClientBridge implements AutoCloseable {

    /** 与现网 registry 连接名一致，便于对照讲。 */
    public static final String CONNECTION_ID = "zhipu-web-search";

    static final String BASE_URL = "https://open.bigmodel.cn";

    /** 现网名册：{@code /api/mcp/web_search_prime/sse?Authorization=} */
    static final String SSE_PATH_PREFIX = "/api/mcp/web_search_prime/sse?Authorization=";

    /** 优先挂载的工具名；没有则退回 list 里第一个。 */
    static final String PREFERRED_TOOL = "web_search";

    private final McpSyncClient client;
    private final ToolCallback toolCallback;

    private ZhipuMcpClientBridge(McpSyncClient client, ToolCallback toolCallback) {
        this.client = client;
        this.toolCallback = toolCallback;
    }

    public static ZhipuMcpClientBridge connect(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("ZHIPU_API_KEY is blank");
        }
        var transport = HttpClientSseClientTransport.builder(BASE_URL)
                .sseEndpoint(SSE_PATH_PREFIX + apiKey.trim())
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .clientInfo(new McpSchema.Implementation("dream-ai-lab-client", "0.1.0"))
                .build();
        client.initialize();

        List<McpSchema.Tool> tools = client.listTools().tools();
        McpSchema.Tool chosen = pickOne(tools);
        ToolCallback callback = SyncMcpToolCallback.builder()
                .mcpClient(client)
                .tool(chosen)
                .prefixedToolName(chosen.name())
                .build();
        return new ZhipuMcpClientBridge(client, callback);
    }

    /** 可见性：单测覆盖选型逻辑。 */
    static McpSchema.Tool pickOne(List<McpSchema.Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            throw new IllegalStateException("智谱 MCP Server returned no tools");
        }
        return tools.stream()
                .filter(t -> PREFERRED_TOOL.equals(t.name()))
                .findFirst()
                .orElse(tools.getFirst());
    }

    public ToolCallback toolCallback() {
        return toolCallback;
    }

    @Override
    public void close() {
        client.close();
    }
}
