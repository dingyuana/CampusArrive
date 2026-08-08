package com.campusarrive.ai.knowledge;

import java.util.List;

/**
 * MaxKB 客户端内存实现占位。
 *
 * <p>规格来源：AID 6.1 — MaxKB 工作流编排层。真实实现通过 HTTP 调用 MaxKB 的
 * 知识库检索 API 与工作流编排 API，在 INFRA-1.3 内网 MaxKB 环境就绪后替换。</p>
 *
 * <p>当前实现将检索请求委托给 {@link HybridRetrievalStrategy}，
 * 使 AI-3.2 对话工作流可在无 MaxKB 环境下完成逻辑联调与降级检索，
 * 并为 {@link MaxKbClient} 接口提供可测试的默认实现。</p>
 */
public class InMemoryMaxKbClient implements MaxKbClient {

    private final HybridRetrievalStrategy strategy;

    public InMemoryMaxKbClient(HybridRetrievalStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<RetrievedSource> search(String query, int topK, KnowledgeCategory category) {
        return strategy.retrieve(query, topK, category);
    }
}
