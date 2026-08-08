package com.campusarrive.ai.mcp;

import java.util.List;
import java.util.Optional;

/**
 * MCP 工具注册中心（AID 7.1 工具定义 — UT-AI-002）。
 *
 * <p>规格来源：FR-01-15 / FR-01-16。
 * 管理工具注册、查询与列表，供工具服务层在调用时按名称查找工具实例。
 * 注册时校验工具定义合法性（名称非空、参数规范完整）。</p>
 */
public interface McpToolRegistry {

    /**
     * 注册工具。
     *
     * @param tool 工具实例
     * @throws IllegalArgumentException 工具定义非法（名称为空或重复注册）
     */
    void register(McpTool tool);

    /**
     * 按名称查找工具。
     *
     * @param toolName 工具名称
     * @return 工具实例；不存在返回 empty
     */
    Optional<McpTool> find(String toolName);

    /** 列出所有已注册工具的定义。 */
    List<McpToolDefinition> listDefinitions();

    /** 已注册工具数量。 */
    int size();
}
