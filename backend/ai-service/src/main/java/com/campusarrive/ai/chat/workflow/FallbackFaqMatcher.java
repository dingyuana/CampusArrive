package com.campusarrive.ai.chat.workflow;

import com.campusarrive.ai.knowledge.KnowledgeCategory;
import com.campusarrive.ai.knowledge.KnowledgeRetrievalService;
import com.campusarrive.ai.knowledge.RetrievedSource;

import java.util.List;

/**
 * FAQ 降级匹配器(AID 9.2 降级模式)。
 *
 * <p>规格来源:FR-01-17(降级模式)、AID 9.2。
 * DeepSeek 不可用时(API 超时/5xx 连续 3 次/网络不可达/成本熔断),
 * 切换为 FAQ 关键词匹配:基于检索结果直接返回知识库片段作为回复,
 * 匹配分 ≥ 0.5 返回 FAQ 标准答案,低于阈值返回兜底话术。</p>
 *
 * <p>能力收敛:仅支持 FAQ 问答与流程环节跳转,不支持 POI 自然语言查询与情绪识别转人工。
 * 降级标识"当前为离线降级模式"。</p>
 */
public class FallbackFaqMatcher {

    /** 降级匹配最低相似度阈值。 */
    public static final double MIN_SCORE = 0.5;

    private final KnowledgeRetrievalService retrieval;

    public FallbackFaqMatcher(KnowledgeRetrievalService retrieval) {
        this.retrieval = retrieval;
    }

    /**
     * FAQ 降级匹配:检索 FAQ 知识库,命中则返回标准答案。
     *
     * @param question 用户问题
     * @return 降级匹配结果;null 表示未命中
     */
    public FallbackResult match(String question) {
        List<RetrievedSource> sources = retrieval.retrieve(question, 1, KnowledgeCategory.FAQ);
        if (sources.isEmpty() || sources.get(0).score() < MIN_SCORE) {
            return new FallbackResult(null, null, 0.0, false);
        }
        RetrievedSource top = sources.get(0);
        return new FallbackResult(top.snippet(), sources, top.score(), true);
    }

    /** 兜底话术(降级也未命中时)。 */
    public static String fallbackMessage() {
        return "抱歉,当前为离线降级模式,未能匹配到您的问题。"
                + "建议咨询现场志愿者或辅导员,或稍后重试。";
    }

    /** 降级匹配结果。 */
    public record FallbackResult(String reply, List<RetrievedSource> sources, double score, boolean hit) {
    }
}
