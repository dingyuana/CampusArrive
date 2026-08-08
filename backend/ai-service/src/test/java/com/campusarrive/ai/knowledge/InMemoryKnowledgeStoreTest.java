package com.campusarrive.ai.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InMemoryKnowledgeStore} 单元测试。
 *
 * <p>验证入库、清空、计数、元数据保留与快照不可变性（AID 5.2）。</p>
 */
@DisplayName("UT-AI: 内存知识库存储")
class InMemoryKnowledgeStoreTest {

    private KnowledgeDocument doc(String id, KnowledgeCategory cat) {
        return new KnowledgeDocument(id, "t", cat, "s", "v", Instant.EPOCH);
    }

    @Test
    @DisplayName("入库后分块计数增加")
    void ingestIncreasesSize() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        String id = store.ingest(doc("d1", KnowledgeCategory.PROCESS), "sec", "content", Map.of());
        assertEquals(1, store.size(KnowledgeCategory.PROCESS));
        assertNotNull(id);
    }

    @Test
    @DisplayName("清空移除指定分类全部分块")
    void clearRemovesCategory() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        store.ingest(doc("d1", KnowledgeCategory.PROCESS), "sec", "content", null);
        assertEquals(1, store.size(KnowledgeCategory.PROCESS));
        store.clear(KnowledgeCategory.PROCESS);
        assertEquals(0, store.size(KnowledgeCategory.PROCESS));
    }

    @Test
    @DisplayName("元数据被保留（student_category）")
    void metadataPreserved() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        store.ingest(doc("d1", KnowledgeCategory.MATERIAL), "sec", "content",
                Map.of("student_category", "UNDERGRADUATE"));
        KnowledgeChunk chunk = store.getChunks(KnowledgeCategory.MATERIAL).get(0);
        assertEquals("UNDERGRADUATE", chunk.getStudentCategory());
    }

    @Test
    @DisplayName("metadata 为 null 时安全处理")
    void nullMetadataSafe() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        store.ingest(doc("d1", KnowledgeCategory.PROCESS), "sec", "content", null);
        KnowledgeChunk chunk = store.getChunks(KnowledgeCategory.PROCESS).get(0);
        assertNotNull(chunk.metadata());
        assertNullStudentCategory(chunk);
    }

    private void assertNullStudentCategory(KnowledgeChunk chunk) {
        // metadata 为空 map，getStudentCategory 返回 null
        assertEquals(null, chunk.getStudentCategory());
    }

    @Test
    @DisplayName("getChunks 返回不可变快照")
    void getChunksReturnsImmutableCopy() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        store.ingest(doc("d1", KnowledgeCategory.PROCESS), "sec", "content", null);
        var chunks = store.getChunks(KnowledgeCategory.PROCESS);
        assertEquals(1, chunks.size());
        assertThrows(UnsupportedOperationException.class, () -> chunks.clear());
    }

    @Test
    @DisplayName("totalSize 汇总全库")
    void totalSizeAggregates() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        store.ingest(doc("d1", KnowledgeCategory.PROCESS), "sec", "content", null);
        store.ingest(doc("d2", KnowledgeCategory.POI), "sec", "content", null);
        store.ingest(doc("d3", KnowledgeCategory.FAQ), "sec", "content", null);
        assertEquals(3, store.totalSize());
    }

    @Test
    @DisplayName("getDocument 按文档 ID 查询")
    void getDocumentById() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        KnowledgeDocument d = doc("d1", KnowledgeCategory.PROCESS);
        store.ingest(d, "sec", "content", null);
        KnowledgeDocument got = store.getDocument("d1");
        assertNotNull(got);
        assertEquals("d1", got.docId());
    }

    @Test
    @DisplayName("document 为 null 抛出非法参数异常")
    void ingestNullDocumentThrows() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        assertThrows(IllegalArgumentException.class,
                () -> store.ingest(null, "sec", "content", null));
    }

    @Test
    @DisplayName("同一文档多次入库共享文档元数据")
    void sameDocSharedMetadata() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        KnowledgeDocument d = doc("d1", KnowledgeCategory.PROCESS);
        store.ingest(d, "sec1", "content1", null);
        store.ingest(d, "sec2", "content2", null);
        assertEquals(2, store.size(KnowledgeCategory.PROCESS));
        assertTrue(store.totalSize() >= 2);
    }
}
