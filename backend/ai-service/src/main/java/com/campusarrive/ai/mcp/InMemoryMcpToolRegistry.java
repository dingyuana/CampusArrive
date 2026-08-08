package com.campusarrive.ai.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存工具注册中心（UT-AI-002）。
 *
 * <p>规格来源：FR-01-15 / FR-01-16。
 * 管理工具注册与查询，注册时校验工具定义合法性。
 * 线程安全，支持并发注册与查询。</p>
 */
public class InMemoryMcpToolRegistry implements McpToolRegistry {

    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();

    @Override
    public void register(McpTool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("工具不能为 null");
        }
        McpToolDefinition def = tool.definition();
        if (def == null || def.toolName() == null || def.toolName().isBlank()) {
            throw new IllegalArgumentException("工具定义非法: 名称不能为空");
        }
        if (def.params() == null) {
            throw new IllegalArgumentException("工具定义非法: 参数列表不能为 null");
        }
        if (tools.containsKey(def.toolName())) {
            throw new IllegalArgumentException("工具已注册: " + def.toolName());
        }
        tools.put(def.toolName(), tool);
    }

    @Override
    public Optional<McpTool> find(String toolName) {
        if (toolName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(toolName));
    }

    @Override
    public List<McpToolDefinition> listDefinitions() {
        return tools.values().stream()
                .map(McpTool::definition)
                .toList();
    }

    @Override
    public int size() {
        return tools.size();
    }
}
