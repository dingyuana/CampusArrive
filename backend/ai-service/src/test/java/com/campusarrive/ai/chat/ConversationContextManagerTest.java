package com.campusarrive.ai.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConversationContextManager} 单元测试。
 *
 * <p>规格来源:FR-01-06(多轮上下文)、AID 7.4(会话级存储)。</p>
 */
@DisplayName("UT-AI: 会话上下文管理")
class ConversationContextManagerTest {

    @Test
    @DisplayName("sessionId 为空时自动新建")
    void newSessionWhenIdNull() {
        ConversationContextManager mgr = new ConversationContextManager();
        ConversationContext ctx = mgr.getOrCreate(null, "STU001");

        assertNotNull(ctx.sessionId(), "应生成新 sessionId");
        assertEquals("STU001", ctx.studentId());
        assertTrue(mgr.activeSessions() >= 1, "活跃会话数应增加");
    }

    @Test
    @DisplayName("相同 sessionId 复用会话")
    void reuseSessionById() {
        ConversationContextManager mgr = new ConversationContextManager();
        ConversationContext ctx1 = mgr.getOrCreate("sess-1", "STU001");
        mgr.appendRound("sess-1", "STU001", "问题", "回答");
        ConversationContext ctx2 = mgr.getOrCreate("sess-1", "STU001");

        assertEquals(ctx1.sessionId(), ctx2.sessionId(), "应复用同一会话");
        assertEquals(2, ctx2.history().size(), "历史应含 1 轮(2 条消息)");
    }

    @Test
    @DisplayName("清除会话")
    void clearSession() {
        ConversationContextManager mgr = new ConversationContextManager();
        mgr.getOrCreate("sess-1", "STU001");
        mgr.clear("sess-1");
        assertEquals(0, mgr.activeSessions(), "清除后活跃会话数为 0");
    }

    @Test
    @DisplayName("历史轮次上限淘汰")
    void historyEviction() {
        ConversationContext ctx = new ConversationContext("sess-1", "STU001");
        for (int i = 0; i < 10; i++) {
            ctx.appendRound("问题" + i, "回答" + i);
        }
        assertTrue(ctx.history().size() <= 10, "历史不超过 5 轮(10 条消息)");
    }

    @Test
    @DisplayName("clear 清空历史")
    void clearHistory() {
        ConversationContext ctx = new ConversationContext("sess-1", "STU001");
        ctx.appendRound("问题", "回答");
        ctx.clear();
        assertEquals(0, ctx.history().size(), "清空后历史为空");
    }
}
