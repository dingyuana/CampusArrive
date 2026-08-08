package com.campusarrive.ai.mcp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 工具 Spring 配置（AI-3.3）。
 *
 * <p>装配工具注册中心、POI 存储、日志存储、频率限制器与服务层，
 * 注册两个 MCP 工具：navigate_to_step + start_navigation。</p>
 */
@Configuration
public class McpToolConfig {

    @Bean
    public PoiStore poiStore() {
        return new InMemoryPoiStore();
    }

    @Bean
    public McpToolRegistry mcpToolRegistry(PoiStore poiStore) {
        InMemoryMcpToolRegistry registry = new InMemoryMcpToolRegistry();
        registry.register(new NavigateToStepTool());
        registry.register(new StartNavigationTool(poiStore));
        return registry;
    }

    @Bean
    public McpToolLogStore mcpToolLogStore() {
        return new InMemoryMcpToolLogStore();
    }

    @Bean
    public McpToolRateLimiter mcpToolRateLimiter() {
        return new McpToolRateLimiter();
    }

    @Bean
    public McpToolService mcpToolService(McpToolRegistry registry,
                                         McpToolRateLimiter rateLimiter,
                                         McpToolLogStore logStore) {
        return new McpToolService(registry, rateLimiter, logStore);
    }
}
