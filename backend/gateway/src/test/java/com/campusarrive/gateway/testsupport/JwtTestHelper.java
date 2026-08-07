package com.campusarrive.gateway.testsupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 测试令牌工厂。
 *
 * <p>规格来源：UT-MW-001 / CT-MW-002 — 测试需要各类合法/非法 JWT 令牌。
 * 密钥与签发方须与 application-test.yml 中 {@code gateway.jwt-*} 保持一致。</p>
 */
public final class JwtTestHelper {

    /** 测试密钥（与 application-test.yml 的 gateway.jwt-secret 一致，≥ 32 字节）。 */
    public static final String SECRET = "campus-arrive-jwt-secret-key-2026-hs256-signing-key";
    /** 测试签发方。 */
    public static final String ISSUER = "freshman-checkin-system";
    /** 测试受众。 */
    public static final String AUDIENCE = "student-miniapp";
    /** 密钥 ID（kid）。 */
    public static final String KID = "fcs-jwt-key-2026";

    private static final long ONE_HOUR = 3_600_000L;

    private final SecretKey key;
    private final SecretKey wrongKey;

    public JwtTestHelper() {
        this.key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        // 错误密钥（长度足够），用于生成签名错误的令牌
        this.wrongKey = Keys.hmacShaKeyFor(("wrong-key-" + SECRET).getBytes(StandardCharsets.UTF_8));
    }

    /** 合法令牌（iss/aud/role/student_id/scope 齐全，1 小时后过期）。 */
    public String validToken() {
        return build(ISSUER, AUDIENCE, now(), now() + ONE_HOUR, key);
    }

    /** 已过期令牌（1 小时前过期）。 */
    public String expiredToken() {
        return build(ISSUER, AUDIENCE, now() - 2 * ONE_HOUR, now() - ONE_HOUR, key);
    }

    /** 签名错误令牌（用错误密钥签发，网关用正确密钥验签会失败）。 */
    public String invalidSignatureToken() {
        return build(ISSUER, AUDIENCE, now(), now() + ONE_HOUR, wrongKey);
    }

    /** 格式错误令牌（非合法 JWT 结构）。 */
    public String malformedToken() {
        return "not.a.valid.jwt.token";
    }

    /** 指定签发方的令牌（用于测试 iss 不匹配）。 */
    public String tokenWithIssuer(String issuer) {
        return build(issuer, AUDIENCE, now(), now() + ONE_HOUR, key);
    }

    /** 指定受众的令牌（用于测试 aud 不匹配）。 */
    public String tokenWithAudience(String audience) {
        return build(ISSUER, audience, now(), now() + ONE_HOUR, key);
    }

    /** 未生效令牌（nbf 在未来 1 小时）。 */
    public String tokenWithNotBeforeFuture() {
        return build(ISSUER, AUDIENCE, now() + ONE_HOUR, now() + 2 * ONE_HOUR, key);
    }

    private String build(String issuer, String audience, long notBeforeMillis, long expirationMillis, SecretKey signKey) {
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer(issuer)
                .subject("student:STU20260001")
                .audience().add(audience).and()
                .claim("role", "student")
                .claim("student_id", "STU20260001")
                .claim("scope", "student:full")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .notBefore(new Date(notBeforeMillis))
                .expiration(new Date(expirationMillis))
                .signWith(signKey, Jwts.SIG.HS256)
                .compact();
    }

    private long now() {
        return System.currentTimeMillis();
    }
}
