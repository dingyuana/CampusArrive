package com.campusarrive.ai.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HybridRetrievalStrategy} 单元测试。
 *
 * <p>验证混合检索（向量 0.7 + BM25 0.3）的 Top-K、分类过滤、
 * 低置信判定、PII 脱敏与材料清单按身份检索（AID 5.4 / 6.2 / FR-01-10）。</p>
 */
@DisplayName("UT-AI: 混合检索策略")
class HybridRetrievalStrategyTest {

    private InMemoryKnowledgeStore store;
    private HybridRetrievalStrategy strategy;

    @BeforeEach
    void setUp() {
        store = new InMemoryKnowledgeStore();
        SeedKnowledgeBase.loadAll(store);
        strategy = new HybridRetrievalStrategy(store);
    }

    @Test
    @DisplayName("retrieve 返回不超过 Top-K 条")
    void retrieveReturnsTopK() {
        var res = strategy.retrieve("缴费", 3, KnowledgeCategory.PROCESS);
        assertTrue(res.size() <= 3, "结果数应 ≤ topK");
    }

    @Test
    @DisplayName("空分类返回空列表")
    void emptyCategoryReturnsEmpty() {
        InMemoryKnowledgeStore empty = new InMemoryKnowledgeStore();
        HybridRetrievalStrategy s = new HybridRetrievalStrategy(empty);
        assertTrue(s.retrieve("query", 5, KnowledgeCategory.POI).isEmpty());
    }

    @Test
    @DisplayName("全库检索（category=null）跨分类召回")
    void retrieveAllCategoriesWhenNull() {
        var res = strategy.retrieve("缴费", 10, null);
        assertFalse(res.isEmpty(), "全库应能召回缴费相关片段");
    }

    @Test
    @DisplayName("低置信判定：无匹配 query 标记低置信")
    void lowConfidenceDetected() {
        HybridRetrievalStrategy highThreshold = new HybridRetrievalStrategy(store, 0.99);
        var res = highThreshold.retrieve("zzzqqqxxx无意义查询", 5, KnowledgeCategory.PROCESS);
        assertTrue(highThreshold.isLowConfidence(res), "无匹配应判定为低置信");
    }

    @Test
    @DisplayName("高置信命中不判低置信")
    void highConfidenceNotLow() {
        var res = strategy.retrieve("宿舍有没有空调", 5, KnowledgeCategory.FAQ);
        assertFalse(strategy.isLowConfidence(res), "精准命中不应判低置信");
    }

    @Test
    @DisplayName("空结果判低置信")
    void emptyResultLowConfidence() {
        assertTrue(strategy.isLowConfidence(java.util.List.of()));
    }

    @Test
    @DisplayName("null 结果判低置信")
    void nullResultLowConfidence() {
        assertTrue(strategy.isLowConfidence(null));
    }

    @Test
    @DisplayName("snippet 对手机号、身份证号脱敏（FR-05-09）")
    void snippetMaskedPii() {
        InMemoryKnowledgeStore s = new InMemoryKnowledgeStore();
        KnowledgeDocument doc = new KnowledgeDocument("d", "t", KnowledgeCategory.PROCESS,
                "s", "v", Instant.EPOCH);
        s.ingest(doc, "sec", "联系电话13800138000身份证110101199003071234其他", null);
        HybridRetrievalStrategy strat = new HybridRetrievalStrategy(s);
        var res = strat.retrieve("13800138000", 1, KnowledgeCategory.PROCESS);
        if (!res.isEmpty()) {
            String snip = res.get(0).snippet();
            assertFalse(snip.contains("13800138000"), "手机号应脱敏: " + snip);
            assertFalse(snip.contains("110101199003071234"), "身份证号应脱敏: " + snip);
        }
    }

    @Test
    @DisplayName("snippet 超长截断并补省略号")
    void snippetTruncated() {
        String longText = "a".repeat(200);
        InMemoryKnowledgeStore s = new InMemoryKnowledgeStore();
        KnowledgeDocument doc = new KnowledgeDocument("d", "t", KnowledgeCategory.PROCESS,
                "s", "v", Instant.EPOCH);
        s.ingest(doc, "sec", longText, null);
        HybridRetrievalStrategy strat = new HybridRetrievalStrategy(s);
        var res = strat.retrieve("aa", 1, KnowledgeCategory.PROCESS);
        if (!res.isEmpty()) {
            String snip = res.get(0).snippet();
            assertTrue(snip.endsWith("..."), "超长 snippet 应以省略号结尾");
            assertTrue(snip.length() <= 123, "snippet 含省略号不超过 123 字符");
        }
    }

    @Test
    @DisplayName("材料清单按身份过滤（本科/研究生/留学生各 1 条）")
    void retrieveMaterialListFiltersByStudentCategory() {
        assertEquals(1, strategy.retrieveMaterialList(StudentCategory.UNDERGRADUATE).size());
        assertEquals(1, strategy.retrieveMaterialList(StudentCategory.GRADUATE).size());
        assertEquals(1, strategy.retrieveMaterialList(StudentCategory.INTERNATIONAL).size());
    }

    @Test
    @DisplayName("材料清单 null 身份返回空")
    void retrieveMaterialListNullReturnsEmpty() {
        assertTrue(strategy.retrieveMaterialList(null).isEmpty());
    }

    @Test
    @DisplayName("topK 为 0 时返回全部命中")
    void topKZeroReturnsAll() {
        var res = strategy.retrieve("的", 0, KnowledgeCategory.PROCESS);
        // topK<=0 视为不限制
        assertTrue(res.size() >= 1);
    }
}
