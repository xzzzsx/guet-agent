package com.atguigu.guliai.controller;

import com.atguigu.guliai.mcp.AmapMcpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import io.modelcontextprotocol.spec.McpSchema;

@Slf4j
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpHealthController {
    
    private final AmapMcpService amapMcpService;
    
    @GetMapping("/health")
    public String health() {
        try {
            McpSchema.ListToolsResult tools = amapMcpService.getAvailableTools();
            return String.format("MCP连接正常，发现 %d 个工具", tools.tools().size());
        } catch (Exception e) {
            return "MCP连接失败: " + e.getMessage();
        }
    }
    
    @GetMapping("/tools")
    public McpSchema.ListToolsResult tools() {
        return amapMcpService.getAvailableTools();
    }
}