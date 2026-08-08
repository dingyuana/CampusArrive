package com.campusarrive.ai.mcp;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具一：跳转报到环节（AID 7.1.1）。
 *
 * <p>规格来源：FR-01-15（MCP 工具-跳转报到环节）。
 * 接收 step_id 与 student_id，校验环节标识合法性与学生权限，
 * 返回环节页面路径与状态，供前端跳转。</p>
 *
 * <p>参数校验（AID 7.3）：</p>
 * <ul>
 *   <li>step_id 须为枚举值（verification / payment / dorm_assign / checkin / material_upload）；</li>
 *   <li>student_id 须非空。</li>
 * </ul>
 */
public class NavigateToStepTool implements McpTool {

    /** 工具名称。 */
    public static final String TOOL_NAME = "navigate_to_step";

    private static final McpToolDefinition DEFINITION = new McpToolDefinition(
            TOOL_NAME,
            "跳转至指定的报到流程环节页面",
            List.of(
                    new McpToolParam("step_id", "string", true,
                            "报到环节唯一标识", stepEnumValues()),
                    new McpToolParam("student_id", "string", true,
                            "新生学号，用于校验环节权限")
            )
    );

    private static List<String> stepEnumValues() {
        return java.util.Arrays.stream(StepId.values())
                .map(StepId::code)
                .toList();
    }

    @Override
    public McpToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public McpToolResult execute(McpToolRequest request) {
        Map<String, Object> params = request.params();
        if (params == null) {
            return McpToolResult.failure("INVALID_PARAM", "参数不能为空");
        }
        Object stepIdRaw = params.get("step_id");
        Object studentIdRaw = params.get("student_id");

        if (studentIdRaw == null || String.valueOf(studentIdRaw).isBlank()) {
            return McpToolResult.failure("INVALID_PARAM", "student_id 不能为空");
        }
        if (stepIdRaw == null || String.valueOf(stepIdRaw).isBlank()) {
            return McpToolResult.failure("INVALID_PARAM", "step_id 不能为空");
        }

        String stepCode = String.valueOf(stepIdRaw);
        StepId step = StepId.fromCode(stepCode);
        if (step == null) {
            return McpToolResult.failure("INVALID_PARAM",
                    "step_id 非法: " + stepCode + "，合法值: " + stepEnumValues());
        }

        // 权限校验占位：真实环境需查询学生报到进度，校验前置环节是否完成
        // 此处简化为始终允许（开发环境）
        Map<String, Object> data = Map.of(
                "step_id", step.code(),
                "step_name", step.displayName(),
                "page_url", step.pageUrl(),
                "step_status", "in_progress"
        );
        return McpToolResult.success(data);
    }
}
