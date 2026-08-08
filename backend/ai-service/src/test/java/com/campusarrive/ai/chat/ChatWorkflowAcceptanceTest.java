package com.campusarrive.ai.chat;

import com.campusarrive.ai.chat.workflow.ChatWorkflow;
import com.campusarrive.ai.knowledge.HybridRetrievalStrategy;
import com.campusarrive.ai.knowledge.InMemoryKnowledgeStore;
import com.campusarrive.ai.knowledge.SeedKnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI-3.2 对话工作流 — 验收测试。
 *
 * <p>规格来源:DEV-CA-2026-11 TASK-AI-3.2 验收测试清单 AT-AI-006~009,
 * 对应 FR-01-02(流程问答)、FR-01-03(POI 查询)、FR-01-14(内容标识)、FR-01-13(首词响应)。</p>
 *
 * <p>注:AT-AI-009(首词响应时间 P95≤2s)在 DeepSeek 流式接入后测量,
 * 当前本地生成器为同步返回,不适用于 TTFB 测量,此处用总耗时兜底验证。</p>
 */
@DisplayName("AT-AI: 对话工作流验收(AI-3.2)")
class ChatWorkflowAcceptanceTest {

    private ChatWorkflow workflow;

    @BeforeEach
    void setUp() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        SeedKnowledgeBase.loadAll(store);
        HybridRetrievalStrategy retrieval = new HybridRetrievalStrategy(store);
        workflow = new ChatWorkflow(
                new com.campusarrive.ai.chat.workflow.KeywordSafetyFilter(),
                retrieval,
                new com.campusarrive.ai.chat.workflow.PiiMasker(),
                new com.campusarrive.ai.chat.workflow.LocalDeepSeekGenerator(),
                new com.campusarrive.ai.chat.workflow.FallbackFaqMatcher(retrieval));
    }

    @Test
    @DisplayName("AT-AI-006 流程问答:提问环节顺序/材料/地点 → 回答可溯源")
    void processQaTraceable() {
        // FR-01-02:回答与流程版本一致,可溯源
        WorkflowResult result = workflow.execute("报到需要带什么材料", null);

        assertNotNull(result.reply(), "回复非空");
        assertFalse(result.sources().isEmpty(), "应返回知识来源支持溯源");
        // 每条来源附带 doc_id/title/section/snippet/score
        assertAll("来源可溯源",
                () -> assertNotNull(result.sources().get(0).docId(), "doc_id 非空"),
                () -> assertNotNull(result.sources().get(0).title(), "title 非空"),
                () -> assertNotNull(result.sources().get(0).section(), "section 非空"),
                () -> assertNotNull(result.sources().get(0).snippet(), "snippet 非空")
        );
    }

    @Test
    @DisplayName("AT-AI-007 POI 查询:提问校园地点 → 信息与导航库一致")
    void poiQueryConsistent() {
        // FR-01-03:POI 信息与导航库同源
        WorkflowResult result = workflow.execute("食堂在哪里", null);

        assertFalse(result.sources().isEmpty(), "应召回 POI 来源");
        boolean hasCanteen = result.sources().stream()
                .anyMatch(s -> s.title().contains("食堂") || s.snippet().contains("食堂"));
        assertTrue(hasCanteen, "POI 来源应包含食堂信息");
    }

    @Test
    @DisplayName("AT-AI-008 内容标识:AI 回复含可见标识与来源")
    void contentLabelVisible() {
        // FR-01-14:每条 AI 回复带可见标识与可点击来源说明
        WorkflowResult result = workflow.execute("宿舍有没有空调", null);

        assertTrue(result.contentLabel().isAiGenerated(), "标识为 AI 生成");
        assertNotNull(result.contentLabel().labelText(), "标识文案非空");
        assertFalse(result.contentLabel().labelText().isBlank(), "标识文案非空白");
        assertFalse(result.sources().isEmpty(), "应附来源支持可点击说明");
    }

    @Test
    @DisplayName("AT-AI-009 回复时间:本地生成总耗时 ≤ 2s(流式 TTFB 待 DeepSeek 接入)")
    void responseTimeWithinTarget() {
        // FR-01-13:首词响应≤2s。本地生成器同步返回,用总耗时兜底验证
        long start = System.currentTimeMillis();
        workflow.execute("学费怎么交", null);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed <= 2000, "本地生成总耗时应 ≤ 2s,实际: " + elapsed + "ms");
    }
}
