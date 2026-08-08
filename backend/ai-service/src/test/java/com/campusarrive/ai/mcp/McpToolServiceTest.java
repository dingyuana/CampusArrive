package com.campusarrive.ai.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 工具服务综合测试。
 *
 * <p>规格来源：AID 7.2 调用流程 + AID 7.3 安全约束。
 * 验证服务层协调逻辑：工具查找、频率限制、审计日志、错误处理。</p>
 */
@DisplayName("UT-AI: MCP 工具服务")
class McpToolServiceTest {

    private McpToolService service;
    private InMemoryMcpToolRegistry registry;
    private InMemoryMcpToolLogStore logStore;
    private McpToolRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        registry = new InMemoryMcpToolRegistry();
        InMemoryPoiStore poiStore = new InMemoryPoiStore();
        logStore = new InMemoryMcpToolLogStore();
        rateLimiter = new McpToolRateLimiter();
        registry.register(new NavigateToStepTool());
        registry.register(new StartNavigationTool(poiStore));
        service = new McpToolService(registry, rateLimiter, logStore);
    }

    @Test
    @DisplayName("正常调用 navigate_to_step 成功")
    void invokeNavigateToStepSuccess() {
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "payment", "student_id", "20260001"),
                "20260001", "sess-1");

        McpToolResult result = service.invoke(req);

        assertTrue(result.success());
        assertEquals("缴纳学费", result.data().get("step_name"));
    }

    @Test
    @DisplayName("调用未注册工具返回 TOOL_NOT_FOUND")
    void invokeUnregisteredTool() {
        McpToolRequest req = new McpToolRequest(
                "nonexistent_tool", Map.of(), "20260001", "sess-1");

        McpToolResult result = service.invoke(req);

        assertFalse(result.success());
        assertEquals("TOOL_NOT_FOUND", result.errorCode());
    }

    @Test
    @DisplayName("null 请求返回 INVALID_PARAM")
    void invokeNullRequest() {
        McpToolResult result = service.invoke(null);

        assertFalse(result.success());
        assertEquals("INVALID_PARAM", result.errorCode());
    }

    @Test
    @DisplayName("频率超限返回 RATE_LIMITED")
    void rateLimitExceeded() {
        // 连续调用 11 次（上限 10），第 11 次应被拒
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "payment", "student_id", "20260001"),
                "20260001", "sess-1");

        for (int i = 0; i < 10; i++) {
            assertTrue(service.invoke(req).success(), "前 10 次应成功");
        }

        McpToolResult result = service.invoke(req);
        assertFalse(result.success());
        assertEquals("RATE_LIMITED", result.errorCode());
    }

    @Test
    @DisplayName("不同学生频率独立计数")
    void rateLimitPerStudent() {
        McpToolRequest req1 = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "payment", "student_id", "STU001"),
                "STU001", "sess-1");
        McpToolRequest req2 = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "payment", "student_id", "STU002"),
                "STU002", "sess-2");

        // STU001 调用 10 次
        for (int i = 0; i < 10; i++) {
            assertTrue(service.invoke(req1).success());
        }
        // STU002 仍可调用
        assertTrue(service.invoke(req2).success(), "不同学生应独立计数");
    }

    @Test
    @DisplayName("审计日志记录成功调用")
    void auditLogSuccess() {
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "payment", "student_id", "20260001"),
                "20260001", "sess-audit");

        service.invoke(req);

        List<McpToolLog> logs = logStore.findByStudent("20260001");
        assertEquals(1, logs.size());
        McpToolLog log = logs.get(0);
        assertEquals("navigate_to_step", log.toolName());
        assertTrue(log.success());
        assertEquals("sess-audit", log.sessionId());
        assertNotNull(log.timestamp());
    }

    @Test
    @DisplayName("审计日志记录失败调用")
    void auditLogFailure() {
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "invalid", "student_id", "20260001"),
                "20260001", "sess-fail");

        service.invoke(req);

        List<McpToolLog> logs = logStore.findByStudent("20260001");
        assertEquals(1, logs.size());
        assertFalse(logs.get(0).success());
        assertEquals("INVALID_PARAM", logs.get(0).errorCode());
    }

    @Test
    @DisplayName("审计日志对 student_id 脱敏")
    void auditLogStudentIdMasked() {
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "payment", "student_id", "20260001"),
                "20260001", "sess-1");

        service.invoke(req);

        McpToolLog log = logStore.findAll().get(0);
        Object loggedStudentId = log.params().get("student_id");
        String masked = String.valueOf(loggedStudentId);
        assertTrue(masked.contains("****"), "审计日志中学号应脱敏");
        assertFalse(masked.contains("20260001"), "审计日志不应含明文学号");
    }

    @Test
    @DisplayName("listTools 返回所有已注册工具")
    void listAllTools() {
        List<McpToolDefinition> tools = service.listTools();

        assertEquals(2, tools.size());
        assertTrue(tools.stream().anyMatch(t -> "navigate_to_step".equals(t.toolName())));
        assertTrue(tools.stream().anyMatch(t -> "start_navigation".equals(t.toolName())));
    }

    @Test
    @DisplayName("工具执行异常被捕获并记录")
    void toolExecutionExceptionCaught() {
        // 注册一个会抛异常的工具
        McpTool throwingTool = new McpTool() {
            @Override
            public McpToolDefinition definition() {
                return new McpToolDefinition("throwing_tool", "测试异常",
                        List.of(new McpToolParam("x", "string", true, "测试")));
            }

            @Override
            public McpToolResult execute(McpToolRequest request) {
                throw new RuntimeException("模拟异常");
            }
        };
        registry.register(throwingTool);

        McpToolRequest req = new McpToolRequest(
                "throwing_tool", Map.of("x", "1"), "20260001", "sess-1");

        McpToolResult result = service.invoke(req);

        assertFalse(result.success());
        assertEquals("INTERNAL_ERROR", result.errorCode());
    }
}
