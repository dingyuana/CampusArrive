package com.campusarrive.ai.knowledge;

import java.util.List;

/**
 * MaxKB REST 客户端接口。
 *
 * <p>规格来源：AID 2.2 / 6.1 — MaxKB 作为编排层，承载工作流编排、知识库管理与 MCP 工具调度。
 * AI 服务层通过该客户端与 MaxKB 交互，完成知识库检索与对话工作流调用。</p>
 *
 * <p>当前提供内存实现占位（{@link InMemoryMaxKbClient}），
 * 真实 HTTP 客户端实现将在 INFRA-1.3 内网 MaxKB 环境就绪后替换，
 * 届时对接 MaxKB 的知识库检索 API 与工作流编排 API。</p>
 */
public interface MaxKbClient {

    /** MaxKB 服务是否可用（健康检查）。 */
    boolean isAvailable();

    /**
     * 调用 MaxKB 知识库检索。
     *
     * @param query    用户问题（已脱敏）
     * @param topK     返回条数
     * @param category 知识库分类过滤；{@code null} 表示全库检索
     * @return 检索结果来源列表
     */
    List<RetrievedSource> search(String query, int topK, KnowledgeCategory category);
}
