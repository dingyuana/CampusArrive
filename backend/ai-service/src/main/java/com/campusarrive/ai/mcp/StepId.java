package com.campusarrive.ai.mcp;

/**
 * 报到流程环节标识枚举（AID 7.1.1 navigate_to_step 参数）。
 *
 * <p>规格来源：FR-01-15（MCP 工具-跳转报到环节）。
 * 枚举值与 {@code LocalDeepSeekGenerator} 输出的 {@code [[STEP:xxx]]} 意图标记一致，
 * 确保 AI 意图标记到 MCP 工具调用的映射无歧义。</p>
 */
public enum StepId {

    /** 身份核验（环节一）。 */
    VERIFICATION("verification", "身份核验", "/pages/checkin/verify/index"),
    /** 缴纳学费（环节二）。 */
    PAYMENT("payment", "缴纳学费", "/pages/checkin/pay/index"),
    /** 宿舍入住（环节三）。 */
    DORM_ASSIGN("dorm_assign", "宿舍入住", "/pages/checkin/dorm/index"),
    /** 入学体检（环节四）。 */
    CHECKIN("checkin", "入学体检", "/pages/checkin/health/index"),
    /** 材料提交（环节五）。 */
    MATERIAL_UPLOAD("material_upload", "材料提交", "/pages/checkin/material/index");

    private final String code;
    private final String displayName;
    private final String pageUrl;

    StepId(String code, String displayName, String pageUrl) {
        this.code = code;
        this.displayName = displayName;
        this.pageUrl = pageUrl;
    }

    /** 工具参数中使用的标识符（如 "payment"）。 */
    public String code() {
        return code;
    }

    /** 环节中文名称（用于展示）。 */
    public String displayName() {
        return displayName;
    }

    /** 小程序页面路径。 */
    public String pageUrl() {
        return pageUrl;
    }

    /**
     * 根据 code 查找枚举值。
     *
     * @param code 环节标识（如 "payment"）
     * @return 对应枚举值；不存在返回 null
     */
    public static StepId fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (StepId s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }

    /** 判断 code 是否为合法环节标识。 */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
