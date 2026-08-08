package com.campusarrive.ai.chat;

/**
 * token 用量统计(API 5.1.5 tokens 字段)。
 *
 * <p>对应 ai_chat_log 表 token_input / token_output 字段,
 * 用于成本计量与三级成本预警(AID 6.5)。</p>
 *
 * @param prompt     输入 token 数
 * @param completion 输出 token 数
 * @param total      总 token 数
 */
public record TokenUsage(int prompt, int completion, int total) {

    /** 全零用量(降级/拒答场景)。 */
    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0);
    }
}
