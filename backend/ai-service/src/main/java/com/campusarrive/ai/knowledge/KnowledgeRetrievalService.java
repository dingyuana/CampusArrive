package com.campusarrive.ai.knowledge;

import java.util.List;

/**
 * 知识库检索服务。
 *
 * <p>规格来源：FR-01-12 — 对用户问题检索相关知识片段供生成引用，
 * Top-K 召回相关性满足生成要求，引用片段可溯源。</p>
 *
 * <p>检索采用混合检索策略（向量相似度 0.7 + BM25 关键词 0.3，见 AID 5.4），
 * 由实现类提供具体算法。该接口是 AI-3.2 对话工作流「节点 2：知识检索 RAG」的核心依赖。</p>
 */
public interface KnowledgeRetrievalService {

    /**
     * 检索相关知识片段。
     *
     * @param query    用户问题（应已完成 PII 脱敏，见 AID 8.5）
     * @param topK     返回条数上限（推荐 K=5，见 AID 附录 A）
     * @param category 知识库分类过滤；{@code null} 表示全库混合检索
     * @return 按相关性降序排列的来源列表；无命中返回空列表
     */
    List<RetrievedSource> retrieve(String query, int topK, KnowledgeCategory category);

    /**
     * 按身份检索材料清单。
     *
     * <p>规格来源：FR-01-10 — 不同身份返回不同材料清单，
     * 留学生额外标注签证、体检等特殊要求。</p>
     *
     * @param studentCategory 新生身份类别
     * @return 该身份对应的材料清单来源列表
     */
    List<RetrievedSource> retrieveMaterialList(StudentCategory studentCategory);
}
