package com.atguigu.guliai.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmapMcpService {

    @Autowired
    private List<McpSyncClient> mcpSyncClients;

    @Autowired
    private McpConnectionManager connectionManager;

    /**
     * 调用高德MCP工具 - 带重试机制
     */
    @Retryable(value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public Object callTool(String toolName, Map<String, Object> arguments) {
        try {
            log.info("调用高德MCP工具: {}, 参数: {}", toolName, arguments);

            // 检查客户端是否可用
            if (mcpSyncClients == null || mcpSyncClients.isEmpty()) {
                throw new RuntimeException("MCP客户端未初始化");
            }

            McpSyncClient mcpSyncClient = mcpSyncClients.get(0);

            // 记录使用时间
            connectionManager.recordUsage("amap-mcp-client");

            // 检查连接健康状态
            if (!connectionManager.isConnectionHealthy("amap-mcp-client")) {
                log.warn("MCP连接可能不健康，尝试继续调用...");
            }

            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, arguments);
            McpSchema.CallToolResult result = mcpSyncClient.callTool(request);

            log.info("高德MCP工具调用成功: {}", toolName);
            return extractContent(result);

        } catch (Exception e) {
            log.error("调用高德MCP工具失败: toolName={}, error={}", toolName, e.getMessage(), e);
            throw new RuntimeException("调用高德MCP工具失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取可用工具列表 - 带重试机制
     */
    @Retryable(value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public McpSchema.ListToolsResult getAvailableTools() {
        try {
            if (mcpSyncClients == null || mcpSyncClients.isEmpty()) {
                throw new RuntimeException("MCP客户端未初始化");
            }

            McpSyncClient mcpSyncClient = mcpSyncClients.get(0);
            McpSchema.ListToolsResult result = mcpSyncClient.listTools();

            // 标记连接健康
            connectionManager.recordUsage("amap-mcp-client");

            return result;
        } catch (Exception e) {
            log.error("获取高德MCP工具列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取高德MCP工具列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取工具调用结果内容
     */
    private String extractContent(McpSchema.CallToolResult result) {
        StringBuilder content = new StringBuilder();
        if (result.content() != null) {
            result.content().forEach(item -> {
                if (item instanceof McpSchema.TextContent) {
                    content.append(((McpSchema.TextContent) item).text());
                }
            });
        }
        return content.toString();
    }
}