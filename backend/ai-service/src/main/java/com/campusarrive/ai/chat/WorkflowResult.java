package com.campusarrive.ai.chat;

import com.campusarrive.ai.chat.workflow.IntentMarker;
import com.campusarrive.ai.knowledge.RetrievedSource;

import java.util.List;

/**
 * 工作流执行结果(内部中间态,非 API 响应)。
 *
 * <p>由 5 节点工作流编排产出,再由 {@link ChatResponse} 映射为 API 响应。
 * 承载各节点产出:检索来源、生成回复、意图标记、token 用量、降级标识等。</p>
 *
 * @param reply          生成回复正文
 * @param sources        知识库来源
 * @param intents        解析出的跳转意图标记
 * @param tokens         token 用量
 * @param contentLabel   内容标识
 * @param transferToHuman 是否转人工
 * @param lowConfidence  是否低置信(检索相似度低于阈值)
 */
public record WorkflowResult(
        String reply,
        List<RetrievedSource> sources,
        List<IntentMarker> intents,
        TokenUsage tokens,
        ContentLabel contentLabel,
        boolean transferToHuman,
        boolean lowConfidence
) {
}
