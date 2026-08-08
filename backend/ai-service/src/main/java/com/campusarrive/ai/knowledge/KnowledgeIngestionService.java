package com.campusarrive.ai.knowledge;

import java.util.Map;

/**
 * 知识库入库服务。
 *
 * <p>规格来源：AID 5.2 文档预处理与入库 —
 * 原始文档经 PII 扫描 → 脱敏 → 清洗 → 分块 → 向量化 → 入库流水线后存入知识库。</p>
 *
 * <p>该接口抽象入库操作，实现可为内存存储（测试/降级）或 MaxKB + pgvector（生产）。</p>
 */
public interface KnowledgeIngestionService {

    /**
     * 入库一个分块。
     *
     * @param document 所属文档元数据
     * @param section  命中章节（如"环节二 缴纳学费"）
     * @param content  分块正文（应已脱敏，不含明文 PII）
     * @param metadata 元数据（如 student_category、step_id、poi_id）；可为 {@code null}
     * @return 分块唯一标识
     */
    String ingest(KnowledgeDocument document, String section, String content, Map<String, String> metadata);

    /** 清空指定分类的全部分块。 */
    void clear(KnowledgeCategory category);

    /** 返回指定分类的分块数量。 */
    int size(KnowledgeCategory category);
}
