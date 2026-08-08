package com.campusarrive.ai.mcp;

import java.util.List;

/**
 * MCP 工具调用审计日志存储（AID 7.3 审计日志）。
 *
 * <p>规格来源：FR-01-15 / FR-01-16。
 * 记录每次工具调用的调用方、参数、时间、结果。
 * 开发环境为内存实现，生产环境可替换为持久化存储。</p>
 */
public interface McpToolLogStore {

    /** 记录一条调用日志。 */
    void append(McpToolLog log);

    /** 查询指定学生的调用日志。 */
    List<McpToolLog> findByStudent(String studentId);

    /** 查询全部调用日志。 */
    List<McpToolLog> findAll();

    /** 清空全部日志（测试用）。 */
    void clear();
}
