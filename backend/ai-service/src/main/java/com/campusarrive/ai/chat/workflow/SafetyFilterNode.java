package com.campusarrive.ai.chat.workflow;

/**
 * 节点 1:安全过滤。
 *
 * <p>规格来源:AID 8.5(安全合规)、FR-05-08(拒答护栏)、FR-05-10(提示词注入防护)。
 * 拦截政治敏感、个人隐私查询、自我伤害、越权请求、与迎新无关等违规问题,
 * 命中即走标准化拒答分支。拦截记录写入安全审计日志(含问题摘要脱敏、违规类别、时间)。</p>
 *
 * <p>实现:关键词正则库 + 提示词注入模式检测,任一命中即拦截。
 * 轻量分类模型在 SEC-6.2 安全护栏任务中接入,此处先用关键词正则占位。</p>
 */
public interface SafetyFilterNode {

    /**
     * 对用户问题执行安全过滤判定。
     *
     * @param message 用户问题(原始,未脱敏)
     * @return 安全过滤判定结果
     */
    SafetyVerdict filter(String message);
}
