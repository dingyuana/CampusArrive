package com.campusarrive.parent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UT-PAR-001：验证码生成单元测试。
 *
 * <p>规格来源：FR-03-01 — 验证码 6 位数字，随机生成，无规律不可预测。</p>
 */
@DisplayName("UT-PAR-001：验证码生成")
class VerificationCodeServiceTest {

    private final VerificationCodeService service = new VerificationCodeService(
            6, 300, 5, 1800, 60, 1);

    @Nested
    @DisplayName("验证码格式")
    class CodeFormat {

        @Test
        @DisplayName("生成的验证码为 6 位数字")
        void testCodeLength() {
            String code = service.generateCode();
            assertThat(code).hasSize(6);
            assertThat(code).matches("^\\d{6}$");
        }

        @RepeatedTest(20)
        @DisplayName("连续生成 20 次验证码均为 6 位数字")
        void testCodeLengthRepeated() {
            String code = service.generateCode();
            assertThat(code).hasSize(6);
            assertThat(code).matches("^\\d{6}$");
        }
    }

    @Nested
    @DisplayName("验证码随机性")
    class CodeRandomness {

        @Test
        @DisplayName("连续生成 100 个验证码无重复")
        void testCodeUniqueness() {
            Set<String> codes = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                codes.add(service.generateCode());
            }
            // 100 个 6 位随机码应有极高概率互不相同
            assertThat(codes).hasSizeGreaterThan(90);
        }

        @Test
        @DisplayName("两个验证码不相等的概率验证")
        void testTwoCodesDifferent() {
            Set<String> codes = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                codes.add(service.generateCode());
            }
            assertThat(codes).hasSizeGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("验证码存储与校验")
    class CodeStorageAndVerification {

        @Test
        @DisplayName("生成的验证码可通过校验")
        void testGeneratedCodeVerifiesSuccess() {
            String phone = "13812345678";
            String code = service.generateAndStore(phone);

            var result = service.verify(phone, code);
            assertThat(result).isInstanceOf(VerificationCodeService.VerifyResult.Success.class);
        }

        @Test
        @DisplayName("错误验证码校验失败")
        void testWrongCodeFails() {
            String phone = "13812345678";
            service.generateAndStore(phone);

            var result = service.verify(phone, "000000");
            assertThat(result).isInstanceOf(VerificationCodeService.VerifyResult.Wrong.class);
        }

        @Test
        @DisplayName("未生成验证码的手机号校验失败")
        void testNoCodeFails() {
            var result = service.verify("13900000000", "123456");
            assertThat(result).isInstanceOf(VerificationCodeService.VerifyResult.NotFound.class);
        }

        @Test
        @DisplayName("验证成功后验证码被清除")
        void testCodeClearedAfterSuccess() {
            String phone = "13812345678";
            String code = service.generateAndStore(phone);
            service.verify(phone, code);

            // 再次用同一验证码应失败
            var result = service.verify(phone, code);
            assertThat(result).isInstanceOf(VerificationCodeService.VerifyResult.NotFound.class);
        }
    }

    @Nested
    @DisplayName("错误锁定")
    class ErrorLockout {

        @Test
        @DisplayName("连续 5 次错误后锁定")
        void testLockedAfterMaxErrors() {
            String phone = "13812345678";
            service.generateAndStore(phone);

            for (int i = 0; i < 5; i++) {
                service.verify(phone, "000000");
            }

            assertThat(service.isLocked(phone)).isTrue();
        }

        @Test
        @DisplayName("锁定后正确验证码也返回 Locked")
        void testLockedReturnsLockedResult() {
            String phone = "13812345678";
            String code = service.generateAndStore(phone);

            for (int i = 0; i < 5; i++) {
                service.verify(phone, "000000");
            }

            var result = service.verify(phone, code);
            assertThat(result).isInstanceOf(VerificationCodeService.VerifyResult.Locked.class);
        }

        @Test
        @DisplayName("未达最大错误次数不锁定")
        void testNotLockedBeforeMaxErrors() {
            String phone = "13812345678";
            service.generateAndStore(phone);

            for (int i = 0; i < 4; i++) {
                service.verify(phone, "000000");
            }

            assertThat(service.isLocked(phone)).isFalse();
        }

        @Test
        @DisplayName("剩余锁定时间大于 0")
        void testRemainingLockSecondsPositive() {
            String phone = "13812345678";
            service.generateAndStore(phone);

            for (int i = 0; i < 5; i++) {
                service.verify(phone, "000000");
            }

            assertThat(service.getRemainingLockSeconds(phone)).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("限频防刷")
    class RateLimiting {

        @Test
        @DisplayName("首次请求不限频")
        void testFirstRequestNotLimited() {
            assertThat(service.isRateLimited("13812345678")).isFalse();
        }

        @Test
        @DisplayName("同一手机号 60 秒内第二次请求被限频")
        void testSecondRequestRateLimited() {
            String phone = "13812345678";
            service.recordRequest(phone, java.time.Instant.now());

            assertThat(service.isRateLimited(phone)).isTrue();
        }

        @Test
        @DisplayName("不同手机号互不影响限频")
        void testDifferentPhonesNotAffected() {
            service.recordRequest("13812345678", java.time.Instant.now());
            assertThat(service.isRateLimited("13987654321")).isFalse();
        }

        @Test
        @DisplayName("窗口外请求不限频")
        void testRequestOutsideWindowNotLimited() {
            String phone = "13812345678";
            // 记录 90 秒前的请求（超出 60 秒窗口）
            service.recordRequest(phone, java.time.Instant.now().minusSeconds(90));

            assertThat(service.isRateLimited(phone)).isFalse();
        }
    }
}
