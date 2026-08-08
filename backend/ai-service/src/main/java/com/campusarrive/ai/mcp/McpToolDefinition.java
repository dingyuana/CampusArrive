package com.campusarrive.ai.mcp;

import java.util.List;

/**
 * MCP 工具定义（AID 7.1 工具定义）。
 *
 * <p>规格来源：FR-01-15 / FR-01-16。
 * 描述工具名称、功能、参数规范，供模型理解工具能力并正确调用。
 * 工具注册中心（{@link McpToolRegistry}）以此为核心注册单元。</p>
 *
 * @param toolName    工具名称（如 "navigate_to_step"）
 * @param description 功能描述
 * @param params      参数定义列表
 */
public record McpToolDefinition(
        String toolName,
        String description,
        List<McpToolParam> params
) {
}
