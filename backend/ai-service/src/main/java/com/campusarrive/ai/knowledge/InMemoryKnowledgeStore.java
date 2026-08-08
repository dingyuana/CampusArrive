package com.campusarrive.ai.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存版知识库存储（线程安全）。
 *
 * <p>规格来源：AID 5.1 / 5.2 — 真实环境由 MaxKB + PostgreSQL/pgvector 承载四类知识库；
 * 此处提供内存实现，用于：</p>
 * <ul>
 *   <li>单元测试与验收测试（不依赖 MaxKB/pgvector 环境）；</li>
 *   <li>FAQ 降级检索（AID 9.2 — DeepSeek 不可用时本地关键词匹配）；</li>
 *   <li>种子数据加载（{@link SeedKnowledgeBase}）。</li>
 * </ul>
 *
 * <p>实现 {@link KnowledgeIngestionService}，维护分类 → 分块列表的映射，
 * 并保留文档级元数据以支持来源展示与版本追溯（AID 8.3）。</p>
 */
public class InMemoryKnowledgeStore implements KnowledgeIngestionService {

    private final Map<KnowledgeCategory, List<KnowledgeChunk>> store = new ConcurrentHashMap<>();
    private final Map<String, KnowledgeDocument> docs = new ConcurrentHashMap<>();
    private final AtomicInteger chunkSeq = new AtomicInteger(0);

    public InMemoryKnowledgeStore() {
        for (KnowledgeCategory c : KnowledgeCategory.values()) {
            store.put(c, Collections.synchronizedList(new ArrayList<>()));
        }
    }

    @Override
    public String ingest(KnowledgeDocument document, String section, String content, Map<String, String> metadata) {
        if (document == null) {
            throw new IllegalArgumentException("document 不能为空");
        }
        docs.putIfAbsent(document.docId(), document);
        String chunkId = document.docId() + "-c" + chunkSeq.incrementAndGet();
        Map<String, String> meta = metadata == null ? Map.of() : new HashMap<>(metadata);
        KnowledgeChunk chunk = new KnowledgeChunk(
                chunkId, document.docId(), document.category(),
                document.title(), section, content, meta);
        store.get(document.category()).add(chunk);
        return chunkId;
    }

    @Override
    public void clear(KnowledgeCategory category) {
        store.get(category).clear();
    }

    @Override
    public int size(KnowledgeCategory category) {
        return store.get(category).size();
    }

    /** 返回指定分类分块列表的不可变快照。 */
    public List<KnowledgeChunk> getChunks(KnowledgeCategory category) {
        return List.copyOf(store.get(category));
    }

    /** 按文档 ID 查询文档元数据。 */
    public KnowledgeDocument getDocument(String docId) {
        return docs.get(docId);
    }

    /** 全库分块总数。 */
    public int totalSize() {
        return store.values().stream().mapToInt(List::size).sum();
    }
}
