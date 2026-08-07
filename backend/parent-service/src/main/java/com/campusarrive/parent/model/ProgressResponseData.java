package com.campusarrive.parent.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 报到进度查询响应数据 DTO。
 *
 * <p>规格来源：FR-03-03 / API 6.2 节 —
 * 家长端查询孩子报到进度，返回脱敏后的学生信息、到校状态、报到环节清单、待办材料。</p>
 *
 * <p>脱敏规则：
 * <ul>
 *   <li>学生姓名脱敏（2 字保留首字，3 字以上保留首尾）</li>
 *   <li>不含身份证号、手机号等敏感字段</li>
 *   <li>环节时间戳仅显示日期部分（YYYY-MM-DD）</li>
 * </ul></p>
 */
@Data
@Builder
public class ProgressResponseData {

    /** 学生 ID。 */
    private String studentId;

    /** 学生脱敏姓名。 */
    private String studentNameMasked;

    /** 到校状态码：not_arrived / arrived / completed。 */
    private String arrivalStatus;

    /** 到校状态显示名称：未到校 / 已到校 / 报到完成。 */
    private String arrivalStatusDisplay;

    /** 报到环节清单。 */
    private List<StepInfo> steps;

    /** 待办材料名称列表。 */
    private List<String> pendingMaterials;

    /** 已完成环节数。 */
    private int completedSteps;

    /** 总环节数。 */
    private int totalSteps;

    /** 整体进度百分比（0-100）。 */
    private int progressPercent;

    /**
     * 报到环节信息。
     */
    @Data
    @Builder
    public static class StepInfo {

        /** 环节代码。 */
        private String stepCode;

        /** 环节显示名称。 */
        private String stepName;

        /** 是否已完成。 */
        private boolean completed;

        /** 完成时间（ISO 8601 日期，未完成为 null）。 */
        private String completedAt;
    }
}
