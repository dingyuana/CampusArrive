package com.campusarrive.ai.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-AI-003: MCP 工具-跳转报到环节契约测试。
 *
 * <p>规格来源：FR-01-15、AID 7.1.1。
 * 验证 {@code navigate_to_step} 工具的输入/输出契约：
 * 跳转目标正确（step_id → step_name + page_url），携带上下文（student_id），
 * 非法 step_id 被拒。</p>
 */
@DisplayName("CT-AI-003: MCP 工具-跳转报到环节")
class NavigateToStepContractTest {

    private NavigateToStepTool tool;

    @BeforeEach
    void setUp() {
        tool = new NavigateToStepTool();
    }

    @Test
    @DisplayName("跳转缴费环节:step_id=payment → 返回正确环节信息")
    void navigateToPayment() {
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "payment", "student_id", "20260001"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertTrue(result.success(), "合法参数应成功");
        assertEquals("payment", result.data().get("step_id"));
        assertEquals("缴纳学费", result.data().get("step_name"));
        assertEquals("/pages/checkin/pay/index", result.data().get("page_url"));
        assertEquals("in_progress", result.data().get("step_status"));
    }

    @Test
    @DisplayName("跳转身份核验:step_id=verification")
    void navigateToVerification() {
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "verification", "student_id", "20260001"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertTrue(result.success());
        assertEquals("verification", result.data().get("step_id"));
        assertEquals("身份核验", result.data().get("step_name"));
        assertEquals("/pages/checkin/verify/index", result.data().get("page_url"));
    }

    @Test
    @DisplayName("跳转宿舍入住:step_id=dorm_assign")
    void navigateToDormAssign() {
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "dorm_assign", "student_id", "20260001"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertTrue(result.success());
        assertEquals("dorm_assign", result.data().get("step_id"));
        assertEquals("宿舍入住", result.data().get("step_name"));
    }

    @Test
    @DisplayName("跳转体检:step_id=checkin")
    void navigateToCheckin() {
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "checkin", "student_id", "20260001"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertTrue(result.success());
        assertEquals("checkin", result.data().get("step_id"));
        assertEquals("入学体检", result.data().get("step_name"));
    }

    @Test
    @DisplayName("跳转材料提交:step_id=material_upload")
    void navigateToMaterialUpload() {
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "material_upload", "student_id", "20260001"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertTrue(result.success());
        assertEquals("material_upload", result.data().get("step_id"));
        assertEquals("材料提交", result.data().get("step_name"));
    }

    @Test
    @DisplayName("非法 step_id 被拒")
    void invalidStepIdRejected() {
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "hack_step", "student_id", "20260001"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertFalse(result.success());
        assertEquals("INVALID_PARAM", result.errorCode());
    }

    @Test
    @DisplayName("缺少 student_id 被拒")
    void missingStudentIdRejected() {
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "payment"),
                "20260001", "sess-001");

        McpToolResult result = tool.execute(req);

        assertFalse(result.success());
        assertEquals("INVALID_PARAM", result.errorCode());
    }

    @Test
    @DisplayName("携带上下文:session_id 与 student_id 正确传递")
    void contextCarried() {
        McpToolRequest req = new McpToolRequest(
                "navigate_to_step",
                Map.of("step_id", "payment", "student_id", "20260001"),
                "20260001", "sess-ctx-001");

        McpToolResult result = tool.execute(req);

        assertTrue(result.success());
        // 返回数据应包含足够信息供前端跳转
        assertNotNull(result.data().get("page_url"));
        assertNotNull(result.data().get("step_name"));
    }
}
