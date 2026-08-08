package com.campusarrive.ai.chat;

/**
 * MCP 工具调用记录(API 5.1.5 tools_invoked[] 字段)。
 *
 * <p>规格来源:FR-01-15(跳转报到环节)、FR-01-16(校园导航)。
 * 由工作流节点 4 解析 DeepSeek 输出的意图标记生成,随回复一并返回前端。</p>
 *
 * @param toolName 工具名称(navigate_to_step / start_navigation)
 * @param toolId   工具调用唯一标识
 * @param params   工具参数(如 step_id、poi_id)
 * @param result   工具执行结果描述
 * @param success  是否执行成功
 */
public record ToolInvocation(
        String toolName,
        String toolId,
        String params,
        String result,
        boolean success
) {
}
