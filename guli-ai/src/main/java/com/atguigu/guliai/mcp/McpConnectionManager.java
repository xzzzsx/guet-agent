package com.atguigu.guliai.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpConnectionManager {
    
    @Autowired
    @Lazy
    private List<McpSyncClient> mcpSyncClients;
    
    private final Map<String, Instant> lastUsedTime = new ConcurrentHashMap<>();
    private final Map<String, Boolean> connectionHealth = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        log.info("初始化MCP连接管理器");
    }
    
    /**
     * 检查连接健康状态并重连
     */
    @Scheduled(fixedDelay = 30000) // 每30秒检查一次
    public void checkAndReconnect() {
        try {
            if (mcpSyncClients == null || mcpSyncClients.isEmpty()) {
                log.warn("MCP客户端未初始化");
                return;
            }
            
            McpSyncClient client = mcpSyncClients.get(0);
            String clientId = "amap-mcp-client";
            
            // 检查连接是否健康
            boolean isHealthy = checkConnectionHealth(client);
            connectionHealth.put(clientId, isHealthy);
            
            if (!isHealthy) {
                log.warn("MCP连接不健康，尝试重新初始化连接...");
                reconnectClient(client);
            }
            
        } catch (Exception e) {
            log.error("检查MCP连接健康状态时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 检查连接健康状态
     */
    private boolean checkConnectionHealth(McpSyncClient client) {
        try {
            // 尝试获取工具列表来检查连接是否正常
            McpSchema.ListToolsResult tools = client.listTools();
            log.debug("MCP连接健康检查成功，发现 {} 个工具", tools.tools().size());
            return true;
        } catch (Exception e) {
            log.debug("MCP连接健康检查失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 重新连接客户端
     */
    private void reconnectClient(McpSyncClient client) {
        try {
            // 这里我们无法直接重连，但可以标记连接状态
            // 实际的重连会在下一次调用时由Spring AI处理
            log.info("标记MCP连接需要重连");
            lastUsedTime.put("amap-mcp-client", Instant.now());
        } catch (Exception e) {
            log.error("重连MCP客户端时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 记录使用时间
     */
    public void recordUsage(String clientId) {
        lastUsedTime.put(clientId, Instant.now());
    }
    
    /**
     * 获取连接状态
     */
    public boolean isConnectionHealthy(String clientId) {
        return connectionHealth.getOrDefault(clientId, false);
    }
}