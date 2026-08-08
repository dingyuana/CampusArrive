package com.campusarrive.ai.knowledge;

/**
 * 知识库分类枚举。
 *
 * <p>规格来源：AID-CA-2026-07 第 5.1 节 — 知识库按内容性质分为四类，
 * 分别对应不同数据来源与更新频率，在 MaxKB 中独立管理、独立检索、独立更新。</p>
 *
 * <ul>
 *   <li>{@link #PROCESS} — 报到流程手册（FR-01-07）</li>
 *   <li>{@link #POI} — 校园 POI 信息（FR-01-08）</li>
 *   <li>{@link #FAQ} — 常见问题 FAQ（FR-01-09）</li>
 *   <li>{@link #MATERIAL} — 材料清单模板（FR-01-10）</li>
 * </ul>
 */
public enum KnowledgeCategory {

    PROCESS("报到流程手册", "process_kb"),
    POI("校园POI信息", "poi_kb"),
    FAQ("常见问题FAQ", "faq_kb"),
    MATERIAL("材料清单模板", "material_kb");

    private final String displayName;
    private final String kbName;

    KnowledgeCategory(String displayName, String kbName) {
        this.displayName = displayName;
        this.kbName = kbName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** MaxKB 中独立知识库的命名标识。 */
    public String getKbName() {
        return kbName;
    }
}
