package com.campusarrive.ai.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话上下文管理器(内存实现)。
 *
 * <p>规格来源:FR-01-06(多轮上下文)、AID 7.4(会话级存储,迎新结束清除)。
 * 维护 sessionId → {@link ConversationContext} 映射,支持新建会话与历史轮次追加。</p>
 *
 * <p>当前为内存实现,后续可替换为 Redis 会话存储(INFRA-1.x)。</p>
 */
public class ConversationContextManager {

    private final Map<String, ConversationContext> sessions = new ConcurrentHashMap<>();

    /** 新建或获取已有会话上下文。 */
    public ConversationContext getOrCreate(String sessionId, String studentId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = generateSessionId();
        }
        String finalSessionId = sessionId;
        return sessions.computeIfAbsent(finalSessionId,
                sid -> new ConversationContext(sid, studentId));
    }

    /** 追加一轮对话到会话历史。 */
    public void appendRound(String sessionId, String studentId, String userMessage, String aiReply) {
        ConversationContext ctx = getOrCreate(sessionId, studentId);
        ctx.appendRound(userMessage, aiReply);
    }

    /** 清除指定会话。 */
    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }

    /** 当前活跃会话数。 */
    public int activeSessions() {
        return sessions.size();
    }

    /** 生成会话 ID。 */
    private String generateSessionId() {
        return "sess-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
