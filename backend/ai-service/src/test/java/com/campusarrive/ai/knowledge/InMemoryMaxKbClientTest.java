package com.campusarrive.ai.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InMemoryMaxKbClient} 单元测试。
 *
 * <p>验证 MaxKB 客户端占位实现将检索委托给混合检索策略（AID 6.1）。</p>
 */
@DisplayName("UT-AI: MaxKB 客户端占位实现")
class InMemoryMaxKbClientTest {

    private InMemoryMaxKbClient client;

    @BeforeEach
    void setUp() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        SeedKnowledgeBase.loadAll(store);
        client = new InMemoryMaxKbClient(new HybridRetrievalStrategy(store));
    }

    @Test
    @DisplayName("isAvailable 始终返回 true（占位实现可用）")
    void isAvailableTrue() {
        assertTrue(client.isAvailable());
    }

    @Test
    @DisplayName("search 委托给混合检索策略")
    void searchDelegatesToStrategy() {
        var res = client.search("食堂在哪里", 5, KnowledgeCategory.POI);
        assertFalse(res.isEmpty(), "应召回食堂 POI");
        boolean hit = res.stream().anyMatch(s ->
                s.title().contains("食堂") || s.snippet().contains("食堂"));
        assertTrue(hit, "结果应包含食堂");
    }

    @Test
    @DisplayName("search 全库检索（category=null）")
    void searchAllCategories() {
        var res = client.search("缴费", 10, null);
        assertFalse(res.isEmpty());
    }
}
