package com.campusarrive.ai.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UT-AI-002: MCP 工具注册与参数校验。
 *
 * <p>规格来源：FR-01-15 / FR-01-16、AID 7.3 参数规范与安全约束。
 * 验证工具注册中心合法注册、重复拒绝、工具定义完整性，
 * 以及工具入参严格校验（非法参数被拒，合法参数执行）。</p>
 */
@DisplayName("UT-AI-002: MCP 工具注册与参数校验")
class McpToolRegistryTest {

    private InMemoryMcpToolRegistry registry;
    private InMemoryPoiStore poiStore;

    @BeforeEach
    void setUp() {
        registry = new InMemoryMcpToolRegistry();
        poiStore = new InMemoryPoiStore();
    }

    // ─── 工具注册 ───────────────────────────────────────────

    @Test
    @DisplayName("合法工具注册成功")
    void registerValidTool() {
        McpTool tool = new NavigateToStepTool();
        registry.register(tool);

        assertEquals(1, registry.size());
        assertTrue(registry.find(NavigateToStepTool.TOOL_NAME).isPresent());
    }

    @Test
    @DisplayName("重复注册同名工具被拒")
    void rejectDuplicateRegistration() {
        registry.register(new NavigateToStepTool());

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new NavigateToStepTool()),
                "应拒绝重复注册");
    }

    @Test
    @DisplayName("注册 null 工具被拒")
    void rejectNullTool() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(null));
    }

    @Test
    @DisplayName("列出所有工具定义")
    void listAllDefinitions() {
        registry.register(new NavigateToStepTool());
        registry.register(new StartNavigationTool(poiStore));

        List<McpToolDefinition> defs = registry.listDefinitions();
        assertEquals(2, defs.size());
        assertTrue(defs.stream().anyMatch(d -> "navigate_to_step".equals(d.toolName())));
        assertTrue(defs.stream().anyMatch(d -> "start_navigation".equals(d.toolName())));
    }

    @Test
    @DisplayName("查找未注册工具返回 empty")
    void findUnregisteredReturnsEmpty() {
        assertTrue(registry.find("nonexistent").isEmpty());
    }

    // ─── 工具定义完整性 ─────────────────────────────────────

    @Test
    @DisplayName("navigate_to_step 定义包含 step_id 与 student_id 参数")
    void navigateToStepDefinitionComplete() {
        McpTool tool = new NavigateToStepTool();
        McpToolDefinition def = tool.definition();

        assertEquals("navigate_to_step", def.toolName());
        assertNotNull(def.description());
        assertEquals(2, def.params().size());

        McpToolParam stepParam = def.params().stream()
                .filter(p -> "step_id".equals(p.name()))
                .findFirst().orElseThrow();
        assertTrue(stepParam.required());
        assertFalse(stepParam.enumValues().isEmpty());
        assertTrue(stepParam.enumValues().contains("payment"));

        McpToolParam studentParam = def.params().stream()
                .filter(p -> "student_id".equals(p.name()))
                .findFirst().orElseThrow();
        assertTrue(studentParam.required());
    }

    @Test
    @DisplayName("start_navigation 定义包含 poi_id 必填参数")
    void startNavigationDefinitionComplete() {
        McpTool tool = new StartNavigationTool(poiStore);
        McpToolDefinition def = tool.definition();

        assertEquals("start_navigation", def.toolName());
        McpToolParam poiParam = def.params().stream()
                .filter(p -> "poi_id".equals(p.name()))
                .findFirst().orElseThrow();
        assertTrue(poiParam.required());
    }

    // ─── 参数校验（非法参数被拒）────────────────────────────

    @Test
    @DisplayName("navigate_to_step 非法 step_id 被拒")
    void navigateInvalidStepIdRejected() {
        NavigateToStepTool tool = new NavigateToStepTool();
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "invalid_step", "student_id", "20260001"),
                "20260001", "sess-1");

        McpToolResult result = tool.execute(req);
        assertFalse(result.success());
        assertEquals("INVALID_PARAM", result.errorCode());
        assertTrue(result.errorMessage().contains("非法"));
    }

    @Test
    @DisplayName("navigate_to_step 缺少 student_id 被拒")
    void navigateMissingStudentIdRejected() {
        NavigateToStepTool tool = new NavigateToStepTool();
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "payment"),
                "20260001", "sess-1");

        McpToolResult result = tool.execute(req);
        assertFalse(result.success());
        assertEquals("INVALID_PARAM", result.errorCode());
    }

    @Test
    @DisplayName("navigate_to_step null 参数被拒")
    void navigateNullParamsRejected() {
        NavigateToStepTool tool = new NavigateToStepTool();
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step", null, "20260001", "sess-1");

        McpToolResult result = tool.execute(req);
        assertFalse(result.success());
    }

    @Test
    @DisplayName("start_navigation 不存在的 poi_id 被拒")
    void navigationNonExistentPoiRejected() {
        StartNavigationTool tool = new StartNavigationTool(poiStore);
        McpToolRequest req = new McpToolRequest(
                "start_navigation",
                Map.of("poi_id", "nonexistent_poi"),
                "20260001", "sess-1");

        McpToolResult result = tool.execute(req);
        assertFalse(result.success());
        assertEquals("NOT_FOUND", result.errorCode());
    }

    @Test
    @DisplayName("start_navigation 缺少 poi_id 被拒")
    void navigationMissingPoiIdRejected() {
        StartNavigationTool tool = new StartNavigationTool(poiStore);
        McpToolRequest req = new McpToolRequest(
                "start_navigation", Map.of(), "20260001", "sess-1");

        McpToolResult result = tool.execute(req);
        assertFalse(result.success());
        assertEquals("INVALID_PARAM", result.errorCode());
    }

    // ─── 合法参数执行 ───────────────────────────────────────

    @Test
    @DisplayName("navigate_to_step 合法参数执行成功")
    void navigateValidParamsExecute() {
        NavigateToStepTool tool = new NavigateToStepTool();
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "payment", "student_id", "20260001"),
                "20260001", "sess-1");

        McpToolResult result = tool.execute(req);
        assertTrue(result.success());
        assertEquals("payment", result.data().get("step_id"));
        assertEquals("缴纳学费", result.data().get("step_name"));
        assertNotNull(result.data().get("page_url"));
    }

    @Test
    @DisplayName("start_navigation 合法参数执行成功")
    void navigationValidParamsExecute() {
        StartNavigationTool tool = new StartNavigationTool(poiStore);
        McpToolRequest req = new McpToolRequest(
                "start_navigation",
                Map.of("poi_id", "library_main"),
                "20260001", "sess-1");

        McpToolResult result = tool.execute(req);
        assertTrue(result.success());
        assertEquals("library_main", result.data().get("poi_id"));
        assertEquals("中心图书馆", result.data().get("poi_name"));
        assertNotNull(result.data().get("nav_url"));
    }
}
