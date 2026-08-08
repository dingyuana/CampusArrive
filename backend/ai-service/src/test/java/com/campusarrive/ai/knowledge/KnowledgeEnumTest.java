package com.campusarrive.ai.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 知识库分类与身份枚举单元测试。
 *
 * <p>确保枚举值与 MaxKB 知识库命名、身份标签契约一致（AID 5.1 / FR-01-10）。</p>
 */
@DisplayName("UT-AI: 知识库枚举契约")
class KnowledgeEnumTest {

    @Test
    @DisplayName("KnowledgeCategory 四类与 MaxKB 库命名一致")
    void categoryKbNames() {
        assertEquals("process_kb", KnowledgeCategory.PROCESS.getKbName());
        assertEquals("poi_kb", KnowledgeCategory.POI.getKbName());
        assertEquals("faq_kb", KnowledgeCategory.FAQ.getKbName());
        assertEquals("material_kb", KnowledgeCategory.MATERIAL.getKbName());
        assertEquals(4, KnowledgeCategory.values().length, "应有 4 个知识库分类");
    }

    @Test
    @DisplayName("KnowledgeCategory 显示名称非空")
    void categoryDisplayNames() {
        for (KnowledgeCategory c : KnowledgeCategory.values()) {
            assertNotNull(c.getDisplayName(), c.name() + " 显示名称非空");
        }
    }

    @Test
    @DisplayName("StudentCategory 三类身份")
    void studentCategories() {
        assertEquals(3, StudentCategory.values().length);
        assertEquals("本科", StudentCategory.UNDERGRADUATE.getDisplayName());
        assertEquals("研究生", StudentCategory.GRADUATE.getDisplayName());
        assertEquals("留学生", StudentCategory.INTERNATIONAL.getDisplayName());
    }

    @Test
    @DisplayName("valueOf 按名称解析")
    void valueOfByName() {
        assertEquals(KnowledgeCategory.PROCESS, KnowledgeCategory.valueOf("PROCESS"));
        assertEquals(StudentCategory.GRADUATE, StudentCategory.valueOf("GRADUATE"));
    }
}
