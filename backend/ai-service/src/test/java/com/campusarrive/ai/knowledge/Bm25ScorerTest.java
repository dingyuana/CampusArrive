package com.campusarrive.ai.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Bm25Scorer} 单元测试。
 *
 * <p>验证分词与 BM25 评分的排序行为，支撑混合检索的关键词分量（AID 5.4）。</p>
 */
@DisplayName("UT-AI: BM25 关键词评分")
class Bm25ScorerTest {

    private KnowledgeDocument doc() {
        return new KnowledgeDocument("d1", "t", KnowledgeCategory.FAQ, "s", "v", Instant.EPOCH);
    }

    @Test
    @DisplayName("中文按字、英文按词分词")
    void tokenizeChineseByCharEnglishByWord() {
        var tokens = Bm25Scorer.tokenize("食堂abc 8:00");
        assertTrue(tokens.contains("食"), "含中文单字'食'");
        assertTrue(tokens.contains("堂"), "含中文单字'堂'");
        assertTrue(tokens.contains("abc"), "含英文词'abc'");
        assertTrue(tokens.contains("8"), "含数字'8'");
    }

    @Test
    @DisplayName("按相关性排序：命中词多的片段得分高")
    void scoreOrdersByRelevance() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        KnowledgeDocument d = doc();
        store.ingest(d, "s1", "食堂在哪里 早餐时间", null);
        store.ingest(d, "s2", "图书馆开门时间 借阅", null);
        List<KnowledgeChunk> chunks = store.getChunks(KnowledgeCategory.FAQ);

        List<Bm25Scorer.ScoredChunk> scored = Bm25Scorer.score("食堂在哪里", chunks);

        assertEquals(2, scored.size(), "应返回全部候选");
        assertEquals("d1-c1", scored.get(0).chunk().chunkId(), "食堂片段应排首位");
        assertTrue(scored.get(0).score() >= scored.get(1).score(), "降序排列");
    }

    @Test
    @DisplayName("空查询返回空结果")
    void emptyQueryReturnsEmpty() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        store.ingest(doc(), "s", "内容", null);
        assertTrue(Bm25Scorer.score("", store.getChunks(KnowledgeCategory.FAQ)).isEmpty());
    }

    @Test
    @DisplayName("空候选集合返回空结果")
    void emptyCandidatesReturnsEmpty() {
        assertTrue(Bm25Scorer.score("查询", List.of()).isEmpty());
    }

    @Test
    @DisplayName("无命中词时分数为 0")
    void noTermMatchScoreZero() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        store.ingest(doc(), "s", "完全无关的内容", null);
        List<Bm25Scorer.ScoredChunk> scored =
                Bm25Scorer.score("library", store.getChunks(KnowledgeCategory.FAQ));
        assertEquals(1, scored.size());
        assertEquals(0.0, scored.get(0).score(), "无命中词分数应为 0");
    }
}
