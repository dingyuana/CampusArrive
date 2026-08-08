package com.campusarrive.ai.mcp;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具调用 HTTP 端点（FR-01-15 / FR-01-16）。
 *
 * <p>规格来源：AID 7.2 调用流程 — 前端解析 AI 回复意图标记后调用。
 * 提供工具列表查询与工具调用两个端点。</p>
 *
 * <p>路由前缀 {@code /api/v1/ai/mcp}，由网关 JWT 鉴权过滤器保护。</p>
 */
@RestController
@RequestMapping("/api/v1/ai/mcp")
public class McpToolController {

    private final McpToolService toolService;

    public McpToolController(McpToolService toolService) {
        this.toolService = toolService;
    }

    /** 列出所有已注册 MCP 工具定义。 */
    @GetMapping("/tools")
    public ResponseEntity<List<McpToolDefinition>> listTools() {
        return ResponseEntity.ok(toolService.listTools());
    }

    /**
     * 调用 MCP 工具。
     *
     * <p>请求体格式：</p>
     * <pre>{@json
     * {
     *   "tool_name": "navigate_to_step",
     *   "params": {"step_id": "payment", "student_id": "20260001"},
     *   "student_id": "20260001",
     *   "session_id": "sess-001"
     * }
     * }</pre>
     */
    @PostMapping("/invoke")
    public ResponseEntity<Map<String, Object>> invoke(@RequestBody Map<String, Object> body) {
        String toolName = (String) body.get("tool_name");
        String studentId = (String) body.get("student_id");
        String sessionId = (String) body.get("session_id");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");

        McpToolRequest request = new McpToolRequest(toolName, params, studentId, sessionId);
        McpToolResult result = toolService.invoke(request);

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("success", result.success());
        if (result.success()) {
            response.put("data", result.data());
        } else {
            response.put("error_code", result.errorCode());
            response.put("error_message", result.errorMessage());
        }
        return ResponseEntity.ok(response);
    }
}
