package com.campusarrive.ai.knowledge;

import java.time.Instant;

/**
 * 知识库文档级元数据。
 *
 * <p>对应 AID 5.1 节四类知识库的文档来源记录，承载文档标识、分类、来源、版本与更新时间，
 * 用于检索结果的来源展示与版本回滚追溯（AID 8.3 知识库安全 — 版本留痕）。</p>
 *
 * @param docId     文档唯一标识（如 kb-manual-2026-v3）
 * @param title      文档标题
 * @param category   知识库分类
 * @param source     数据来源（如"学生处"、"各学院"）
 * @param version    文档版本
 * @param updatedAt  更新时间
 */
public record KnowledgeDocument(
        String docId,
        String title,
        KnowledgeCategory category,
        String source,
        String version,
        Instant updatedAt
) {
}
