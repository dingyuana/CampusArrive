package com.campusarrive.ai.mcp;

import java.util.List;

/**
 * MCP 工具参数定义（AID 7.3 参数规范）。
 *
 * <p>规格来源：FR-01-15 / FR-01-16。
 * 描述工具入参的名称、类型、必填性与合法枚举值，
 * 供工具注册中心做参数校验（UT-AI-002）。</p>
 *
 * @param name        参数名（如 "step_id"）
 * @param type        参数类型（"string" / "object"）
 * @param required    是否必填
 * @param description 参数说明
 * @param enumValues  合法枚举值列表；为空表示无枚举约束
 */
public record McpToolParam(
        String name,
        String type,
        boolean required,
        String description,
        List<String> enumValues
) {

    /** 简化构造（无枚举约束）。 */
    public McpToolParam(String name, String type, boolean required, String description) {
        this(name, type, required, description, List.of());
    }
}
