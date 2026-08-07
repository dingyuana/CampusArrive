package com.campusarrive.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关配置属性。
 *
 * <p>规格来源：FR-04-01~04 — 集中管理白名单路径、令牌桶限流规则与 JWT 校验参数。
 * 绑定前缀 {@code gateway}，对应 application.yml 中 {@code gateway.*} 配置段。</p>
 */
@Data
@ConfigurationProperties("gateway")
public class GatewayProperties {

    /** JWT 签名密钥（HS256，需 ≥ 256 bit / 32 字节）。 */
    private String jwtSecret;

    /** JWT 期望签发方（iss），固定 freshman-checkin-system。 */
    private String jwtIssuer = "freshman-checkin-system";

    /** 网关接受的 JWT 受众（aud）集合，如 student-miniapp、parent-h5、admin-web。 */
    private List<String> jwtAudiences = new ArrayList<>();

    /** 鉴权白名单路径（Ant 风格），命中即跳过 JWT 校验。 */
    private List<String> whitelist = new ArrayList<>();

    /** 令牌桶限流规则列表，按路径前缀匹配。 */
    private List<RateLimitRule> rateLimit = new ArrayList<>();

    /**
     * 令牌桶限流规则。
     */
    @Data
    public static class RateLimitRule {

        /** 路径前缀，如 /api/v1/ai。 */
        private String pathPrefix;

        /** 桶容量（最大令牌数）。 */
        private int bucketSize = 10;

        /** 令牌补充速率（次/分钟）。 */
        private long refillRatePerMinute = 600;
    }
}
