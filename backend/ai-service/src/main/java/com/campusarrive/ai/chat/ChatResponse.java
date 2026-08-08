package com.campusarrive.ai.chat;

import com.campusarrive.ai.knowledge.RetrievedSource;

import java.util.List;

/**
 * AI 对话响应(API 5.1.5 data 字段)。
 *
 * <p>规格来源:FR-01-01 至 FR-01-18。映射 API 设计文档 §5.1.5 响应字段表,
 * 由 {@link com.campusarrive.ai.chat.workflow.ChatWorkflow} 编排产出。</p>
 *
 * @param sessionId      会话 ID(后续多轮对话需携带)
 * @param messageId      本次回答消息 ID
 * @param reply          AI 回复正文
 * @param sources        知识库来源列表(支持溯源,FR-01-12/14)
 * @param contentLabel   AI 内容标识(FR-01-14、FR-05-07)
 * @param toolsInvoked   MCP 工具调用记录(FR-01-15/16)
 * @param transferToHuman 是否触发转人工(FR-01-05)
 * @param tokens         token 用量统计(prompt/completion/total)
 */
public record ChatResponse(
        String sessionId,
        String messageId,
        String reply,
        List<RetrievedSource> sources,
        ContentLabel contentLabel,
        List<ToolInvocation> toolsInvoked,
        boolean transferToHuman,
        TokenUsage tokens
) {
}
