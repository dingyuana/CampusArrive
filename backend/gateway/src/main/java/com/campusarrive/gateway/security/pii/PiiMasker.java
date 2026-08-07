package com.campusarrive.gateway.security.pii;

import java.util.Set;

/**
 * PII 脱敏执行器。
 *
 * <p>规格来源：SCS-CA-2026-09 第 3.2 节 PII 五层防护 —
 * 第 4 层展示层：API 响应中间件自动脱敏，身份证号显示前 3 后 4
 * （如 {@code 110***********1234}），手机号显示前 3 后 4
 * （如 {@code 138****5678}），家庭住址仅显示到区县级。
 * 第 5 层日志层：日志写入前脱敏过滤，审计日志不含明文 PII。</p>
 *
 * <p>本类为纯函数工具类，所有方法均无副作用，可直接用于
 * 展示层响应过滤器与日志层脱敏过滤器。</p>
 */
public final class PiiMasker {

    private PiiMasker() {
    }

    /** 需要在地址中识别的区县级行政区划后缀。 */
    private static final Set<String> DISTRICT_SUFFIXES = Set.of("区", "县", "市", "旗", "自治县", "自治旗");

    /**
     * 按指定 PII 类型执行脱敏。
     *
     * @param value  原始值
     * @param type   PII 类型
     * @return 脱敏后的值；入参为 {@code null} 或空白时原样返回
     */
    public static String mask(String value, PiiType type) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return switch (type) {
            case ID_CARD -> maskIdCard(value);
            case PHONE -> maskPhone(value);
            case NAME -> maskName(value);
            case EMAIL -> maskEmail(value);
            case ADDRESS -> maskAddress(value);
            case BANK_CARD -> maskBankCard(value);
        };
    }

    /**
     * 身份证号脱敏：保留前 3 后 4，中间以 {@code *} 填充。
     *
     * <p>示例：{@code 110101199001011234} → {@code 110***********1234}</p>
     *
     * @param idCard 18 位身份证号
     * @return 脱敏后的身份证号
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 9) {
            return maskShort(idCard);
        }
        return maskKeepEnds(idCard, 3, 4);
    }

    /**
     * 手机号脱敏：保留前 3 后 4，中间以 {@code *} 填充。
     *
     * <p>示例：{@code 13812345678} → {@code 138****5678}</p>
     *
     * @param phone 11 位手机号
     * @return 脱敏后的手机号
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 9) {
            return maskShort(phone);
        }
        return maskKeepEnds(phone, 3, 4);
    }

    /**
     * 姓名脱敏：2 字保留首字掩码末字；3 字及以上保留首尾，中间掩码。
     *
     * <p>示例：{@code 张三} → {@code 张*}；{@code 张三丰} → {@code 张*丰}；
     * {@code 欧阳修文} → {@code 欧**文}</p>
     *
     * @param name 中文姓名
     * @return 脱敏后的姓名
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        int len = name.length();
        if (len == 1) {
            return "*";
        }
        if (len == 2) {
            return name.charAt(0) + "*";
        }
        // 3 字及以上：保留首尾，中间全掩码
        String first = String.valueOf(name.charAt(0));
        String last = String.valueOf(name.charAt(len - 1));
        String middle = "*".repeat(len - 2);
        return first + middle + last;
    }

    /**
     * 邮箱脱敏：本地名仅保留首字符，其余以 {@code ***} 替代，域名完整保留。
     *
     * <p>示例：{@code zhangsan@example.com} → {@code z***@example.com}</p>
     *
     * @param email 邮箱地址
     * @return 脱敏后的邮箱地址
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return maskShort(email);
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() == 1) {
            return "*" + domain;
        }
        return localPart.charAt(0) + "***" + domain;
    }

    /**
     * 家庭住址脱敏：仅保留到区县级行政区划，后续详细地址以 {@code ***} 替代。
     *
     * <p>示例：{@code 北京市海淀区中关村南大街5号} → {@code 北京市海淀区***}</p>
     *
     * @param address 完整家庭住址
     * @return 脱敏后的住址
     */
    public static String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return address;
        }
        int cutIndex = -1;
        for (String suffix : DISTRICT_SUFFIXES) {
            int idx = address.indexOf(suffix);
            if (idx >= 0) {
                int afterSuffix = idx + suffix.length();
                if (afterSuffix > cutIndex) {
                    cutIndex = afterSuffix;
                }
            }
        }
        if (cutIndex <= 0 || cutIndex >= address.length()) {
            // 未找到行政区划后缀或全部匹配，保留前半部分
            return address.length() > 6
                    ? address.substring(0, 6) + "***"
                    : maskShort(address);
        }
        return address.substring(0, cutIndex) + "***";
    }

    /**
     * 银行卡号脱敏：保留前 4 后 4，中间以 {@code *} 填充。
     *
     * <p>示例：{@code 6222021234567890123} → {@code 6222***********0123}</p>
     *
     * @param bankCard 银行卡号
     * @return 脱敏后的银行卡号
     */
    public static String maskBankCard(String bankCard) {
        if (bankCard == null || bankCard.length() < 9) {
            return maskShort(bankCard);
        }
        return maskKeepEnds(bankCard, 4, 4);
    }

    // ─── 内部工具方法 ───────────────────────────────────────────

    /**
     * 保留首尾指定长度，中间填充 {@code *}。
     */
    private static String maskKeepEnds(String value, int prefixLen, int suffixLen) {
        int len = value.length();
        if (len <= prefixLen + suffixLen) {
            return "*".repeat(len);
        }
        String prefix = value.substring(0, prefixLen);
        String suffix = value.substring(len - suffixLen);
        int maskLen = len - prefixLen - suffixLen;
        return prefix + "*".repeat(maskLen) + suffix;
    }

    /**
     * 过短值兜底：全部掩码。
     */
    private static String maskShort(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return "*".repeat(value.length());
    }
}
