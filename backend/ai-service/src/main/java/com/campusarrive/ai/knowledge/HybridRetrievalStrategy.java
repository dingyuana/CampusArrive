package com.campusarrive.ai.knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 混合检索策略实现：向量相似度（权重 0.7）+ BM25 关键词（权重 0.3）。
 *
 * <p>规格来源：AID 5.4 — 混合检索 = 向量相似度检索（权重 0.7）+ 关键词 BM25 检索（权重 0.3），
 * 提升召回率。向量化由 {@link TextVectorizer} 模拟，BM25 由 {@link Bm25Scorer} 计算。</p>
 *
 * <p>检索流程：</p>
 * <ol>
 *   <li>确定候选分块（按分类过滤或全库）；</li>
 *   <li>对每个候选计算向量相似度（Jaccard，归一化 ∈ [0,1]）；</li>
 *   <li>对候选集合执行 BM25 评分并归一化（最高分 → 1.0）；</li>
 *   <li>融合分数 = 0.7 × 向量 + 0.3 × BM25归一；</li>
 *   <li>按融合分数降序、过滤零分、取 Top-K。</li>
 * </ol>
 *
 * <p>另提供低置信判定（AID 6.2 节点 2 — 相似度低于阈值标记低置信，
 * 生成节点据此回复"建议咨询现场志愿者"）与材料清单按身份检索。</p>
 */
public class HybridRetrievalStrategy implements KnowledgeRetrievalService {

    /** 向量相似度权重。 */
    public static final double VECTOR_WEIGHT = 0.7;
    /** BM25 关键词权重。 */
    public static final double BM25_WEIGHT = 0.3;
    /** 默认相似度阈值（低于此值标记低置信）。 */
    public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.6;
    /** snippet 摘要最大长度。 */
    private static final int SNIPPET_MAX = 120;

    private final InMemoryKnowledgeStore store;
    private final double confidenceThreshold;

    public HybridRetrievalStrategy(InMemoryKnowledgeStore store) {
        this(store, DEFAULT_CONFIDENCE_THRESHOLD);
    }

    public HybridRetrievalStrategy(InMemoryKnowledgeStore store, double confidenceThreshold) {
        this.store = store;
        this.confidenceThreshold = confidenceThreshold;
    }

    @Override
    public List<RetrievedSource> retrieve(String query, int topK, KnowledgeCategory category) {
        List<KnowledgeChunk> candidates = (category == null) ? allChunks() : store.getChunks(category);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Bm25Scorer.ScoredChunk> bm25 = Bm25Scorer.score(query, candidates);
        double maxBm25 = bm25.isEmpty() ? 0.0 : bm25.get(0).score();
        Map<String, Double> bm25ById = new HashMap<>();
        for (Bm25Scorer.ScoredChunk s : bm25) {
            bm25ById.put(s.chunk().chunkId(), s.score());
        }
        List<Scored> scored = new ArrayList<>();
        for (KnowledgeChunk c : candidates) {
            double vec = TextVectorizer.similarity(query, c.searchableText());
            double bm = bm25ById.getOrDefault(c.chunkId(), 0.0);
            double bmNorm = maxBm25 > 0 ? bm / maxBm25 : 0.0;
            double hybrid = VECTOR_WEIGHT * vec + BM25_WEIGHT * bmNorm;
            scored.add(new Scored(c, hybrid));
        }
        scored.sort((x, y) -> Double.compare(y.score, x.score));
        return scored.stream()
                .filter(s -> s.score > 0)
                .limit(topK <= 0 ? Integer.MAX_VALUE : topK)
                .map(s -> toSource(s.chunk, s.score))
                .collect(Collectors.toList());
    }

    @Override
    public List<RetrievedSource> retrieveMaterialList(StudentCategory studentCategory) {
        if (studentCategory == null) {
            return List.of();
        }
        return store.getChunks(KnowledgeCategory.MATERIAL).stream()
                .filter(c -> studentCategory.name().equals(c.getStudentCategory()))
                .map(c -> toSource(c, 1.0))
                .collect(Collectors.toList());
    }

    /**
     * 判定检索结果是否为低置信（AID 6.2 节点 2 兜底）。
     *
     * <p>结果为空或最高分低于阈值即为低置信，生成节点应回复"建议咨询现场志愿者"。</p>
     */
    public boolean isLowConfidence(List<RetrievedSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return true;
        }
        return sources.get(0).score() < confidenceThreshold;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    // ─── 内部方法 ──────────────────────────────────────────────

    private List<KnowledgeChunk> allChunks() {
        List<KnowledgeChunk> all = new ArrayList<>();
        for (KnowledgeCategory c : KnowledgeCategory.values()) {
            all.addAll(store.getChunks(c));
        }
        return all;
    }

    private RetrievedSource toSource(KnowledgeChunk c, double score) {
        return new RetrievedSource(
                c.docId(), c.title(), c.section(), snippet(c.content()), score, c.category());
    }

    /**
     * 生成片段摘要（前 {@value #SNIPPET_MAX} 字），并对手机号、身份证号做基础脱敏，
     * 确保检索片段不含明文 PII（FR-05-09）。
     */
    String snippet(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        String masked = content
                .replaceAll("\\d{18}", "[ID]")
                .replaceAll("1[3-9]\\d{9}", "[PHONE]");
        return masked.length() <= SNIPPET_MAX ? masked : masked.substring(0, SNIPPET_MAX) + "...";
    }

    private record Scored(KnowledgeChunk chunk, double score) {
    }
}
