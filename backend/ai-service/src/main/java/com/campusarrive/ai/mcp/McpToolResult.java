package com.campusarrive.ai.mcp;

import java.util.Map;

/**
 * MCP 工具调用结果（AID 7.1 返回值）。
 *
 * <p>规格来源：FR-01-15 / FR-01-16。
 * 工具执行后返回成功/失败状态与数据，供前端跳转或展示错误。</p>
 *
 * @param success     是否执行成功
 * @param errorCode   错误码（失败时非空，如 "INVALID_PARAM" / "RATE_LIMITED" / "NOT_FOUND"）
 * @param errorMessage 错误信息（失败时非空）
 * @param data        返回数据（成功时非空，如 {"step_id":"pay","page_url":"/pages/checkin/pay/index"}）
 */
public record McpToolResult(
        boolean success,
        String errorCode,
        String errorMessage,
        Map<String, Object> data
) {

    /** 成功结果工厂方法。 */
    public static McpToolResult success(Map<String, Object> data) {
        return new McpToolResult(true, null, null, data);
    }

    /** 失败结果工厂方法。 */
    public static McpToolResult failure(String errorCode, String errorMessage) {
        return new McpToolResult(false, errorCode, errorMessage, Map.of());
    }
}
