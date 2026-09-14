package com.zhu.ai.mcp;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.ai.mcp.AsyncMcpToolCallback;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * MCP <b>Client</b>：用 SSE 对接智谱开放平台上的 Web Search MCP Server。
 * <p>
 * 角色边界：
 * <ul>
 *   <li>Server 在智谱侧（{@code open.bigmodel.cn}），本仓<b>不</b>自建 MCP Server，也不把本进程当工具提供方。</li>
 *   <li>本类只当 Client：建连 → {@code initialize} → {@code list_tools} → 把声明的工具全部包装成
 *       {@link ToolCallback}，交给已有的同步 {@code ToolCallingLoop}。</li>
 * </ul>
 * 连接形态对齐现网 {@code zhipu-web-search}：HTTP SSE，路径见 {@link #SSE_PATH_PREFIX}，
 * API Key 拼在 query 的 {@code Authorization=} 上（智谱 MCP 现网约定，不是 Bearer header）。
 * <p>
 * 挂载策略：{@code list_tools} 返回的工具<b>全部</b>包装，不再只留第一个。
 * {@code prefixedToolName} 用工具原名，避免模型看到带前缀的别名。本类同时实现
 * {@link ToolCallbackProvider}，由 {@code ChatConfig} 与本地 {@code MethodToolCallbackProvider} 一并合并。
 * <p>
 * 两种模式（{@link Mode}）只影响底层 MCP SDK 与 Spring AI 回调类型，<b>对外仍是同步
 * {@link ToolCallback#call}</b>：
 * <ul>
 *   <li>{@link Mode#SYNC} — {@link McpSyncClient} + {@link SyncMcpToolCallback}</li>
 *   <li>{@link Mode#ASYNC} — {@link McpAsyncClient} + {@link AsyncMcpToolCallback}；
 *       {@code initialize}/{@code listTools} 在本类内 {@code block}，
 *       {@code AsyncMcpToolCallback.call()} 内部也会 block，以适配同步工具循环。</li>
 * </ul>
 * 生命周期：{@link #connect(String, Mode)} 建连；Spring 侧由 {@code McpConfig} 以
 * {@code destroyMethod=close} 注册；也可 {@code try-with-resources}。未开
 * {@code dream.mcp.client.enabled} 时本类不会被装配。
 */
public final class ZhipuMcpClientBridge implements AutoCloseable, ToolCallbackProvider {

    /**
     * 逻辑连接名，与现网 registry 里的 {@code zhipu-web-search} 对齐，便于对照日志 / 讲义。
     * 不是 MCP 协议字段，只用于本仓标识。
     */
    public static final String CONNECTION_ID = "zhipu-web-search";

    /** 智谱开放平台根地址；SSE 具体路径由 {@link #SSE_PATH_PREFIX} 拼接。 */
    static final String BASE_URL = "https://open.bigmodel.cn";

    /**
     * 现网 MCP SSE 入口前缀。完整 URL 为 {@code BASE_URL + SSE_PATH_PREFIX + apiKey}，
     * 例如 {@code /api/mcp/web_search_prime/sse?Authorization=<key>}。
     */
    static final String SSE_PATH_PREFIX = "/api/mcp/web_search_prime/sse?Authorization=";

    /**
     * 智谱 Web Search 在 list 中的标准名，供日志与真连单测断言「至少挂上了搜索」。
     * 不再作为唯一挂载项。
     */
    static final String PREFERRED_TOOL = "web_search";

    /**
     * 底层 MCP 客户端实现。由配置 {@code dream.mcp.client.mode} 解析，缺省 {@link #SYNC}。
     */
    public enum Mode {
        /** 同步 MCP Client + {@link SyncMcpToolCallback}。 */
        SYNC,
        /**
         * 异步 MCP Client + {@link AsyncMcpToolCallback}。
         * 建连与 {@code call()} 仍会在当前线程 block，不是把工具循环改成响应式。
         */
        ASYNC;

        /**
         * 解析配置字符串。{@code null}/空白 → {@link #SYNC}；大小写不敏感。
         *
         * @param raw {@code sync} / {@code async}，或空白
         * @return 对应模式
         * @throws IllegalArgumentException 无法识别的取值（例如 {@code sse}）
         */
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

    /** 真正的 MCP 连接；{@link #close()} 时关掉，避免 SSE 泄漏。 */
    private final AutoCloseable closer;

    /** {@code list_tools} 声明的远端工具，已全部包装成 Spring AI {@link ToolCallback}。 */
    private final List<ToolCallback> toolCallbacks;

    private final Mode mode;

    private ZhipuMcpClientBridge(AutoCloseable closer, List<ToolCallback> toolCallbacks, Mode mode) {
        this.closer = closer;
        this.toolCallbacks = List.copyOf(toolCallbacks);
        this.mode = mode;
    }

    /**
     * 用 API Key 按 {@link Mode#SYNC} 建连。
     *
     * @param apiKey 智谱 API Key（环境变量 {@code ZHIPU_API_KEY} 或配置 {@code dream.mcp.zhipu-api-key}）
     */
    public static ZhipuMcpClientBridge connect(String apiKey) {
        return connect(apiKey, Mode.SYNC);
    }

    /**
     * 建 SSE 传输并按 {@code mode} 完成 initialize / list_tools / 包装回调。
     * <p>
     * Key 会进 SSE URL 的 query，调用方须保证来自环境或本地配置，不要写进仓库。
     *
     * @param apiKey 智谱 API Key，不能为空
     * @param mode   {@link Mode#SYNC} 或 {@link Mode#ASYNC}
     * @return 已 initialize、且挂上 Server 声明的全部工具的桥
     * @throws IllegalArgumentException Key 为空，或 {@code mode} 为 {@code null}
     * @throws IllegalStateException    Server 未返回任何工具、工具无名、或工具重名
     */
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

    /**
     * 同步路径：{@link McpClient#sync} → initialize → listTools → 每个工具一个 {@link SyncMcpToolCallback}。
     * {@code closer} 直接用 {@link McpSyncClient}（其自身实现 {@link AutoCloseable}）。
     */
    private static ZhipuMcpClientBridge connectSync(HttpClientSseClientTransport transport) {
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .clientInfo(new McpSchema.Implementation("dream-ai-lab-client", "0.1.0"))
                .build();
        client.initialize();
        List<ToolCallback> callbacks = wrapAll(
                requireTools(client.listTools().tools()),
                tool -> SyncMcpToolCallback.builder()
                        .mcpClient(client)
                        .tool(tool)
                        .prefixedToolName(tool.name())
                        .build());
        return new ZhipuMcpClientBridge(client, callbacks, Mode.SYNC);
    }

    /**
     * 异步路径：{@link McpClient#async}；initialize / listTools 在此 {@code block} 最多 60s。
     * {@code closer} 用 {@code client::close}：{@link McpAsyncClient} 的关闭签名与
     * {@link AutoCloseable} 不完全一致，不能像 sync 那样直接把 client 塞进字段。
     */
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
        List<ToolCallback> callbacks = wrapAll(
                requireTools(listed.tools()),
                tool -> AsyncMcpToolCallback.builder()
                        .mcpClient(client)
                        .tool(tool)
                        .prefixedToolName(tool.name())
                        .build());
        return new ZhipuMcpClientBridge(client::close, callbacks, Mode.ASYNC);
    }

    /**
     * 校验 {@code list_tools} 结果：非空、每项有名、名不重复；保持 Server 返回顺序。
     * <p>
     * 包内可见，供单测覆盖，不走真 SSE。
     *
     * @param tools {@code list_tools} 结果；{@code null} 或空视为失败
     * @return 原列表的不可变副本
     * @throws IllegalStateException 列表为空、工具无名、或出现重名
     */
    static List<McpSchema.Tool> requireTools(List<McpSchema.Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            throw new IllegalStateException("智谱 MCP Server returned no tools");
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (McpSchema.Tool tool : tools) {
            if (tool == null || tool.name() == null || tool.name().isBlank()) {
                throw new IllegalStateException("智谱 MCP Server returned a tool without name");
            }
            if (!names.add(tool.name())) {
                throw new IllegalStateException("duplicate MCP tool name: " + tool.name());
            }
        }
        return List.copyOf(tools);
    }

    private static List<ToolCallback> wrapAll(
            List<McpSchema.Tool> tools, Function<McpSchema.Tool, ToolCallback> wrapper) {
        return tools.stream().map(wrapper).toList();
    }

    /**
     * 给 {@code ChatConfig} / {@code ToolPort} 注册的远端工具回调（Server 声明的全部）。
     * 模型侧看到的名字即各 {@link ToolCallback#getToolDefinition()} 的 name（通常含 {@code web_search}）。
     */
    public List<ToolCallback> toolCallbacks() {
        return toolCallbacks;
    }

    /** 供 {@code ChatConfig} 的 {@code List<ToolCallbackProvider>} 合并。 */
    @Override
    public ToolCallback[] getToolCallbacks() {
        return toolCallbacks.toArray(ToolCallback[]::new);
    }

    /** 本次建连实际使用的模式，供启动日志对照配置。 */
    public Mode mode() {
        return mode;
    }

    /**
     * 关闭底层 MCP / SSE。失败包装为 {@link IllegalStateException}，避免 checked exception 泄漏到 Spring destroy。
     */
    @Override
    public void close() {
        try {
            closer.close();
        } catch (Exception ex) {
            throw new IllegalStateException("close MCP client failed", ex);
        }
    }
}
