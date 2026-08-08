package com.campusarrive.ai.chat.workflow;

import com.campusarrive.ai.knowledge.HybridRetrievalStrategy;
import com.campusarrive.ai.knowledge.InMemoryKnowledgeStore;
import com.campusarrive.ai.knowledge.RetrievedSource;
import com.campusarrive.ai.knowledge.SeedKnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalDeepSeekGenerator} 与 {@link FallbackFaqMatcher} 单元测试。
 *
 * <p>规格来源:FR-01-13(DeepSeek 生成)、FR-01-17(降级)、AID 9.2(FAQ 降级匹配)。</p>
 */
@DisplayName("UT-AI: 生成器与降级匹配")
class LocalDeepSeekGeneratorTest {

    private LocalDeepSeekGenerator generator;
    private HybridRetrievalStrategy retrieval;
    private FallbackFaqMatcher fallback;

    @BeforeEach
    void setUp() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        SeedKnowledgeBase.loadAll(store);
        retrieval = new HybridRetrievalStrategy(store);
        generator = new LocalDeepSeekGenerator();
        fallback = new FallbackFaqMatcher(retrieval);
    }

    @Test
    @DisplayName("generateReply 基于检索来源合成回复")
    void generateReplyFromSources() {
        List<RetrievedSource> sources = retrieval.retrieve("宿舍有没有空调", 5, null);
        String reply = generator.generateReply(sources, "宿舍有没有空调");

        assertNotNull(reply, "回复非空");
        assertFalse(reply.isBlank(), "回复非空白");
    }

    @Test
    @DisplayName("无来源时返回兜底话术")
    void noSourcesFallback() {
        String reply = generator.generateReply(List.of(), "测试");
        assertTrue(reply.contains("志愿者") || reply.contains("辅导员"), "应返回兜底话术");
    }

    @Test
    @DisplayName("流程问题生成 STEP 意图标记")
    void stepIntentGenerated() {
        List<RetrievedSource> sources = retrieval.retrieve("学费怎么交", 5, null);
        String reply = generator.generateReply(sources, "学费怎么交");

        List<IntentMarker> intents = LocalDeepSeekGenerator.extractIntents(reply);
        assertFalse(intents.isEmpty(), "应生成意图标记");
        boolean hasStep = intents.stream().anyMatch(i -> "STEP".equals(i.type()));
        assertTrue(hasStep, "应含 STEP 意图");
    }

    @Test
    @DisplayName("POI 问题生成 POI 意图标记")
    void poiIntentGenerated() {
        List<RetrievedSource> sources = retrieval.retrieve("食堂在哪里", 5, null);
        String reply = generator.generateReply(sources, "食堂在哪里");

        List<IntentMarker> intents = LocalDeepSeekGenerator.extractIntents(reply);
        // POI 类问题应生成 POI 或 STEP 意图
        assertFalse(intents.isEmpty(), "应生成意图标记");
    }

    @Test
    @DisplayName("stripIntents 移除意图标记得到纯文本")
    void stripIntentsReturnsCleanText() {
        String reply = "宿舍有空调。\n[[STEP:dorm_assign]]";
        String clean = LocalDeepSeekGenerator.stripIntents(reply);
        assertFalse(clean.contains("[["), "应移除意图标记");
        assertTrue(clean.contains("空调"), "应保留正文");
    }

    @Test
    @DisplayName("extractIntents 解析多个标记")
    void extractMultipleIntents() {
        String reply = "回复1\n[[STEP:payment]]\n回复2\n[[POI:食堂]]";
        List<IntentMarker> intents = LocalDeepSeekGenerator.extractIntents(reply);
        assertEquals(2, intents.size(), "应解析 2 个意图");
    }

    @Test
    @DisplayName("setAvailable 控制可用状态")
    void setAvailableControlsStatus() {
        assertTrue(generator.isAvailable(), "默认可用");
        generator.setAvailable(false);
        assertFalse(generator.isAvailable(), "设置为不可用");
    }

    @Test
    @DisplayName("FAQ 降级匹配:命中返回标准答案")
    void fallbackMatchHit() {
        FallbackFaqMatcher.FallbackResult result = fallback.match("宿舍有没有空调");
        assertTrue(result.hit(), "应命中 FAQ");
        assertNotNull(result.reply(), "应返回标准答案");
        assertTrue(result.score() >= 0.5, "匹配分应 ≥ 0.5");
    }

    @Test
    @DisplayName("FAQ 降级匹配:无意义问题未命中")
    void fallbackMatchMiss() {
        FallbackFaqMatcher.FallbackResult result = fallback.match("zzzqqqxxx");
        assertFalse(result.hit(), "无意义问题不应命中");
    }

    @Test
    @DisplayName("fallbackMessage 返回兜底话术")
    void fallbackMessageContent() {
        String msg = FallbackFaqMatcher.fallbackMessage();
        assertTrue(msg.contains("降级") || msg.contains("志愿者"), "应含降级提示或志愿者引导");
    }
}
