package com.atguigu.guliai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class McpClientConfig {

    @Bean
    public McpSyncClientCustomizer mcpSyncClientCustomizer() {
        return (serverConfigurationName, spec) -> {
            log.info("自定义MCP客户端配置: {}", serverConfigurationName);
            
            // 设置更长的请求超时时间
            spec.requestTimeout(Duration.ofSeconds(60));
            
            // 添加工具变化监听器，可用于调试
            spec.toolsChangeConsumer(tools -> {
                log.info("MCP工具列表发生变化，当前工具数量: {}", tools.size());
            });
            
            // 添加资源变化监听器
            spec.resourcesChangeConsumer(resources -> {
                log.info("MCP资源列表发生变化，当前资源数量: {}", resources.size());
            });
            
            // 添加日志监听器
            spec.loggingConsumer(logMessage -> {
                log.debug("MCP服务器日志: {}", logMessage);
            });
        };
    }
}