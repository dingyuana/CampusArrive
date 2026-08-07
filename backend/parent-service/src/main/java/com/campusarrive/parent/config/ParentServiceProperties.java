package com.campusarrive.parent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 家长端服务配置属性。
 *
 * <p>规格来源：FR-03-01 / FR-03-02 —
 * 验证码 6 位数字、5 分钟有效、限频防刷；
 * JWT 30 天有效期、仅关联学生 ID。</p>
 */
@Data
@ConfigurationProperties(prefix = "parent")
public class ParentServiceProperties {

    /** JWT 签名密钥（HS256，≥ 32 字节）。 */
    private String jwtSecret;

    /** JWT 签发方（iss）。 */
    private String jwtIssuer;

    /** JWT 受众（aud）。 */
    private String jwtAudience;

    /** JWT 有效期（秒），默认 30 天。 */
    private long jwtExpirySeconds = 2592000L;

    /** 验证码长度，默认 6 位。 */
    private int codeLength = 6;

    /** 验证码有效期（秒），默认 5 分钟。 */
    private long codeExpirySeconds = 300L;

    /** 验证码最大错误次数，超限锁定。 */
    private int maxErrorCount = 5;

    /** 验证码错误锁定时长（秒），默认 30 分钟。 */
    private long lockDurationSeconds = 1800L;

    /** 限频窗口（秒），默认 60 秒。 */
    private long rateLimitWindowSeconds = 60L;

    /** 限频窗口内最大请求数。 */
    private int rateLimitMaxRequests = 1;
}
