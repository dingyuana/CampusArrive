package com.campusarrive.ai.mcp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存工具调用日志存储（开发环境占位）。
 *
 * <p>规格来源：AID 7.3 审计日志。
 * 按学生分组存储调用日志，供查询与审计。生产环境替换为持久化存储。</p>
 */
public class InMemoryMcpToolLogStore implements McpToolLogStore {

    private final Map<String, List<McpToolLog>> byStudent = new ConcurrentHashMap<>();
    private final List<McpToolLog> allLogs = new CopyOnWriteArrayList<>();

    @Override
    public void append(McpToolLog log) {
        if (log == null) {
            return;
        }
        allLogs.add(log);
        byStudent.computeIfAbsent(log.studentId(), k -> new CopyOnWriteArrayList<>()).add(log);
    }

    @Override
    public List<McpToolLog> findByStudent(String studentId) {
        if (studentId == null) {
            return List.of();
        }
        return List.copyOf(byStudent.getOrDefault(studentId, List.of()));
    }

    @Override
    public List<McpToolLog> findAll() {
        return List.copyOf(allLogs);
    }

    @Override
    public void clear() {
        allLogs.clear();
        byStudent.clear();
    }
}
