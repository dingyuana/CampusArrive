package com.campusarrive.ai.knowledge;

/**
 * 检索结果来源，对应 API 5.1.5 sources[] 字段契约。
 *
 * <p>规格来源：FR-01-12（知识检索引用片段可溯源）、FR-01-14（AI 生成内容标识）、
 * FR-05-09（知识库脱敏 — 检索片段不含明文 PII）。</p>
 *
 * <p>该记录是 AI 对话接口响应中 {@code data.sources[]} 的领域映射，
 * 由 {@link KnowledgeRetrievalService} 产出，供生成节点引用与前端溯源展示。</p>
 *
 * @param docId     知识文档 ID
 * @param title      文档标题
 * @param section    命中章节
 * @param snippet    命中片段（已脱敏，不含明文 PII）
 * @param score      检索相关性分数 ∈ [0,1]
 * @param category   知识库分类
 */
public record RetrievedSource(
        String docId,
        String title,
        String section,
        String snippet,
        double score,
        KnowledgeCategory category
) {
}
