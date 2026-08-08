package com.campusarrive.ai.chat.workflow;

import com.campusarrive.ai.chat.WorkflowResult;
import com.campusarrive.ai.knowledge.HybridRetrievalStrategy;
import com.campusarrive.ai.knowledge.InMemoryKnowledgeStore;
import com.campusarrive.ai.knowledge.SeedKnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * UT-AI-001:工作流编排节点配置可版本化。
 *
 * <p>规格来源:FR-01-11(MaxKB 工作流编排,变更可版本化)、NFR-MAINT-02。
 * 验证工作流配置参数(Top-K、阈值、限流等)集中管理且可追溯,
 * 节点配置变更不影响已有对话逻辑的正确性。</p>
 *
 * <p>当前工作流常量定义在 {@link ChatWorkflow} 中作为版本化配置基线,
 * 后续可抽取为外部配置文件(application.yml)实现动态版本化。</p>
 */
@DisplayName("UT-AI-001: 工作流编排可版本化")
class WorkflowVersioningTest {

    private ChatWorkflow workflow;

    @BeforeEach
    void setUp() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        SeedKnowledgeBase.loadAll(store);
        HybridRetrievalStrategy retrieval = new HybridRetrievalStrategy(store);
        workflow = new ChatWorkflow(
                new KeywordSafetyFilter(), retrieval, new PiiMasker(),
                new LocalDeepSeekGenerator(), new FallbackFaqMatcher(retrieval));
    }

    @Test
    @DisplayName("工作流配置参数可读取(版本化基线)")
    void configParamsReadable() {
        assertEquals(5, ChatWorkflow.TOP_K, "Top-K=5(AID 附录 A)");
        assertEquals(0.6, ChatWorkflow.LOW_CONFIDENCE_THRESHOLD, "相似度阈值=0.6");
        assertEquals(500, ChatWorkflow.MAX_REPLY_LENGTH, "回复最大长度=500");
    }

    @Test
    @DisplayName("节点配置变更后工作流仍正确执行")
    void configChangeWorkflowStillCorrect() {
        // 使用自定义参数构造工作流(模拟配置变更)
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        SeedKnowledgeBase.loadAll(store);
        HybridRetrievalStrategy retrieval = new HybridRetrievalStrategy(store, 0.5); // 阈值变更
        ChatWorkflow customWorkflow = new ChatWorkflow(
                new KeywordSafetyFilter(), retrieval, new PiiMasker(),
                new LocalDeepSeekGenerator(), new FallbackFaqMatcher(retrieval));

        WorkflowResult result = customWorkflow.execute("报到材料", null);
        assertNotNull(result.reply(), "配置变更后仍应返回回复");
    }

    @Test
    @DisplayName("工作流节点顺序固定:安全→检索→脱敏→生成→标识")
    void nodeOrderFixed() {
        // 验证安全过滤优先于检索:违规问题不返回知识来源
        WorkflowResult blocked = workflow.execute("查一下某学号同学的信息", null);
        assertEquals(true, blocked.reply().contains("无法查询") || blocked.reply().contains("个人信息"),
                "安全过滤应先执行,返回拒答");

        // 验证正常流程:安全通过→检索→生成→标识
        WorkflowResult normal = workflow.execute("学费怎么交", null);
        assertNotNull(normal.reply(), "正常流程应返回回复");
        assertNotNull(normal.contentLabel(), "应含内容标识");
    }
}
