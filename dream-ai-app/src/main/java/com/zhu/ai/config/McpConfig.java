package com.zhu.ai.config;

import com.zhu.ai.llm.ToolCallbackSupport;
import com.zhu.ai.mcp.ZhipuMcpClientBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * MCP Client 接线：本仓对接智谱（外部 Server），不自环本进程。
 * <p>
 * 对齐现网 8090 Client → {@code zhipu-web-search}；{@code list_tools} 声明的工具全部经
 * {@link org.springframework.ai.tool.ToolCallbackProvider} 进 {@link ChatConfig} 的合并列表。
 * {@code dream.mcp.client.mode=sync|async} 选择 {@code SyncMcpToolCallback} 或 {@code AsyncMcpToolCallback}。
 * 须同时 {@code dream.mcp.client.enabled=true} 且配置 {@code ZHIPU_API_KEY} / {@code dream.mcp.zhipu-api-key}。
 */
@Configuration
@ConditionalOnProperty(name = "dream.mcp.client.enabled", havingValue = "true")
public class McpConfig {

    private static final Logger log = LoggerFactory.getLogger(McpConfig.class);

    /**
     * 建连并作为 {@link org.springframework.ai.tool.ToolCallbackProvider} 暴露全部远端工具。
     * {@code ChatConfig} 注入 {@code List<ToolCallbackProvider>} 时会收到本 Bean。
     */
    @Bean(destroyMethod = "close")
    ZhipuMcpClientBridge zhipuMcpClientBridge(
            @Value("${dream.mcp.zhipu-api-key:${ZHIPU_API_KEY:}}") String apiKey,
            @Value("${dream.mcp.client.mode:sync}") String mode) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "dream.mcp.client.enabled=true but ZHIPU_API_KEY / dream.mcp.zhipu-api-key is blank");
        }
        ZhipuMcpClientBridge bridge =
                ZhipuMcpClientBridge.connect(apiKey, ZhipuMcpClientBridge.Mode.from(mode));
        log.info(
                "MCP Client connected connection={} mode={} tools={}",
                ZhipuMcpClientBridge.CONNECTION_ID,
                bridge.mode(),
                ToolCallbackSupport.summarize(bridge.toolCallbacks()));
        return bridge;
    }
}
