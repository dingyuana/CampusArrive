package com.campusarrive.ai.mcp;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * MCP 工具调用服务（AID 7.2 调用流程 + AID 7.3 安全约束）。
 *
 * <p>规格来源：FR-01-15 / FR-01-16。
 * 协调工具注册中心、频率限制器与审计日志，完成一次完整的工具调用：</p>
 * <ol>
 *   <li>按名称查找工具（未注册 → TOOL_NOT_FOUND）；</li>
 *   <li>频率限制校验（超限 → RATE_LIMITED）；</li>
 *   <li>执行工具（参数校验由工具内部完成）；</li>
 *   <li>记录审计日志。</li>
 * </ol>
 */
public class McpToolService {

    private final McpToolRegistry registry;
    private final McpToolRateLimiter rateLimiter;
    private final McpToolLogStore logStore;

    public McpToolService(McpToolRegistry registry,
                          McpToolRateLimiter rateLimiter,
                          McpToolLogStore logStore) {
        this.registry = registry;
        this.rateLimiter = rateLimiter;
        this.logStore = logStore;
    }

    /**
     * 调用工具。
     *
     * @param request 工具调用请求
     * @return 调用结果
     */
    public McpToolResult invoke(McpToolRequest request) {
        if (request == null) {
            return McpToolResult.failure("INVALID_PARAM", "请求不能为空");
        }

        // 1. 查找工具
        McpTool tool = registry.find(request.toolName()).orElse(null);
        if (tool == null) {
            return logAndReturn(request, McpToolResult.failure(
                    "TOOL_NOT_FOUND", "工具未注册: " + request.toolName()));
        }

        // 2. 频率限制
        if (!rateLimiter.tryAcquire(request.studentId())) {
            return logAndReturn(request, McpToolResult.failure(
                    "RATE_LIMITED", "工具调用频率超限（10次/分钟）"));
        }

        // 3. 执行工具
        McpToolResult result;
        try {
            result = tool.execute(request);
        } catch (Exception e) {
            result = McpToolResult.failure("INTERNAL_ERROR", "工具执行异常: " + e.getMessage());
        }

        // 4. 记录日志
        logAndReturn(request, result);
        return result;
    }

    /** 列出所有已注册工具定义。 */
    public java.util.List<McpToolDefinition> listTools() {
        return registry.listDefinitions();
    }

    private McpToolResult logAndReturn(McpToolRequest request, McpToolResult result) {
        McpToolLog log = new McpToolLog(
                UUID.randomUUID().toString(),
                request.toolName(),
                request.studentId(),
                request.sessionId(),
                sanitizeParams(request.params()),
                result.success(),
                result.errorCode(),
                Instant.now());
        logStore.append(log);
        return result;
    }

    /** 参数脱敏：对学号等 PII 做基础遮蔽（审计日志不含明文学号）。 */
    private Map<String, Object> sanitizeParams(Map<String, Object> params) {
        if (params == null) {
            return Map.of();
        }
        Map<String, Object> sanitized = new java.util.LinkedHashMap<>();
        for (var entry : params.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if ("student_id".equals(key) && val != null) {
                String s = String.valueOf(val);
                sanitized.put(key, s.length() > 4
                        ? "****" + s.substring(s.length() - 4)
                        : "****");
            } else {
                sanitized.put(key, val);
            }
        }
        return sanitized;
    }
}
