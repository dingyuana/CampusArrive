package com.campusarrive.parent.config;

import com.campusarrive.parent.service.ParentJwtService;
import com.campusarrive.parent.service.PreRegistrationStore;
import com.campusarrive.parent.service.ProgressStore;
import com.campusarrive.parent.service.TokenRevocationStore;
import com.campusarrive.parent.service.VerificationCodeService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.List;

/**
 * 家长端服务 Bean 配置。
 *
 * <p>规格来源：FR-03-01 / FR-03-02 —
 * 注册验证码服务、JWT 服务、预登记存储、令牌吊销存储。</p>
 */
@Configuration
@EnableConfigurationProperties(ParentServiceProperties.class)
public class ParentServiceConfig {

    @Bean
    public ParentJwtService parentJwtService(ParentServiceProperties properties) {
        return new ParentJwtService(
                properties.getJwtSecret(),
                properties.getJwtIssuer(),
                properties.getJwtAudience(),
                properties.getJwtExpirySeconds());
    }

    @Bean
    public VerificationCodeService verificationCodeService(ParentServiceProperties properties) {
        return new VerificationCodeService(
                properties.getCodeLength(),
                properties.getCodeExpirySeconds(),
                properties.getMaxErrorCount(),
                properties.getLockDurationSeconds(),
                properties.getRateLimitWindowSeconds(),
                properties.getRateLimitMaxRequests());
    }

    @Bean
    public PreRegistrationStore preRegistrationStore() {
        PreRegistrationStore store = new PreRegistrationStore();
        // 测试用预登记数据（生产环境从数据库加载）
        store.register("13812345678", "STU20260001", "张三丰");
        store.register("13987654321", "STU20260002", "李四");
        return store;
    }

    @Bean
    public TokenRevocationStore tokenRevocationStore() {
        return new TokenRevocationStore();
    }

    @Bean
    public ProgressStore progressStore() {
        ProgressStore store = new ProgressStore();
        // 测试用进度数据（生产环境从数据库加载）
        // 学生 1：已完成签到和缴费，待资格核验和报到完成
        store.register("STU20260001", "张三丰",
                List.of("身份证复印件", "体检报告", "一寸照片"));
        Instant now = Instant.now();
        store.markStepCompleted("STU20260001",
                ProgressStore.CheckinStep.CHECKIN_SUCCESS, now.minusSeconds(3600));
        store.markStepCompleted("STU20260001",
                ProgressStore.CheckinStep.PAYMENT_COMPLETED, now.minusSeconds(1800));

        // 学生 2：全部完成
        store.register("STU20260002", "李四", List.of());
        store.markStepCompleted("STU20260002",
                ProgressStore.CheckinStep.CHECKIN_SUCCESS, now.minusSeconds(7200));
        store.markStepCompleted("STU20260002",
                ProgressStore.CheckinStep.PAYMENT_COMPLETED, now.minusSeconds(5400));
        store.markStepCompleted("STU20260002",
                ProgressStore.CheckinStep.VERIFIED_SUCCESS, now.minusSeconds(3600));
        store.markStepCompleted("STU20260002",
                ProgressStore.CheckinStep.CHECKIN_COMPLETED, now.minusSeconds(1800));

        return store;
    }
}
