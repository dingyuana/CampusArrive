package com.campusarrive.ai.chat;

/**
 * AI 内容标识(API 5.1.5 content_label 字段)。
 *
 * <p>规格来源:FR-01-14(AI 生成内容显式标识)、FR-01-17(降级模式标识)、FR-05-07。
 * 每条 AI 回复强制带可见标识与来源说明,标识不可由模型自行删除(AID 8.6)。</p>
 *
 * @param isAiGenerated 是否为 AI 生成内容
 * @param labelType     标识类型:ai_content(正常)/ faq_mode(降级模式)
 * @param labelText     面向用户的标识文案
 * @param degraded      是否处于降级模式
 * @param degradeMode   降级模式标识:faq_keyword / null
 */
public record ContentLabel(
        boolean isAiGenerated,
        String labelType,
        String labelText,
        boolean degraded,
        String degradeMode
) {

    /** 正常 AI 生成内容标识。 */
    public static ContentLabel normal() {
        return new ContentLabel(true, "ai_content", "本内容由 AI 生成,仅供参考", false, null);
    }

    /** FAQ 关键词降级模式标识。 */
    public static ContentLabel faqDegraded() {
        return new ContentLabel(true, "faq_mode", "当前为离线降级模式,回复来自 FAQ 匹配", true, "faq_keyword");
    }
}
