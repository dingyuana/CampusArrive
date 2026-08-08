package com.campusarrive.ai.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI-3.1 知识库构建与检索验证 — 验收测试。
 *
 * <p>规格来源：DEV-CA-2026-11 任务 TASK-AI-3.1 测试先行清单 AT-AI-001~005，
 * 对应 FR-01-07 / FR-01-08 / FR-01-09 / FR-01-10 / FR-01-12。</p>
 *
 * <p>验收基线：四类知识库种子数据加载后，对典型迎新提问应召回相关片段，
 * 支持溯源（每条结果附带 doc_id/title/section/snippet/score）。</p>
 */
@DisplayName("AT-AI: 知识库构建与检索验收（AI-3.1）")
class KnowledgeAcceptanceTest {

    private InMemoryKnowledgeStore store;
    private KnowledgeRetrievalService retrieval;

    @BeforeEach
    void setUp() {
        store = new InMemoryKnowledgeStore();
        SeedKnowledgeBase.loadAll(store);
        retrieval = new HybridRetrievalStrategy(store);
    }

    @Test
    @DisplayName("AT-AI-001 流程手册检索：提问报到材料 → 召回相关片段（召回率达标）")
    void retrieveProcessManual() {
        // FR-01-07：报到流程手册结构化入库，检索召回率≥85%
        var sources = retrieval.retrieve("报到需要带什么材料", 5, KnowledgeCategory.PROCESS);

        assertFalse(sources.isEmpty(), "应召回流程手册片段");
        // 召回片段须与"材料"主题相关（命中材料或录取通知书关键词）
        boolean hit = sources.stream().anyMatch(s ->
                s.snippet().contains("材料") || s.snippet().contains("录取通知书"));
        assertTrue(hit, "召回片段应与报到材料相关");
    }

    @Test
    @DisplayName("AT-AI-002 POI 检索：提问食堂在哪里 → 返回正确 POI")
    void retrievePoi() {
        // FR-01-08：校园 POI 信息入库，POI 与导航库同源
        var sources = retrieval.retrieve("食堂在哪里", 5, KnowledgeCategory.POI);

        assertFalse(sources.isEmpty(), "应召回 POI 片段");
        boolean hit = sources.stream().anyMatch(s ->
                s.title().contains("食堂") || s.snippet().contains("食堂"));
        assertTrue(hit, "应返回食堂 POI");
    }

    @Test
    @DisplayName("AT-AI-003 FAQ 检索：高频问题匹配标准答案")
    void retrieveFaq() {
        // FR-01-09：常见问题 FAQ 入库，可作为降级答案源
        var sources = retrieval.retrieve("宿舍有没有空调", 5, KnowledgeCategory.FAQ);

        assertFalse(sources.isEmpty(), "应召回 FAQ 片段");
        assertEquals(SeedKnowledgeBase.DOC_FAQ, sources.get(0).docId(), "首选来源应为 FAQ 文档");
        assertTrue(sources.get(0).snippet().contains("空调"), "应匹配空调相关标准答案");
    }

    @Test
    @DisplayName("AT-AI-004 材料清单：按身份返回不同清单（本科/研究生/留学生）")
    void retrieveMaterialListByStudentCategory() {
        // FR-01-10：材料清单模板按身份维护，不同身份返回不同清单
        var undergrad = retrieval.retrieveMaterialList(StudentCategory.UNDERGRADUATE);
        var graduate = retrieval.retrieveMaterialList(StudentCategory.GRADUATE);
        var international = retrieval.retrieveMaterialList(StudentCategory.INTERNATIONAL);

        assertFalse(undergrad.isEmpty(), "本科清单非空");
        assertFalse(graduate.isEmpty(), "研究生清单非空");
        assertFalse(international.isEmpty(), "留学生清单非空");

        assertTrue(undergrad.get(0).snippet().contains("高中档案"), "本科应含高中档案");
        assertTrue(graduate.get(0).snippet().contains("学位证"), "研究生应含学位证");
        assertTrue(international.get(0).snippet().contains("护照"), "留学生应含护照");
    }

    @Test
    @DisplayName("AT-AI-005 溯源：每条结果附带来源片段（doc_id/title/section/snippet/score）")
    void sourcesTraceable() {
        // FR-01-12：检索引用片段可溯源
        var sources = retrieval.retrieve("缴费在哪交", 5, null);

        assertFalse(sources.isEmpty(), "应召回相关片段");
        for (RetrievedSource s : sources) {
            assertNotNull(s.docId(), "doc_id 非空");
            assertNotNull(s.title(), "title 非空");
            assertNotNull(s.section(), "section 非空");
            assertNotNull(s.snippet(), "snippet 非空");
            assertTrue(s.score() >= 0.0 && s.score() <= 1.0,
                    "score ∈ [0,1]: " + s.score());
        }
    }
}
