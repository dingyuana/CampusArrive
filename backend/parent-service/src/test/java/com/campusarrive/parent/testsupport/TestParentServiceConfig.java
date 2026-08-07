package com.campusarrive.parent.testsupport;

import com.campusarrive.parent.config.ParentServiceProperties;
import com.campusarrive.parent.service.VerificationCodeService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 测试配置：提供可控的验证码服务（固定验证码 "123456"）。
 *
 * <p>契约测试需要知道验证码才能调用绑定接口，而生产环境的验证码不通过 API 返回。
 * 本配置仅覆盖 VerificationCodeService，使用固定验证码便于测试断言。
 * 其余 Bean（ParentJwtService / PreRegistrationStore / TokenRevocationStore）
 * 由 {@link com.campusarrive.parent.config.ParentServiceConfig} 提供，无需重复定义。</p>
 */
@TestConfiguration
public class TestParentServiceConfig {

    @Bean
    @Primary
    public VerificationCodeService verificationCodeService(ParentServiceProperties properties) {
        return new TestableVerificationCodeService(
                properties.getCodeLength(),
                properties.getCodeExpirySeconds(),
                properties.getMaxErrorCount(),
                properties.getLockDurationSeconds(),
                properties.getRateLimitWindowSeconds(),
                properties.getRateLimitMaxRequests());
    }

    /**
     * 可测试验证码服务：生成固定验证码 "123456"。
     */
    public static class TestableVerificationCodeService extends VerificationCodeService {

        private static final String FIXED_CODE = "123456";

        public TestableVerificationCodeService(int codeLength, long codeExpirySeconds,
                                               int maxErrorCount, long lockDurationSeconds,
                                               long rateLimitWindowSeconds, int rateLimitMaxRequests) {
            super(codeLength, codeExpirySeconds, maxErrorCount, lockDurationSeconds,
                    rateLimitWindowSeconds, rateLimitMaxRequests);
        }

        @Override
        public String generateCode() {
            return FIXED_CODE;
        }

        /**
         * 重置所有限频与错误状态（测试间隔离用）。
         */
        public void reset() {
            clearAll();
        }
    }
}
