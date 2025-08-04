package com.atguigu.guliai.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolValidator {

    private final ToolCallbackProvider toolCallbackProvider;

    @EventListener(ApplicationReadyEvent.class)
    public void validateMcpTools() {
        try {
            log.info("=== 验证MCP工具连接 ===");

            // 获取所有可用的MCP工具
            ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();

            log.info("发现 {} 个MCP工具:", toolCallbacks.length);
            for (ToolCallback toolCallback : toolCallbacks) {
                // 使用正确的API获取工具信息
                String toolName = toolCallback.getToolDefinition().name();
                String toolDescription = toolCallback.getToolDefinition().description();
                log.info("工具名称: {}, 描述: {}", toolName, toolDescription);
            }

            log.info("=== MCP工具连接验证完成 ===");

        } catch (Exception e) {
            log.error("MCP工具验证失败: {}", e.getMessage(), e);
        }
    }
}