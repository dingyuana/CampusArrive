package com.campusarrive.ai.chat.workflow;

/**
 * 安全过滤判定结果(节点 1 产出)。
 *
 * <p>规格来源:AID 8.5 / FR-05-08。节点 1 对用户问题执行敏感词正则 + 分类判定,
 * 命中违规类别则拦截并走标准化拒答分支。</p>
 *
 * @param blocked       是否拦截
 * @param violationCategory 违规类别(政治敏感/个人隐私查询/自我伤害/越权请求/与迎新无关);未拦截为 null
 * @param rejectMessage 拦截时的标准化拒答话术;未拦截为 null
 */
public record SafetyVerdict(boolean blocked, String violationCategory, String rejectMessage) {

    /** 通过(未命中违规)。 */
    public static SafetyVerdict pass() {
        return new SafetyVerdict(false, null, null);
    }

    /** 拦截。 */
    public static SafetyVerdict block(String category, String rejectMessage) {
        return new SafetyVerdict(true, category, rejectMessage);
    }
}
