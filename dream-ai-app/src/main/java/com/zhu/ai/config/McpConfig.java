package com.zhu.ai.config;

import com.zhu.ai.mcp.ZhipuMcpClientBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * MCP Client 接线：本仓对接智谱（外部 Server），不自环本进程。
 * <p>
 * 对齐现网 8090 Client → {@code zhipu-web-search}；最多挂 1 个 Tool 进 {@link ChatConfig} 的工具列表。
 * 须同时 {@code dream.mcp.client.enabled=true} 且配置 {@code ZHIPU_API_KEY} / {@code dream.mcp.zhipu-api-key}。
 */
@Configuration
@ConditionalOnProperty(name = "dream.mcp.client.enabled", havingValue = "true")
public class McpConfig {

    private static final Logger log = LoggerFactory.getLogger(McpConfig.class);

    @Bean(destroyMethod = "close")
    ZhipuMcpClientBridge zhipuMcpClientBridge(
            @Value("${dream.mcp.zhipu-api-key:${ZHIPU_API_KEY:}}") String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "dream.mcp.client.enabled=true but ZHIPU_API_KEY / dream.mcp.zhipu-api-key is blank");
        }
        ZhipuMcpClientBridge bridge = ZhipuMcpClientBridge.connect(apiKey);
        log.info(
                "MCP Client connected connection={} tool={}",
                ZhipuMcpClientBridge.CONNECTION_ID,
                bridge.toolCallback().getToolDefinition().name());
        return bridge;
    }

    @Bean
    ToolCallback zhipuMcpToolCallback(ZhipuMcpClientBridge bridge) {
        return bridge.toolCallback();
    }
}
