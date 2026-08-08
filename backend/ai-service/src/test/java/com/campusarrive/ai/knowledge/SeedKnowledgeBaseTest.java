package com.campusarrive.ai.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SeedKnowledgeBase} 单元测试。
 *
 * <p>验证四类种子知识库数据的完整性与元数据正确性（FR-01-07/08/09/10）。</p>
 */
@DisplayName("UT-AI: 种子知识库数据")
class SeedKnowledgeBaseTest {

    @Test
    @DisplayName("loadAll 填充全部四类知识库")
    void loadAllPopulatesAllCategories() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        SeedKnowledgeBase.loadAll(store);

        assertTrue(store.size(KnowledgeCategory.PROCESS) >= 5, "流程手册应≥5环节");
        assertTrue(store.size(KnowledgeCategory.POI) >= 5, "POI 应≥5条");
        assertTrue(store.size(KnowledgeCategory.FAQ) >= 5, "FAQ 应≥5条");
        assertEquals(3, store.size(KnowledgeCategory.MATERIAL), "材料清单应有 3 类身份");
        assertTrue(store.totalSize() >= 18, "全库应≥18条");
    }

    @Test
    @DisplayName("各类种子数据可独立加载")
    void loadEachIndependently() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();

        SeedKnowledgeBase.loadProcessManual(store);
        assertTrue(store.size(KnowledgeCategory.PROCESS) >= 5);
        assertEquals(0, store.size(KnowledgeCategory.POI));

        SeedKnowledgeBase.loadPoi(store);
        assertTrue(store.size(KnowledgeCategory.POI) >= 5);
        assertEquals(0, store.size(KnowledgeCategory.FAQ));

        SeedKnowledgeBase.loadFaq(store);
        assertTrue(store.size(KnowledgeCategory.FAQ) >= 5);
        assertEquals(0, store.size(KnowledgeCategory.MATERIAL));

        SeedKnowledgeBase.loadMaterial(store);
        assertEquals(3, store.size(KnowledgeCategory.MATERIAL));
    }

    @Test
    @DisplayName("材料清单分块含 student_category 元数据")
    void materialListHasStudentCategory() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        SeedKnowledgeBase.loadMaterial(store);

        for (KnowledgeChunk c : store.getChunks(KnowledgeCategory.MATERIAL)) {
            assertNotNull(c.getStudentCategory(), "材料分块须含 student_category");
        }
    }

    @Test
    @DisplayName("文档元数据版本与来源可追溯（AID 8.3 版本留痕）")
    void documentMetadataTraceable() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        SeedKnowledgeBase.loadAll(store);

        KnowledgeDocument processDoc = store.getDocument(SeedKnowledgeBase.DOC_PROCESS);
        assertNotNull(processDoc);
        assertEquals(KnowledgeCategory.PROCESS, processDoc.category());
        assertEquals("v3", processDoc.version());

        KnowledgeDocument faqDoc = store.getDocument(SeedKnowledgeBase.DOC_FAQ);
        assertNotNull(faqDoc);
        assertEquals("学生处", faqDoc.source());
    }
}
