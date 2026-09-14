package com.zhu.ai.mcp;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.mcp.AsyncMcpToolCallback;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;

/**
 * MCP <b>Client</b>：SSE 对接智谱开放平台（对齐现网 {@code zhipu-web-search}）。
 * <p>
 * Server 在智谱侧；本仓只当 Client，把远端工具收成 {@link ToolCallback} 进已有工具循环。
 * 支持 {@link SyncMcpToolCallback} 与 {@link AsyncMcpToolCallback}（后者 {@code call()} 内仍会 block，
 * 以适配同步 {@code ToolCallingLoop}）。课表约束：最多挂 1 个 Tool（优先 {@code web_search}）。
 */
public final class ZhipuMcpClientBridge implements AutoCloseable {

    /** 与现网 registry 连接名一致，便于对照讲。 */
    public static final String CONNECTION_ID = "zhipu-web-search";

    static final String BASE_URL = "https://open.bigmodel.cn";

    /** 现网名册：{@code /api/mcp/web_search_prime/sse?Authorization=} */
    static final String SSE_PATH_PREFIX = "/api/mcp/web_search_prime/sse?Authorization=";

    /** 优先挂载的工具名；没有则退回 list 里第一个。 */
    static final String PREFERRED_TOOL = "web_search";

    public enum Mode {
        SYNC,
        ASYNC;

        public static Mode from(String raw) {
            if (raw == null || raw.isBlank()) {
                return SYNC;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "async" -> ASYNC;
                case "sync" -> SYNC;
                default -> throw new IllegalArgumentException(
                        "dream.mcp.client.mode must be sync or async, got: " + raw);
            };
        }
    }

    private final AutoCloseable closer;
    private final ToolCallback toolCallback;
    private final Mode mode;

    private ZhipuMcpClientBridge(AutoCloseable closer, ToolCallback toolCallback, Mode mode) {
        this.closer = closer;
        this.toolCallback = toolCallback;
        this.mode = mode;
    }

    public static ZhipuMcpClientBridge connect(String apiKey) {
        return connect(apiKey, Mode.SYNC);
    }

    public static ZhipuMcpClientBridge connect(String apiKey, Mode mode) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("ZHIPU_API_KEY is blank");
        }
        Objects.requireNonNull(mode, "mode");
        var transport = HttpClientSseClientTransport.builder(BASE_URL)
                .sseEndpoint(SSE_PATH_PREFIX + apiKey.trim())
                .build();
        return mode == Mode.ASYNC ? connectAsync(transport) : connectSync(transport);
    }

    private static ZhipuMcpClientBridge connectSync(HttpClientSseClientTransport transport) {
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .clientInfo(new McpSchema.Implementation("dream-ai-lab-client", "0.1.0"))
                .build();
        client.initialize();
        McpSchema.Tool chosen = pickOne(client.listTools().tools());
        ToolCallback callback = SyncMcpToolCallback.builder()
                .mcpClient(client)
                .tool(chosen)
                .prefixedToolName(chosen.name())
                .build();
        return new ZhipuMcpClientBridge(client, callback, Mode.SYNC);
    }

    private static ZhipuMcpClientBridge connectAsync(HttpClientSseClientTransport transport) {
        McpAsyncClient client = McpClient.async(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .clientInfo(new McpSchema.Implementation("dream-ai-lab-client", "0.1.0"))
                .build();
        client.initialize().block(Duration.ofSeconds(60));
        McpSchema.ListToolsResult listed = client.listTools().block(Duration.ofSeconds(60));
        if (listed == null) {
            throw new IllegalStateException("智谱 MCP Server listTools returned null");
        }
        McpSchema.Tool chosen = pickOne(listed.tools());
        ToolCallback callback = AsyncMcpToolCallback.builder()
                .mcpClient(client)
                .tool(chosen)
                .prefixedToolName(chosen.name())
                .build();
        return new ZhipuMcpClientBridge(client::close, callback, Mode.ASYNC);
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

    public Mode mode() {
        return mode;
    }

    @Override
    public void close() {
        try {
            closer.close();
        } catch (Exception ex) {
            throw new IllegalStateException("close MCP client failed", ex);
        }
    }
}
