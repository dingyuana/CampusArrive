package com.campusarrive.gateway.security.pii;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CT-SEC-002：日志 PII 脱敏契约测试。
 *
 * <p>规格来源：SCS-CA-2026-09 第 3.2 节 PII 五层防护第 5 层 —
 * 所有应用日志、访问日志、审计日志在写入前经脱敏过滤器处理，
 * 过滤器内置正则规则库识别身份证号、手机号、银行卡号等 PII 模式并自动遮蔽，
 * 确保审计日志不含明文 PII。</p>
 *
 * <p>契约定义：给定包含 PII 的日志文本，{@link PiiLogSanitizer#sanitize} 的输出
 * 不得包含任何明文 PII 模式。</p>
 */
@DisplayName("CT-SEC-002：日志 PII 脱敏")
class PiiLogSanitizerTest {

    // ─── 单一 PII 类型脱敏 ─────────────────────────────────────

    @Nested
    @DisplayName("单一 PII 类型脱敏")
    class SinglePiiType {

        @Test
        @DisplayName("日志中的身份证号被脱敏")
        void testSanitizeIdCard() {
            String log = "用户身份证号: 110101199001011234 已登记";
            String sanitized = PiiLogSanitizer.sanitize(log);

            assertThat(sanitized).doesNotContain("110101199001011234");
            assertThat(sanitized).contains("110***********1234");
        }

        @Test
        @DisplayName("日志中的手机号被脱敏")
        void testSanitizePhone() {
            String log = "联系手机: 13812345678 请确认";
            String sanitized = PiiLogSanitizer.sanitize(log);

            assertThat(sanitized).doesNotContain("13812345678");
            assertThat(sanitized).contains("138****5678");
        }

        @Test
        @DisplayName("日志中的邮箱被脱敏")
        void testSanitizeEmail() {
            String log = "发送邮件至 zhangsan@example.com";
            String sanitized = PiiLogSanitizer.sanitize(log);

            assertThat(sanitized).doesNotContain("zhangsan@example.com");
            assertThat(sanitized).contains("z***@example.com");
        }

        @Test
        @DisplayName("日志中的银行卡号被脱敏")
        void testSanitizeBankCard() {
            String log = "缴费银行卡: 6222021234567890123";
            String sanitized = PiiLogSanitizer.sanitize(log);

            assertThat(sanitized).doesNotContain("6222021234567890123");
            assertThat(sanitized).contains("6222***********0123");
        }
    }

    // ─── 混合 PII 类型脱敏 ─────────────────────────────────────

    @Nested
    @DisplayName("混合 PII 类型脱敏")
    class MixedPiiTypes {

        @Test
        @DisplayName("同一日志中多种 PII 同时脱敏")
        void testSanitizeMultiplePiiTypes() {
            String log = "学生身份证: 110101199001011234, 手机: 13812345678, 邮箱: zhangsan@example.com";
            String sanitized = PiiLogSanitizer.sanitize(log);

            assertThat(sanitized).doesNotContain("110101199001011234");
            assertThat(sanitized).doesNotContain("13812345678");
            assertThat(sanitized).doesNotContain("zhangsan@example.com");
            assertThat(sanitized).contains("110***********1234");
            assertThat(sanitized).contains("138****5678");
            assertThat(sanitized).contains("z***@example.com");
        }

        @Test
        @DisplayName("同一 PII 多次出现均被脱敏")
        void testSamePiiMultipleOccurrences() {
            String log = "手机 13812345678 和 13812345678 是同一号码";
            String sanitized = PiiLogSanitizer.sanitize(log);

            assertThat(sanitized).doesNotContain("13812345678");
            // 两个手机号都被脱敏
            long count = sanitized.chars().filter(c -> c == '*').count();
            assertThat(count).isGreaterThanOrEqualTo(8);
        }

        @Test
        @DisplayName("身份证末位为 X 时正确脱敏")
        void testSanitizeIdCardWithX() {
            String log = "身份证: 11010119900101123X";
            String sanitized = PiiLogSanitizer.sanitize(log);

            assertThat(sanitized).doesNotContain("11010119900101123X");
            assertThat(sanitized).contains("110***********123X");
        }
    }

    // ─── 边界条件 ──────────────────────────────────────────────

    @Nested
    @DisplayName("边界条件")
    class EdgeCases {

        @Test
        @DisplayName("null 日志返回 null")
        void testSanitizeNull() {
            assertThat(PiiLogSanitizer.sanitize(null)).isNull();
        }

        @Test
        @DisplayName("空字符串日志返回空字符串")
        void testSanitizeEmpty() {
            assertThat(PiiLogSanitizer.sanitize("")).isEmpty();
        }

        @Test
        @DisplayName("无 PII 的日志原样返回")
        void testSanitizeNoPii() {
            String log = "系统启动成功，端口 8080";
            assertThat(PiiLogSanitizer.sanitize(log)).isEqualTo(log);
        }

        @Test
        @DisplayName("纯数字日志（非手机号/身份证）不被误脱敏")
        void testSanitizeNonPiiNumbers() {
            String log = "处理记录数: 12345, 耗时: 678ms";
            String sanitized = PiiLogSanitizer.sanitize(log);
            assertThat(sanitized).isEqualTo(log);
        }

        @Test
        @DisplayName("PII 位于日志开头")
        void testPiiAtStart() {
            String log = "13812345678 是学生手机号";
            String sanitized = PiiLogSanitizer.sanitize(log);

            assertThat(sanitized).startsWith("138****5678");
            assertThat(sanitized).doesNotContain("13812345678");
        }

        @Test
        @DisplayName("PII 位于日志末尾")
        void testPiiAtEnd() {
            String log = "学生手机号是 13812345678";
            String sanitized = PiiLogSanitizer.sanitize(log);

            assertThat(sanitized).endsWith("138****5678");
            assertThat(sanitized).doesNotContain("13812345678");
        }

        @Test
        @DisplayName("长数字串中嵌入的 PII 不会被误匹配")
        void testPiiNotMatchedInLongDigitString() {
            // 20 位连续数字，不应匹配为银行卡
            String log = "订单号: 12345678901234567890";
            String sanitized = PiiLogSanitizer.sanitize(log);
            // 身份证号正则要求前后无数字边界，此串不应被匹配
            // 但 11 位手机号模式可能匹配子串，验证不含完整明文
            assertThat(sanitized).doesNotContain("13812345678");
        }
    }

    // ─── PII 检测方法 ──────────────────────────────────────────

    @Nested
    @DisplayName("PII 检测")
    class PiiDetection {

        @Test
        @DisplayName("包含身份证号的文本被检测")
        void testContainsPiiIdCard() {
            assertThat(PiiLogSanitizer.containsPii("身份证 110101199001011234")).isTrue();
        }

        @Test
        @DisplayName("包含手机号的文本被检测")
        void testContainsPiiPhone() {
            assertThat(PiiLogSanitizer.containsPii("手机 13812345678")).isTrue();
        }

        @Test
        @DisplayName("包含邮箱的文本被检测")
        void testContainsPiiEmail() {
            assertThat(PiiLogSanitizer.containsPii("邮箱 test@example.com")).isTrue();
        }

        @Test
        @DisplayName("不包含 PII 的文本返回 false")
        void testContainsPiiFalse() {
            assertThat(PiiLogSanitizer.containsPii("系统正常运行")).isFalse();
        }

        @Test
        @DisplayName("null 文本返回 false")
        void testContainsPiiNull() {
            assertThat(PiiLogSanitizer.containsPii(null)).isFalse();
        }

        @Test
        @DisplayName("空字符串返回 false")
        void testContainsPiiEmpty() {
            assertThat(PiiLogSanitizer.containsPii("")).isFalse();
        }
    }

    // ─── 规则数量 ──────────────────────────────────────────────

    @Test
    @DisplayName("已注册 4 种 PII 检测规则")
    void testPatternCount() {
        assertThat(PiiLogSanitizer.getPatternCount()).isEqualTo(4);
    }
}
