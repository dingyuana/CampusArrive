package com.campusarrive.ai.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话上下文(多轮对话历史)。
 *
 * <p>规格来源:FR-01-06(多轮上下文)、AID 附录 A(会话上下文轮数=5)。
 * 维护最近 5 轮用户消息与 AI 回复,作为 DeepSeek 生成的上下文输入(FR-01-13)。</p>
 *
 * <p>会话级存储,迎新结束自动清除(AID 7.4)。当前为内存实现,
 * 后续可替换为 Redis 会话存储(INFRA-1.x)。</p>
 */
public class ConversationContext {

    /** 最大保留的历史轮次数(AID 附录 A 推荐值=5)。 */
    public static final int MAX_HISTORY_ROUNDS = 5;

    private final String sessionId;
    private final String studentId;
    private final List<Message> history = Collections.synchronizedList(new ArrayList<>());

    public ConversationContext(String sessionId, String studentId) {
        this.sessionId = sessionId;
        this.studentId = studentId;
    }

    public String sessionId() {
        return sessionId;
    }

    public String studentId() {
        return studentId;
    }

    /** 返回历史消息的不可变快照。 */
    public List<Message> history() {
        return List.copyOf(history);
    }

    /** 追加一轮对话(用户消息 + AI 回复),超出上限时淘汰最早轮次。 */
    public void appendRound(String userMessage, String aiReply) {
        history.add(new Message("user", userMessage));
        history.add(new Message("assistant", aiReply));
        while (history.size() > MAX_HISTORY_ROUNDS * 2) {
            history.remove(0);
            history.remove(0);
        }
    }

    /** 清空会话历史。 */
    public void clear() {
        history.clear();
    }

    /** 单条消息。 */
    public record Message(String role, String content) {
    }
}
