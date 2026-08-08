package com.campusarrive.ai.chat.workflow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 节点 3:PII 脱敏。
 *
 * <p>规格来源:AID 8.5 PII 脱敏、FR-05-09(数据不出校)。
 * 对将发送至 DeepSeek API 的查询文本执行 PII 过滤:
 * 身份证号→[ID]、手机号→[PHONE]、学号→[STUDENT_NO]、银行卡号→[CARD]。</p>
 *
 * <p>映射表仅存内存,请求结束即销毁,不落盘、不上传(AID 8.5)。
 * 仅对"即将出校传给 DeepSeek 的文本"脱敏,本地知识库检索不经过此节点(知识库本身已脱敏入库)。</p>
 */
public class PiiMasker {

    /** 18 位身份证号(含校验位)。 */
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
    /** 11 位手机号(1 开头)。 */
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    /** 16-19 位银行卡号(排除 18 位身份证;用词边界避免截取身份证前 16-17 位)。 */
    private static final Pattern BANK_CARD = Pattern.compile("\\b\\d{16,17}\\b|\\b\\d{19}\\b");
    /** 学号(STU 开头 + 数字,或 8 位纯数字学号)。 */
    private static final Pattern STUDENT_NO = Pattern.compile("STU\\d+|\\b\\d{8}\\b");

    /**
     * 对文本执行 PII 脱敏,返回脱敏后文本与映射表。
     *
     * <p>匹配顺序:银行卡(16-19位)→ 身份证(18位)→ 手机号(11位)→ 学号。
     * 银行卡先于身份证,避免 19 位银行卡号被身份证正则截取前 18 位。</p>
     *
     * @param text 原始文本
     * @return 脱敏结果(含脱敏后文本与原值→占位符映射表;映射表请求结束即销毁)
     */
    public MaskResult mask(String text) {
        if (text == null || text.isEmpty()) {
            return new MaskResult(text, Map.of());
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        String masked = text;
        masked = replaceAll(masked, BANK_CARD, "[CARD]", mapping);
        masked = replaceAll(masked, ID_CARD, "[ID]", mapping);
        masked = replaceAll(masked, PHONE, "[PHONE]", mapping);
        masked = replaceAll(masked, STUDENT_NO, "[STUDENT_NO]", mapping);
        return new MaskResult(masked, mapping);
    }

    private String replaceAll(String text, Pattern pattern, String placeholder,
                              Map<String, String> mapping) {
        return pattern.matcher(text).replaceAll(mr -> {
            String original = mr.group();
            mapping.putIfAbsent(original, placeholder);
            return placeholder;
        });
    }

    /** 脱敏结果。 */
    public record MaskResult(String maskedText, Map<String, String> mapping) {
    }
}
