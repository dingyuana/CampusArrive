package com.campusarrive.ai.chat.workflow;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 节点 1:安全过滤 — 关键词正则 + 提示词注入检测实现。
 *
 * <p>规格来源:AID 8.5 安全合规、FR-05-08 拒答护栏、FR-05-10 提示词注入防护。
 * 拦截五类违规:政治敏感、个人隐私查询、自我伤害、越权请求、与迎新无关,
 * 并检测提示词注入模式(忽略以上指令/ignore previous/你现在是/system:等)。</p>
 *
 * <p>轻量分类模型在 SEC-6.2 任务中接入,当前用关键词正则库占位。
 * 拦截命中即返回标准化拒答话术,由工作流走拒答分支。</p>
 */
public class KeywordSafetyFilter implements SafetyFilterNode {

    /** 政治敏感关键词(示例,SEC-6.2 扩充完整词库)。 */
    private static final List<Pattern> POLITICAL_PATTERNS = List.of(
            Pattern.compile("政治|国家领导|政权|颠覆"),
            Pattern.compile("反动|叛乱|政变")
    );

    /** 个人隐私查询模式(查某学号/某人的信息)。 */
    private static final List<Pattern> PRIVACY_PATTERNS = List.of(
            Pattern.compile("查一下.{0,6}(学号|信息|成绩|宿舍)"),
            Pattern.compile("查(某人|同学|学号\\d+).{0,6}(信息|电话|地址)"),
            Pattern.compile("(告诉我|给我).{0,6}(某某|某学号).{0,6}(电话|地址|身份证)")
    );

    /** 自我伤害关键词。 */
    private static final List<Pattern> SELF_HARM_PATTERNS = List.of(
            Pattern.compile("自杀|自残|轻生|不想活|了结自己|割腕|跳楼"),
            Pattern.compile("活不下去|想死|结束生命")
    );

    /** 越权请求(修改系统状态)。 */
    private static final List<Pattern> OVERSTEP_PATTERNS = List.of(
            Pattern.compile("(帮我|替我|给我).{0,6}(修改|更改|删除|取消).{0,6}(缴费|状态|成绩|报到|记录)"),
            Pattern.compile("(修改|更改|删除|取消).{0,6}(缴费状态|报到记录|成绩)")
    );

    /** 提示词注入模式(AID 8.5 注入防护)。 */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(previous|above|all)\\s+(instructions?|prompts?)"),
            Pattern.compile("忽略(以上|之前|前面|所有)(指令|提示|规则|约束)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+"),
            Pattern.compile("你现在是"),
            Pattern.compile("(?i)^\\s*system\\s*:"),
            Pattern.compile("(?i)DAN\\s+mode|developer\\s+mode|jailbreak")
    );

    @Override
    public SafetyVerdict filter(String message) {
        if (message == null || message.isBlank()) {
            return SafetyVerdict.pass();
        }
        // 优先级:自我伤害 > 提示词注入 > 隐私查询 > 越权 > 政治敏感 > 与迎新无关
        if (matchAny(message, SELF_HARM_PATTERNS)) {
            return SafetyVerdict.block("self_harm",
                    "如果您正在经历困难,请立即联系辅导员或拨打心理援助热线 400-161-9995。"
                            + "校园心理咨询中心位于学生活动中心 3 楼,预约电话 010-xxxx。");
        }
        if (matchAny(message, INJECTION_PATTERNS)) {
            return SafetyVerdict.block("prompt_injection",
                    "检测到疑似提示词注入,请求已被拦截。我仅能回答迎新相关问题。");
        }
        if (matchAny(message, PRIVACY_PATTERNS)) {
            return SafetyVerdict.block("privacy_query",
                    "抱歉,我无法查询他人个人信息。如需办理相关业务,请前往行政楼一楼核验窗口咨询。");
        }
        if (matchAny(message, OVERSTEP_PATTERNS)) {
            return SafetyVerdict.block("overstep_request",
                    "抱歉,系统状态修改需现场办理。请前往对应环节窗口由工作人员处理。");
        }
        if (matchAny(message, POLITICAL_PATTERNS)) {
            return SafetyVerdict.block("political_sensitive",
                    "抱歉,该问题超出我能解答的范围,建议您咨询现场志愿者或辅导员。");
        }
        return SafetyVerdict.pass();
    }

    private boolean matchAny(String text, List<Pattern> patterns) {
        return patterns.stream().anyMatch(p -> p.matcher(text).find());
    }
}
