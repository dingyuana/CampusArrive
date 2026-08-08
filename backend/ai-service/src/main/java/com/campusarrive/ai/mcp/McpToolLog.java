package com.campusarrive.ai.mcp;

import java.time.Instant;
import java.util.Map;

/**
 * MCP 工具调用审计日志（AID 7.3 审计日志）。
 *
 * <p>规格来源：FR-01-15 / FR-01-16。
 * 每次工具调用记录调用方、参数、时间、结果，用于事后追溯。
 * 日志存储请求结束即销毁，不落盘（开发环境内存实现）。</p>
 *
 * @param logId      日志唯一标识
 * @param toolName   工具名称
 * @param studentId  调用方学号
 * @param sessionId  会话 ID
 * @param params     调用参数（已脱敏）
 * @param success    是否成功
 * @param errorCode  错误码（失败时）
 * @param timestamp  调用时间
 */
public record McpToolLog(
        String logId,
        String toolName,
        String studentId,
        String sessionId,
        Map<String, Object> params,
        boolean success,
        String errorCode,
        Instant timestamp
) {
}
