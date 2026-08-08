package com.campusarrive.ai.knowledge;

import java.util.Map;

/**
 * 知识库分块，向量检索单元。
 *
 * <p>规格来源：AID 5.3 分块策略 — 不同类型文档采用差异化分块策略，
 * 每个分块附带元数据（知识库类型、来源文档、更新时间、环节 ID/POI ID/身份类别），
 * 检索时可按元数据过滤，提升检索精准度。</p>
 *
 * @param chunkId   分块唯一标识
 * @param docId      所属文档 ID
 * @param category   知识库分类
 * @param title      所属文档标题（便于来源展示）
 * @param section    命中章节（如"环节二 缴纳学费"）
 * @param content    分块正文（已脱敏入库，不含明文 PII — FR-05-09）
 * @param metadata   元数据（如 student_category、step_id、poi_id）
 */
public record KnowledgeChunk(
        String chunkId,
        String docId,
        KnowledgeCategory category,
        String title,
        String section,
        String content,
        Map<String, String> metadata
) {

    /** 材料清单分块的身份标签（仅 MATERIAL 分类有意义）。 */
    public String getStudentCategory() {
        return metadata == null ? null : metadata.get("student_category");
    }

    /**
     * 检索可读文本：section + content 联合。
     *
     * <p>section 标题本身常是问题主题或 POI 名称（如"宿舍有没有空调"、"第一食堂"），
     * 将其纳入相似度与 BM25 计算可显著提升精准命中召回（AID 5.4）。</p>
     */
    public String searchableText() {
        String sec = section == null ? "" : section;
        String con = content == null ? "" : content;
        return sec.isEmpty() ? con : (con.isEmpty() ? sec : sec + " " + con);
    }
}
