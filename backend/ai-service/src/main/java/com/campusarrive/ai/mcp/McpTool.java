package com.campusarrive.ai.mcp;

/**
 * MCP 工具接口（AID 7.1 工具定义）。
 *
 * <p>规格来源：FR-01-15 / FR-01-16。
 * 每个具体工具实现此接口，提供工具定义（名称、描述、参数规范）
 * 与执行逻辑（参数校验 + 业务处理 → 返回结果）。</p>
 */
public interface McpTool {

    /** 工具定义（名称、描述、参数规范）。 */
    McpToolDefinition definition();

    /**
     * 执行工具调用。
     *
     * @param request 工具调用请求（含参数与调用方信息）
     * @return 调用结果（成功返回数据，失败返回错误码与信息）
     */
    McpToolResult execute(McpToolRequest request);
}
