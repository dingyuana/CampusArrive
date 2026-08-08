package com.campusarrive.ai.knowledge;

/**
 * 新生身份类别，用于材料清单差异化检索。
 *
 * <p>规格来源：FR-01-10 — 材料清单模板入库按身份维护，
 * 本科 / 研究生 / 留学生返回不同清单（留学生额外标注签证、体检等特殊要求）。</p>
 */
public enum StudentCategory {

    UNDERGRADUATE("本科"),
    GRADUATE("研究生"),
    INTERNATIONAL("留学生");

    private final String displayName;

    StudentCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
