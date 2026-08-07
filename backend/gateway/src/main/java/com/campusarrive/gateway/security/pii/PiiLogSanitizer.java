package com.campusarrive.gateway.security.pii;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 日志 PII 脱敏处理器。
 *
 * <p>规格来源：SCS-CA-2026-09 第 3.2 节 PII 五层防护第 5 层 —
 * 所有应用日志、访问日志、审计日志在写入前经脱敏过滤器处理，
 * 过滤器内置正则规则库识别身份证号、手机号、银行卡号等 PII 模式并自动遮蔽，
 * 确保审计日志不含明文 PII，满足等保 2.0 审计要求。</p>
 *
 * <p>本类使用正则表达式扫描日志文本中的 PII 模式并替换为脱敏值，
 * 支持身份证号、手机号、邮箱、银行卡号的自动识别。</p>
 */
public final class PiiLogSanitizer {

    private PiiLogSanitizer() {
    }

    /**
     * PII 检测规则定义。
     *
     * @param pattern  正则表达式
     * @param type     PII 类型
     */
    private record PiiPattern(Pattern pattern, PiiType type) {
    }

    /** 身份证号正则：18 位数字（最后一位可为 X），前后需有边界。 */
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("(?<![0-9])([1-9]\\d{5})(\\d{8})(\\d{3})([0-9Xx])(?![0-9])");

    /** 手机号正则：1 开头的 11 位数字，前后需有边界。 */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<![0-9])(1[3-9]\\d{9})(?![0-9])");

    /** 邮箱正则：标准邮箱格式。 */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");

    /** 银行卡号正则：16-19 位数字，前后需有边界。 */
    private static final Pattern BANK_CARD_PATTERN =
            Pattern.compile("(?<![0-9])([622]\\d{14,18})(?![0-9])");

    /** 按优先级排序的 PII 检测规则列表。 */
    private static final List<PiiPattern> PATTERNS = List.of(
            new PiiPattern(ID_CARD_PATTERN, PiiType.ID_CARD),
            new PiiPattern(PHONE_PATTERN, PiiType.PHONE),
            new PiiPattern(EMAIL_PATTERN, PiiType.EMAIL),
            new PiiPattern(BANK_CARD_PATTERN, PiiType.BANK_CARD)
    );

    /**
     * 对日志文本执行 PII 脱敏。
     *
     * <p>依次使用身份证号、手机号、邮箱、银行卡号正则扫描文本，
     * 匹配到的 PII 自动替换为脱敏值。多次匹配的同一 PII 均被脱敏。</p>
     *
     * @param message 原始日志文本
     * @return 脱敏后的日志文本；入参为 {@code null} 时返回 {@code null}
     */
    public static String sanitize(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String result = message;
        for (PiiPattern piiPattern : PATTERNS) {
            result = piiPattern.pattern().matcher(result)
                    .replaceAll(match -> PiiMasker.mask(match.group(), piiPattern.type()));
        }
        return result;
    }

    /**
     * 检查日志文本是否包含明文 PII。
     *
     * @param message 日志文本
     * @return {@code true} 表示包含至少一种明文 PII
     */
    public static boolean containsPii(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        for (PiiPattern piiPattern : PATTERNS) {
            if (piiPattern.pattern().matcher(message).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取已注册的 PII 检测规则数量。
     *
     * @return 规则数量
     */
    public static int getPatternCount() {
        return PATTERNS.size();
    }
}
