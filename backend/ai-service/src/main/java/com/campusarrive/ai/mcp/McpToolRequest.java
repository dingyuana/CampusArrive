package com.campusarrive.ai.mcp;

import java.util.Map;

/**
 * MCP 工具调用请求（前端解析意图标记后发起）。
 *
 * <p>规格来源：AID 7.2 调用流程 — 前端解析 AI 回复中的 {@code [[STEP:xxx]]} / {@code [[POI:xxx]]}
 * 意图标记后，构造请求调用 MCP 工具服务执行跳转。</p>
 *
 * @param toolName  工具名称（如 "navigate_to_step"）
 * @param params    工具参数键值对（如 {"step_id":"payment","student_id":"20260001"}）
 * @param studentId 调用方学号（用于权限校验与频率限制）
 * @param sessionId 对话会话 ID（用于关联审计日志）
 */
public record McpToolRequest(
        String toolName,
        Map<String, Object> params,
        String studentId,
        String sessionId
) {
}
